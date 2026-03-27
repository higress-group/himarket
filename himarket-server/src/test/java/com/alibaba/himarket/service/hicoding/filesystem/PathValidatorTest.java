package com.alibaba.himarket.service.hicoding.filesystem;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * PathValidator 单元测试。
 */
class PathValidatorTest {

    @TempDir Path tempDir;

    // ===== 合法路径测试 =====

    @Test
    void validatePath_simpleFileName_returnsResolvedPath() {
        Path result = PathValidator.validatePath(tempDir.toString(), "file.txt");
        assertEquals(tempDir.resolve("file.txt"), result);
    }

    @Test
    void validatePath_nestedPath_returnsResolvedPath() {
        Path result = PathValidator.validatePath(tempDir.toString(), "sub/dir/file.txt");
        assertEquals(tempDir.resolve("sub/dir/file.txt"), result);
    }

    // ===== 路径遍历攻击测试 =====

    @Test
    void validatePath_dotDotSlash_throwsSecurityException() {
        assertThrows(
                SecurityException.class,
                () -> PathValidator.validatePath(tempDir.toString(), "../etc/passwd"));
    }

    @Test
    void validatePath_dotDotBackslash_throwsSecurityException() {
        assertThrows(
                SecurityException.class,
                () -> PathValidator.validatePath(tempDir.toString(), "..\\windows\\system32"));
    }

    @Test
    void validatePath_embeddedTraversal_throwsSecurityException() {
        assertThrows(
                SecurityException.class,
                () -> PathValidator.validatePath(tempDir.toString(), "sub/../../../etc/passwd"));
    }

    @Test
    void validatePath_doubleDotOnly_throwsSecurityException() {
        assertThrows(
                SecurityException.class,
                () -> PathValidator.validatePath(tempDir.toString(), ".."));
    }

    @Test
    void validatePath_trailingDoubleDot_throwsSecurityException() {
        assertThrows(
                SecurityException.class,
                () -> PathValidator.validatePath(tempDir.toString(), "sub/.."));
    }

    // ===== 绝对路径测试 =====

    @Test
    void validatePath_unixAbsolutePath_throwsSecurityException() {
        assertThrows(
                SecurityException.class,
                () -> PathValidator.validatePath(tempDir.toString(), "/etc/passwd"));
    }

    @Test
    void validatePath_windowsDriveLetter_throwsSecurityException() {
        assertThrows(
                SecurityException.class,
                () -> PathValidator.validatePath(tempDir.toString(), "C:\\Windows\\System32"));
    }

    @Test
    void validatePath_backslashAbsolutePath_throwsSecurityException() {
        assertThrows(
                SecurityException.class,
                () -> PathValidator.validatePath(tempDir.toString(), "\\etc\\passwd"));
    }

    // ===== null 字节测试 =====

    @Test
    void validatePath_nullByte_throwsSecurityException() {
        assertThrows(
                SecurityException.class,
                () -> PathValidator.validatePath(tempDir.toString(), "file\0.txt"));
    }

    // ===== 空值/null 测试 =====

    @Test
    void validatePath_nullBasePath_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> PathValidator.validatePath(null, "file.txt"));
    }

    @Test
    void validatePath_emptyBasePath_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> PathValidator.validatePath("", "file.txt"));
    }

    @Test
    void validatePath_nullRelativePath_throwsSecurityException() {
        assertThrows(
                SecurityException.class,
                () -> PathValidator.validatePath(tempDir.toString(), null));
    }

    @Test
    void validatePath_emptyRelativePath_throwsSecurityException() {
        assertThrows(
                SecurityException.class, () -> PathValidator.validatePath(tempDir.toString(), ""));
    }
}
