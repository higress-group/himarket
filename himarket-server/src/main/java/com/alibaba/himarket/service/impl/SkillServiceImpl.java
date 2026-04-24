package com.alibaba.himarket.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.skill.FileTreeBuilder;
import com.alibaba.himarket.core.skill.SkillMdBuilder;
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
import com.alibaba.himarket.service.SkillService;
import com.alibaba.himarket.support.enums.ProductStatus;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.product.ProductFeature;
import com.alibaba.himarket.support.product.SkillConfig;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.SkillMaintainerService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class SkillServiceImpl implements SkillService {

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
        SkillRef ref = getSkillRef(productId, true);

        byte[] zipBytes = file.getBytes();

        SkillConfig config = product.getFeature().getSkillConfig();

        if (StrUtil.isBlank(ref.getSkillName())) {
            // First upload: use overwrite mode in case Nacos already has a skill with the same name
            String skillName =
                    execute(
                            ref.getNacosId(),
                            s -> s.uploadSkillFromZip(ref.getNamespace(), zipBytes, true));
            log.info("Uploaded new Skill draft: {}", skillName);
            config.setSkillName(skillName);
        } else {
            // Subsequent upload: use overwrite mode to bypass reviewing version blocking
            execute(
                    ref.getNacosId(),
                    s -> s.uploadSkillFromZip(ref.getNamespace(), zipBytes, true));
            log.info("Uploaded (overwrite) for Skill {}", ref.getSkillName());
        }

        productRepository.save(product);

        // Set scope to PUBLIC immediately after creation
        setScopeToPublic(ref);
    }

    @Override
    public void deleteSkill(String productId) {
        Product product = findProduct(productId);
        SkillRef ref = getSkillRef(productId, false);

        if (ref == null || StrUtil.isBlank(ref.getSkillName())) {
            return;
        }
        execute(
                ref.getNacosId(),
                s -> {
                    s.deleteSkill(ref.getNamespace(), ref.getSkillName());
                    return null;
                });

        SkillConfig config = product.getFeature().getSkillConfig();
        config.setSkillName(null);

        productRepository.save(product);
    }

    @Override
    public List<FileTreeNode> getFileTree(String productId, String version) {
        SkillRef ref = getSkillRef(productId, false);
        if (ref == null || StrUtil.isBlank(ref.getSkillName())) {
            return Collections.emptyList();
        }

        version = validateAndResolveVersion(productId, version);

        try {
            Skill skill = fetchSkill(ref, version);
            return FileTreeBuilder.build(skill);
        } catch (Exception e) {
            log.warn("Failed to fetch file tree for Skill {}", ref.getSkillName(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public FileContentResult getFileContent(String productId, String path, String version) {
        version = validateAndResolveVersion(productId, version);

        SkillRef ref = getSkillRef(productId, true);
        Skill skill = fetchSkill(ref, version);

        // Virtual SKILL.md generated from Skill metadata
        if ("SKILL.md".equals(path)) {
            String skillMd = SkillMdBuilder.build(skill);
            return FileContentResult.builder()
                    .path("SKILL.md")
                    .content(skillMd)
                    .encoding("text")
                    .size(skillMd.getBytes(StandardCharsets.UTF_8).length)
                    .build();
        }

        // Strip skill name prefix from resource paths for consistent matching
        String skillNamePrefix = StrUtil.isNotBlank(skill.getName()) ? skill.getName() + "/" : "";

        if (skill.getResource() != null) {
            for (SkillResource resource : skill.getResource().values()) {
                String resourcePath = buildResourcePath(resource);

                if (!skillNamePrefix.isEmpty() && resourcePath.startsWith(skillNamePrefix)) {
                    resourcePath = resourcePath.substring(skillNamePrefix.length());
                }

                if (path.equals(resourcePath)) {
                    Map<String, Object> meta = resource.getMetadata();
                    String encoding =
                            meta != null && "base64".equals(meta.get("encoding"))
                                    ? "base64"
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
        throw new BusinessException(ErrorCode.NOT_FOUND, Resources.SKILL, path);
    }

    @Override
    public List<VersionResult> listVersions(String productId) {
        Product product = findProduct(productId);
        SkillRef ref = getSkillRef(productId, false);

        if (ref == null || StrUtil.isBlank(ref.getSkillName())) {
            return Collections.emptyList();
        }

        SkillMeta meta;
        try {
            meta =
                    execute(
                            ref.getNacosId(),
                            s -> s.getSkillMeta(ref.getNamespace(), ref.getSkillName()));
        } catch (Exception e) {
            log.warn("Skill {} not found in Nacos, returning empty versions", ref.getSkillName());
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
                                                SkillMeta.SkillVersionSummary::getCreateTime,
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
        SkillRef ref = getSkillRef(productId, true);

        String submittedVersion =
                execute(
                        ref.getNacosId(),
                        s -> s.submit(ref.getNamespace(), ref.getSkillName(), version));
        log.info("Submitted Skill {}, version {}", ref.getSkillName(), submittedVersion);
    }

    @Override
    public void changeVersionStatus(String productId, String version, boolean online) {
        Product product = findProduct(productId);
        SkillRef ref = getSkillRef(productId, true);

        execute(
                ref.getNacosId(),
                s -> {
                    s.changeOnlineStatus(
                            ref.getNamespace(), ref.getSkillName(), "", version, online);
                    return null;
                });
        log.info(
                "{}: Skill {}, version {}",
                online ? "Online" : "Offline",
                ref.getSkillName(),
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
        SkillRef ref = getSkillRef(productId, true);

        execute(
                ref.getNacosId(),
                s ->
                        s.forcePublish(
                                ref.getNamespace(),
                                ref.getSkillName(),
                                version,
                                updateLatestLabel));
        log.info("Force-published Skill {}, version {}", ref.getSkillName(), version);

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
    private void syncProductStatusAfterVersionChange(Product product, SkillRef ref) {
        try {
            SkillMeta meta =
                    execute(
                            ref.getNacosId(),
                            s -> s.getSkillMeta(ref.getNamespace(), ref.getSkillName()));

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
                    "Failed to sync product status after version change for Skill {}",
                    ref.getSkillName(),
                    e);
        }
    }

    @Override
    public void deleteDraft(String productId) {
        Product product = findProduct(productId);
        SkillRef ref = getSkillRef(productId, true);

        boolean deleted =
                execute(
                        ref.getNacosId(),
                        s -> s.deleteDraft(ref.getNamespace(), ref.getSkillName()));
        if (!deleted) {
            log.warn("Nacos returned false for deleteDraft of Skill {}", ref.getSkillName());
        }
        log.info("Deleted draft for Skill {}", ref.getSkillName());

        // Clear skillName if no versions remain after deletion
        try {
            SkillMeta meta =
                    execute(
                            ref.getNacosId(),
                            s -> s.getSkillMeta(ref.getNamespace(), ref.getSkillName()));

            // Auto-publish approved reviewing version to clear the blocking state
            autoPublishReviewingVersion(ref, meta);

            if (meta == null || CollUtil.isEmpty(meta.getVersions())) {
                // If no versions remain, delete the skill
                execute(
                        ref.getNacosId(),
                        s -> s.deleteSkill(ref.getNamespace(), ref.getSkillName()));

                if (product.getStatus() != ProductStatus.PUBLISHED) {
                    product.setStatus(ProductStatus.PENDING);
                }
                SkillConfig config = product.getFeature().getSkillConfig();
                config.setSkillName(null);
                productRepository.save(product);
            } else {
                // Versions still remain — sync Product status
                // (auto-publish may have created a new online version)
                syncProductStatusAfterVersionChange(product, ref);
            }
        } catch (Exception e) {
            // Skill no longer exists in Nacos after draft deletion
            log.info(
                    "Skill {} not found after draft deletion, clearing reference",
                    ref.getSkillName());
            SkillConfig config = product.getFeature().getSkillConfig();
            config.setSkillName(null);
            productRepository.save(product);
        }
    }

    @Override
    public void setLatestVersion(String productId, String version) {
        SkillRef ref = getSkillRef(productId, true);

        // If the target version is still marked as "reviewing" in Nacos metadata
        // (e.g. pipeline APPROVED but not yet formally published), publish it first
        // to clear the reviewingVersion pointer, otherwise updateLabels will reject it.
        ensurePublished(ref, version);

        Map<String, String> labels = new HashMap<>();
        labels.put("latest", version);

        execute(
                ref.getNacosId(),
                s ->
                        s.updateLabels(
                                ref.getNamespace(),
                                ref.getSkillName(),
                                JSONUtil.toJsonStr(labels)));
        log.info("Set latest: Skill {}, version {}", ref.getSkillName(), version);
    }

    private void ensurePublished(SkillRef ref, String version) {
        SkillMeta meta;
        try {
            meta =
                    execute(
                            ref.getNacosId(),
                            s -> s.getSkillMeta(ref.getNamespace(), ref.getSkillName()));
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
                    s -> s.submit(ref.getNamespace(), ref.getSkillName(), version));
            execute(
                    ref.getNacosId(),
                    s -> s.publish(ref.getNamespace(), ref.getSkillName(), version, false));
            log.info(
                    "Auto submit+published Skill {} version {} from editing state",
                    ref.getSkillName(),
                    version);
            setScopeToPublic(ref);
        }
        // If the version is still reviewing, publish it to clear the reviewing pointer
        if (version.equals(meta.getReviewingVersion())) {
            execute(
                    ref.getNacosId(),
                    s -> s.publish(ref.getNamespace(), ref.getSkillName(), version, false));
            log.info(
                    "Auto-published Skill {} version {} to clear reviewing state",
                    ref.getSkillName(),
                    version);
            setScopeToPublic(ref);
        }
    }

    @Override
    public void downloadPackage(String productId, String version, HttpServletResponse response)
            throws IOException {
        SkillRef ref = getSkillRef(productId, true);

        // 先调用 Nacos HTTP API 下载，让 Nacos 增加下载计数
        downloadFromNacos(ref, version, response);
    }

    /**
     * 通过调用 Nacos HTTP API 下载 Skill ZIP 包，使 Nacos 自动增加下载计数
     * API: GET /v3/console/ai/skills/version/download?namespaceId=xxx&skillName=xxx&version=xxx
     */
    private void downloadFromNacos(SkillRef ref, String version, HttpServletResponse response)
            throws IOException {
        try {
            NacosInstance nacosInstance = nacosService.findNacosInstanceById(ref.getNacosId());
            // 优先使用展示地址，不存在则使用 serverUrl
            String nacosBaseUrl =
                    StrUtil.isNotBlank(nacosInstance.getDisplayServerUrl())
                            ? nacosInstance.getDisplayServerUrl()
                            : nacosInstance.getServerUrl();

            // 构建 Nacos 下载 URL:
            // /v3/console/ai/skills/version/download?namespaceId=xxx&skillName=xxx&version=xxx
            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder.append(nacosBaseUrl);
            if (!nacosBaseUrl.endsWith("/")) {
                urlBuilder.append("/");
            }
            urlBuilder.append("v3/console/ai/skills/version/download?");
            urlBuilder.append("namespaceId=").append(ref.getNamespace());
            urlBuilder
                    .append("&skillName=")
                    .append(
                            java.net.URLEncoder.encode(
                                    ref.getSkillName(), StandardCharsets.UTF_8.name()));
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
                                        ref.getSkillName() + ".zip", StandardCharsets.UTF_8)
                                .replace("+", "%20");
                response.setHeader(
                        "Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);

                // 流式传输 ZIP 文件
                try (var input = conn.getInputStream();
                        var output = response.getOutputStream()) {
                    input.transferTo(output);
                }
                log.info("Downloaded skill {} from Nacos successfully", ref.getSkillName());
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
    private void fallbackToLocalDownload(SkillRef ref, String version, HttpServletResponse response)
            throws IOException {
        Skill skill = fetchSkill(ref, version);

        response.setContentType("application/zip");
        String encodedName =
                java.net.URLEncoder.encode(skill.getName() + ".zip", StandardCharsets.UTF_8)
                        .replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);

        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            String rootDir = skill.getName() + "/";

            // Write virtual SKILL.md
            String skillMd = SkillMdBuilder.build(skill);
            writeZipEntry(zos, rootDir + "SKILL.md", skillMd.getBytes(StandardCharsets.UTF_8));

            // Write each resource
            if (skill.getResource() != null) {
                for (SkillResource resource : skill.getResource().values()) {
                    if (resource.getContent() == null) {
                        continue;
                    }
                    String path = buildResourcePath(resource);
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
    public Skill getSkillDetail(
            String nacosId, String namespace, String skillName, String version) {
        return execute(
                nacosId,
                s ->
                        StrUtil.isBlank(version)
                                ? s.getSkillVersionDetail(namespace, skillName, null)
                                : s.getSkillVersionDetail(namespace, skillName, version));
    }

    @Override
    public void deleteSkill(String nacosId, String namespace, String skillName) {
        execute(
                nacosId,
                s -> {
                    s.deleteSkill(namespace, skillName);
                    return null;
                });
    }

    @Override
    public String uploadSkillFromZip(String nacosId, String namespace, byte[] zipBytes) {
        return execute(nacosId, s -> s.uploadSkillFromZip(namespace, zipBytes));
    }

    /**
     * Fetches Skill from Nacos.
     *
     * @param ref     Skill reference
     * @param version Skill version
     * @return Skill detail
     */
    private Skill fetchSkill(SkillRef ref, String version) {
        if (StrUtil.isBlank(ref.getSkillName())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, Resources.SKILL, ref.getSkillName());
        }
        return execute(
                ref.getNacosId(),
                s -> {
                    String targetVersion =
                            StrUtil.isBlank(version)
                                    ? resolveLatestVersion(
                                            s, ref.getNamespace(), ref.getSkillName())
                                    : version;
                    return s.getSkillVersionDetail(
                            ref.getNamespace(), ref.getSkillName(), targetVersion);
                });
    }

    /**
     * Resolves the latest version for a Skill from Nacos.
     * First checks the "latest" label, then falls back to the most recent version by createTime.
     *
     * @param service   SkillMaintainerService instance
     * @param namespace Nacos namespace
     * @param skillName Skill name
     * @return Latest version string
     * @throws BusinessException if Skill or versions not found
     */
    private String resolveLatestVersion(
            SkillMaintainerService service, String namespace, String skillName) {
        try {
            SkillMeta meta = service.getSkillMeta(namespace, skillName);
            if (meta == null || CollUtil.isEmpty(meta.getVersions())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, Resources.SKILL, skillName);
            }
            // Find latest version from labels
            if (meta.getLabels() != null && StrUtil.isNotBlank(meta.getLabels().get("latest"))) {
                return meta.getLabels().get("latest");
            }
            // Fallback: sort by createTime desc and use the first one
            return meta.getVersions().stream()
                    .sorted(
                            Comparator.comparing(
                                            SkillMeta.SkillVersionSummary::getCreateTime,
                                            Comparator.nullsLast(Long::compareTo))
                                    .reversed())
                    .map(SkillMeta.SkillVersionSummary::getVersion)
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new BusinessException(
                                            ErrorCode.NOT_FOUND, Resources.SKILL, skillName));
        } catch (NacosException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND, Resources.SKILL, skillName);
        }
    }

    private String buildResourcePath(SkillResource resource) {
        String type = resource.getType();
        String name = resource.getName();
        if (StrUtil.isNotBlank(type)) {
            return type + "/" + name;
        }
        return name;
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

    private SkillRef getSkillRef(String productId, boolean force) {
        SkillRef result =
                productRepository
                        .findByProductId(productId)
                        .map(Product::getFeature)
                        .map(ProductFeature::getSkillConfig)
                        .filter(sc -> StrUtil.isNotBlank(sc.getNacosId()))
                        .map(sc -> new SkillRef().convertFrom(sc))
                        .orElse(null);

        if (force && result == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Skill config not found for product: " + productId);
        }

        return result;
    }

    @FunctionalInterface
    private interface NacosOperation<T> {
        T execute(SkillMaintainerService service) throws NacosException;
    }

    /**
     * Auto-publish a reviewing version to clear the reviewing state that blocks new draft creation.
     * Nacos's deleteDraft only removes editing versions; a reviewing version left behind will
     * prevent createDraft from succeeding.
     */
    private void autoPublishReviewingVersion(SkillRef ref, SkillMeta meta) {
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
                    s -> s.publish(ref.getNamespace(), ref.getSkillName(), reviewing, true));
            log.info(
                    "Auto-published reviewing version {} for Skill {} to unblock draft operations",
                    reviewing,
                    ref.getSkillName());
        } catch (Exception e) {
            log.warn(
                    "Failed to auto-publish reviewing version {} for Skill {}: {}",
                    reviewing,
                    ref.getSkillName(),
                    e.getMessage());
        }
    }

    private <T> T execute(String nacosId, NacosOperation<T> operation) {
        try {
            AiMaintainerService service = nacosService.getAiMaintainerService(nacosId);
            return operation.execute(service.skill());
        } catch (NacosException e) {
            log.error("Nacos operation failed", e);
            throw toBusinessException(e);
        }
    }

    /**
     * Set skill scope to PUBLIC so that it can be downloaded without authentication.
     * Failures are logged but do not block the main operation.
     */
    private void setScopeToPublic(SkillRef ref) {
        try {
            execute(
                    ref.getNacosId(),
                    s -> s.updateScope(ref.getNamespace(), ref.getSkillName(), "PUBLIC"));
            log.info("Set Skill {} scope to PUBLIC", ref.getSkillName());
        } catch (Exception e) {
            log.warn(
                    "Failed to set Skill {} scope to PUBLIC: {}",
                    ref.getSkillName(),
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
    @NoArgsConstructor
    @AllArgsConstructor
    private static class SkillRef implements OutputConverter<SkillRef, SkillConfig> {
        private String nacosId;
        private String namespace;
        private String skillName;
    }

    @Override
    public CliDownloadInfo getCliDownloadInfo(String productId) {
        Product product = findProduct(productId);
        SkillConfig config = product.getFeature().getSkillConfig();

        if (config == null
                || StrUtil.isBlank(config.getNacosId())
                || StrUtil.isBlank(config.getSkillName())) {
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
            return CliDownloadInfo.builder()
                    .nacosHost(nacosUrl.getHost())
                    .nacosPort(port == -1 ? null : port)
                    .namespace(config.getNamespace())
                    .resourceName(config.getSkillName())
                    .resourceType("skill")
                    .build();
        } catch (Exception e) {
            log.warn("Failed to get CLI download info for skill product {}", productId, e);
            return null;
        }
    }

    @Override
    public ImportResult importFromNacos(String nacosId, String namespace) {
        int successCount = 0;
        int skippedCount = 0;

        try {
            AiMaintainerService aiService = nacosService.getAiMaintainerService(nacosId);

            Page<SkillSummary> page =
                    aiService.skill().listSkills(namespace, null, null, 1, Integer.MAX_VALUE);

            if (page == null || page.getPageItems() == null) {
                return ImportResult.builder()
                        .resourceType("skill")
                        .successCount(0)
                        .skippedCount(0)
                        .build();
            }

            for (SkillSummary info : page.getPageItems()) {
                String name = info.getName();

                // Skip if product already exists
                if (productRepository
                        .findByNameAndAdminId(name, contextHolder.getUser())
                        .isPresent()) {
                    log.info("Skill product '{}' already exists, skipping", name);
                    skippedCount++;
                    continue;
                }

                // Create product
                Product product =
                        Product.builder()
                                .productId(IdGenerator.genApiProductId())
                                .name(name)
                                .description(info.getDescription())
                                .type(ProductType.AGENT_SKILL)
                                .adminId(contextHolder.getUser())
                                .status(
                                        info.getOnlineCnt() != null && info.getOnlineCnt() > 0
                                                ? ProductStatus.READY
                                                : ProductStatus.PENDING)
                                .build();

                // Set skill config
                SkillConfig skillConfig =
                        SkillConfig.builder()
                                .nacosId(nacosId)
                                .namespace(namespace)
                                .skillName(name)
                                .downloadCount(info.getDownloadCount())
                                .build();

                ProductFeature feature = ProductFeature.builder().skillConfig(skillConfig).build();
                product.setFeature(feature);

                productRepository.save(product);
                successCount++;
                log.info("Imported skill product '{}' from Nacos", name);
            }
        } catch (Exception e) {
            log.error("Failed to import skills from Nacos", e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Failed to import skills: " + e.getMessage());
        }

        log.info("Imported {} skills from Nacos, skipped {}", successCount, skippedCount);

        return ImportResult.builder()
                .resourceType("skill")
                .successCount(successCount)
                .skippedCount(skippedCount)
                .build();
    }
}
