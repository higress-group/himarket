package com.alibaba.himarket.service.hicoding.sandbox.init;

import com.alibaba.himarket.service.hicoding.sandbox.ExecResult;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxInfo;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxProvider;
import com.alibaba.himarket.service.hicoding.session.ResolvedSessionConfig;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skill download phase.
 *
 * <p>Runs after ConfigInjectionPhase (300) and before SidecarConnectPhase (400). Skills are grouped
 * by nacosId and downloaded in batches through the Sidecar exec API with
 * {@code nacos-cli skill-get}.
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

        // exec does not convert relative paths automatically, while writeFile does.
        String workspacePath = info.workspacePath();

        // Group by nacosId.
        Map<String, List<ResolvedSessionConfig.ResolvedSkillEntry>> byNacosId =
                new LinkedHashMap<>();
        for (ResolvedSessionConfig.ResolvedSkillEntry skill : skills) {
            String nacosId =
                    Objects.requireNonNull(
                            skill.getNacosId(), "element cannot be mapped to a null key");
            byNacosId.computeIfAbsent(nacosId, key -> new ArrayList<>()).add(skill);
        }

        logger.info(
                "Starting skill download, skillCount={}, nacosInstanceCount={}, provider={},"
                        + " skillsDir={}",
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
            // Use absolute paths because exec cwd may not be the user's workspace.
            String nacosEnvPath =
                    toAbsolutePath(
                            workspacePath, NACOS_ENV_DIR + "/nacos-env-" + nacosId + ".yaml");
            String absoluteSkillsDir = toAbsolutePath(workspacePath, skillsDir);

            // Build args: skill-get skill1 skill2 ... --config path -o dir.
            List<String> args = new ArrayList<>();
            args.add("skill-get");
            args.addAll(skillNames);
            args.add("--config");
            args.add(nacosEnvPath);
            args.add("-o");
            args.add(absoluteSkillsDir);

            try {
                ExecResult result = provider.exec(info, "nacos-cli", args, EXEC_TIMEOUT);

                if (result.exitCode() != 0) {
                    logger.warn(
                            "Skill download command failed, nacosId={}, skills={}, exitCode={},"
                                    + " stderr={}",
                            nacosId,
                            skillNames,
                            result.exitCode(),
                            result.stderr());
                } else {
                    successGroups++;
                    logger.info(
                            "Skill download command succeeded, nacosId={}, skills={}",
                            nacosId,
                            skillNames);
                }
            } catch (Exception e) {
                logger.warn(
                        "Skill download command error, nacosId={}, skills={}, errorMessage={}",
                        nacosId,
                        skillNames,
                        e.getMessage());
            }
        }

        logger.info(
                "Skill download completed, successGroupCount={}, totalGroupCount={}",
                successGroups,
                byNacosId.size());
    }

    @Override
    public boolean verify(InitContext context) {
        return true;
    }

    @Override
    public RetryPolicy retryPolicy() {
        return RetryPolicy.none();
    }

    /**
     * Converts a relative path to an absolute path rooted at workspacePath.
     *
     * <p>Matches the logic in RemoteSandboxProvider.toAbsolutePath.
     */
    private static String toAbsolutePath(String workspacePath, String relativePath) {
        if (workspacePath == null || workspacePath.isEmpty()) {
            return relativePath;
        }
        String cleaned = relativePath;
        if (cleaned.startsWith("./")) {
            cleaned = cleaned.substring(2);
        } else if (cleaned.startsWith("/")) {
            Path normalized = Paths.get(cleaned).normalize();
            if (!normalized.startsWith(Paths.get(workspacePath).normalize())) {
                throw new SecurityException("Path escapes workspace: " + relativePath);
            }
            return normalized.toString();
        }
        String full =
                workspacePath.endsWith("/")
                        ? workspacePath + cleaned
                        : workspacePath + "/" + cleaned;
        Path normalized = Paths.get(full).normalize();
        if (!normalized.startsWith(Paths.get(workspacePath).normalize())) {
            throw new SecurityException("Path escapes workspace: " + relativePath);
        }
        return normalized.toString();
    }
}
