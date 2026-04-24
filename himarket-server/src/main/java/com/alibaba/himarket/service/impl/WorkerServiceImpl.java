package com.alibaba.himarket.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.dto.result.cli.CliDownloadInfo;
import com.alibaba.himarket.dto.result.common.FileContentResult;
import com.alibaba.himarket.dto.result.common.FileTreeNode;
import com.alibaba.himarket.dto.result.common.ImportResult;
import com.alibaba.himarket.dto.result.common.VersionResult;
import com.alibaba.himarket.entity.NacosInstance;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.service.WorkerService;
import com.alibaba.himarket.support.enums.ProductStatus;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.product.ProductFeature;
import com.alibaba.himarket.support.product.WorkerConfig;
import com.alibaba.nacos.api.ai.model.agentspecs.*;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.maintainer.client.ai.AgentSpecMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class WorkerServiceImpl implements WorkerService {

    private static final long MAX_ZIP_SIZE = 10 * 1024 * 1024;

    private final NacosService nacosService;

    private final ProductRepository productRepository;
    private final ContextHolder contextHolder;

    @Override
    public void uploadPackage(String productId, MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_ZIP_SIZE) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "ZIP file cannot be empty or exceed 10MB");
        }

        Product product = findProduct(productId);
        AgentSpecRef ref = getAgentSpecRef(productId, true);

        byte[] zipBytes = file.getBytes();

        WorkerConfig config = product.getFeature().getWorkerConfig();

        if (StrUtil.isBlank(ref.getAgentSpecName())) {
            // First upload: use overwrite mode in case Nacos already has an agent spec with the
            // same name
            String agentSpecName =
                    execute(
                            ref.getNacosId(),
                            s -> s.uploadAgentSpecFromZip(ref.getNamespace(), zipBytes, true));
            log.info("Uploaded new AgentSpec draft: {}", agentSpecName);
            config.setAgentSpecName(agentSpecName);
        } else {
            // Subsequent upload: use overwrite mode to bypass reviewing version blocking
            execute(
                    ref.getNacosId(),
                    s -> s.uploadAgentSpecFromZip(ref.getNamespace(), zipBytes, true));
            log.info("Uploaded (overwrite) for AgentSpec {}", ref.getAgentSpecName());
        }

        productRepository.save(product);

        // Set scope to PUBLIC immediately after creation
        setScopeToPublic(ref);
    }

    @Override
    public void deleteAgentSpec(String productId) {
        Product product = findProduct(productId);
        AgentSpecRef ref = getAgentSpecRef(productId, false);
        if (ref == null || StrUtil.isBlank(ref.getAgentSpecName())) {
            return;
        }
        execute(
                ref.getNacosId(),
                s -> {
                    s.deleteAgentSpec(ref.getNamespace(), ref.getAgentSpecName());
                    return null;
                });
        WorkerConfig config = product.getFeature().getWorkerConfig();
        config.setAgentSpecName(null);
        productRepository.save(product);
    }

    @Override
    public List<FileTreeNode> getFileTree(String productId, String version) {
        AgentSpecRef ref = getAgentSpecRef(productId, false);
        if (ref == null || StrUtil.isBlank(ref.getAgentSpecName())) {
            return Collections.emptyList();
        }

        version = validateAndResolveVersion(productId, version);

        try {
            AgentSpec agentSpec = fetchAgentSpec(ref, version);
            return buildFileTree(agentSpec);
        } catch (Exception e) {
            log.warn("Failed to fetch file tree for AgentSpec {}", ref.getAgentSpecName());
            return Collections.emptyList();
        }
    }

    @Override
    public FileContentResult getFileContent(String productId, String path, String version) {
        version = validateAndResolveVersion(productId, version);

        AgentSpecRef ref = getAgentSpecRef(productId, true);

        return getFileContent(ref, path, version);
    }

    @Override
    public List<VersionResult> listVersions(String productId) {
        Product product = findProduct(productId);
        AgentSpecRef ref = getAgentSpecRef(productId, false);

        if (ref == null || StrUtil.isBlank(ref.getAgentSpecName())) {
            return Collections.emptyList();
        }

        AgentSpecMeta meta;
        try {
            meta =
                    execute(
                            ref.getNacosId(),
                            s ->
                                    s.getAgentSpecAdminDetail(
                                            ref.getNamespace(), ref.getAgentSpecName()));
        } catch (Exception e) {
            log.warn(
                    "AgentSpec {} not found in Nacos, returning empty versions",
                    ref.getAgentSpecName());
            return Collections.emptyList();
        }

        if (meta == null || CollUtil.isEmpty(meta.getVersions())) {
            return Collections.emptyList();
        }

        String latestLabel = null;
        if (meta.getLabels() != null) {
            latestLabel = meta.getLabels().get("latest");
        }
        final String latestVersion = latestLabel;

        List<VersionResult> results =
                meta.getVersions().stream()
                        .sorted(
                                Comparator.comparing(
                                                AgentSpecMeta.AgentSpecVersionSummary
                                                        ::getCreateTime,
                                                Comparator.nullsLast(Long::compareTo))
                                        .reversed())
                        .map(
                                v ->
                                        VersionResult.builder()
                                                .version(v.getVersion())
                                                .status(
                                                        VersionResult.resolveStatus(
                                                                v.getStatus(),
                                                                v.getPublishPipelineInfo()))
                                                .updateTime(v.getUpdateTime())
                                                .downloadCount(v.getDownloadCount())
                                                .publishPipelineInfo(v.getPublishPipelineInfo())
                                                .isLatest(v.getVersion().equals(latestVersion))
                                                .build())
                        .toList();

        // Sync Product status based on whether any online version exists
        boolean hasOnline = results.stream().anyMatch(v -> "online".equals(v.getStatus()));
        ProductStatus current = product.getStatus();
        ProductStatus targetStatus;
        if (hasOnline) {
            targetStatus = (current != ProductStatus.PUBLISHED) ? ProductStatus.READY : current;
        } else {
            targetStatus =
                    (current == ProductStatus.PUBLISHED)
                            ? ProductStatus.READY
                            : ProductStatus.PENDING;
        }

        if (current != targetStatus) {
            product.setStatus(targetStatus);
            productRepository.save(product);
        }

        // Non-admin users can only see online versions
        if (!contextHolder.isAdministrator()) {
            results =
                    results.stream()
                            .filter(v -> "online".equals(v.getStatus()))
                            .collect(Collectors.toList());
        }

        return results;
    }

    @Override
    public void publishVersion(String productId, String version) {
        AgentSpecRef ref = getAgentSpecRef(productId, true);
        if (StrUtil.isBlank(ref.getAgentSpecName())) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND, Resources.AGENT_SPEC, ref.getAgentSpecName());
        }

        String submittedVersion =
                execute(
                        ref.getNacosId(),
                        s -> s.submit(ref.getNamespace(), ref.getAgentSpecName(), version));
        log.info("Submitted AgentSpec {}, version {}", ref.getAgentSpecName(), submittedVersion);
    }

    @Override
    public void downloadPackage(String productId, String version, HttpServletResponse response)
            throws IOException {
        AgentSpecRef ref = getAgentSpecRef(productId, true);

        // 先调用 Nacos HTTP API 下载，让 Nacos 增加下载计数
        downloadFromNacos(ref, version, response);
    }

    /**
     * 通过调用 Nacos HTTP API 下载 Worker ZIP 包，使 Nacos 自动增加下载计数
     * API: GET /v3/console/ai/agentspecs/version/download?namespaceId=xxx&agentSpecName=xxx&version=xxx
     */
    private void downloadFromNacos(AgentSpecRef ref, String version, HttpServletResponse response)
            throws IOException {
        try {
            NacosInstance nacosInstance = nacosService.findNacosInstanceById(ref.getNacosId());
            // 优先使用展示地址，不存在则使用 serverUrl
            String nacosBaseUrl =
                    StrUtil.isNotBlank(nacosInstance.getDisplayServerUrl())
                            ? nacosInstance.getDisplayServerUrl()
                            : nacosInstance.getServerUrl();

            // 构建 Nacos 下载 URL:
            // /v3/console/ai/agentspecs/version/download?namespaceId=xxx&agentSpecName=xxx&version=xxx
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append(nacosBaseUrl);
            if (!nacosBaseUrl.endsWith("/")) {
                urlBuilder.append("/");
            }
            urlBuilder.append("v3/console/ai/agentspecs/version/download?");
            urlBuilder.append("namespaceId=").append(ref.getNamespace());
            urlBuilder
                    .append("&agentSpecName=")
                    .append(
                            java.net.URLEncoder.encode(
                                    ref.getAgentSpecName(), StandardCharsets.UTF_8.name()));
            if (StrUtil.isNotBlank(version)) {
                urlBuilder
                        .append("&version=")
                        .append(java.net.URLEncoder.encode(version, StandardCharsets.UTF_8.name()));
            }

            // 添加认证参数（如果 Nacos 有用户名密码）
            if (StrUtil.isNotBlank(nacosInstance.getUsername())
                    && StrUtil.isNotBlank(nacosInstance.getPassword())) {
                urlBuilder
                        .append("&username=")
                        .append(
                                java.net.URLEncoder.encode(
                                        nacosInstance.getUsername(),
                                        StandardCharsets.UTF_8.name()));
                urlBuilder
                        .append("&password=")
                        .append(
                                java.net.URLEncoder.encode(
                                        nacosInstance.getPassword(),
                                        StandardCharsets.UTF_8.name()));
            }

            String downloadUrl = urlBuilder.toString();
            // 日志中密码脱敏
            String loggedUrl = downloadUrl.replaceAll("password=[^&]+", "password=***");
            log.info("Calling Nacos download API: {}", loggedUrl);

            // 创建 HTTP 连接
            java.net.URL url = new java.net.URL(downloadUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            int responseCode = conn.getResponseCode();
            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                // 设置响应头
                response.setContentType("application/zip");
                String encodedName =
                        java.net.URLEncoder.encode(
                                        ref.getAgentSpecName() + ".zip", StandardCharsets.UTF_8)
                                .replace("+", "%20");
                response.setHeader(
                        "Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);

                // 流式传输 ZIP 文件
                try (var input = conn.getInputStream();
                        var output = response.getOutputStream()) {
                    input.transferTo(output);
                }
                log.info("Downloaded worker {} from Nacos successfully", ref.getAgentSpecName());
            } else {
                log.warn("Nacos download API returned non-OK status: {}", responseCode);
                // 降级为本地生成 ZIP
                fallbackToLocalDownload(ref, version, response);
            }
        } catch (Exception e) {
            log.warn("Failed to download from Nacos, fallback to local generation", e);
            // 降级为本地生成 ZIP
            fallbackToLocalDownload(ref, version, response);
        }
    }

    /**
     * 降级方案：本地生成 ZIP 包（不增加 Nacos 下载计数）
     */
    private void fallbackToLocalDownload(
            AgentSpecRef ref, String version, HttpServletResponse response) throws IOException {
        AgentSpec spec = fetchAgentSpec(ref, version);

        response.setContentType("application/zip");
        String encodedName =
                java.net.URLEncoder.encode(spec.getName() + ".zip", StandardCharsets.UTF_8)
                        .replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            String rootDir = spec.getName() + "/";

            // Write manifest.json from AgentSpec content
            if (spec.getContent() != null) {
                writeZipEntry(
                        zos,
                        rootDir + "manifest.json",
                        spec.getContent().getBytes(StandardCharsets.UTF_8));
            }

            // Write each resource directly from AgentSpec
            if (spec.getResource() != null) {
                for (AgentSpecResource resource : spec.getResource().values()) {
                    if (resource.getContent() == null) {
                        continue;
                    }
                    String path =
                            StrUtil.isNotBlank(resource.getType())
                                    ? resource.getType() + "/" + resource.getName()
                                    : resource.getName();
                    Map<String, Object> meta = resource.getMetadata();
                    boolean isBinary = meta != null && "base64".equals(meta.get("encoding"));
                    byte[] data =
                            isBinary
                                    ? Base64.getDecoder().decode(resource.getContent())
                                    : resource.getContent().getBytes(StandardCharsets.UTF_8);
                    writeZipEntry(zos, rootDir + path, data);
                }
            }
        }
    }

    @Override
    public void changeVersionStatus(String productId, String version, boolean online) {
        Product product = findProduct(productId);
        AgentSpecRef ref = getAgentSpecRef(productId, true);

        execute(
                ref.getNacosId(),
                s -> {
                    s.changeOnlineStatus(
                            ref.getNamespace(), ref.getAgentSpecName(), "", version, online);
                    return null;
                });
        log.info(
                "{}: AgentSpec {}, version {}",
                online ? "Online" : "Offline",
                ref.getAgentSpecName(),
                version);

        // Set scope to PUBLIC after going online so that downloads work without auth issues
        if (online) {
            setScopeToPublic(ref);
        }

        syncProductStatusAfterVersionChange(product, ref);
    }

    @Override
    public void forcePublishVersion(String productId, String version, Boolean updateLatestLabel) {
        Product product = findProduct(productId);
        AgentSpecRef ref = getAgentSpecRef(productId, true);

        execute(
                ref.getNacosId(),
                s ->
                        s.forcePublish(
                                ref.getNamespace(),
                                ref.getAgentSpecName(),
                                version,
                                updateLatestLabel));
        log.info("Force-published AgentSpec {}, version {}", ref.getAgentSpecName(), version);

        // Set scope to PUBLIC after publishing so that downloads work without auth issues
        setScopeToPublic(ref);

        syncProductStatusAfterVersionChange(product, ref);
    }

    /**
     * For non-admin users, validates that the requested version is online.
     * If no version specified, returns the latest online version.
     * Admins can access any version without restriction.
     *
     * @param productId the product ID
     * @param version   the requested version (may be null or empty)
     * @return the version to use
     */
    private String validateAndResolveVersion(String productId, String version) {
        if (contextHolder.isAdministrator()) {
            return version;
        }

        List<VersionResult> versions = listVersions(productId);
        List<VersionResult> onlineVersions =
                versions.stream()
                        .filter(v -> "online".equals(v.getStatus()))
                        .collect(Collectors.toList());

        if (onlineVersions.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND, "version", "No online version available");
        }

        if (StrUtil.isBlank(version)) {
            return onlineVersions.get(onlineVersions.size() - 1).getVersion();
        }

        boolean isOnline = onlineVersions.stream().anyMatch(v -> version.equals(v.getVersion()));
        if (!isOnline) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "version", version);
        }

        return version;
    }

    /**
     * Syncs Product status based on whether any Nacos version is online.
     *
     * <p>Rules:
     * <ul>
     *   <li>Has online version + non-PUBLISHED → set to READY</li>
     *   <li>No online version + non-PUBLISHED → set to PENDING</li>
     *   <li>No online version + PUBLISHED → downgrade to READY</li>
     * </ul>
     *
     * <p>Wrapped in try-catch so failures don't break the main operation.
     */
    void syncProductStatusAfterVersionChange(Product product, AgentSpecRef ref) {
        try {
            AgentSpecMeta meta =
                    execute(
                            ref.getNacosId(),
                            s ->
                                    s.getAgentSpecAdminDetail(
                                            ref.getNamespace(), ref.getAgentSpecName()));

            boolean hasOnline = false;
            if (meta != null && CollUtil.isNotEmpty(meta.getVersions())) {
                hasOnline =
                        meta.getVersions().stream()
                                .anyMatch(
                                        v ->
                                                "online"
                                                        .equals(
                                                                VersionResult.resolveStatus(
                                                                        v.getStatus(),
                                                                        v
                                                                                .getPublishPipelineInfo())));
            }

            ProductStatus current = product.getStatus();
            ProductStatus target;
            if (hasOnline) {
                target = (current != ProductStatus.PUBLISHED) ? ProductStatus.READY : current;
            } else {
                target =
                        (current == ProductStatus.PUBLISHED)
                                ? ProductStatus.READY
                                : ProductStatus.PENDING;
            }

            if (current != target) {
                product.setStatus(target);
                productRepository.save(product);
                log.info(
                        "Synced product {} status: {} → {}",
                        product.getProductId(),
                        current,
                        target);
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to sync product status after version change for AgentSpec {}",
                    ref.getAgentSpecName(),
                    e);
        }
    }

    @Override
    public void deleteDraft(String productId) {
        Product product = findProduct(productId);
        AgentSpecRef ref = getAgentSpecRef(productId, true);

        boolean deleted =
                execute(
                        ref.getNacosId(),
                        s -> s.deleteDraft(ref.getNamespace(), ref.getAgentSpecName()));
        if (!deleted) {
            log.warn(
                    "Nacos returned false for deleteDraft of AgentSpec {}", ref.getAgentSpecName());
        }
        log.info("Deleted draft for AgentSpec {}", ref.getAgentSpecName());

        // Clear agentSpecName if no versions remain after deletion
        try {
            AgentSpecMeta meta =
                    execute(
                            ref.getNacosId(),
                            s ->
                                    s.getAgentSpecAdminDetail(
                                            ref.getNamespace(), ref.getAgentSpecName()));

            // Auto-publish approved reviewing version to clear the blocking state
            autoPublishReviewingVersion(ref, meta);

            if (meta == null || CollUtil.isEmpty(meta.getVersions())) {
                // If no versions remain, delete the AgentSpec
                execute(
                        ref.getNacosId(),
                        s -> s.deleteAgentSpec(ref.getNamespace(), ref.getAgentSpecName()));

                if (product.getStatus() != ProductStatus.PUBLISHED) {
                    product.setStatus(ProductStatus.PENDING);
                }

                WorkerConfig config = product.getFeature().getWorkerConfig();
                config.setAgentSpecName(null);
                productRepository.save(product);
            } else {
                // Versions still remain — sync Product status
                // (auto-publish may have created a new online version)
                syncProductStatusAfterVersionChange(product, ref);
            }
        } catch (Exception e) {
            // AgentSpec no longer exists in Nacos
            log.info(
                    "AgentSpec {} not found after draft deletion, clearing reference",
                    ref.getAgentSpecName());
            WorkerConfig config = product.getFeature().getWorkerConfig();
            config.setAgentSpecName(null);
            if (product.getStatus() != ProductStatus.PUBLISHED) {
                product.setStatus(ProductStatus.PENDING);
            }
            productRepository.save(product);
        }
    }

    @Override
    public void setLatestVersion(String productId, String version) {
        AgentSpecRef ref = getAgentSpecRef(productId, true);

        // If the target version is still marked as "reviewing" in Nacos metadata
        // (e.g. pipeline APPROVED but not yet formally published), publish it first
        // to clear the reviewingVersion pointer, otherwise updateLabels will reject it.
        ensurePublished(ref, version);

        // Latest version label
        Map<String, String> labels = new HashMap<>();
        labels.put("latest", version);

        execute(
                ref.getNacosId(),
                s ->
                        s.updateLabels(
                                ref.getNamespace(),
                                ref.getAgentSpecName(),
                                JSONUtil.toJsonStr(labels)));

        log.info("Set latest: AgentSpec {}, version {}", ref.getAgentSpecName(), version);
    }

    private void ensurePublished(AgentSpecRef ref, String version) {
        AgentSpecMeta meta;
        try {
            meta =
                    execute(
                            ref.getNacosId(),
                            s ->
                                    s.getAgentSpecAdminDetail(
                                            ref.getNamespace(), ref.getAgentSpecName()));
        } catch (Exception e) {
            return;
        }
        if (meta == null) {
            return;
        }
        // If the version is still editing, submit then publish it
        if (version.equals(meta.getEditingVersion())) {
            execute(
                    ref.getNacosId(),
                    s -> s.submit(ref.getNamespace(), ref.getAgentSpecName(), version));
            execute(
                    ref.getNacosId(),
                    s -> s.publish(ref.getNamespace(), ref.getAgentSpecName(), version, false));
            log.info(
                    "Auto submit+published AgentSpec {} version {} from editing state",
                    ref.getAgentSpecName(),
                    version);
            setScopeToPublic(ref);
        }
        // If the version is still reviewing, publish it to clear the reviewing pointer
        if (version.equals(meta.getReviewingVersion())) {
            execute(
                    ref.getNacosId(),
                    s -> s.publish(ref.getNamespace(), ref.getAgentSpecName(), version, false));
            log.info(
                    "Auto-published AgentSpec {} version {} to clear reviewing state",
                    ref.getAgentSpecName(),
                    version);
            setScopeToPublic(ref);
        }
    }

    /**
     * Fetch AgentSpec from Nacos.
     *
     * @param ref     AgentSpec reference
     * @param version AgentSpec version
     * @return AgentSpec
     */
    private AgentSpec fetchAgentSpec(AgentSpecRef ref, String version) {
        if (StrUtil.isBlank(ref.getAgentSpecName())) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND, Resources.AGENT_SPEC, ref.getAgentSpecName());
        }

        return execute(
                ref.getNacosId(),
                s ->
                        StrUtil.isBlank(version)
                                ? s.getAgentSpecDetail(ref.getNamespace(), ref.getAgentSpecName())
                                : s.getAgentSpecVersionDetail(
                                        ref.getNamespace(), ref.getAgentSpecName(), version));
    }

    private FileContentResult getFileContent(AgentSpecRef ref, String path, String version) {
        AgentSpec spec = fetchAgentSpec(ref, version);

        if ("manifest.json".equals(path)) {
            String content = StrUtil.nullToDefault(spec.getContent(), "");
            return FileContentResult.builder()
                    .path("manifest.json")
                    .content(content)
                    .encoding("text")
                    .size(content.getBytes(StandardCharsets.UTF_8).length)
                    .build();
        }

        String specNamePrefix = StrUtil.isNotBlank(spec.getName()) ? spec.getName() + "/" : "";

        if (spec.getResource() != null) {
            for (AgentSpecResource resource : spec.getResource().values()) {
                String resourcePath =
                        StrUtil.isNotBlank(resource.getType())
                                ? resource.getType() + "/" + resource.getName()
                                : resource.getName();

                // Strip spec name prefix for consistent path matching
                if (!specNamePrefix.isEmpty() && resourcePath.startsWith(specNamePrefix)) {
                    resourcePath = resourcePath.substring(specNamePrefix.length());
                }

                if (path.equals(resourcePath)) {
                    Map<String, Object> meta = resource.getMetadata();
                    String encoding =
                            meta != null && meta.containsKey("encoding")
                                    ? java.lang.String.valueOf(meta.get("encoding"))
                                    : "text";
                    String content = StrUtil.nullToDefault(resource.getContent(), "");
                    return FileContentResult.builder()
                            .path(resourcePath)
                            .content(content)
                            .encoding(encoding)
                            .size(content.getBytes(StandardCharsets.UTF_8).length)
                            .build();
                }
            }
        }

        throw new BusinessException(ErrorCode.NOT_FOUND, "AgentSpec file", path);
    }

    private List<FileTreeNode> buildFileTree(AgentSpec spec) {
        Map<String, FileTreeNode> dirMap = new LinkedHashMap<>();
        List<FileTreeNode> rootChildren = new ArrayList<>();

        // Add manifest.json
        rootChildren.add(
                createFileNode("manifest.json", "manifest.json", spec.getContent(), "text"));

        // Strip spec name prefix from resource paths if Nacos prepends it.
        String specNamePrefix = StrUtil.isNotBlank(spec.getName()) ? spec.getName() + "/" : "";

        // Add resources
        if (spec.getResource() != null) {
            for (AgentSpecResource resource : spec.getResource().values()) {
                String resourcePath =
                        StrUtil.isNotBlank(resource.getType())
                                ? resource.getType() + "/" + resource.getName()
                                : resource.getName();

                // Remove spec name prefix to avoid redundant directory layer
                if (!specNamePrefix.isEmpty() && resourcePath.startsWith(specNamePrefix)) {
                    resourcePath = resourcePath.substring(specNamePrefix.length());
                }

                String[] parts = resourcePath.split("/");

                // Handle directories
                List<FileTreeNode> currentChildren = rootChildren;
                StringBuilder dirPath = new StringBuilder();

                for (int i = 0; i < parts.length - 1; i++) {
                    // Prepare directory info
                    if (!dirPath.isEmpty()) {
                        dirPath.append("/");
                    }
                    dirPath.append(parts[i]);

                    final String dirName = parts[i];
                    final String dirFullPath = dirPath.toString();
                    final List<FileTreeNode> parentChildren = currentChildren;

                    // Create or get directory node
                    FileTreeNode dirNode =
                            dirMap.computeIfAbsent(
                                    dirFullPath,
                                    k -> {
                                        FileTreeNode newDir = new FileTreeNode();
                                        newDir.setName(dirName);
                                        newDir.setPath(dirFullPath);
                                        newDir.setType("directory");
                                        newDir.setChildren(new ArrayList<>());
                                        parentChildren.add(newDir);
                                        return newDir;
                                    });

                    currentChildren = dirNode.getChildren();
                }

                // Add file node
                Map<String, Object> meta = resource.getMetadata();
                String encoding =
                        meta != null && meta.containsKey("encoding")
                                ? java.lang.String.valueOf(meta.get("encoding"))
                                : "text";

                currentChildren.add(
                        createFileNode(
                                parts[parts.length - 1],
                                resourcePath,
                                resource.getContent(),
                                encoding));
            }
        }

        sortNodes(rootChildren);

        // Wrap children under a root directory node named after the AgentSpec
        String rootName = StrUtil.isNotBlank(spec.getName()) ? spec.getName() : "worker";
        FileTreeNode rootNode = new FileTreeNode();
        rootNode.setName(rootName);
        rootNode.setPath("__root__");
        rootNode.setType("directory");
        rootNode.setChildren(rootChildren);
        return List.of(rootNode);
    }

    private FileTreeNode createFileNode(String name, String path, String content, String encoding) {
        FileTreeNode node = new FileTreeNode();
        node.setName(name);
        node.setPath(path);
        node.setType("file");
        node.setEncoding(encoding);
        node.setSize(content != null ? content.getBytes(StandardCharsets.UTF_8).length : 0);
        return node;
    }

    private void sortNodes(List<FileTreeNode> nodes) {
        Comparator<FileTreeNode> comparator =
                Comparator.comparing((FileTreeNode n) -> "file".equals(n.getType()) ? 1 : 0)
                        .thenComparing(
                                FileTreeNode::getName, java.lang.String.CASE_INSENSITIVE_ORDER);

        nodes.sort(comparator);
        nodes.forEach(
                node -> {
                    if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                        sortNodes(node.getChildren());
                    }
                });
    }

    private void writeZipEntry(ZipOutputStream zos, String path, byte[] data) throws IOException {
        zos.putNextEntry(new ZipEntry(path));
        zos.write(data);
        zos.closeEntry();
    }

    private Product findProduct(String productId) {
        return productRepository
                .findByProductId(productId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.PRODUCT, productId));
    }

    private AgentSpecRef getAgentSpecRef(String productId, boolean force) {
        AgentSpecRef result =
                productRepository
                        .findByProductId(productId)
                        .map(Product::getFeature)
                        .map(ProductFeature::getWorkerConfig)
                        .filter(wc -> StrUtil.isNotBlank(wc.getNacosId()))
                        .map(wc -> new AgentSpecRef().convertFrom(wc))
                        .orElse(null);

        if (force && result == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Worker config not found for product: " + productId);
        }
        return result;
    }

    @FunctionalInterface
    private interface NacosOperation<T> {
        T execute(AgentSpecMaintainerService service) throws NacosException;
    }

    /**
     * Auto-publish a reviewing version to clear the reviewing state that blocks new draft creation.
     * Nacos's deleteDraft only removes editing versions; a reviewing version left behind will
     * prevent createDraft from succeeding.
     */
    private void autoPublishReviewingVersion(AgentSpecRef ref, AgentSpecMeta meta) {
        if (meta == null) {
            return;
        }
        String reviewing = meta.getReviewingVersion();
        if (StrUtil.isBlank(reviewing)) {
            return;
        }
        try {
            execute(
                    ref.getNacosId(),
                    s -> s.publish(ref.getNamespace(), ref.getAgentSpecName(), reviewing, true));
            log.info(
                    "Auto-published reviewing version {} for AgentSpec {} to unblock draft"
                            + " operations",
                    reviewing,
                    ref.getAgentSpecName());
        } catch (Exception e) {
            log.warn(
                    "Failed to auto-publish reviewing version {} for AgentSpec {}: {}",
                    reviewing,
                    ref.getAgentSpecName(),
                    e.getMessage());
        }
    }

    private <T> T execute(String nacosId, NacosOperation<T> operation) {
        try {
            AiMaintainerService service = nacosService.getAiMaintainerService(nacosId);
            return operation.execute(service.agentSpec());
        } catch (NacosException e) {
            log.error("Nacos operation failed", e);
            throw toBusinessException(e);
        }
    }

    /**
     * Set agentspec scope to PUBLIC so that it can be downloaded without authentication.
     * Failures are logged but do not block the main operation.
     */
    private void setScopeToPublic(AgentSpecRef ref) {
        try {
            execute(
                    ref.getNacosId(),
                    s -> s.updateScope(ref.getNamespace(), ref.getAgentSpecName(), "PUBLIC"));
            log.info("Set AgentSpec {} scope to PUBLIC", ref.getAgentSpecName());
        } catch (Exception e) {
            log.warn(
                    "Failed to set AgentSpec {} scope to PUBLIC: {}",
                    ref.getAgentSpecName(),
                    e.getMessage());
        }
    }

    private BusinessException toBusinessException(NacosException e) {
        String detail = extractNacosDetail(e.getMessage());
        if (detail.contains("resource conflict")) {
            String conflictMsg = detail.replaceFirst("^resource conflict:\\s*", "");
            return new BusinessException(ErrorCode.CONFLICT, conflictMsg);
        }
        return new BusinessException(ErrorCode.INTERNAL_ERROR, detail);
    }

    private String extractNacosDetail(String message) {
        if (message == null) {
            return "Unknown error";
        }
        int idx = message.lastIndexOf("last errMsg: ");
        if (idx >= 0) {
            return message.substring(idx + "last errMsg: ".length());
        }
        return message;
    }

    @Data
    private static class AgentSpecRef implements OutputConverter<AgentSpecRef, WorkerConfig> {
        private String nacosId;
        private String namespace;
        private String agentSpecName;
    }

    @Override
    public CliDownloadInfo getCliDownloadInfo(String productId) {
        Product product = findProduct(productId);
        WorkerConfig config = product.getFeature().getWorkerConfig();

        if (config == null
                || StrUtil.isBlank(config.getNacosId())
                || StrUtil.isBlank(config.getAgentSpecName())) {
            return null;
        }

        try {
            var nacos = nacosService.getNacosInstance(config.getNacosId());
            if (nacos == null || StrUtil.isBlank(nacos.getServerUrl())) {
                return null;
            }
            URL nacosUrl =
                    URLUtil.url(
                            StrUtil.isNotBlank(nacos.getDisplayServerUrl())
                                    ? nacos.getDisplayServerUrl()
                                    : nacos.getServerUrl());
            int port = nacosUrl.getPort();
            String namespace =
                    StrUtil.isNotBlank(config.getNamespace())
                            ? config.getNamespace()
                            : nacos.getDefaultNamespace();
            return CliDownloadInfo.builder()
                    .nacosHost(nacosUrl.getHost())
                    .nacosPort(port == -1 ? null : port)
                    .namespace(namespace)
                    .resourceName(config.getAgentSpecName())
                    .resourceType("worker")
                    .build();
        } catch (Exception e) {
            log.warn("Failed to get CLI download info for worker product {}", productId, e);
            return null;
        }
    }

    @Override
    public ImportResult importFromNacos(String nacosId, String namespace) {
        int successCount = 0;
        int skippedCount = 0;

        try {
            AiMaintainerService aiService = nacosService.getAiMaintainerService(nacosId);

            Page<AgentSpecSummary> page =
                    aiService
                            .agentSpec()
                            .listAgentSpecAdminItems(namespace, null, null, 1, Integer.MAX_VALUE);

            if (page == null || page.getPageItems() == null) {
                return ImportResult.builder()
                        .resourceType("worker")
                        .successCount(0)
                        .skippedCount(0)
                        .build();
            }

            String adminId = contextHolder.getUser();

            for (AgentSpecSummary info : page.getPageItems()) {
                String name = info.getName();

                // Skip if product already exists
                if (productRepository.findByNameAndAdminId(name, adminId).isPresent()) {
                    log.info("Worker product '{}' already exists, skipping", name);
                    skippedCount++;
                    continue;
                }

                // Create product
                Product product =
                        Product.builder()
                                .productId(IdGenerator.genApiProductId())
                                .name(name)
                                .description(info.getDescription())
                                .type(ProductType.WORKER)
                                .adminId(adminId)
                                .status(
                                        info.getOnlineCnt() != null && info.getOnlineCnt() > 0
                                                ? ProductStatus.READY
                                                : ProductStatus.PENDING)
                                .build();
                // Set worker config
                WorkerConfig workerConfig =
                        WorkerConfig.builder()
                                .nacosId(nacosId)
                                .namespace(namespace)
                                .agentSpecName(name)
                                .downloadCount(info.getDownloadCount())
                                .build();

                ProductFeature feature =
                        ProductFeature.builder().workerConfig(workerConfig).build();
                product.setFeature(feature);

                productRepository.save(product);
                successCount++;
                log.info("Imported worker product '{}' from Nacos", name);
            }
        } catch (Exception e) {
            log.error("Failed to import workers from Nacos", e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Failed to import workers: " + e.getMessage());
        }

        log.info("Imported {} worker products from Nacos, skipped {}", successCount, skippedCount);

        return ImportResult.builder()
                .resourceType("worker")
                .successCount(successCount)
                .skippedCount(skippedCount)
                .build();
    }
}
