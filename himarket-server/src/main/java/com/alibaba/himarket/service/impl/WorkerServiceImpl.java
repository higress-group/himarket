package com.alibaba.himarket.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.agentspec.AgentSpecZipParser;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.dto.result.common.FileContentResult;
import com.alibaba.himarket.dto.result.common.FileTreeNode;
import com.alibaba.himarket.dto.result.common.VersionResult;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.service.WorkerService;
import com.alibaba.himarket.support.enums.ProductStatus;
import com.alibaba.himarket.support.product.ProductFeature;
import com.alibaba.himarket.support.product.WorkerConfig;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecResource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.maintainer.client.ai.AgentSpecMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class WorkerServiceImpl implements WorkerService {

    private static final long MAX_ZIP_SIZE = 10 * 1024 * 1024;

    private final NacosService nacosService;

    private final ProductRepository productRepository;

    @Override
    public void uploadPackage(String productId, MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_ZIP_SIZE) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "ZIP file cannot be empty or exceed 10MB");
        }

        Product product = findProduct(productId);
        AgentSpecRef ref = parseAgentSpecRef(product);
        byte[] zipBytes = file.getBytes();

        WorkerConfig config = product.getFeature().getWorkerConfig();

        if (StrUtil.isBlank(ref.getAgentSpecName())) {
            // First upload: create AgentSpec from ZIP (draft v1)
            String agentSpecName =
                    execute(
                            ref.getNacosId(),
                            s -> s.uploadAgentSpecFromZip(ref.getNamespace(), zipBytes));
            log.info("Uploaded new AgentSpec draft: {}", agentSpecName);
            config.setAgentSpecName(agentSpecName);
        } else {
            // Subsequent upload: create new draft version and update content
            String draftVersion =
                    execute(
                            ref.getNacosId(),
                            s -> s.createDraft(ref.getNamespace(), ref.getAgentSpecName(), null));
            log.info("Created draft {} for AgentSpec {}", draftVersion, ref.getAgentSpecName());

            String payload =
                    AgentSpecZipParser.parse(zipBytes, ref.getNamespace(), ref.getAgentSpecName());
            execute(ref.getNacosId(), s -> s.updateDraft(ref.getNamespace(), payload, false));
            log.info("Updated draft {} for AgentSpec {}", draftVersion, ref.getAgentSpecName());
        }

        productRepository.save(product);
    }

    @Override
    public void deleteAgentSpec(String productId) {
        Product product = findProduct(productId);
        AgentSpecRef ref = getAgentSpecRef(productId);
        if (StrUtil.isBlank(ref.getAgentSpecName())) {
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
        AgentSpecRef ref = getAgentSpecRef(productId);
        if (StrUtil.isBlank(ref.getAgentSpecName())) {
            return Collections.emptyList();
        }

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
        AgentSpecRef ref = getAgentSpecRef(productId);

        return getFileContent(ref, path, version);
    }

    @Override
    public List<VersionResult> listVersions(String productId) {
        Product product = findProduct(productId);
        AgentSpecRef ref = parseAgentSpecRef(product);

        if (StrUtil.isBlank(ref.getAgentSpecName())) {
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
                                                .status(v.getStatus())
                                                .updateTime(v.getUpdateTime())
                                                .downloadCount(v.getDownloadCount())
                                                .publishPipelineInfo(v.getPublishPipelineInfo())
                                                .build())
                        .toList();

        // Sync Product status based on whether any online version exists
        ProductStatus status = product.getStatus();
        if (status != ProductStatus.PUBLISHED) {
            ProductStatus targetStatus =
                    results.stream().anyMatch(v -> "online".equals(v.getStatus()))
                            ? ProductStatus.READY
                            : ProductStatus.PENDING;

            if (product.getStatus() != targetStatus) {
                product.setStatus(targetStatus);
                productRepository.save(product);
            }
        }

        return results;
    }

    @Override
    public void publishVersion(String productId, String version) {
        AgentSpecRef ref = getAgentSpecRef(productId);
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
        AgentSpecRef ref = getAgentSpecRef(productId);
        AgentSpec spec = fetchAgentSpec(ref, version);

        response.setContentType("application/zip");
        response.setHeader(
                "Content-Disposition", "attachment; filename=\"" + spec.getName() + ".zip\"");

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
        AgentSpecRef ref = parseAgentSpecRef(product);
        if (StrUtil.isBlank(ref.getAgentSpecName())) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND, Resources.AGENT_SPEC, ref.getAgentSpecName());
        }

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
    }

    @Override
    public void deleteDraft(String productId) {
        Product product = findProduct(productId);
        AgentSpecRef ref = parseAgentSpecRef(product);
        if (StrUtil.isBlank(ref.getAgentSpecName())) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND, Resources.AGENT_SPEC, ref.getAgentSpecName());
        }
        execute(
                ref.getNacosId(),
                s -> {
                    s.deleteDraft(ref.getNamespace(), ref.getAgentSpecName());
                    return null;
                });
        log.info("Deleted draft for AgentSpec {}", ref.getAgentSpecName());

        // Clear agentSpecName if no versions remain after deletion
        try {
            AgentSpecMeta meta =
                    execute(
                            ref.getNacosId(),
                            s ->
                                    s.getAgentSpecAdminDetail(
                                            ref.getNamespace(), ref.getAgentSpecName()));

            if (meta == null || CollUtil.isEmpty(meta.getVersions())) {
                // If no versions remain, delete the AgentSpec
                execute(
                        ref.getNacosId(),
                        s -> s.deleteAgentSpec(ref.getNamespace(), ref.getAgentSpecName()));

                WorkerConfig config = product.getFeature().getWorkerConfig();
                config.setAgentSpecName(null);
                productRepository.save(product);
            }
        } catch (Exception e) {
            // AgentSpec no longer exists in Nacos
            log.info(
                    "AgentSpec {} not found after draft deletion, clearing reference",
                    ref.getAgentSpecName());
        }
    }

    @Override
    public void setLatestVersion(String productId, String version) {
        Product product = findProduct(productId);
        AgentSpecRef ref = parseAgentSpecRef(product);
        if (StrUtil.isBlank(ref.getAgentSpecName())) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND, Resources.AGENT_SPEC, ref.getAgentSpecName());
        }

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

    private AgentSpecRef getAgentSpecRef(String productId) {
        return productRepository.findByProductId(productId).map(this::parseAgentSpecRef).get();
    }

    private AgentSpecRef parseAgentSpecRef(Product product) {
        return Optional.ofNullable(product)
                .map(Product::getFeature)
                .map(ProductFeature::getWorkerConfig)
                .filter(wc -> StrUtil.isNotBlank(wc.getNacosId()))
                .map(wc -> new AgentSpecRef().convertFrom(wc))
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.INVALID_REQUEST,
                                        "Product not linked to Nacos instance"));
    }

    @FunctionalInterface
    private interface NacosOperation<T> {
        T execute(AgentSpecMaintainerService service) throws NacosException;
    }

    private <T> T execute(String nacosId, NacosOperation<T> operation) {
        try {
            AiMaintainerService service = nacosService.getAiMaintainerService(nacosId);
            return operation.execute(service.agentSpec());
        } catch (NacosException e) {
            log.error("Nacos operation failed", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    @Data
    private static class AgentSpecRef implements OutputConverter<AgentSpecRef, WorkerConfig> {
        private String nacosId;
        private String namespace;
        private String agentSpecName;
    }
}
