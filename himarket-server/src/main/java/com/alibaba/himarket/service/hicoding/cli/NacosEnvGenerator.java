package com.alibaba.himarket.service.hicoding.cli;

import com.alibaba.himarket.service.hicoding.session.ResolvedSessionConfig;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * nacos-env.yaml 生成工具。
 * 按 nacosId 分组，为每个 Nacos 实例生成独立的配置文件。
 */
public final class NacosEnvGenerator {

    private static final Logger logger = LoggerFactory.getLogger(NacosEnvGenerator.class);
    private static final String NACOS_DIR = ".nacos";

    private NacosEnvGenerator() {}

    /**
     * 为 Skill 列表生成 nacos-env.yaml 文件。
     * 按 nacosId 分组，每个 nacosId 生成一个 .nacos/nacos-env-{nacosId}.yaml。
     */
    public static void generateNacosEnvFiles(
            String workingDirectory, List<ResolvedSessionConfig.ResolvedSkillEntry> skills)
            throws IOException {

        Path nacosDir = Path.of(workingDirectory, NACOS_DIR);
        Files.createDirectories(nacosDir);

        // 按 nacosId 分组，取每组第一个 entry 的凭证信息
        Map<String, ResolvedSessionConfig.ResolvedSkillEntry> byNacosId =
                skills.stream()
                        .collect(
                                Collectors.toMap(
                                        ResolvedSessionConfig.ResolvedSkillEntry::getNacosId,
                                        s -> s,
                                        (existing, replacement) -> existing));

        for (var entry : byNacosId.entrySet()) {
            String nacosId = entry.getKey();
            var skill = entry.getValue();
            try {
                String yaml = buildNacosEnvYaml(skill);
                Path filePath = nacosDir.resolve("nacos-env-" + nacosId + ".yaml");
                Files.writeString(filePath, yaml);
            } catch (Exception e) {
                logger.error("生成 nacos-env-{}.yaml 失败: {}", nacosId, e.getMessage(), e);
            }
        }
    }

    /**
     * 从 ResolvedSkillEntry 构建 nacos-env.yaml 内容。
     */
    static String buildNacosEnvYaml(ResolvedSessionConfig.ResolvedSkillEntry skill) {
        HostPort hp = parseServerAddr(skill.getServerAddr());

        StringBuilder sb = new StringBuilder();
        sb.append("host: ").append(hp.host()).append('\n');
        sb.append("port: ").append(hp.port()).append('\n');

        boolean isAliyun =
                skill.getAccessKey() != null
                        && !skill.getAccessKey().isBlank()
                        && skill.getSecretKey() != null
                        && !skill.getSecretKey().isBlank();
        sb.append("authType: ").append(isAliyun ? "aliyun" : "nacos").append('\n');

        sb.append("username: ").append(nullToEmpty(skill.getUsername())).append('\n');
        sb.append("password: ").append(nullToEmpty(skill.getPassword())).append('\n');
        sb.append("namespace: ").append(nullToEmpty(skill.getNamespace())).append('\n');

        if (isAliyun) {
            sb.append("accessKey: ").append(skill.getAccessKey()).append('\n');
            sb.append("secretKey: ").append(skill.getSecretKey()).append('\n');
        }

        return sb.toString();
    }

    /**
     * 解析 serverAddr URL 为 host 和 port。
     * 支持格式: http://host:port, https://host:port, host:port
     */
    static HostPort parseServerAddr(String serverAddr) {
        if (serverAddr == null || serverAddr.isBlank()) {
            throw new IllegalArgumentException("serverAddr 不能为空");
        }
        try {
            String uriStr = serverAddr;
            if (!uriStr.contains("://")) {
                uriStr = "http://" + uriStr;
            }
            URI uri = URI.create(uriStr);
            String host = uri.getHost();
            int port = uri.getPort();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("无法从 serverAddr 解析 host: " + serverAddr);
            }
            if (port == -1) {
                port = 8848; // Nacos 默认端口
            }
            return new HostPort(host, port);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("serverAddr 格式不合法: " + serverAddr, e);
        }
    }

    record HostPort(String host, int port) {}

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
