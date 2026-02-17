package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;

import net.jqwik.api.*;

/**
 * 路径遍历防护属性测试。
 *
 * <p>Feature: sandbox-runtime-strategy, Property 3: 路径遍历防护
 *
 * <p><b>Validates: Requirements 6.5</b>
 *
 * <p>对于任意包含路径遍历模式（如 {@code ../}、绝对路径 {@code /etc/passwd}、{@code ..\\}
 * 等）的文件路径，FileSystemAdapter 的所有操作（读取、写入、列举、删除）应该拒绝该路径并返回安全错误，不执行任何文件系统操作。
 */
class PathTraversalPropertyTest {

    private static final String BASE_PATH = "/tmp/test-workspace";

    // ===== 生成器：包含路径遍历模式的随机路径 =====

    @Provide
    Arbitrary<String> pathsWithDotDotSlash() {
        Arbitrary<String> prefix = Arbitraries.of("", "sub/", "a/b/c/", "dir/");
        Arbitrary<String> traversal = Arbitraries.of("../", "../../", "../../../");
        Arbitrary<String> suffix =
                Arbitraries.of(
                        "etc/passwd",
                        "etc/shadow",
                        "windows/system32",
                        "secret.txt",
                        "root/.ssh/id_rsa",
                        "var/log/syslog");
        return Combinators.combine(prefix, traversal, suffix).as((p, t, s) -> p + t + s);
    }

    @Provide
    Arbitrary<String> pathsWithDotDotBackslash() {
        Arbitrary<String> prefix = Arbitraries.of("", "sub\\", "a\\b\\", "dir\\");
        Arbitrary<String> traversal = Arbitraries.of("..\\", "..\\..\\", "..\\..\\..\\");
        Arbitrary<String> suffix =
                Arbitraries.of(
                        "windows\\system32", "etc\\passwd", "secret.txt", "Users\\admin\\Desktop");
        return Combinators.combine(prefix, traversal, suffix).as((p, t, s) -> p + t + s);
    }

