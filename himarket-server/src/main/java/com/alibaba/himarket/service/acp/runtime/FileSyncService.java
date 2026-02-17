package com.alibaba.himarket.service.acp.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 文件同步服务，负责文件持久化。
 *
 * <p>将文件同步到服务端本地磁盘，
 * 并支持从服务端恢复文件列表。
 *
 * <p>文件存储在 {@code {workspaceRoot}/{userId}/} 目录下。
 *
 * <p>Requirements: 9.3, 9.5
 */
@Slf4j
@Service
public class FileSyncService {

    private final String workspaceRoot;

    public FileSyncService(com.alibaba.himarket.config.AcpProperties acpProperties) {
        this.workspaceRoot = acpProperties.getWorkspaceRoot();
    }

    /**
     * 同步文件到服务端持久化存储。
     *
     * @param userId 用户 ID
     * @param files  待同步的文件列表（每个文件包含 path 和 content）
     * @throws SecurityException 当路径校验失败时抛出
     * @throws IOException       当文件写入失败时抛出
     */
    public void syncFiles(String userId, List<FileItem> files) throws IOException {
        String userDir = resolveUserDir(userId);
        for (FileItem file : files) {
            Path resolved = PathValidator.validatePath(userDir, file.path());
            Path parent = resolved.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    resolved,
                    file.content(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        }
        log.info("已同步 {} 个文件到用户 [{}] 的工作空间", files.size(), userId);
    }

    /**
     * 获取用户持久化的所有文件列表。
     *
     * @param userId 用户 ID
     * @return 文件列表（每个文件包含相对路径和内容）
     * @throws IOException 当文件读取失败时抛出
     */
    public List<FileItem> listFiles(String userId) throws IOException {
        String userDir = resolveUserDir(userId);
        Path userPath = Path.of(userDir);
        if (!Files.exists(userPath) || !Files.isDirectory(userPath)) {
            return List.of();
        }

        List<FileItem> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(userPath)) {
            walk.filter(Files::isRegularFile)
                    .forEach(
                            filePath -> {
                                try {
                                    String relativePath =
                                            userPath.relativize(filePath)
                                                    .toString()
                                                    .replace('\\', '/');
                                    String content = Files.readString(filePath);
                                    result.add(new FileItem(relativePath, content));
                                } catch (IOException e) {
                                    log.warn("读取文件失败: {}", filePath, e);
                                }
                            });
        }
        return result;
    }

    private String resolveUserDir(String userId) {
        Path base = Path.of(workspaceRoot).toAbsolutePath().normalize();
        Path userPath = base.resolve(userId).normalize();
        // 确保 userId 不会导致路径逃逸
        if (!userPath.startsWith(base)) {
            throw new SecurityException("非法的用户 ID: " + userId);
        }
        return userPath.toString();
    }

    /**
     * 文件同步数据项。
     */
    public record FileItem(String path, String content) {}
}
