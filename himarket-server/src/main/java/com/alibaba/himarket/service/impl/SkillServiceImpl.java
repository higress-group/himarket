package com.alibaba.himarket.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.skill.FileTreeBuilder;
import com.alibaba.himarket.core.skill.SkillMdBuilder;
import com.alibaba.himarket.core.skill.SkillZipParser;
import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.dto.result.common.FileContentResult;
import com.alibaba.himarket.dto.result.common.FileTreeNode;
import com.alibaba.himarket.dto.result.common.VersionResult;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.service.SkillService;
import com.alibaba.himarket.support.enums.ProductStatus;
import com.alibaba.himarket.support.product.ProductFeature;
import com.alibaba.himarket.support.product.SkillConfig;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.SkillMaintainerService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {

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
        SkillRef ref = parseSkillRef(product);
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
        SkillRef ref = getSkillRef(productId);

        if (StrUtil.isBlank(ref.getSkillName())) {
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
        config.setCurrentVersion(null);

        productRepository.save(product);
    }

    @Override
    public List<FileTreeNode> getFileTree(String productId, String version) {
        SkillRef ref = getSkillRef(productId);
        if (StrUtil.isBlank(ref.getSkillName())) {
            return Collections.emptyList();
        }

        try {
            Skill skill = fetchSkill(ref, version);
            return FileTreeBuilder.build(skill);
        } catch (Exception e) {
            log.warn("Failed to fetch file tree for Skill {}", ref.getSkillName());
            return Collections.emptyList();
        }
    }

    @Override
    public FileContentResult getFileContent(String productId, String path, String version) {
        SkillRef ref = getSkillRef(productId);
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
        SkillRef ref = parseSkillRef(product);

        if (StrUtil.isBlank(ref.getSkillName())) {
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
        SkillRef ref = getSkillRef(productId);
        if (StrUtil.isBlank(ref.getSkillName())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, Resources.SKILL, ref.getSkillName());
        }

        String submittedVersion =
                execute(
                        ref.getNacosId(),
                        s -> s.submit(ref.getNamespace(), ref.getSkillName(), version));
        log.info("Submitted Skill {}, version {}", ref.getSkillName(), submittedVersion);
    }

    @Override
    public void changeVersionStatus(String productId, String version, boolean online) {
        SkillRef ref = getSkillRef(productId);
        if (StrUtil.isBlank(ref.getSkillName())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, Resources.SKILL, ref.getSkillName());
        }

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
        SkillRef ref = parseSkillRef(product);
        if (StrUtil.isBlank(ref.getSkillName())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, Resources.SKILL, ref.getSkillName());
        }
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

                SkillConfig config = product.getFeature().getSkillConfig();
                config.setSkillName(null);
                config.setCurrentVersion(null);
                productRepository.save(product);
            }
        } catch (Exception e) {
            // Skill no longer exists in Nacos after draft deletion
            log.info(
                    "Skill {} not found after draft deletion, clearing reference",
                    ref.getSkillName());
            SkillConfig config = product.getFeature().getSkillConfig();
            config.setSkillName(null);
            config.setCurrentVersion(null);
            productRepository.save(product);
        }
    }

    @Override
    public void setLatestVersion(String productId, String version) {
        SkillRef ref = getSkillRef(productId);
        if (StrUtil.isBlank(ref.getSkillName())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, Resources.SKILL, ref.getSkillName());
        }
        execute(
                ref.getNacosId(),
                s -> {
                    s.updateLabels(ref.getNamespace(), ref.getSkillName(), "latest=" + version);
                    return null;
                });
        log.info("Set latest: Skill {}, version {}", ref.getSkillName(), version);
    }

    @Override
    public void downloadPackage(String productId, String version, HttpServletResponse response)
            throws IOException {
        SkillRef ref = getSkillRef(productId);
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

    private SkillRef getSkillRef(String productId) {
        Product product =
                productRepository
                        .findByProductId(productId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND, Resources.PRODUCT, productId));
        return parseSkillRef(product);
    }

    private SkillRef parseSkillRef(Product product) {
        return Optional.ofNullable(product)
                .map(Product::getFeature)
                .map(ProductFeature::getSkillConfig)
                .filter(sc -> StrUtil.isNotBlank(sc.getNacosId()))
                .map(sc -> new SkillRef().convertFrom(sc))
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.INVALID_REQUEST,
                                        "Product not linked to Nacos instance"));
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
}
