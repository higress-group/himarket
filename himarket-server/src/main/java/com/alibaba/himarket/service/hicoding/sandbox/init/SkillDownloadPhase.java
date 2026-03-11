package com.alibaba.himarket.service.hicoding.sandbox.init;

import com.alibaba.himarket.service.hicoding.sandbox.ExecResult;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxInfo;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxProvider;
import com.alibaba.himarket.service.hicoding.session.ResolvedSessionConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skill 下载阶段。
 * 在 ConfigInjectionPhase（300）之后、SidecarConnectPhase（400）之前执行。
 * 按 nacosId 分组，通过 sidecar exec API 调用 nacos-cli skill-get 批量下载 Skill 文件。
 */
public class SkillDownloadPhase implements InitPhase {

    private static final Logger logger = LoggerFactory.getLogger(SkillDownloadPhase.class);
    private static final Duration EXEC_TIMEOUT = Duration.ofSeconds(60);
    private static final String NACOS_ENV_DIR = ".nacos";

    private static final Map<String, String> PROVIDER_SKILLS_DIR =
            Map.of(
                    "qodercli", ".qoder/skills/",
                    "claude-code", ".claude/skills/",
                    "qwen-code", ".qwen/skills/",
                    "opencode", ".opencode/skills/");

    @Override
    public String name() {
        return "skill-download";
    }

    @Override
    public int order() {
        return 350;
    }

    @Override
    public boolean shouldExecute(InitContext context) {
        ResolvedSessionConfig resolved = context.getResolvedSessionConfig();
        return resolved != null && resolved.getSkills() != null && !resolved.getSkills().isEmpty();
    }

    @Override
    public void execute(InitContext context) throws InitPhaseException {
        ResolvedSessionConfig resolved = context.getResolvedSessionConfig();
        List<ResolvedSessionConfig.ResolvedSkillEntry> skills = resolved.getSkills();
        String providerKey = context.getRuntimeConfig().getProviderKey();
        String skillsDir = PROVIDER_SKILLS_DIR.getOrDefault(providerKey, "skills/");
        SandboxProvider provider = context.getProvider();
        SandboxInfo info = context.getSandboxInfo();

        // 按 nacosId 分组
        Map<String, List<ResolvedSessionConfig.ResolvedSkillEntry>> byNacosId =
                skills.stream()
                        .collect(
                                Collectors.groupingBy(
                                        ResolvedSessionConfig.ResolvedSkillEntry::getNacosId,
                                        LinkedHashMap::new,
                                        Collectors.toList()));

        logger.info(
                "[SkillDownload] 开始下载 {} 个 Skill ({} 个 Nacos 实例), provider={}, skillsDir={}",
                skills.size(),
                byNacosId.size(),
                providerKey,
                skillsDir);

        int successGroups = 0;
        for (var entry : byNacosId.entrySet()) {
            String nacosId = entry.getKey();
            List<String> skillNames =
                    entry.getValue().stream()
                            .map(ResolvedSessionConfig.ResolvedSkillEntry::getSkillName)
                            .toList();
            String nacosEnvPath = NACOS_ENV_DIR + "/nacos-env-" + nacosId + ".yaml";

            // 构建参数: skill-get skill1 skill2 ... --config path -o dir
            List<String> args = new ArrayList<>();
            args.add("skill-get");
            args.addAll(skillNames);
            args.add("--config");
            args.add(nacosEnvPath);
            args.add("-o");
            args.add(skillsDir);

            try {
                ExecResult result = provider.exec(info, "nacos-cli", args, EXEC_TIMEOUT);

                if (result.exitCode() != 0) {
                    logger.warn(
                            "[SkillDownload] Skill 下载失败: nacosId={}, skills={}, exitCode={},"
                                    + " stderr={}",
                            nacosId,
                            skillNames,
                            result.exitCode(),
                            result.stderr());
                } else {
                    successGroups++;
                    logger.info(
                            "[SkillDownload] Skill 下载成功: nacosId={}, skills={}",
                            nacosId,
                            skillNames);
                }
            } catch (Exception e) {
                logger.warn(
                        "[SkillDownload] Skill 下载异常: nacosId={}, skills={}, error={}",
                        nacosId,
                        skillNames,
                        e.getMessage());
            }
        }

        logger.info("[SkillDownload] 下载完成: {}/{} 分组成功", successGroups, byNacosId.size());
    }

    @Override
    public boolean verify(InitContext context) {
        return true;
    }

    @Override
    public RetryPolicy retryPolicy() {
        return RetryPolicy.none();
    }
}
