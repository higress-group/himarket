package com.alibaba.himarket.service.hicoding.filesystem;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * 路径安全校验工具类。
 * <p>
 * 防止路径遍历攻击，确保所有文件操作限制在用户工作空间范围内。
 */
public final class PathValidator {

    private PathValidator() {
        // 工具类，禁止实例化
    }

    /**
     * 校验相对路径的安全性，确保解析后的路径不会逃逸出基础目录。
     *
     * @param basePath     工作空间基础目录的绝对路径
     * @param relativePath 待校验的相对路径
     * @return 校验通过后的规范化绝对路径
     * @throws SecurityException 当路径不安全时抛出
     */
    public static Path validatePath(String basePath, String relativePath) {
        if (basePath == null || basePath.isEmpty()) {
            throw new SecurityException("基础路径不能为空");
        }
        if (relativePath == null || relativePath.isEmpty()) {
            throw new SecurityException("相对路径不能为空");
        }

        // 拒绝包含 null 字节的路径
        if (relativePath.indexOf('\0') >= 0) {
            throw new SecurityException("路径包含非法的 null 字节: " + sanitize(relativePath));
        }

        // 拒绝绝对路径（Unix 风格 / 或 Windows 驱动器号如 C:\）
        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            throw new SecurityException("不允许使用绝对路径: " + sanitize(relativePath));
        }
        if (relativePath.length() >= 2
                && Character.isLetter(relativePath.charAt(0))
                && (relativePath.charAt(1) == ':')) {
            throw new SecurityException("不允许使用绝对路径: " + sanitize(relativePath));
        }

        // 拒绝包含路径遍历模式的路径
        if (containsTraversalPattern(relativePath)) {
            throw new SecurityException("路径包含非法的遍历模式: " + sanitize(relativePath));
        }

        // 规范化并验证最终路径在基础目录内
        try {
            Path base = Path.of(basePath).toAbsolutePath().normalize();
            Path resolved = base.resolve(relativePath).normalize();

            if (!resolved.startsWith(base)) {
                throw new SecurityException("路径逃逸出工作空间目录: " + sanitize(relativePath));
            }
            return resolved;
        } catch (InvalidPathException e) {
            throw new SecurityException("路径格式无效: " + sanitize(relativePath), e);
        }
    }

    /**
     * 检查路径是否包含遍历模式（../ 或 ..\）。
     */
    private static boolean containsTraversalPattern(String path) {
        // 检查 ../ 和 ..\ 模式
        if (path.contains("../") || path.contains("..\\")) {
            return true;
        }
        // 检查路径是否恰好是 ".." 或以 ".." 结尾
        if (path.equals("..") || path.endsWith("/..") || path.endsWith("\\..")) {
            return true;
        }
        // 检查以 ../ 或 ..\ 开头
        return path.startsWith("..")
                && path.length() > 2
                && (path.charAt(2) == '/' || path.charAt(2) == '\\');
    }

    /**
     * 清理路径字符串用于日志/异常消息，移除 null 字节等不可见字符。
     */
    private static String sanitize(String path) {
        return path.replace("\0", "\\0");
    }
}
