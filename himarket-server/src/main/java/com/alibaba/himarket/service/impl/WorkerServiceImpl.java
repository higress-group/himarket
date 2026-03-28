package com.alibaba.himarket.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.agentspec.AgentSpecZipParser;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.event.ProductQueriedEvent;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.CacheUtil;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.dto.result.cli.CliDownloadInfo;
import com.alibaba.himarket.dto.result.common.FileContentResult;
import com.alibaba.himarket.dto.result.common.FileTreeNode;
import com.alibaba.himarket.dto.result.common.ImportResult;
import com.alibaba.himarket.dto.result.common.VersionResult;
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
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
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

    /**
     * Cache to prevent duplicate download count sync within 5 minutes
     */
    private final Cache<String, Boolean> downloadCountSyncCache = CacheUtil.newCache(5);

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
    }

    @Override
    public void deleteDraft(String productId) {
        Product product = findProduct(productId);
        AgentSpecRef ref = getAgentSpecRef(productId, true);

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

                if (product.getStatus() != ProductStatus.PUBLISHED) {
                    product.setStatus(ProductStatus.PENDING);
                }

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
        AgentSpecRef ref = getAgentSpecRef(productId, true);

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
            return CliDownloadInfo.builder()
                    .nacosHost(
                            URLUtil.url(
                                            StrUtil.isNotBlank(nacos.getDisplayServerUrl())
                                                    ? nacos.getDisplayServerUrl()
                                                    : nacos.getServerUrl())
                                    .getHost())
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

    /**
     * Listen for product queried events and sync download counts for worker products. Groups by
     * Nacos instance to minimize API calls.
     *
     * @param event the product queried event
     */
    @EventListener
    @Async("taskExecutor")
    public void onProductQueried(ProductQueriedEvent event) {
        if (CollUtil.isEmpty(event.getProductIds())) {
            return;
        }

        // Filter out products in cooldown
        List<String> productIdsToSync =
                event.getProductIds().stream()
                        .filter(id -> downloadCountSyncCache.getIfPresent(id) == null)
                        .toList();

        if (CollUtil.isEmpty(productIdsToSync)) {
            return;
        }

        // Batch fetch worker products
        List<Product> productsToSync =
                productRepository.findByProductIdIn(productIdsToSync).stream()
                        .filter(
                                p ->
                                        p.getFeature() != null
                                                && p.getFeature().getWorkerConfig() != null)
                        .toList();

        if (productsToSync.isEmpty()) {
            return;
        }

        // Group by Nacos instance (nacosId:namespace) and sync each group
        productsToSync.stream()
                .collect(
                        Collectors.groupingBy(
                                p -> {
                                    WorkerConfig c = p.getFeature().getWorkerConfig();
                                    return c.getNacosId() + ":" + c.getNamespace();
                                }))
                .forEach(
                        (key, group) -> {
                            Product first = group.get(0);
                            WorkerConfig config = first.getFeature().getWorkerConfig();
                            syncWorkerGroup(config.getNacosId(), config.getNamespace(), group);
                        });
    }

    /**
     * Sync a group of worker products from the same Nacos instance.
     */
    private void syncWorkerGroup(String nacosId, String namespace, List<Product> products) {
        try {
            AiMaintainerService aiService = nacosService.getAiMaintainerService(nacosId);
            Page<AgentSpecSummary> page =
                    aiService
                            .agentSpec()
                            .listAgentSpecAdminItems(namespace, null, null, 1, Integer.MAX_VALUE);

            if (page == null || CollUtil.isEmpty(page.getPageItems())) {
                return;
            }

            Map<String, Long> downloadCountMap =
                    page.getPageItems().stream()
                            .collect(
                                    Collectors.toMap(
                                            AgentSpecSummary::getName,
                                            AgentSpecSummary::getDownloadCount,
                                            (v1, v2) -> v1));

            for (Product product : products) {
                try {
                    WorkerConfig config = product.getFeature().getWorkerConfig();
                    Long count = downloadCountMap.get(config.getAgentSpecName());

                    if (count != null && !Objects.equals(config.getDownloadCount(), count)) {
                        config.setDownloadCount(count);
                        productRepository.save(product);
                        log.info(
                                "Synced download count for worker product {}: {}",
                                product.getProductId(),
                                count);
                    }
                    downloadCountSyncCache.put(product.getProductId(), Boolean.TRUE);
                } catch (Exception e) {
                    log.warn(
                            "Failed to sync download count for worker product {}",
                            product.getProductId(),
                            e);
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to sync download counts for worker products from Nacos {}: {}",
                    nacosId,
                    e.getMessage());
        }
    }
}
