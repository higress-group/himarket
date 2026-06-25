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
 * Generates nacos-env.yaml files grouped by Nacos instance.
 */
public final class NacosEnvGenerator {

    private static final Logger logger = LoggerFactory.getLogger(NacosEnvGenerator.class);
    private static final String NACOS_DIR = ".nacos";

    private NacosEnvGenerator() {}

    /**
     * Generates one .nacos/nacos-env-{nacosId}.yaml file for each Nacos instance.
     */
    public static void generateNacosEnvFiles(
            String workingDirectory, List<ResolvedSessionConfig.ResolvedSkillEntry> skills)
            throws IOException {

        Path nacosDir = Path.of(workingDirectory, NACOS_DIR);
        Files.createDirectories(nacosDir);

        // Group by nacosId and use the first entry's credentials for each group.
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
                logger.error(
                        "Failed to generate Nacos env file, nacosId={}, errorMessage={}",
                        nacosId,
                        e.getMessage(),
                        e);
            }
        }
    }

    /**
     * Builds nacos-env.yaml content from a ResolvedSkillEntry.
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
     * Parses serverAddr into host and port.
     * Supports http://host:port, https://host:port, and host:port.
     */
    static HostPort parseServerAddr(String serverAddr) {
        if (serverAddr == null || serverAddr.isBlank()) {
            throw new IllegalArgumentException("serverAddr must not be empty");
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
                throw new IllegalArgumentException(
                        "Failed to parse host from serverAddr: " + serverAddr);
            }
            if (port == -1) {
                port = 8848; // Default Nacos port.
            }
            return new HostPort(host, port);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid serverAddr format: " + serverAddr, e);
        }
    }

    record HostPort(String host, int port) {}

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
