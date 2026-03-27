package com.alibaba.himarket.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.event.ProductQueriedEvent;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.skill.FileTreeBuilder;
import com.alibaba.himarket.core.skill.SkillMdBuilder;
import com.alibaba.himarket.core.skill.SkillZipParser;
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
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
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
        SkillRef ref = getSkillRef(productId, true);

        byte[] zipBytes = file.getBytes();

        SkillConfig config = product.getFeature().getSkillConfig();

        if (StrUtil.isBlank(ref.getSkillName())) {
            // First upload: create Skill from ZIP (produces draft v1)
            String skillName =
                    execute(
                            ref.getNacosId(),
                            s -> s.uploadSkillFromZip(ref.getNamespace(), zipBytes));
            log.info("Uploaded new Skill draft: {}", skillName);
            config.setSkillName(skillName);
        } else {
            // Subsequent upload: create new draft version and update content
            String draftVersion =
                    execute(
                            ref.getNacosId(),
                            s -> s.createDraft(ref.getNamespace(), ref.getSkillName(), null));
            log.info("Created draft {} for Skill {}", draftVersion, ref.getSkillName());

            Skill skill = SkillZipParser.parseSkillFromZip(zipBytes, ref.getNamespace());
            skill.setName(ref.getSkillName());
            String skillCard = JSONUtil.toJsonStr(skill);
            execute(
                    ref.getNacosId(),
                    s -> {
                        s.updateDraft(ref.getNamespace(), skillCard, false);
                        return null;
                    });
            log.info("Updated draft {} for Skill {}", draftVersion, ref.getSkillName());
        }

        productRepository.save(product);
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
        SkillRef ref = getSkillRef(productId, true);

        String submittedVersion =
                execute(
                        ref.getNacosId(),
                        s -> s.submit(ref.getNamespace(), ref.getSkillName(), version));
        log.info("Submitted Skill {}, version {}", ref.getSkillName(), submittedVersion);
    }

    @Override
    public void changeVersionStatus(String productId, String version, boolean online) {
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
    }

    @Override
    public void deleteDraft(String productId) {
        Product product = findProduct(productId);
        SkillRef ref = getSkillRef(productId, true);

        execute(ref.getNacosId(), s -> s.deleteDraft(ref.getNamespace(), ref.getSkillName()));
        log.info("Deleted draft for Skill {}", ref.getSkillName());

        // Clear skillName if no versions remain after deletion
        try {
            SkillMeta meta =
                    execute(
                            ref.getNacosId(),
                            s -> s.getSkillMeta(ref.getNamespace(), ref.getSkillName()));

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

    @Override
    public void downloadPackage(String productId, String version, HttpServletResponse response)
            throws IOException {
        SkillRef ref = getSkillRef(productId, true);
        Skill skill = fetchSkill(ref, version);

        response.setContentType("application/zip");
        response.setHeader(
                "Content-Disposition", "attachment; filename=\"" + skill.getName() + ".zip\"");

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
                s ->
                        StrUtil.isBlank(version)
                                ? s.getSkillVersionDetail(
                                        ref.getNamespace(), ref.getSkillName(), null)
                                : s.getSkillVersionDetail(
                                        ref.getNamespace(), ref.getSkillName(), version));
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

    private <T> T execute(String nacosId, NacosOperation<T> operation) {
        try {
            AiMaintainerService service = nacosService.getAiMaintainerService(nacosId);
            return operation.execute(service.skill());
        } catch (NacosException e) {
            log.error("Nacos operation failed", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
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
            return CliDownloadInfo.builder()
                    .nacosHost(
                            URLUtil.url(
                                            StrUtil.isNotBlank(nacos.getDisplayServerUrl())
                                                    ? nacos.getDisplayServerUrl()
                                                    : nacos.getServerUrl())
                                    .getHost())
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

    /**
     * Listen for product queried events and sync download counts for skill products. Groups by
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

        // Batch fetch skill products
        List<Product> productsToSync =
                productRepository.findByProductIdIn(productIdsToSync).stream()
                        .filter(
                                p ->
                                        p.getFeature() != null
                                                && p.getFeature().getSkillConfig() != null)
                        .toList();

        if (productsToSync.isEmpty()) {
            return;
        }

        // Group by Nacos instance (nacosId:namespace) and sync each group
        productsToSync.stream()
                .collect(
                        Collectors.groupingBy(
                                p -> {
                                    SkillConfig c = p.getFeature().getSkillConfig();
                                    return c.getNacosId() + ":" + c.getNamespace();
                                }))
                .forEach(
                        (key, group) -> {
                            Product first = group.get(0);
                            SkillConfig config = first.getFeature().getSkillConfig();
                            syncSkillGroup(config.getNacosId(), config.getNamespace(), group);
                        });
    }

    /**
     * Sync a group of skill products from the same Nacos instance.
     */
    private void syncSkillGroup(String nacosId, String namespace, List<Product> products) {
        try {
            AiMaintainerService aiService = nacosService.getAiMaintainerService(nacosId);
            Page<SkillSummary> page =
                    aiService.skill().listSkills(namespace, null, null, 1, Integer.MAX_VALUE);

            if (page == null || CollUtil.isEmpty(page.getPageItems())) {
                return;
            }

            Map<String, Long> downloadCountMap =
                    page.getPageItems().stream()
                            .collect(
                                    Collectors.toMap(
                                            SkillSummary::getName,
                                            SkillSummary::getDownloadCount,
                                            (v1, v2) -> v1));

            for (Product product : products) {
                try {
                    SkillConfig config = product.getFeature().getSkillConfig();
                    Long count = downloadCountMap.get(config.getSkillName());

                    if (count != null && !Objects.equals(config.getDownloadCount(), count)) {
                        config.setDownloadCount(count);
                        productRepository.save(product);
                        log.info(
                                "Synced download count for skill product {}: {}",
                                product.getProductId(),
                                count);
                    }
                    downloadCountSyncCache.put(product.getProductId(), Boolean.TRUE);
                } catch (Exception e) {
                    log.warn(
                            "Failed to sync download count for skill product {}",
                            product.getProductId(),
                            e);
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to sync download counts for skill products from Nacos {}: {}",
                    nacosId,
                    e.getMessage());
        }
    }
}
