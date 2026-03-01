package com.alibaba.himarket.service.acp.runtime;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地文件系统适配器，通过 Java NIO 操作本地文件系统。
 * <p>
 * 所有操作前均通过 {@link PathValidator#validatePath} 进行路径安全校验，
 * 防止路径遍历攻击。错误信息包含统一的 errorType 和 runtimeType 字段。
 *
 * @deprecated 文件操作已统一通过 Sidecar HTTP API 完成。
 *     请使用 {@link LocalSandboxProvider} 的 writeFile/readFile 方法。
 */
@Deprecated
public class LocalFileSystemAdapter implements FileSystemAdapter {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileSystemAdapter.class);
    private static final SandboxType SANDBOX_TYPE = SandboxType.LOCAL;

    private final String basePath;

    /**
     * 构造本地文件系统适配器。
     *
     * @param basePath 工作空间根目录的绝对路径
     */
    public LocalFileSystemAdapter(String basePath) {
        if (basePath == null || basePath.isEmpty()) {
            throw new IllegalArgumentException("basePath 不能为空");
        }
        this.basePath = basePath;
    }

    @Override
    public String readFile(String relativePath) throws IOException {
        Path resolved = validateAndResolve(relativePath);
        try {
            if (!Files.exists(resolved)) {
                throw new FileSystemException(
                        FileSystemException.ErrorType.FILE_NOT_FOUND,
                        SANDBOX_TYPE,
                        "文件不存在: " + relativePath);
            }
            if (Files.isDirectory(resolved)) {
                throw new FileSystemException(
                        FileSystemException.ErrorType.NOT_A_FILE,
                        SANDBOX_TYPE,
                        "路径是目录而非文件: " + relativePath);
            }
            return Files.readString(resolved);
        } catch (FileSystemException e) {
            throw e;
        } catch (AccessDeniedException e) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.PERMISSION_DENIED,
                    SANDBOX_TYPE,
                    "无权读取文件: " + relativePath,
                    e);
        } catch (IOException e) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    SANDBOX_TYPE,
                    "读取文件失败: " + relativePath,
                    e);
        }
    }

    @Override
    public void writeFile(String relativePath, String content) throws IOException {
        Path resolved = validateAndResolve(relativePath);
        try {
            // 确保父目录存在
            Path parent = resolved.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    resolved,
                    content,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (AccessDeniedException e) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.PERMISSION_DENIED,
                    SANDBOX_TYPE,
                    "无权写入文件: " + relativePath,
                    e);
        } catch (IOException e) {
            if (isDiskFullError(e)) {
                throw new FileSystemException(
                        FileSystemException.ErrorType.DISK_FULL,
                        SANDBOX_TYPE,
                        "磁盘空间不足: " + relativePath,
                        e);
            }
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    SANDBOX_TYPE,
                    "写入文件失败: " + relativePath,
                    e);
        }
    }

    @Override
    public List<FileEntry> listDirectory(String relativePath) throws IOException {
        Path resolved = validateAndResolve(relativePath);
        try {
            if (!Files.exists(resolved)) {
                throw new FileSystemException(
                        FileSystemException.ErrorType.FILE_NOT_FOUND,
                        SANDBOX_TYPE,
                        "目录不存在: " + relativePath);
            }
            if (!Files.isDirectory(resolved)) {
                throw new FileSystemException(
                        FileSystemException.ErrorType.NOT_A_DIRECTORY,
                        SANDBOX_TYPE,
                        "路径不是目录: " + relativePath);
            }
            try (Stream<Path> stream = Files.list(resolved)) {
                return stream.map(this::toFileEntry).toList();
            }
        } catch (FileSystemException e) {
            throw e;
        } catch (AccessDeniedException e) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.PERMISSION_DENIED,
                    SANDBOX_TYPE,
                    "无权列举目录: " + relativePath,
                    e);
        } catch (IOException e) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    SANDBOX_TYPE,
                    "列举目录失败: " + relativePath,
                    e);
        }
    }

    @Override
    public void createDirectory(String relativePath) throws IOException {
        Path resolved = validateAndResolve(relativePath);
        try {
            Files.createDirectories(resolved);
        } catch (AccessDeniedException e) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.PERMISSION_DENIED,
                    SANDBOX_TYPE,
                    "无权创建目录: " + relativePath,
                    e);
        } catch (FileAlreadyExistsException e) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.ALREADY_EXISTS,
                    SANDBOX_TYPE,
                    "路径已存在且不是目录: " + relativePath,
                    e);
        } catch (IOException e) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    SANDBOX_TYPE,
                    "创建目录失败: " + relativePath,
                    e);
        }
    }

    @Override
    public void delete(String relativePath) throws IOException {
        Path resolved = validateAndResolve(relativePath);
        try {
            if (!Files.exists(resolved)) {
                throw new FileSystemException(
                        FileSystemException.ErrorType.FILE_NOT_FOUND,
                        SANDBOX_TYPE,
                        "文件或目录不存在: " + relativePath);
            }
            if (Files.isDirectory(resolved)) {
                deleteRecursively(resolved);
            } else {
                Files.deleteIfExists(resolved);
            }
        } catch (FileSystemException e) {
            throw e;
        } catch (AccessDeniedException e) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.PERMISSION_DENIED,
                    SANDBOX_TYPE,
                    "无权删除: " + relativePath,
                    e);
        } catch (IOException e) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    SANDBOX_TYPE,
                    "删除失败: " + relativePath,
                    e);
        }
    }

    @Override
    public FileInfo getFileInfo(String relativePath) throws IOException {
        Path resolved = validateAndResolve(relativePath);
        try {
            if (!Files.exists(resolved)) {
                throw new FileSystemException(
                        FileSystemException.ErrorType.FILE_NOT_FOUND,
                        SANDBOX_TYPE,
                        "文件不存在: " + relativePath);
            }
            BasicFileAttributes attrs = Files.readAttributes(resolved, BasicFileAttributes.class);
            return new FileInfo(
                    relativePath,
                    attrs.isDirectory(),
                    attrs.size(),
                    attrs.lastModifiedTime().toMillis(),
                    Files.isReadable(resolved),
                    Files.isWritable(resolved));
        } catch (FileSystemException e) {
            throw e;
        } catch (AccessDeniedException e) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.PERMISSION_DENIED,
                    SANDBOX_TYPE,
                    "无权获取文件信息: " + relativePath,
                    e);
        } catch (IOException e) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.IO_ERROR,
                    SANDBOX_TYPE,
                    "获取文件信息失败: " + relativePath,
                    e);
        }
    }

    /**
     * 校验路径安全性并返回解析后的绝对路径。
     * 路径遍历攻击会被转换为 FileSystemException。
     */
    private Path validateAndResolve(String relativePath) throws FileSystemException {
        try {
            return PathValidator.validatePath(basePath, relativePath);
        } catch (SecurityException e) {
            throw new FileSystemException(
                    FileSystemException.ErrorType.PATH_TRAVERSAL, SANDBOX_TYPE, e.getMessage());
        }
    }

    /** 递归删除目录及其所有内容。 */
    private void deleteRecursively(Path directory) throws IOException {
        Files.walkFileTree(
                directory,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                            throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                            throws IOException {
                        if (exc != null) {
                            throw exc;
                        }
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    /** 将 Path 转换为 FileEntry 记录。 */
    private FileEntry toFileEntry(Path path) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return new FileEntry(
                    path.getFileName().toString(),
                    attrs.isDirectory(),
                    attrs.size(),
                    attrs.lastModifiedTime().toMillis());
        } catch (IOException e) {
            logger.warn("读取文件属性失败: {}", path, e);
            return new FileEntry(path.getFileName().toString(), false, 0L, 0L);
        }
    }

    /** 判断 IOException 是否由磁盘空间不足引起。 */
    private boolean isDiskFullError(IOException e) {
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("no space left")
                || lower.contains("disk full")
                || lower.contains("not enough space");
    }
}
