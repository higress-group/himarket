package com.alibaba.himarket.service.acp;

import com.alibaba.himarket.dto.result.skill.SkillFileContentResult;
import com.alibaba.himarket.service.SkillPackageService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * 会话配置解析服务。
 *
 * <p>将前端传入的标识符（{@link CliSessionConfig}）解析为完整的配置信息（{@link ResolvedSessionConfig}）。
 *
 * <p>从 {@code AcpWebSocketHandler.prepareConfigFiles()} 中提取，使配置解析可独立测试。
 * 封装了模型配置查询、MCP 连接信息解析、Skill 文件下载等数据库/远程调用。
 */
@Service
@RequiredArgsConstructor
public class SessionConfigResolver {

    private static final Logger logger = LoggerFactory.getLogger(SessionConfigResolver.class);

    private final ModelConfigResolver modelConfigResolver;
    private final McpConfigResolver mcpConfigResolver;
    private final SkillPackageService skillPackageService;

    /**
     * 解析会话配置。
     *
     * @param sessionConfig 前端传入的配置（仅含 productId 等标识符）
     * @param userId 当前用户 ID（用于设置 Security 上下文）
     * @return 已解析的完整配置
     */
    public ResolvedSessionConfig resolve(CliSessionConfig sessionConfig, String userId) {
        ResolvedSessionConfig resolved = new ResolvedSessionConfig();
        resolved.setAuthToken(sessionConfig.getAuthToken());

        // 1. 模型配置解析
        resolveModelConfig(sessionConfig, userId, resolved);

        // 2. MCP 配置解析
        resolveMcpConfig(sessionConfig, resolved);

        // 3. Skill 文件下载
        resolveSkillConfig(sessionConfig, resolved);

        return resolved;
    }

    /**
     * 解析模型配置。
     *
     * <p>设置 Spring Security 上下文 → 调用 {@link ModelConfigResolver#resolve(String)} →
     * 清理 Security 上下文。异常时记录错误日志但不抛出，customModelConfig 保持 null。
     */
    private void resolveModelConfig(
            CliSessionConfig config, String userId, ResolvedSessionConfig resolved) {
        if (config.getModelProductId() == null || config.getModelProductId().isBlank()) {
            logger.info("[Sandbox-Config] 未提供 modelProductId，跳过模型配置解析");
            return;
        }
        logger.info("[Sandbox-Config] 开始解析模型配置: modelProductId={}", config.getModelProductId());
        try {
            // 设置 Spring Security 上下文，以便 ModelConfigResolver 能获取当前用户
            // principal 必须是 String 类型的 userId，因为 ContextHolder.getUser() 期望如此
            SecurityContextHolder.getContext()
                    .setAuthentication(
                            new UsernamePasswordAuthenticationToken(
                                    userId, null, Collections.emptyList()));

            CustomModelConfig customModelConfig =
                    modelConfigResolver.resolve(config.getModelProductId());
            if (customModelConfig != null) {
                resolved.setCustomModelConfig(customModelConfig);
                logger.info(
                        "[Sandbox-Config] 模型配置解析成功: modelProductId={}, baseUrl={},"
                                + " hasApiKey={}",
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
        } finally {
            // 清理 Security 上下文
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 解析 MCP 配置。
     *
     * <p>调用 {@link McpConfigResolver#resolve(List)} 解析 MCP Server 连接信息。
     * 异常时记录错误日志并跳过。
     */
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
     * 解析 Skill 配置（下载文件内容）。
     *
     * <p>遍历 skills 列表，对每个有 productId 的条目调用
     * {@link SkillPackageService#getAllFiles(String)} 下载文件。
     * 单项失败时记录错误日志并跳过该项，继续处理其余项。
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
                List<SkillFileContentResult> files =
                        skillPackageService.getAllFiles(skillEntry.getProductId());
                ResolvedSessionConfig.ResolvedSkillEntry resolvedSkill =
                        new ResolvedSessionConfig.ResolvedSkillEntry();
                resolvedSkill.setName(skillEntry.getName());
                resolvedSkill.setFiles(files);
                resolvedSkills.add(resolvedSkill);
            } catch (Exception e) {
                logger.error(
                        "[Sandbox-Config] Skill 文件下载失败, 跳过: productId={}, name={}," + " error={}",
                        skillEntry.getProductId(),
                        skillEntry.getName(),
                        e.getMessage(),
                        e);
            }
        }
        resolved.setSkills(resolvedSkills);
    }
}
