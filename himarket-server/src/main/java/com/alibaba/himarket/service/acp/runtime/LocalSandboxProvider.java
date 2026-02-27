package com.alibaba.himarket.service.acp.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 本地沙箱提供者。
 *
 * <p>在本地 Mac 上启动 Sidecar Server（Node.js 进程）， 通过 WebSocket 桥接 CLI，通过 HTTP API 操作文件系统，
 * 使本地开发流程与 K8s 沙箱完全一致。
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>writeFile/readFile 统一通过 Sidecar HTTP API，不直接调用 Java Files API</li>
 *   <li>每个用户独立的 Sidecar 进程（不同端口）</li>
 *   <li>首次 acquire() 时启动，后续复用存活进程</li>
 * </ul>
 *
 * <p>Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8
 */
@Component
public class LocalSandboxProvider implements SandboxProvider {

    private static final Logger logger = LoggerFactory.getLogger(LocalSandboxProvider.class);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration HEALTH_POLL_INTERVAL = Duration.ofMillis(500);
    private static final Duration SIDECAR_READY_TIMEOUT = Duration.ofSeconds(10);
    private static final String DEFAULT_ALLOWED_COMMANDS = "qodercli,qwen";

    /** 用户 → 本地 Sidecar 进程映射，支持复用 */
    private final ConcurrentHashMap<String, LocalSidecarProcess> sidecarProcesses =
            new ConcurrentHashMap<>();

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LocalSandboxProvider() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public SandboxType getType() {
        return SandboxType.LOCAL;
    }

    @Override
    public SandboxInfo acquire(SandboxConfig config) {
        String userId = config.userId();
        String cwd = config.workspacePath();

        // 1. 确保工作空间目录存在
        try {
            Files.createDirectories(Path.of(cwd));
        } catch (IOException e) {
            throw new RuntimeException("无法创建工作空间目录: " + cwd, e);
        }

        // 2. 检查是否有可复用的 Sidecar 进程
        LocalSidecarProcess existing = sidecarProcesses.get(userId);
        if (existing != null && existing.isAlive()) {
            logger.info(
                    "[LocalSandboxProvider] 复用已有 Sidecar 进程: userId={}, port={}",
                    userId,
                    existing.port());
            return new SandboxInfo(
                    SandboxType.LOCAL,
                    "local-" + existing.port(),
                    "127.0.0.1",
                    existing.port(),
                    cwd,
                    true,
                    Map.of());
        }

        // 3. 启动新的 Sidecar Server 进程
        int port = config.localSidecarPort() > 0 ? config.localSidecarPort() : findAvailablePort();
        LocalSidecarProcess sidecar = startSidecarProcess(port, config);
        sidecarProcesses.put(userId, sidecar);

        logger.info("[LocalSandboxProvider] 启动新 Sidecar 进程: userId={}, port={}", userId, port);
        return new SandboxInfo(
                SandboxType.LOCAL, "local-" + port, "127.0.0.1", port, cwd, false, Map.of());
    }

    @Override
    public void release(SandboxInfo info) {
        // 根据 sandboxId 格式 "local-{port}" 找到对应用户并终止进程
        String targetPort = info.sandboxId().replace("local-", "");
        sidecarProcesses
                .entrySet()
                .removeIf(
                        entry -> {
                            LocalSidecarProcess process = entry.getValue();
                            if (String.valueOf(process.port()).equals(targetPort)) {
                                logger.info(
                                        "[LocalSandboxProvider] 终止 Sidecar 进程: userId={}, port={}",
                                        entry.getKey(),
                                        process.port());
                                process.stop();
                                return true;
                            }
                            return false;
                        });
    }

