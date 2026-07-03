package com.alibaba.himarket.service.impl;

import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.skill.FileTreeBuilder;
import com.alibaba.himarket.core.skill.SkillMdBuilder;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.dto.params.skill.CreateSkillDraftParam;
import com.alibaba.himarket.dto.params.skill.UpdateSkillDraftParam;
import com.alibaba.himarket.dto.params.skill.UpdateSkillVersionParam;
import com.alibaba.himarket.dto.result.cli.CliDownloadInfo;
import com.alibaba.himarket.dto.result.common.FileContentResult;
import com.alibaba.himarket.dto.result.common.FileTreeNode;
import com.alibaba.himarket.dto.result.common.ImportResult;
import com.alibaba.himarket.dto.result.common.SkillDraftResult;
import com.alibaba.himarket.dto.result.common.VersionResult;
import com.alibaba.himarket.entity.NacosInstance;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.AiRegistrySkillService;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.service.SkillService;
import com.alibaba.himarket.support.common.Strings;
import com.alibaba.himarket.support.enums.ProductStatus;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.enums.SkillRegistryType;
import com.alibaba.himarket.support.product.ProductFeature;
import com.alibaba.himarket.support.product.SkillConfig;
import com.alibaba.himarket.support.product.VersionInfo;
import com.alibaba.himarket.utils.JsonUtil;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.SkillMaintainerService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
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
    private final AiRegistrySkillService aiRegistrySkillService;

    @Override
    public void uploadPackage(String productId, MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_ZIP_SIZE) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "ZIP file cannot be empty or exceed 10MB");
        }

        Product product = findProduct(productId);
        byte[] zipBytes = file.getBytes();

        SkillConfig config = resolveSkillConfig(product);
        if (config.getRegistryType() == SkillRegistryType.AIREGISTRY) {
            if (Strings.isBlank(config.getAiRegistryId())
                    || Strings.isBlank(config.getNamespace())) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST, "AIRegistry skill config not found");
            }
            String skillName =
                    aiRegistrySkillService.uploadFromZip(
                            config.getAiRegistryId(),
                            config.getNamespace(),
                            zipBytes,
                            file.getOriginalFilename(),
                            true);
            if (Strings.isBlank(config.getSkillName())) {
                config.setSkillName(skillName);
                config.setVersionInfos(null);
                config.setLatestVersion(null);
            }
            productRepository.save(product);
            return;
        }

        SkillRef ref = getSkillRef(productId, true);

        if (Strings.isBlank(ref.getSkillName())) {
            // First upload: use overwrite mode in case Nacos already has a skill with the same name
            String skillName =
                    execute(
                            ref.getNacosId(),
                            s -> s.uploadSkillFromZip(ref.getNamespace(), zipBytes, true));
            log.info("Uploaded new Skill draft, skillName={}", skillName);
            config.setSkillName(skillName);
            config.setVersionInfos(null);
            config.setLatestVersion(null);
            ref.setSkillName(skillName);
        } else {
            // Subsequent upload: use overwrite mode to bypass reviewing version blocking
            execute(
                    ref.getNacosId(),
                    s -> s.uploadSkillFromZip(ref.getNamespace(), zipBytes, true));
            log.info("Uploaded Skill draft with overwrite, skillName={}", ref.getSkillName());
        }

        productRepository.save(product);
    }

    private SkillConfig resolveSkillConfig(Product product) {
        if (product.getFeature() == null || product.getFeature().getSkillConfig() == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Skill registry is not configured");
        }
        return product.getFeature().getSkillConfig();
    }

    @Override
    public void deleteSkill(String productId) {
        deleteSkill(productId, false);
    }

    @Override
    public void deleteSkill(String productId, boolean ignoreError) {
        Product product = findProduct(productId);
        SkillConfig config = product.getFeature().getSkillConfig();
        if (config != null && config.getRegistryType() == SkillRegistryType.AIREGISTRY) {
            if (Strings.isNotBlank(config.getSkillName())) {
                try {
                    aiRegistrySkillService.deleteSkill(
                            config.getAiRegistryId(), config.getNamespace(), config.getSkillName());
                } catch (RuntimeException e) {
                    if (!ignoreError) {
                        throw e;
                    }
                    log.warn(
                            "Failed to delete source AIRegistry Skill, ignoring error,"
                                    + " productId={}, skillName={}",
                            productId,
                            config.getSkillName(),
                            e);
                }
                config.setSkillName(null);
                config.setVersionInfos(null);
                config.setLatestVersion(null);
                productRepository.save(product);
            }
            return;
        }

        SkillRef ref = getSkillRef(productId, false);

        if (ref == null || Strings.isBlank(ref.getSkillName())) {
            return;
        }
        try {
            execute(
                    ref.getNacosId(),
                    s -> {
                        s.deleteSkill(ref.getNamespace(), ref.getSkillName());
                        return null;
                    });
        } catch (RuntimeException e) {
            if (!ignoreError) {
                throw e;
            }
            log.warn(
                    "Failed to delete source Skill, ignoring error, productId={}, skillName={}",
                    productId,
                    ref.getSkillName(),
                    e);
        }

        config.setSkillName(null);
        config.setVersionInfos(null);
        config.setLatestVersion(null);

        productRepository.save(product);
    }

    @Override
    public List<FileTreeNode> getFileTree(String productId, String version) {
        Product product = findProduct(productId);
        SkillConfig config = product.getFeature().getSkillConfig();
        if (config != null && config.getRegistryType() == SkillRegistryType.AIREGISTRY) {
            if (Strings.isBlank(config.getSkillName())) {
                return Collections.emptyList();
            }
            version = validateAndResolveVersion(productId, version);
            Skill skill =
                    aiRegistrySkillService.getSkillVersion(
                            config.getAiRegistryId(),
                            config.getNamespace(),
                            config.getSkillName(),
                            version);
            return FileTreeBuilder.build(skill);
        }

        SkillRef ref = getSkillRef(productId, false);
        if (ref == null || Strings.isBlank(ref.getSkillName())) {
            return Collections.emptyList();
        }

        version = validateAndResolveVersion(productId, version);

        try {
            Skill skill = fetchSkill(ref, version);
            return FileTreeBuilder.build(skill);
        } catch (Exception e) {
            log.warn(
                    "Failed to fetch file tree for Skill, skillName={}, errorMessage={}",
                    ref.getSkillName(),
                    e.getMessage(),
                    e);
            return Collections.emptyList();
        }
    }

    @Override
    public FileContentResult getFileContent(String productId, String path, String version) {
        version = validateAndResolveVersion(productId, version);

        Product product = findProduct(productId);
        SkillConfig config = product.getFeature().getSkillConfig();
        if (config != null && config.getRegistryType() == SkillRegistryType.AIREGISTRY) {
            Skill skill =
                    aiRegistrySkillService.getSkillVersion(
                            config.getAiRegistryId(),
                            config.getNamespace(),
                            config.getSkillName(),
                            version);
            return getFileContentFromSkill(skill, path);
        }

        SkillRef ref = getSkillRef(productId, true);
        Skill skill = fetchSkill(ref, version);
        return getFileContentFromSkill(skill, path);
    }

    private FileContentResult getFileContentFromSkill(Skill skill, String path) {
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
        String skillNamePrefix = Strings.isNotBlank(skill.getName()) ? skill.getName() + "/" : "";

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
                    String content = resource.getContent() == null ? "" : resource.getContent();

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
        SkillConfig config = product.getFeature().getSkillConfig();
        if (config != null && config.getRegistryType() == SkillRegistryType.AIREGISTRY) {
            if (Strings.isBlank(config.getSkillName())) {
                return Collections.emptyList();
            }
            List<VersionResult> results =
                    aiRegistrySkillService.listVersions(
                            config.getAiRegistryId(), config.getNamespace(), config.getSkillName());
            String latestVersion =
                    results.stream()
                            .filter(version -> Boolean.TRUE.equals(version.getIsLatest()))
                            .map(VersionResult::getVersion)
                            .findFirst()
                            .orElse(null);
            if (!Objects.equals(config.getLatestVersion(), latestVersion)) {
                config.setLatestVersion(latestVersion);
                productRepository.save(product);
            }
            results.forEach(
                    version ->
                            version.setAuthor(resolveVersionAuthor(config, version.getVersion())));
            syncProductStatus(product, results);
            if (!contextHolder.isAdministrator()) {
                results = results.stream().filter(v -> "online".equals(v.getStatus())).toList();
            }
            return results;
        }

        SkillRef ref = getSkillRef(productId, false);

        if (ref == null || Strings.isBlank(ref.getSkillName())) {
            return Collections.emptyList();
        }

        SkillMeta meta;
        try {
            meta =
                    execute(
                            ref.getNacosId(),
                            s -> s.getSkillMeta(ref.getNamespace(), ref.getSkillName()));
        } catch (Exception e) {
            log.warn(
                    "Skill not found in Nacos, returning empty versions, skillName={}",
                    ref.getSkillName());
            return Collections.emptyList();
        }

        if (meta == null || CollectionUtils.isEmpty(meta.getVersions())) {
            return Collections.emptyList();
        }

        String latestLabel = null;
        if (meta.getLabels() != null) {
            latestLabel = meta.getLabels().get("latest");
        }
        final String latestVersion = latestLabel;
        if (!Objects.equals(config.getLatestVersion(), latestVersion)) {
            config.setLatestVersion(latestVersion);
            productRepository.save(product);
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
                                                .status(
                                                        VersionResult.resolveStatus(
                                                                v.getStatus(),
                                                                v.getPublishPipelineInfo(),
                                                                false))
                                                .updateTime(v.getUpdateTime())
                                                .downloadCount(v.getDownloadCount())
                                                .author(
                                                        resolveVersionAuthor(
                                                                config, v.getVersion()))
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
            results = results.stream().filter(v -> "online".equals(v.getStatus())).toList();
        }

        return results;
    }

    private String resolveVersionAuthor(SkillConfig config, String version) {
        VersionInfo info =
                config.getVersionInfos() == null ? null : config.getVersionInfos().get(version);
        return info == null ? null : info.getAuthor();
    }

    private void syncProductStatus(Product product, List<VersionResult> versions) {
        boolean hasOnline = versions.stream().anyMatch(v -> "online".equals(v.getStatus()));
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
    }

    private void submitVersion(String productId, String version) {
        Product product = findProduct(productId);
        SkillConfig config = product.getFeature().getSkillConfig();
        if (config != null && config.getRegistryType() == SkillRegistryType.AIREGISTRY) {
            String submittedVersion =
                    aiRegistrySkillService.submit(
                            config.getAiRegistryId(),
                            config.getNamespace(),
                            config.getSkillName(),
                            version);
            log.info(
                    "Submitted AIRegistry Skill, skillName={}, version={}",
                    config.getSkillName(),
                    submittedVersion);
            return;
        }

        SkillRef ref = getSkillRef(productId, true);

        String submittedVersion =
                execute(
                        ref.getNacosId(),
                        s -> s.submit(ref.getNamespace(), ref.getSkillName(), version));
        log.info("Submitted Skill, skillName={}, version={}", ref.getSkillName(), submittedVersion);
    }

    private void publishApprovedVersion(String productId, String version) {
        Product product = findProduct(productId);
        SkillConfig config = product.getFeature().getSkillConfig();
        if (config != null && config.getRegistryType() == SkillRegistryType.AIREGISTRY) {
            aiRegistrySkillService.publish(
                    config.getAiRegistryId(),
                    config.getNamespace(),
                    config.getSkillName(),
                    version,
                    true);
            syncProductStatus(product, listVersions(productId));
            return;
        }

        SkillRef ref = getSkillRef(productId, true);
        execute(
                ref.getNacosId(),
                s -> {
                    s.publish(ref.getNamespace(), ref.getSkillName(), version, true);
                    return null;
                });
        log.info("Published Skill, skillName={}, version={}", ref.getSkillName(), version);

        syncProductStatusAfterVersionChange(product, ref);
    }

    @Override
    public void updateVersion(String productId, String version, UpdateSkillVersionParam param) {
        if (param.getAuthor() != null) {
            updateVersionAuthor(productId, version, param.getAuthor());
            return;
        }

        if (Boolean.TRUE.equals(param.getLatest())) {
            setLatestVersion(productId, version);
            return;
        }

        String status = param.getStatus();
        if ("reviewing".equals(status)) {
            submitVersion(productId, version);
            return;
        }
        if ("online".equals(status)) {
            // Force publish bypasses the normal review pipeline and can update the latest label.
            if (Boolean.TRUE.equals(param.getForce())) {
                forcePublishVersion(productId, version, param.getUpdateLatestLabel());
                return;
            }

            // "online" means publishing an approved review result for approved versions,
            // but only toggling online status for versions that already left the review pipeline.
            boolean approved =
                    listVersions(productId).stream()
                            .anyMatch(
                                    item ->
                                            version.equals(item.getVersion())
                                                    && "approved".equals(item.getStatus()));
            if (approved) {
                publishApprovedVersion(productId, version);
            } else {
                changeVersionStatus(productId, version, true);
            }
            return;
        }
        if ("offline".equals(status)) {
            changeVersionStatus(productId, version, false);
            return;
        }
        throw new BusinessException(
                ErrorCode.INVALID_PARAMETER, "Unsupported Skill version update");
    }

    private void updateVersionAuthor(String productId, String version, String author) {
        Product product = findProduct(productId);
        SkillConfig config = resolveSkillConfig(product);
        boolean versionExists =
                listVersions(productId).stream()
                        .anyMatch(item -> version.equals(item.getVersion()));
        if (!versionExists) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "version", version);
        }
        Map<String, VersionInfo> versionInfos = config.getVersionInfos();
        if (Strings.isBlank(author)) {
            if (versionInfos != null) {
                versionInfos.remove(version);
                if (versionInfos.isEmpty()) {
                    config.setVersionInfos(null);
                }
            }
        } else {
            if (versionInfos == null) {
                versionInfos = new HashMap<>();
                config.setVersionInfos(versionInfos);
            }
            VersionInfo info = versionInfos.getOrDefault(version, VersionInfo.builder().build());
            info.setAuthor(author.trim());
            versionInfos.put(version, info);
        }
        productRepository.save(product);
        log.info("Updated Skill version author, productId={}, version={}", productId, version);
    }

    @Override
    public void createDraft(String productId, CreateSkillDraftParam param) {
        Product product = findProduct(productId);
        SkillConfig config = resolveSkillConfig(product);
        String baseVersion = param.getBaseVersion().trim();
        String version = param.getVersion().trim();
        if (config.getRegistryType() == SkillRegistryType.AIREGISTRY) {
            if (Strings.isBlank(config.getAiRegistryId())
                    || Strings.isBlank(config.getNamespace())
                    || Strings.isBlank(config.getSkillName())) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST, "AIRegistry skill config not found");
            }
            validateDraftCreation(
                    aiRegistrySkillService.listVersions(
                            config.getAiRegistryId(), config.getNamespace(), config.getSkillName()),
                    baseVersion,
                    version);
            String draftVersion =
                    aiRegistrySkillService.createDraft(
                            config.getAiRegistryId(),
                            config.getNamespace(),
                            config.getSkillName(),
                            baseVersion,
                            version);
            log.info(
                    "Created AIRegistry Skill draft, skillName={}, baseVersion={}, version={}",
                    config.getSkillName(),
                    baseVersion,
                    draftVersion);
            syncProductStatus(product, listVersions(productId));
            return;
        }

        SkillRef ref = getSkillRef(productId, true);
        if (Strings.isBlank(ref.getSkillName())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, Resources.SKILL, productId);
        }
        SkillMeta meta =
                execute(
                        ref.getNacosId(),
                        s -> s.getSkillMeta(ref.getNamespace(), ref.getSkillName()));
        validateDraftCreation(meta, baseVersion, version);

        String draftVersion =
                execute(
                        ref.getNacosId(),
                        s ->
                                s.createDraft(
                                        ref.getNamespace(),
                                        ref.getSkillName(),
                                        baseVersion,
                                        version));
        log.info(
                "Created Skill draft, skillName={}, baseVersion={}, version={}",
                ref.getSkillName(),
                baseVersion,
                draftVersion);
        syncProductStatusAfterVersionChange(product, ref);
    }

    @Override
    public SkillDraftResult getDraft(String productId) {
        Product product = findProduct(productId);
        SkillConfig config = resolveSkillConfig(product);
        if (config.getRegistryType() == SkillRegistryType.AIREGISTRY) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "AIRegistry does not support reading draft");
        }

        SkillRef ref = getSkillRef(productId, true);
        if (Strings.isBlank(ref.getSkillName())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, Resources.SKILL, productId);
        }

        SkillMeta meta =
                execute(
                        ref.getNacosId(),
                        s -> s.getSkillMeta(ref.getNamespace(), ref.getSkillName()));
        String draftVersion = findDraftVersion(meta);
        if (Strings.isBlank(draftVersion)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "draft", productId);
        }

        Skill skill = fetchSkill(ref, draftVersion);
        return SkillDraftResult.builder()
                .version(draftVersion)
                .skillCard(JsonUtil.convert(skill, JsonNode.class))
                .build();
    }

    @Override
    public void updateDraft(String productId, UpdateSkillDraftParam param) {
        Product product = findProduct(productId);
        SkillConfig config = resolveSkillConfig(product);
        if (config.getRegistryType() == SkillRegistryType.AIREGISTRY) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "AIRegistry does not support updating draft");
        }

        SkillRef ref = getSkillRef(productId, true);
        if (Strings.isBlank(ref.getSkillName())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, Resources.SKILL, productId);
        }

        Skill skill = JsonUtil.convert(param.getSkillCard(), Skill.class);
        if (skill == null || Strings.isBlank(skill.getName())) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "Skill card name cannot be blank");
        }
        if (!ref.getSkillName().equals(skill.getName())) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "Skill card name must match current Skill name");
        }

        SkillMeta meta =
                execute(
                        ref.getNacosId(),
                        s -> s.getSkillMeta(ref.getNamespace(), ref.getSkillName()));
        String draftVersion = findDraftVersion(meta);
        if (Strings.isBlank(draftVersion)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "draft", productId);
        }

        boolean updated =
                execute(
                        ref.getNacosId(),
                        s ->
                                s.updateDraft(
                                        ref.getNamespace(),
                                        JsonUtil.toJson(param.getSkillCard()),
                                        false));
        if (!updated) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Failed to update Skill draft");
        }
        log.info("Updated Skill draft, skillName={}, version={}", ref.getSkillName(), draftVersion);
    }

    private void validateDraftCreation(SkillMeta meta, String baseVersion, String version) {
        if (meta == null || CollectionUtils.isEmpty(meta.getVersions())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "version", baseVersion);
        }

        SkillMeta.SkillVersionSummary base = null;
        for (SkillMeta.SkillVersionSummary item : meta.getVersions()) {
            if (version.equals(item.getVersion())) {
                throw new BusinessException(
                        ErrorCode.CONFLICT, "Skill version already exists: " + version);
            }
            if ("draft".equals(item.getStatus()) || "reviewing".equals(item.getStatus())) {
                throw new BusinessException(
                        ErrorCode.CONFLICT, "Skill already has a draft or reviewing version");
            }
            if (baseVersion.equals(item.getVersion())) {
                base = item;
            }
        }

        if (base == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "version", baseVersion);
        }
    }

    private void validateDraftCreation(
            List<VersionResult> versions, String baseVersion, String version) {
        if (CollectionUtils.isEmpty(versions)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "version", baseVersion);
        }

        VersionResult base = null;
        for (VersionResult item : versions) {
            if (version.equals(item.getVersion())) {
                throw new BusinessException(
                        ErrorCode.CONFLICT, "Skill version already exists: " + version);
            }
            if ("draft".equals(item.getStatus()) || "reviewing".equals(item.getStatus())) {
                throw new BusinessException(
                        ErrorCode.CONFLICT, "Skill already has a draft or reviewing version");
            }
            if (baseVersion.equals(item.getVersion())) {
                base = item;
            }
        }

        if (base == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "version", baseVersion);
        }
    }

    private String findDraftVersion(SkillMeta meta) {
        if (meta == null || CollectionUtils.isEmpty(meta.getVersions())) {
            return null;
        }
        for (SkillMeta.SkillVersionSummary item : meta.getVersions()) {
            if ("draft"
                    .equals(
                            VersionResult.resolveStatus(
                                    item.getStatus(), item.getPublishPipelineInfo(), false))) {
                return item.getVersion();
            }
        }
        return null;
    }

    private void changeVersionStatus(String productId, String version, boolean online) {
        Product product = findProduct(productId);
        SkillConfig config = product.getFeature().getSkillConfig();
        if (config != null && config.getRegistryType() == SkillRegistryType.AIREGISTRY) {
            aiRegistrySkillService.changeVersionStatus(
                    config.getAiRegistryId(),
                    config.getNamespace(),
                    config.getSkillName(),
                    version,
                    online);
            syncProductStatus(product, listVersions(productId));
            return;
        }

        SkillRef ref = getSkillRef(productId, true);

        execute(
                ref.getNacosId(),
                s -> {
                    s.changeOnlineStatus(
                            ref.getNamespace(), ref.getSkillName(), "", version, online);
                    return null;
                });
        log.info(
                "Changed Skill version status, skillName={}, version={}, online={}",
                ref.getSkillName(),
                version,
                online);

        syncProductStatusAfterVersionChange(product, ref);
    }

    private void forcePublishVersion(String productId, String version, Boolean updateLatestLabel) {
        Boolean effectiveUpdateLatestLabel =
                updateLatestLabel == null ? Boolean.TRUE : updateLatestLabel;
        Product product = findProduct(productId);
        SkillConfig config = product.getFeature().getSkillConfig();
        if (config != null && config.getRegistryType() == SkillRegistryType.AIREGISTRY) {
            aiRegistrySkillService.forcePublish(
                    config.getAiRegistryId(),
                    config.getNamespace(),
                    config.getSkillName(),
                    version,
                    effectiveUpdateLatestLabel);
            syncProductStatus(product, listVersions(productId));
            return;
        }

        SkillRef ref = getSkillRef(productId, true);

        execute(
                ref.getNacosId(),
                s ->
                        s.forcePublish(
                                ref.getNamespace(),
                                ref.getSkillName(),
                                version,
                                effectiveUpdateLatestLabel));
        log.info("Force-published Skill, skillName={}, version={}", ref.getSkillName(), version);

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
                versions.stream().filter(v -> "online".equals(v.getStatus())).toList();

        if (onlineVersions.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.NOT_FOUND, "version", "No online version available");
        }

        if (Strings.isBlank(version)) {
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
     *   <li>Has online version + non-PUBLISHED -> set to READY</li>
     *   <li>No online version + non-PUBLISHED -> set to PENDING</li>
     *   <li>No online version + PUBLISHED -> downgrade to READY</li>
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
            if (meta != null && !CollectionUtils.isEmpty(meta.getVersions())) {
                hasOnline =
                        meta.getVersions().stream()
                                .anyMatch(
                                        v ->
                                                "online"
                                                        .equals(
                                                                VersionResult.resolveStatus(
                                                                        v.getStatus(),
                                                                        v.getPublishPipelineInfo(),
                                                                        false)));
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
                        "Synced product status, productId={}, previousStatus={}, currentStatus={}",
                        product.getProductId(),
                        current,
                        target);
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to sync product status after version change for Skill, skillName={}",
                    ref.getSkillName(),
                    e);
        }
    }

    @Override
    public void deleteDraft(String productId) {
        Product product = findProduct(productId);
        SkillConfig config = product.getFeature().getSkillConfig();
        if (config != null && config.getRegistryType() == SkillRegistryType.AIREGISTRY) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "AIRegistry does not support deleting draft");
        }

        SkillRef ref = getSkillRef(productId, true);

        boolean deleted =
                execute(
                        ref.getNacosId(),
                        s -> s.deleteDraft(ref.getNamespace(), ref.getSkillName()));
        if (!deleted) {
            log.warn(
                    "Nacos returned false for Skill draft deletion, skillName={}",
                    ref.getSkillName());
        }
        log.info("Deleted Skill draft, skillName={}", ref.getSkillName());

        // Clear skillName if no versions remain after deletion
        try {
            SkillMeta meta =
                    execute(
                            ref.getNacosId(),
                            s -> s.getSkillMeta(ref.getNamespace(), ref.getSkillName()));

            // Auto-publish approved reviewing version to clear the blocking state
            autoPublishReviewingVersion(ref, meta);

            if (meta == null || CollectionUtils.isEmpty(meta.getVersions())) {
                // If no versions remain, delete the skill
                execute(
                        ref.getNacosId(),
                        s -> s.deleteSkill(ref.getNamespace(), ref.getSkillName()));

                if (product.getStatus() != ProductStatus.PUBLISHED) {
                    product.setStatus(ProductStatus.PENDING);
                }
                config.setSkillName(null);
                config.setVersionInfos(null);
                config.setLatestVersion(null);
                productRepository.save(product);
            } else {
                // Versions still remain — sync Product status
                // (auto-publish may have created a new online version)
                syncProductStatusAfterVersionChange(product, ref);
            }
        } catch (Exception e) {
            // Skill no longer exists in Nacos after draft deletion
            log.info(
                    "Skill not found after draft deletion, clearing reference, skillName={}",
                    ref.getSkillName());
            config.setSkillName(null);
            config.setVersionInfos(null);
            config.setLatestVersion(null);
            productRepository.save(product);
        }
    }

    private void setLatestVersion(String productId, String version) {
        Product product = findProduct(productId);
        SkillConfig config = product.getFeature().getSkillConfig();
        if (config != null && config.getRegistryType() == SkillRegistryType.AIREGISTRY) {
            aiRegistrySkillService.setLatestVersion(
                    config.getAiRegistryId(),
                    config.getNamespace(),
                    config.getSkillName(),
                    version);
            config.setLatestVersion(version);
            productRepository.save(product);
            return;
        }

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
                                ref.getNamespace(), ref.getSkillName(), JsonUtil.toJson(labels)));
        config.setLatestVersion(version);
        productRepository.save(product);
        log.info("Set latest Skill version, skillName={}, version={}", ref.getSkillName(), version);
    }

    private boolean ensurePublished(SkillRef ref, String version) {
        SkillMeta meta;
        try {
            meta =
                    execute(
                            ref.getNacosId(),
                            s -> s.getSkillMeta(ref.getNamespace(), ref.getSkillName()));
        } catch (Exception e) {
            return false;
        }
        if (meta == null) {
            return false;
        }
        // Check if the version is still the reviewingVersion in Nacos metadata
        if (version.equals(meta.getReviewingVersion())) {
            execute(
                    ref.getNacosId(),
                    s -> s.publish(ref.getNamespace(), ref.getSkillName(), version, false));
            log.info(
                    "Auto-published Skill version to clear reviewing state, skillName={},"
                            + " version={}",
                    ref.getSkillName(),
                    version);
            return true;
        }
        return false;
    }

    @Override
    public void downloadPackage(String productId, String version, HttpServletResponse response)
            throws IOException {
        Product product = findProduct(productId);
        SkillConfig config = product.getFeature().getSkillConfig();
        if (config != null && config.getRegistryType() == SkillRegistryType.AIREGISTRY) {
            String resolvedVersion = version;
            if (Strings.isBlank(resolvedVersion)) {
                List<VersionResult> versions =
                        aiRegistrySkillService.listVersions(
                                config.getAiRegistryId(),
                                config.getNamespace(),
                                config.getSkillName());
                resolvedVersion =
                        versions.stream()
                                .filter(item -> Boolean.TRUE.equals(item.getIsLatest()))
                                .map(VersionResult::getVersion)
                                .findFirst()
                                .orElse(null);
                if (Strings.isBlank(resolvedVersion)) {
                    throw new BusinessException(
                            ErrorCode.INVALID_PARAMETER,
                            "version is required when latest version is not configured");
                }
                if (!Objects.equals(config.getLatestVersion(), resolvedVersion)) {
                    config.setLatestVersion(resolvedVersion);
                    productRepository.save(product);
                }
            }
            byte[] zipBytes =
                    aiRegistrySkillService.downloadZip(
                            config.getAiRegistryId(),
                            config.getNamespace(),
                            config.getSkillName(),
                            resolvedVersion);
            response.setContentType("application/zip");
            response.setHeader(
                    "Content-Disposition",
                    "attachment; filename=\"" + config.getSkillName() + ".zip\"");
            response.getOutputStream().write(zipBytes);
            return;
        }

        SkillRef ref = getSkillRef(productId, true);
        String resolvedVersion = version;
        if (Strings.isBlank(resolvedVersion)) {
            SkillMeta meta =
                    execute(
                            ref.getNacosId(),
                            s -> s.getSkillMeta(ref.getNamespace(), ref.getSkillName()));
            resolvedVersion =
                    meta == null || meta.getLabels() == null
                            ? null
                            : meta.getLabels().get("latest");
            if (Strings.isBlank(resolvedVersion)) {
                throw new BusinessException(
                        ErrorCode.INVALID_PARAMETER,
                        "version is required when latest version is not configured");
            }
            if (!Objects.equals(config.getLatestVersion(), resolvedVersion)) {
                config.setLatestVersion(resolvedVersion);
                productRepository.save(product);
            }
        }

        // Download through the Nacos HTTP API first so Nacos can update its download count.
        downloadFromNacos(ref, resolvedVersion, response);
    }

    /**
     * Downloads the Skill ZIP package through the Nacos HTTP API so Nacos can update its download
     * count.
     * API: GET /nacos/v3/admin/ai/skills/version/download?namespaceId=xxx&skillName=xxx&version=xxx
     */
    private void downloadFromNacos(SkillRef ref, String version, HttpServletResponse response)
            throws IOException {
        if (Strings.isBlank(version)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "version is required");
        }
        try {
            NacosInstance nacosInstance = nacosService.findNacosInstanceById(ref.getNacosId());
            String nacosBaseUrl =
                    Strings.isNotBlank(nacosInstance.getDisplayServerUrl())
                            ? nacosInstance.getDisplayServerUrl()
                            : nacosInstance.getServerUrl();
            String normalizedBaseUrl =
                    nacosBaseUrl.endsWith("/") ? nacosBaseUrl : nacosBaseUrl + "/";
            String nacosContextPath = normalizedBaseUrl.endsWith("/nacos/") ? "" : "nacos/";

            StringBuilder urlBuilder = new StringBuilder();
            urlBuilder
                    .append(normalizedBaseUrl)
                    .append(nacosContextPath)
                    .append("v3/admin/ai/skills/version/download?")
                    .append("namespaceId=")
                    .append(URLEncoder.encode(ref.getNamespace(), StandardCharsets.UTF_8))
                    .append("&skillName=")
                    .append(URLEncoder.encode(ref.getSkillName(), StandardCharsets.UTF_8))
                    .append("&version=")
                    .append(URLEncoder.encode(version, StandardCharsets.UTF_8));

            if (Strings.isNotBlank(nacosInstance.getUsername())
                    && Strings.isNotBlank(nacosInstance.getPassword())) {
                urlBuilder
                        .append("&username=")
                        .append(
                                URLEncoder.encode(
                                        nacosInstance.getUsername(), StandardCharsets.UTF_8));
                urlBuilder
                        .append("&password=")
                        .append(
                                URLEncoder.encode(
                                        nacosInstance.getPassword(), StandardCharsets.UTF_8));
            }

            String downloadUrl = urlBuilder.toString();
            log.info(
                    "Calling Nacos skill download API, dependency=Nacos,"
                            + " operation=downloadSkillZip, nacosId={}, namespace={}, skillName={},"
                            + " version={}, serverUrl={}, username={}",
                    ref.getNacosId(),
                    ref.getNamespace(),
                    ref.getSkillName(),
                    version,
                    nacosInstance.getServerUrl(),
                    nacosInstance.getUsername());

            URL url = new URL(downloadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                String responseBody = "";
                try (var errorStream = conn.getErrorStream()) {
                    if (errorStream != null) {
                        byte[] bodyBytes = errorStream.readNBytes(2048);
                        responseBody =
                                new String(bodyBytes, StandardCharsets.UTF_8)
                                        .replace('\n', ' ')
                                        .replace('\r', ' ')
                                        .trim();
                        if (bodyBytes.length == 2048) {
                            responseBody += "...(truncated)";
                        }
                    }
                } catch (IOException readError) {
                    responseBody = "Failed to read response body: " + readError.getMessage();
                }
                log.warn(
                        "Nacos skill download API returned non-OK status, dependency=Nacos,"
                                + " operation=downloadSkillZip, nacosId={}, namespace={},"
                                + " skillName={}, status={}, responseBody={}",
                        ref.getNacosId(),
                        ref.getNamespace(),
                        ref.getSkillName(),
                        responseCode,
                        responseBody);
                fallbackToLocalDownload(ref, version, response);
                return;
            }

            response.setContentType("application/zip");
            String encodedName =
                    URLEncoder.encode(ref.getSkillName() + ".zip", StandardCharsets.UTF_8)
                            .replace("+", "%20");
            response.setHeader(
                    "Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);

            try (var input = conn.getInputStream();
                    var output = response.getOutputStream()) {
                input.transferTo(output);
            }
            log.info(
                    "Downloaded skill ZIP from Nacos, dependency=Nacos,"
                            + " operation=downloadSkillZip, nacosId={}, namespace={},"
                            + " skillName={}, version={}",
                    ref.getNacosId(),
                    ref.getNamespace(),
                    ref.getSkillName(),
                    version);
        } catch (Exception e) {
            log.warn(
                    "Failed to download skill ZIP from Nacos, falling back to local generation,"
                            + " dependency=Nacos, operation=downloadSkillZip, nacosId={},"
                            + " namespace={}, skillName={}, errorType={}, errorMessage={}",
                    ref.getNacosId(),
                    ref.getNamespace(),
                    ref.getSkillName(),
                    e.getClass().getSimpleName(),
                    e.getMessage(),
                    e);
            fallbackToLocalDownload(ref, version, response);
        }
    }

    /**
     * Fallback path: generates the ZIP package locally without increasing the Nacos download count.
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
                        Strings.isBlank(version)
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
        if (Strings.isBlank(ref.getSkillName())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, Resources.SKILL, ref.getSkillName());
        }
        return execute(
                ref.getNacosId(),
                s -> {
                    String targetVersion =
                            Strings.isBlank(version)
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
            if (meta == null || CollectionUtils.isEmpty(meta.getVersions())) {
                throw new BusinessException(ErrorCode.NOT_FOUND, Resources.SKILL, skillName);
            }
            // Find latest version from labels
            if (meta.getLabels() != null && Strings.isNotBlank(meta.getLabels().get("latest"))) {
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
        if (Strings.isNotBlank(type)) {
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
                        .filter(sc -> Strings.isNotBlank(sc.getNacosId()))
                        .map(sc -> new SkillRef().convertFrom(sc))
                        .orElse(null);

        if (force && result == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    String.format("Skill config not found for product: %s", productId));
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
        if (Strings.isBlank(reviewing)) {
            return;
        }
        try {
            execute(
                    ref.getNacosId(),
                    s -> s.publish(ref.getNamespace(), ref.getSkillName(), reviewing, true));
            log.info(
                    "Auto-published reviewing Skill version to unblock draft operations,"
                            + " version={}, skillName={}",
                    reviewing,
                    ref.getSkillName());
        } catch (Exception e) {
            log.warn(
                    "Failed to auto-publish reviewing Skill version, version={}, skillName={},"
                            + " errorMessage={}",
                    reviewing,
                    ref.getSkillName(),
                    e.getMessage(),
                    e);
        }
    }

    private <T> T execute(String nacosId, NacosOperation<T> operation) {
        try {
            AiMaintainerService service = nacosService.getAiMaintainerService(nacosId);
            return operation.execute(service.skill());
        } catch (NacosException e) {
            log.error(
                    "Nacos Skill operation failed, nacosId={}, errorMessage={}",
                    nacosId,
                    e.getMessage(),
                    e);
            throw toBusinessException(e);
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
                || config.getRegistryType() == SkillRegistryType.AIREGISTRY
                || Strings.isBlank(config.getNacosId())
                || Strings.isBlank(config.getSkillName())) {
            return null;
        }

        try {
            var nacos = nacosService.getNacosInstance(config.getNacosId());
            if (nacos == null || Strings.isBlank(nacos.getServerUrl())) {
                return null;
            }
            URL nacosUrl =
                    new URL(
                            Strings.isNotBlank(nacos.getDisplayServerUrl())
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
            log.warn(
                    "Failed to get CLI download info for skill product, productId={}",
                    productId,
                    e);
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
                    log.info("Skill product already exists, skipping import, name={}", name);
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
                log.info("Imported skill product from Nacos, name={}", name);
            }
        } catch (Exception e) {
            log.error("Failed to import skills from Nacos, errorMessage={}", e.getMessage(), e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    String.format("Failed to import skills: %s", e.getMessage()));
        }

        log.info(
                "Imported skills from Nacos, successCount={}, skippedCount={}",
                successCount,
                skippedCount);

        return ImportResult.builder()
                .resourceType("skill")
                .successCount(successCount)
                .skippedCount(skippedCount)
                .build();
    }
}
