package com.alibaba.himarket.service.hicoding.filesystem;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Path safety validator.
 *
 * <p>Prevents path traversal attacks and ensures file operations stay inside the workspace.
 */
public final class PathValidator {

    private PathValidator() {
        // Utility class.
    }

    /**
     * Validates that a relative path does not escape the base directory.
     *
     * @param basePath absolute workspace base directory
     * @param relativePath relative path to validate
     * @return normalized absolute path after validation
     * @throws SecurityException when the path is unsafe
     */
    public static Path validatePath(String basePath, String relativePath) {
        if (basePath == null || basePath.isEmpty()) {
            throw new SecurityException("Base path must not be empty");
        }
        if (relativePath == null || relativePath.isEmpty()) {
            throw new SecurityException("Relative path must not be empty");
        }

        // Reject paths containing null bytes.
        if (relativePath.indexOf('\0') >= 0) {
            throw new SecurityException(
                    "Path contains an illegal null byte: " + sanitize(relativePath));
        }

        // Reject absolute paths, including Unix-style roots and Windows drive letters.
        if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            throw new SecurityException(
                    "Absolute paths are not allowed: " + sanitize(relativePath));
        }
        if (relativePath.length() >= 2
                && Character.isLetter(relativePath.charAt(0))
                && (relativePath.charAt(1) == ':')) {
            throw new SecurityException(
                    "Absolute paths are not allowed: " + sanitize(relativePath));
        }

        // Reject path traversal patterns.
        if (containsTraversalPattern(relativePath)) {
            throw new SecurityException(
                    "Path contains an illegal traversal pattern: " + sanitize(relativePath));
        }

        // Normalize and ensure the final path stays inside the base directory.
        try {
            Path base = Path.of(basePath).toAbsolutePath().normalize();
            Path resolved = base.resolve(relativePath).normalize();

            if (!resolved.startsWith(base)) {
                throw new SecurityException(
                        "Path escapes the workspace directory: " + sanitize(relativePath));
            }
            return resolved;
        } catch (InvalidPathException e) {
            throw new SecurityException("Invalid path format: " + sanitize(relativePath), e);
        }
    }

    /**
     * Checks whether a path contains traversal patterns such as ../ or ..\.
     */
    private static boolean containsTraversalPattern(String path) {
        // Check ../ and ..\ patterns.
        if (path.contains("../") || path.contains("..\\")) {
            return true;
        }
        // Check whether the path is exactly ".." or ends with "..".
        if (path.equals("..") || path.endsWith("/..") || path.endsWith("\\..")) {
            return true;
        }
        // Check paths starting with ../ or ..\.
        return path.startsWith("..")
                && path.length() > 2
                && (path.charAt(2) == '/' || path.charAt(2) == '\\');
    }

    /**
     * Sanitizes a path string for logs and exception messages.
     */
    private static String sanitize(String path) {
        return path.replace("\0", "\\0");
    }
}