    @Override
    public void writeFile(SandboxInfo info, String relativePath, String content)
            throws IOException {
        String url = sidecarBaseUrl(info) + "/files/write";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body =
                objectMapper.writeValueAsString(Map.of("path", relativePath, "content", content));
        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IOException(
                        "Sidecar writeFile 失败 (Local: "
                                + info.sandboxId()
                                + "): "
                                + response.getBody());
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(
                    "Sidecar writeFile 失败 (Local: " + info.sandboxId() + "): " + e.getMessage(), e);
        }
    }

    @Override
    public String readFile(SandboxInfo info, String relativePath) throws IOException {
        String url = sidecarBaseUrl(info) + "/files/read";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = objectMapper.writeValueAsString(Map.of("path", relativePath));
        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IOException(
                        "Sidecar readFile 失败 (Local: "
                                + info.sandboxId()
                                + "): "
                                + response.getBody());
            }
            return objectMapper.readTree(response.getBody()).get("content").asText();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(
                    "Sidecar readFile 失败 (Local: " + info.sandboxId() + "): " + e.getMessage(), e);
        }
    }

    @Override
    public boolean healthCheck(SandboxInfo info) {
        try {
            String url = sidecarBaseUrl(info) + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            logger.warn(
                    "[LocalSandboxProvider] healthCheck 失败 (Local: {}): {}",
                    info.sandboxId(),
                    e.getMessage());
            return false;
        }
    }

    @Override
    public RuntimeAdapter connectSidecar(SandboxInfo info, RuntimeConfig config) {
        String command = config.getCommand();
        String args = config.getArgs() != null ? String.join(" ", config.getArgs()) : "";
        URI baseUri = info.sidecarWsUri(command, args);

        // 将 RuntimeConfig 中的环境变量通过 query param 传递给 Sidecar，
        // 以便 Sidecar 在 spawn CLI 子进程时注入（解决 Sidecar 进程复用时环境变量无法动态更新的问题）
        URI wsUri = baseUri;
        if (config.getEnv() != null && !config.getEnv().isEmpty()) {
            try {
                String envJson = new com.fasterxml.jackson.databind.ObjectMapper()
                        .writeValueAsString(config.getEnv());
                String encodedEnv = java.net.URLEncoder.encode(envJson, java.nio.charset.StandardCharsets.UTF_8);
                String separator = baseUri.getRawQuery() != null ? "&" : "?";
                wsUri = URI.create(baseUri.toString() + separator + "env=" + encodedEnv);
            } catch (Exception e) {
                logger.warn("[LocalSandboxProvider] 序列化 env 失败，跳过环境变量传递: {}", e.getMessage());
            }
        }

        LocalSidecarAdapter adapter = new LocalSidecarAdapter(wsUri);
        adapter.connect();
        return adapter;
    }

    @Override
    public URI getSidecarUri(SandboxInfo info, String command, String args) {
        return info.sidecarWsUri(command, args);
    }

    // ===== 私有辅助方法 =====

    private String sidecarBaseUrl(SandboxInfo info) {
        return "http://" + info.host() + ":" + info.sidecarPort();
    }

    /**
     * 查找可用端口。使用 ServerSocket(0) 让操作系统分配一个空闲端口。
     */
    private int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("无法找到可用端口", e);
        }
    }

    /**
     * 获取 login shell 的完整 PATH。
     *
     * <p>Java 进程继承的 PATH 通常不包含 nvm/fnm 等版本管理器注入的路径，
     * 导致 Sidecar spawn 的子进程（如 qwen）找不到命令。
     * 通过 bash -lc "echo $PATH" 获取用户 login shell 的完整 PATH。
     */
    private String resolveShellPath() {
        try {
            Process proc =
                    new ProcessBuilder("bash", "-lc", "echo $PATH")
                            .redirectErrorStream(true)
                            .start();
            String output = new String(proc.getInputStream().readAllBytes()).trim();
            int exitCode = proc.waitFor();
            if (exitCode == 0 && !output.isEmpty()) {
                logger.debug("[LocalSandboxProvider] login shell PATH: {}", output);
                return output;
            }
        } catch (Exception e) {
            logger.debug("[LocalSandboxProvider] 获取 shell PATH 失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 解析 node 可执行文件的绝对路径。
     *
     * <p>Java ProcessBuilder 继承的 PATH 可能不包含 nvm/fnm 等版本管理器注入的路径，
     * 导致直接用 "node" 找不到命令。此方法通过 bash -lc "which node" 获取完整路径。
     */
    private String resolveNodeCommand() {
        // 1. 尝试通过 login shell 获取 node 路径（兼容 nvm/fnm）
        try {
            Process which =
                    new ProcessBuilder("bash", "-lc", "which node")
                            .redirectErrorStream(true)
                            .start();
            String output = new String(which.getInputStream().readAllBytes()).trim();
            int exitCode = which.waitFor();
            if (exitCode == 0 && !output.isEmpty() && Files.exists(Path.of(output))) {
                logger.debug("[LocalSandboxProvider] 通过 bash -lc 找到 node: {}", output);
                return output;
            }
        } catch (Exception e) {
            logger.debug("[LocalSandboxProvider] bash -lc which node 失败: {}", e.getMessage());
        }

        // 2. 检查常见 nvm 路径
        String home = System.getProperty("user.home");
        Path nvmCurrent = Path.of(home, ".nvm", "current", "bin", "node");
        if (Files.exists(nvmCurrent)) {
            return nvmCurrent.toString();
        }

        // 3. 检查 /usr/local/bin/node
        if (Files.exists(Path.of("/usr/local/bin/node"))) {
            return "/usr/local/bin/node";
        }

        // 4. 回退到 "node"，依赖 ProcessBuilder 的 PATH
        logger.warn("[LocalSandboxProvider] 未找到 node 绝对路径，回退到 PATH 查找");
        return "node";
    }

    /**
     * 启动本地 Sidecar Server 进程。
     *
     * <p>使用 ProcessBuilder 启动 {@code node sidecar-server/index.js}，
     * 设置 SIDECAR_PORT、SIDECAR_MODE、ALLOWED_COMMANDS 环境变量，
     * 并轮询 /health 端点等待就绪（超时 10s）。
     */
    private LocalSidecarProcess startSidecarProcess(int port, SandboxConfig config) {
        try {
            // 解析 Sidecar Server 脚本路径（相对于项目根目录）
            String projectRoot = System.getProperty("user.dir");
            String sidecarServerPath =
                    Path.of(projectRoot, "sandbox", "sidecar-server", "index.js").toString();

            // 检查脚本文件是否存在
            if (!Files.exists(Path.of(sidecarServerPath))) {
                throw new RuntimeException(
                        "Sidecar 脚本不存在: " + sidecarServerPath + " (user.dir=" + projectRoot + ")");
            }

            // 检查 node_modules 是否已安装
            Path nodeModulesPath =
                    Path.of(projectRoot, "sandbox", "sidecar-server", "node_modules");
            if (!Files.exists(nodeModulesPath)) {
                throw new RuntimeException(
                        "Sidecar 依赖未安装，请先执行: cd sandbox/sidecar-server && npm install");
            }

            // 解析 node 可执行文件路径：优先使用 which node 的结果，兼容 nvm 等版本管理器
            String nodeCommand = resolveNodeCommand();
            logger.info(
                    "[LocalSandboxProvider] 启动 Sidecar: node={}, script={}, port={}",
                    nodeCommand,
                    sidecarServerPath,
                    port);

            ProcessBuilder pb =
                    new ProcessBuilder(nodeCommand, sidecarServerPath)
                            .directory(new File(config.workspacePath()));

            // 设置环境变量
            Map<String, String> env = pb.environment();
            env.put("SIDECAR_PORT", String.valueOf(port));
            env.put("SIDECAR_MODE", "local");
            env.put("WORKSPACE_ROOT", config.workspacePath());
            env.put("ALLOWED_COMMANDS", DEFAULT_ALLOWED_COMMANDS);

            // 获取 login shell 的完整 PATH，确保 Sidecar spawn 的子进程
            // 能找到 nvm/fnm 等版本管理器安装的命令（如 qwen）
            String shellPath = resolveShellPath();
            if (shellPath != null && !shellPath.isEmpty()) {
                env.put("PATH", shellPath);
            } else {
                // 回退：至少确保 node 所在目录在 PATH 中
                String nodeBinDir =
                        Path.of(nodeCommand).getParent() != null
                                ? Path.of(nodeCommand).getParent().toString()
                                : null;
                if (nodeBinDir != null) {
                    String currentPath = env.getOrDefault("PATH", "");
                    if (!currentPath.contains(nodeBinDir)) {
                        env.put("PATH", nodeBinDir + ":" + currentPath);
                    }
                }
            }

            // 合并用户自定义环境变量
            if (config.env() != null) {
                env.putAll(config.env());
            }

            // 重定向 stderr 到 stdout，便于调试
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // 异步消费进程输出，防止缓冲区满导致进程阻塞
            Thread convergingThread =
                    new Thread(
                            () -> {
                                try (var reader =
                                        new java.io.BufferedReader(
                                                new java.io.InputStreamReader(
                                                        process.getInputStream()))) {
                                    String line;
                                    while ((line = reader.readLine()) != null) {
                                        logger.info("[Sidecar:{}] {}", port, line);
                                    }
                                } catch (IOException e) {
                                    logger.debug("[Sidecar:{}] 输出流读取结束: {}", port, e.getMessage());
                                }
                            },
                            "sidecar-output-" + port);
            convergingThread.setDaemon(true);
            convergingThread.start();

            // 短暂等待，让进程有机会启动或快速失败
            Thread.sleep(300);

            // 检查进程是否已经退出（快速失败检测）
            if (!process.isAlive()) {
                int exitCode = process.exitValue();
                throw new RuntimeException(
                        "Sidecar 进程启动后立即退出 (port="
                                + port
                                + ", exitCode="
                                + exitCode
                                + ")，请检查 node 和依赖是否正常");
            }

            // 轮询 /health 端点等待 Sidecar 就绪
            waitForSidecarReady(port, SIDECAR_READY_TIMEOUT);

            return new LocalSidecarProcess(process, port, Instant.now());
        } catch (IOException e) {
            throw new RuntimeException(
                    "启动本地 Sidecar 进程失败 (port=" + port + "): " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("启动本地 Sidecar 进程被中断 (port=" + port + ")", e);
        }
    }

    /**
     * 轮询 Sidecar /health 端点，等待其就绪。
     *
     * @param port    Sidecar 端口
     * @param timeout 最大等待时间
     */
    private void waitForSidecarReady(int port, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        // 使用 127.0.0.1 而非 localhost，避免 macOS 上 localhost 解析到 IPv6 (::1)
        String healthUrl = "http://127.0.0.1:" + port + "/health";

        logger.info("[LocalSandboxProvider] 开始轮询 Sidecar 健康检查: url={}", healthUrl);

        while (Instant.now().isBefore(deadline)) {
            try {
                // 使用 URLConnection 替代 HttpClient，避免 HttpClient 在 macOS 上的阻塞问题
                java.net.URL url = new java.net.URL(healthUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                int statusCode = conn.getResponseCode();
                conn.disconnect();
                if (statusCode == 200) {
                    logger.info("[LocalSandboxProvider] Sidecar 就绪: port={}", port);
                    return;
                }
                logger.info(
                        "[LocalSandboxProvider] Sidecar 返回非 200: port={}, status={}",
                        port,
                        statusCode);
            } catch (Exception e) {
                logger.info(
                        "[LocalSandboxProvider] 等待 Sidecar 就绪: port={}, error={}",
                        port,
                        e.getMessage());
            }

            try {
                Thread.sleep(HEALTH_POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("等待 Sidecar 就绪时被中断", e);
            }
        }

        throw new RuntimeException(
                "Sidecar 启动超时 (port=" + port + ", timeout=" + timeout.getSeconds() + "s)");
    }
}
