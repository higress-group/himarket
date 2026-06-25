package com.alibaba.himarket.service.hicoding.session;

import com.alibaba.himarket.entity.NacosInstance;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.support.product.ProductFeature;
import com.alibaba.himarket.support.product.SkillConfig;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resolves session configuration.
 *
 * <p>Converts frontend identifiers ({@link CliSessionConfig}) into complete configuration details
 * ({@link ResolvedSessionConfig}).
 */
@Service
@RequiredArgsConstructor
public class SessionConfigResolver {

    private static final Logger logger = LoggerFactory.getLogger(SessionConfigResolver.class);

    private final ModelConfigResolver modelConfigResolver;
    private final McpConfigResolver mcpConfigResolver;
    private final ProductRepository productRepository;
    private final NacosService nacosService;

    public ResolvedSessionConfig resolve(CliSessionConfig sessionConfig, String userId) {
        ResolvedSessionConfig resolved = new ResolvedSessionConfig();
        resolved.setAuthToken(sessionConfig.getAuthToken());
        resolveModelConfig(sessionConfig, userId, resolved);
        resolveMcpConfig(sessionConfig, resolved);
        resolveSkillConfig(sessionConfig, resolved);
        return resolved;
    }

    private void resolveModelConfig(
            CliSessionConfig config, String userId, ResolvedSessionConfig resolved) {
        if (config.getModelProductId() == null || config.getModelProductId().isBlank()) {
            logger.info("Model product ID not provided, skipping model config resolution");
            return;
        }
        logger.info(
                "Resolving sandbox model config, modelProductId={}", config.getModelProductId());
        try {
            CustomModelConfig customModelConfig =
                    modelConfigResolver.resolve(config.getModelProductId(), userId);
            if (customModelConfig != null) {
                resolved.setCustomModelConfig(customModelConfig);
                logger.info(
                        "Resolved sandbox model config, modelProductId={}, baseUrl={}",
                        config.getModelProductId(),
                        customModelConfig.getBaseUrl());
            } else {
                logger.warn(
                        "Sandbox model config resolver returned null, modelProductId={}",
                        config.getModelProductId());
            }
        } catch (Exception e) {
            logger.error(
                    "Failed to resolve sandbox model config, modelProductId={}, errorMessage={}",
                    config.getModelProductId(),
                    e.getMessage(),
                    e);
        }
    }

    private void resolveMcpConfig(CliSessionConfig config, ResolvedSessionConfig resolved) {
        if (config.getMcpServers() == null || config.getMcpServers().isEmpty()) {
            return;
        }
        try {
            List<ResolvedSessionConfig.ResolvedMcpEntry> resolvedMcpServers =
                    mcpConfigResolver.resolve(config.getMcpServers());
            resolved.setMcpServers(resolvedMcpServers);
        } catch (Exception e) {
            logger.error(
                    "Failed to resolve sandbox MCP config, errorMessage={}", e.getMessage(), e);
        }
    }

    /**
     * Resolves Skill configuration from Product SkillConfig coordinates and NacosInstance
     * credentials.
     */
    private void resolveSkillConfig(CliSessionConfig config, ResolvedSessionConfig resolved) {
        if (config.getSkills() == null || config.getSkills().isEmpty()) {
            return;
        }
        List<ResolvedSessionConfig.ResolvedSkillEntry> resolvedSkills = new ArrayList<>();
        for (CliSessionConfig.SkillEntry skillEntry : config.getSkills()) {
            if (skillEntry.getProductId() == null || skillEntry.getProductId().isBlank()) {
                continue;
            }
            try {
                Product product =
                        productRepository.findByProductId(skillEntry.getProductId()).orElse(null);
                if (product == null) {
                    logger.warn(
                            "Skill product not found, skipping sandbox skill entry, productId={}",
                            skillEntry.getProductId());
                    continue;
                }
                ProductFeature feature = product.getFeature();
                if (feature == null || feature.getSkillConfig() == null) {
                    logger.warn(
                            "Skill product has no SkillConfig, skipping sandbox skill entry,"
                                    + " productId={}",
                            skillEntry.getProductId());
                    continue;
                }
                SkillConfig skillConfig = feature.getSkillConfig();
                if (skillConfig.getNacosId() == null || skillConfig.getSkillName() == null) {
                    logger.warn(
                            "SkillConfig coordinates are incomplete, skipping sandbox skill entry,"
                                    + " productId={}",
                            skillEntry.getProductId());
                    continue;
                }

                NacosInstance nacos = nacosService.findNacosInstanceById(skillConfig.getNacosId());

                ResolvedSessionConfig.ResolvedSkillEntry resolvedSkill =
                        new ResolvedSessionConfig.ResolvedSkillEntry();
                resolvedSkill.setName(skillEntry.getName());
                resolvedSkill.setNacosId(skillConfig.getNacosId());
                resolvedSkill.setNamespace(skillConfig.getNamespace());
                resolvedSkill.setSkillName(skillConfig.getSkillName());
                resolvedSkill.setServerAddr(nacos.getServerUrl());
                resolvedSkill.setUsername(nacos.getUsername());
                resolvedSkill.setPassword(nacos.getPassword());
                resolvedSkill.setAccessKey(nacos.getAccessKey());
                resolvedSkill.setSecretKey(nacos.getSecretKey());
                resolvedSkills.add(resolvedSkill);
            } catch (Exception e) {
                logger.error(
                        "Failed to resolve sandbox skill coordinates, skipping entry, productId={},"
                                + " name={}, errorMessage={}",
                        skillEntry.getProductId(),
                        skillEntry.getName(),
                        e.getMessage(),
                        e);
            }
        }
        resolved.setSkills(resolvedSkills);
    }
}