    @Provide
    Arbitrary<String> absoluteUnixPaths() {
        Arbitrary<String> paths =
                Arbitraries.of(
                        "/etc/passwd",
                        "/etc/shadow",
                        "/var/log/syslog",
                        "/root/.ssh/id_rsa",
                        "/tmp/malicious",
                        "/home/user/secret",
                        "/usr/bin/env",
                        "/proc/self/environ");
        Arbitrary<String> randomSuffix =
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10);
        return Arbitraries.oneOf(
                paths, randomSuffix.map(s -> "/" + s), randomSuffix.map(s -> "/" + s + "/" + s));
    }

    @Provide
    Arbitrary<String> absoluteWindowsPaths() {
        Arbitrary<Character> driveLetter = Arbitraries.chars().range('A', 'Z');
        Arbitrary<String> suffix =
                Arbitraries.of(
                        "\\Windows\\System32",
                        "\\Users\\admin",
                        "\\Program Files",
                        "\\temp\\malicious.exe");
        return Combinators.combine(driveLetter, suffix).as((drive, s) -> drive + ":" + s);
    }

    @Provide
    Arbitrary<String> backslashAbsolutePaths() {
        return Arbitraries.of(
                "\\etc\\passwd", "\\windows\\system32", "\\tmp\\malicious", "\\var\\log\\syslog");
    }

    @Provide
    Arbitrary<String> nullBytePaths() {
        Arbitrary<String> prefix = Arbitraries.of("file", "sub/file", "a/b/file");
        Arbitrary<String> suffix = Arbitraries.of(".txt", ".java", ".conf", "");
        return Combinators.combine(prefix, suffix).as((p, s) -> p + "\0" + s);
    }

    @Provide
    Arbitrary<String> allTraversalPaths() {
        return Arbitraries.oneOf(
                pathsWithDotDotSlash(),
                pathsWithDotDotBackslash(),
                absoluteUnixPaths(),
                absoluteWindowsPaths(),
                backslashAbsolutePaths(),
                nullBytePaths(),
                Arbitraries.of("..", "sub/..", "a/b/.."));
    }

    // ===== Property 3: PathValidator 拒绝所有路径遍历模式 =====

    /**
     * <b>Validates: Requirements 6.5</b>
     *
     * <p>对于任意包含 ../ 模式的路径，PathValidator 应抛出 SecurityException。
     */
    @Property(tries = 200)
    void pathValidator_rejectsDotDotSlashPaths(
            @ForAll("pathsWithDotDotSlash") String maliciousPath) {
        assertThrows(
                SecurityException.class,
                () -> PathValidator.validatePath(BASE_PATH, maliciousPath),
                "应拒绝包含 ../ 的路径: " + maliciousPath);
    }

    /**
     * <b>Validates: Requirements 6.5</b>
     *
     * <p>对于任意包含 ..\\ 模式的路径，PathValidator 应抛出 SecurityException。
     */
    @Property(tries = 200)
    void pathValidator_rejectsDotDotBackslashPaths(
            @ForAll("pathsWithDotDotBackslash") String maliciousPath) {
        assertThrows(
                SecurityException.class,
                () -> PathValidator.validatePath(BASE_PATH, maliciousPath),
                "应拒绝包含 ..\\ 的路径: " + maliciousPath);
    }

    /**
     * <b>Validates: Requirements 6.5</b>
     *
     * <p>对于任意 Unix 绝对路径，PathValidator 应抛出 SecurityException。
     */
    @Property(tries = 200)
    void pathValidator_rejectsAbsoluteUnixPaths(@ForAll("absoluteUnixPaths") String maliciousPath) {
        assertThrows(
                SecurityException.class,
                () -> PathValidator.validatePath(BASE_PATH, maliciousPath),
                "应拒绝 Unix 绝对路径: " + maliciousPath);
    }

    /**
     * <b>Validates: Requirements 6.5</b>
     *
     * <p>对于任意 Windows 驱动器号绝对路径，PathValidator 应抛出 SecurityException。
     */
    @Property(tries = 100)
    void pathValidator_rejectsWindowsDriveLetterPaths(
            @ForAll("absoluteWindowsPaths") String maliciousPath) {
        assertThrows(
                SecurityException.class,
                () -> PathValidator.validatePath(BASE_PATH, maliciousPath),
                "应拒绝 Windows 绝对路径: " + maliciousPath);
    }

    /**
     * <b>Validates: Requirements 6.5</b>
     *
     * <p>对于任意以反斜杠开头的绝对路径，PathValidator 应抛出 SecurityException。
     */
    @Property(tries = 100)
    void pathValidator_rejectsBackslashAbsolutePaths(
            @ForAll("backslashAbsolutePaths") String maliciousPath) {
        assertThrows(
                SecurityException.class,
                () -> PathValidator.validatePath(BASE_PATH, maliciousPath),
                "应拒绝反斜杠绝对路径: " + maliciousPath);
    }

    /**
     * <b>Validates: Requirements 6.5</b>
     *
     * <p>对于任意包含 null 字节的路径，PathValidator 应抛出 SecurityException。
     */
    @Property(tries = 100)
    void pathValidator_rejectsNullBytePaths(@ForAll("nullBytePaths") String maliciousPath) {
        assertThrows(
                SecurityException.class,
                () -> PathValidator.validatePath(BASE_PATH, maliciousPath),
                "应拒绝包含 null 字节的路径: " + maliciousPath.replace("\0", "\\0"));
    }

    // ===== Property 3: LocalFileSystemAdapter 对所有操作拒绝遍历路径 =====

    /**
     * <b>Validates: Requirements 6.5</b>
     *
     * <p>对于任意包含路径遍历模式的路径，LocalFileSystemAdapter.readFile 应抛出 FileSystemException（PATH_TRAVERSAL）。
     */
    @Property(tries = 200)
    void localAdapter_readFile_rejectsTraversalPaths(
            @ForAll("allTraversalPaths") String maliciousPath) {
        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(BASE_PATH);
        FileSystemException ex =
                assertThrows(
                        FileSystemException.class,
                        () -> adapter.readFile(maliciousPath),
                        "readFile 应拒绝遍历路径: " + sanitize(maliciousPath));
        assertEquals(FileSystemException.ErrorType.PATH_TRAVERSAL, ex.getErrorType());
        assertEquals(RuntimeType.LOCAL, ex.getRuntimeType());
    }

    /**
     * <b>Validates: Requirements 6.5</b>
     *
     * <p>对于任意包含路径遍历模式的路径，LocalFileSystemAdapter.writeFile 应抛出 FileSystemException（PATH_TRAVERSAL）。
     */
    @Property(tries = 200)
    void localAdapter_writeFile_rejectsTraversalPaths(
            @ForAll("allTraversalPaths") String maliciousPath) {
        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(BASE_PATH);
        FileSystemException ex =
                assertThrows(
                        FileSystemException.class,
                        () -> adapter.writeFile(maliciousPath, "malicious content"),
                        "writeFile 应拒绝遍历路径: " + sanitize(maliciousPath));
        assertEquals(FileSystemException.ErrorType.PATH_TRAVERSAL, ex.getErrorType());
        assertEquals(RuntimeType.LOCAL, ex.getRuntimeType());
    }

    /**
     * <b>Validates: Requirements 6.5</b>
     *
     * <p>对于任意包含路径遍历模式的路径，LocalFileSystemAdapter.listDirectory 应抛出
     * FileSystemException（PATH_TRAVERSAL）。
     */
    @Property(tries = 200)
    void localAdapter_listDirectory_rejectsTraversalPaths(
            @ForAll("allTraversalPaths") String maliciousPath) {
        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(BASE_PATH);
        FileSystemException ex =
                assertThrows(
                        FileSystemException.class,
                        () -> adapter.listDirectory(maliciousPath),
                        "listDirectory 应拒绝遍历路径: " + sanitize(maliciousPath));
        assertEquals(FileSystemException.ErrorType.PATH_TRAVERSAL, ex.getErrorType());
        assertEquals(RuntimeType.LOCAL, ex.getRuntimeType());
    }

    /**
     * <b>Validates: Requirements 6.5</b>
     *
     * <p>对于任意包含路径遍历模式的路径，LocalFileSystemAdapter.delete 应抛出 FileSystemException（PATH_TRAVERSAL）。
     */
    @Property(tries = 200)
    void localAdapter_delete_rejectsTraversalPaths(
            @ForAll("allTraversalPaths") String maliciousPath) {
        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(BASE_PATH);
        FileSystemException ex =
                assertThrows(
                        FileSystemException.class,
                        () -> adapter.delete(maliciousPath),
                        "delete 应拒绝遍历路径: " + sanitize(maliciousPath));
        assertEquals(FileSystemException.ErrorType.PATH_TRAVERSAL, ex.getErrorType());
        assertEquals(RuntimeType.LOCAL, ex.getRuntimeType());
    }

    private static String sanitize(String path) {
        return path == null ? "null" : path.replace("\0", "\\0");
    }
}
