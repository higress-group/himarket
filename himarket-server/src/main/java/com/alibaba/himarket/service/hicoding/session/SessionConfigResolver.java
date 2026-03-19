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
 * 会话配置解析服务。
 *
 * <p>将前端传入的标识符（{@link CliSessionConfig}）解析为完整的配置信息（{@link ResolvedSessionConfig}）。
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
            logger.info("[Sandbox-Config] 未提供 modelProductId，跳过模型配置解析");
            return;
        }
        logger.info("[Sandbox-Config] 开始解析模型配置: modelProductId={}", config.getModelProductId());
        try {
            CustomModelConfig customModelConfig =
                    modelConfigResolver.resolve(config.getModelProductId(), userId);
            if (customModelConfig != null) {
                resolved.setCustomModelConfig(customModelConfig);
                logger.info(
                        "[Sandbox-Config] 模型配置解析成功: modelProductId={}, baseUrl={}, hasApiKey={}",
                        config.getModelProductId(),
                        customModelConfig.getBaseUrl(),
                        customModelConfig.getApiKey() != null);
            } else {
                logger.warn(
                        "[Sandbox-Config] 模型配置解析返回 null: modelProductId={}",
                        config.getModelProductId());
            }
        } catch (Exception e) {
            logger.error(
                    "[Sandbox-Config] 模型配置解析失败: modelProductId={}, error={}",
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
            logger.error("[Sandbox-Config] MCP 配置解析失败: error={}", e.getMessage(), e);
        }
    }

    /**
     * 解析 Skill 配置：从 Product 读取 SkillConfig 坐标 + 从 NacosInstance 提取凭证。
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
                            "[Sandbox-Config] Skill Product 不存在, 跳过: productId={}",
                            skillEntry.getProductId());
                    continue;
                }
                ProductFeature feature = product.getFeature();
                if (feature == null || feature.getSkillConfig() == null) {
                    logger.warn(
                            "[Sandbox-Config] Skill Product 无 SkillConfig, 跳过: productId={}",
                            skillEntry.getProductId());
                    continue;
                }
                SkillConfig skillConfig = feature.getSkillConfig();
                if (skillConfig.getNacosId() == null || skillConfig.getSkillName() == null) {
                    logger.warn(
                            "[Sandbox-Config] SkillConfig 坐标不完整, 跳过: productId={}",
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
                        "[Sandbox-Config] Skill 坐标解析失败, 跳过: productId={}, name={}, error={}",
                        skillEntry.getProductId(),
                        skillEntry.getName(),
                        e.getMessage(),
                        e);
            }
        }
        resolved.setSkills(resolvedSkills);
    }
}
