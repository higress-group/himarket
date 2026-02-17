package com.alibaba.himarket.service.acp.runtime;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pod 文件系统适配器，通过 K8s exec API 在 Pod 容器内执行 shell 命令操作文件。
 * <p>
 * 所有文件操作映射为容器内的 shell 命令：
 * <ul>
 *   <li>readFile → exec "cat {path}"</li>
 *   <li>writeFile → exec "tee {path}" with content piped to stdin</li>
 *   <li>listDirectory → exec "ls -la {path}" and parse output</li>
 *   <li>createDirectory → exec "mkdir -p {path}"</li>
 *   <li>delete → exec "rm -rf {path}"</li>
 *   <li>getFileInfo → exec "stat {path}" and parse output</li>
 * </ul>
 * <p>
 * Requirements: 3.6, 6.3
 */
public class PodFileSystemAdapter implements FileSystemAdapter {

    private static final Logger logger = LoggerFactory.getLogger(PodFileSystemAdapter.class);
    private static final RuntimeType RUNTIME_TYPE = RuntimeType.K8S;
    private static final long EXEC_TIMEOUT_SECONDS = 30;

    private final KubernetesClient k8sClient;
    private final String podName;
    private final String namespace;
    private final String containerName;
    private final String basePath;

    /**
     * 构造 Pod 文件系统适配器。
     *
     * @param k8sClient     Fabric8 KubernetesClient 实例
     * @param podName       目标 Pod 名称
     * @param namespace     Pod 所在的 K8s 命名空间
     * @param containerName 目标容器名称（默认 "cli-agent"）
     * @param basePath      工作空间根目录（默认 "/workspace"）
     */
    public PodFileSystemAdapter(
            KubernetesClient k8sClient,
            String podName,
            String namespace,
            String containerName,
            String basePath) {
        if (k8sClient == null) {
            throw new IllegalArgumentException("k8sClient must not be null");
        }
        if (podName == null || podName.isBlank()) {
            throw new IllegalArgumentException("podName must not be null or blank");
        }
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("namespace must not be null or blank");
        }
        this.k8sClient = k8sClient;
        this.podName = podName;
        this.namespace = namespace;
        this.containerName = containerName != null ? containerName : "cli-agent";
        this.basePath = basePath != null ? basePath : "/workspace";
    }

    /**
     * 使用默认容器名和基础路径构造适配器。
     *
     * @param k8sClient Fabric8 KubernetesClient 实例
     * @param podName   目标 Pod 名称
     * @param namespace Pod 所在的 K8s 命名空间
     */
    public PodFileSystemAdapter(KubernetesClient k8sClient, String podName, String namespace) {
        this(k8sClient, podName, namespace, "cli-agent", "/workspace");
    }

    @Override
    public String readFile(String relativePath) throws IOException {
        String fullPath = resolveAndValidate(relativePath);
        ExecResult result = execInPod("cat", fullPath);
        if (result.exitCode() != 0) {
            String error = result.stderr().trim();
            if (error.contains("No such file or directory")) {
                throw new FileSystemException(
                        FileSystemException.ErrorType.FILE_NOT_FOUND,
                        RUNTIME_TYPE,
                        "文件不存在: " + relativePath);
            }
            if (error.contains("Is a directory")) {
                throw new FileSystemException(
                        FileSystemException.ErrorType.NOT_A_FILE,
                        RUNTIME_TYPE,
                        "路径是目录而非文件: " + relativePath);
            }
            if (error.contains("Permission denied")) {
                throw new FileSystemException(
                        FileSystemException.ErrorType.PERMISSION_DENIED,
                        RUNTIME_TYPE,
                        "无权读取文件: " + relativePath);
            }
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    RUNTIME_TYPE,
                    "读取文件失败: " + relativePath + " (" + error + ")");
        }
        return result.stdout();
    }

    @Override
    public void writeFile(String relativePath, String content) throws IOException {
        String fullPath = resolveAndValidate(relativePath);
        // 确保父目录存在
        String parentDir = fullPath.substring(0, fullPath.lastIndexOf('/'));
        if (!parentDir.isEmpty()) {
            execInPod("mkdir", "-p", parentDir);
        }
        // 使用 tee 写入文件，通过 stdin 传入内容
        ExecResult result = execInPodWithStdin(content, "tee", fullPath);
        if (result.exitCode() != 0) {
            String error = result.stderr().trim();
            if (error.contains("Permission denied")) {
                throw new FileSystemException(
                        FileSystemException.ErrorType.PERMISSION_DENIED,
                        RUNTIME_TYPE,
                        "无权写入文件: " + relativePath);
            }
            if (error.contains("No space left") || error.contains("Disk quota exceeded")) {
                throw new FileSystemException(
                        FileSystemException.ErrorType.DISK_FULL,
                        RUNTIME_TYPE,
                        "磁盘空间不足: " + relativePath);
            }
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    RUNTIME_TYPE,
                    "写入文件失败: " + relativePath + " (" + error + ")");
        }
    }

    @Override
    public List<FileEntry> listDirectory(String relativePath) throws IOException {
        String fullPath = resolveAndValidate(relativePath);
        ExecResult result = execInPod("ls", "-la", fullPath);
        if (result.exitCode() != 0) {
            String error = result.stderr().trim();
            if (error.contains("No such file or directory")) {
                throw new FileSystemException(
                        FileSystemException.ErrorType.FILE_NOT_FOUND,
                        RUNTIME_TYPE,
                        "目录不存在: " + relativePath);
            }
            if (error.contains("Not a directory")) {
                throw new FileSystemException(
                        FileSystemException.ErrorType.NOT_A_DIRECTORY,
                        RUNTIME_TYPE,
                        "路径不是目录: " + relativePath);
            }
            if (error.contains("Permission denied")) {
                throw new FileSystemException(
                        FileSystemException.ErrorType.PERMISSION_DENIED,
                        RUNTIME_TYPE,
                        "无权列举目录: " + relativePath);
            }
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    RUNTIME_TYPE,
                    "列举目录失败: " + relativePath + " (" + error + ")");
        }
        return parseLsOutput(result.stdout());
    }

    @Override
    public void createDirectory(String relativePath) throws IOException {
        String fullPath = resolveAndValidate(relativePath);
        ExecResult result = execInPod("mkdir", "-p", fullPath);
        if (result.exitCode() != 0) {
            String error = result.stderr().trim();
            if (error.contains("Permission denied")) {
                throw new FileSystemException(
                        FileSystemException.ErrorType.PERMISSION_DENIED,
                        RUNTIME_TYPE,
                        "无权创建目录: " + relativePath);
            }
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    RUNTIME_TYPE,
                    "创建目录失败: " + relativePath + " (" + error + ")");
        }
    }

    @Override
    public void delete(String relativePath) throws IOException {
        String fullPath = resolveAndValidate(relativePath);
        // 先检查文件是否存在
        ExecResult checkResult = execInPod("test", "-e", fullPath);
        if (checkResult.exitCode() != 0) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.FILE_NOT_FOUND,
                    RUNTIME_TYPE,
                    "文件或目录不存在: " + relativePath);
        }
        ExecResult result = execInPod("rm", "-rf", fullPath);
        if (result.exitCode() != 0) {
            String error = result.stderr().trim();
            if (error.contains("Permission denied")) {
                throw new FileSystemException(
                        FileSystemException.ErrorType.PERMISSION_DENIED,
                        RUNTIME_TYPE,
                        "无权删除: " + relativePath);
            }
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    RUNTIME_TYPE,
                    "删除失败: " + relativePath + " (" + error + ")");
        }
    }

    @Override
    public FileInfo getFileInfo(String relativePath) throws IOException {
        String fullPath = resolveAndValidate(relativePath);
        // 使用 stat 获取文件信息，指定输出格式
        // 格式: type|size|lastModified|readable|writable
        ExecResult result = execInPod("stat", "-c", "%F|%s|%Y", fullPath);
        if (result.exitCode() != 0) {
            String error = result.stderr().trim();
            if (error.contains("No such file or directory")) {
                throw new FileSystemException(
                        FileSystemException.ErrorType.FILE_NOT_FOUND,
                        RUNTIME_TYPE,
                        "文件不存在: " + relativePath);
            }
            if (error.contains("Permission denied")) {
                throw new FileSystemException(
                        FileSystemException.ErrorType.PERMISSION_DENIED,
                        RUNTIME_TYPE,
                        "无权获取文件信息: " + relativePath);
            }
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    RUNTIME_TYPE,
                    "获取文件信息失败: " + relativePath + " (" + error + ")");
        }
        // 检查可读可写权限
        ExecResult readCheck = execInPod("test", "-r", fullPath);
        ExecResult writeCheck = execInPod("test", "-w", fullPath);
        return parseStatOutput(
                relativePath,
                result.stdout().trim(),
                readCheck.exitCode() == 0,
                writeCheck.exitCode() == 0);
    }

    // ===== 内部辅助方法 =====

    /**
     * 校验路径安全性并返回容器内的完整路径。
     */
    String resolveAndValidate(String relativePath) throws FileSystemException {
        try {
            // 使用 PathValidator 进行安全校验（防止路径遍历）
            PathValidator.validatePath(basePath, relativePath);
        } catch (SecurityException e) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.PATH_TRAVERSAL, RUNTIME_TYPE, e.getMessage());
        }
        // 构建容器内的完整路径
        return basePath + "/" + relativePath;
    }

    /**
     * 在 Pod 容器内执行命令并返回结果。
     */
    ExecResult execInPod(String... command) throws IOException {
        return execInPodWithStdin(null, command);
    }

    /**
     * 在 Pod 容器内执行命令，可选通过 stdin 传入数据。
     */
    ExecResult execInPodWithStdin(String stdinData, String... command) throws IOException {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        CompletableFuture<Integer> exitCodeFuture = new CompletableFuture<>();

        try {
            var execable =
                    k8sClient
                            .pods()
                            .inNamespace(namespace)
                            .withName(podName)
                            .inContainer(containerName)
                            .redirectingInput()
                            .writingOutput(stdout)
                            .writingError(stderr)
                            .usingListener(
                                    new ExecListener() {
                                        @Override
                                        public void onOpen() {
                                            logger.trace("Exec session opened for pod {}", podName);
                                        }

                                        @Override
                                        public void onFailure(
                                                Throwable t, Response failureResponse) {
                                            logger.warn(
                                                    "Exec failed for pod {}: {}",
                                                    podName,
                                                    t.getMessage());
                                            exitCodeFuture.completeExceptionally(t);
                                        }

                                        @Override
                                        public void onClose(int code, String reason) {
                                            exitCodeFuture.complete(code);
                                        }
                                    });

            try (ExecWatch exec = execable.exec(command)) {
                // 如果有 stdin 数据，写入后关闭 stdin
                if (stdinData != null) {
                    InputStream inputStream =
                            new ByteArrayInputStream(stdinData.getBytes(StandardCharsets.UTF_8));
                    exec.getInput().write(inputStream.readAllBytes());
                    exec.getInput().flush();
                    exec.getInput().close();
                }

                // 等待命令执行完成
                int exitCode;
                try {
                    exitCode = exitCodeFuture.get(EXEC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new FileSystemException(
                            FileSystemException.ErrorType.IO_ERROR,
                            RUNTIME_TYPE,
                            "Pod 命令执行超时或失败: " + String.join(" ", command),
                            e);
                }

                return new ExecResult(
                        exitCode,
                        stdout.toString(StandardCharsets.UTF_8),
                        stderr.toString(StandardCharsets.UTF_8));
            }
        } catch (FileSystemException e) {
            throw e;
        } catch (Exception e) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    RUNTIME_TYPE,
                    "Pod exec 调用失败: " + e.getMessage(),
                    e);
        }
    }

    /**
     * 解析 ls -la 输出为 FileEntry 列表。
     * <p>
     * ls -la 输出格式示例：
     * <pre>
     * total 8
     * drwxr-xr-x 2 root root 4096 Jan  1 00:00 .
     * drwxr-xr-x 3 root root 4096 Jan  1 00:00 ..
     * -rw-r--r-- 1 root root   13 Jan  1 00:00 hello.txt
     * </pre>
     */
    List<FileEntry> parseLsOutput(String output) {
        List<FileEntry> entries = new ArrayList<>();
        String[] lines = output.split("\n");
        for (String line : lines) {
            line = line.trim();
            // 跳过 total 行、空行、. 和 .. 条目
            if (line.isEmpty() || line.startsWith("total")) {
                continue;
            }
            String[] parts = line.split("\\s+", 9);
            if (parts.length < 9) {
                continue;
            }
            String name = parts[8];
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            boolean isDirectory = parts[0].startsWith("d");
            long size = 0;
            try {
                size = Long.parseLong(parts[4]);
            } catch (NumberFormatException e) {
                // 忽略解析失败
            }
            // ls -la 不直接提供 epoch 时间戳，此处设为 0
            entries.add(new FileEntry(name, isDirectory, size, 0L));
        }
        return entries;
    }

    /**
     * 解析 stat -c "%F|%s|%Y" 输出为 FileInfo。
     * <p>
     * 输出格式: "regular file|1234|1700000000" 或 "directory|4096|1700000000"
     */
    FileInfo parseStatOutput(String relativePath, String output, boolean readable, boolean writable)
            throws FileSystemException {
        String[] parts = output.split("\\|");
        if (parts.length < 3) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    RUNTIME_TYPE,
                    "无法解析 stat 输出: " + output);
        }
        boolean isDirectory = parts[0].contains("directory");
        long size = 0;
        try {
            size = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            // 忽略
        }
        long lastModified = 0;
        try {
            // stat %Y 返回秒级时间戳，转换为毫秒
            lastModified = Long.parseLong(parts[2]) * 1000;
        } catch (NumberFormatException e) {
            // 忽略
        }
        return new FileInfo(relativePath, isDirectory, size, lastModified, readable, writable);
    }

    // ===== 内部记录类 =====

    /**
     * Pod exec 命令执行结果。
     */
    record ExecResult(int exitCode, String stdout, String stderr) {}

    // ===== 用于测试的 Getter =====

    String getPodName() {
        return podName;
    }

    String getNamespace() {
        return namespace;
    }

    String getContainerName() {
        return containerName;
    }

    String getBasePath() {
        return basePath;
    }
}
