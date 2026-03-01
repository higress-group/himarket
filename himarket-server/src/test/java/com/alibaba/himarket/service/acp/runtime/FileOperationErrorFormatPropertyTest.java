package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;

import net.jqwik.api.*;

/**
 * 文件操作错误格式一致性属性测试。
 *
 * <p>Feature: sandbox-runtime-strategy, Property 4: 文件操作错误格式一致性
 *
 * <p><b>Validates: Requirements 6.6</b>
 *
 * <p>对于任意运行时类型和任意导致失败的文件操作（如读取不存在的文件、写入只读路径），
 * FileSystemAdapter 返回的错误信息应该包含 errorType 字段和 runtimeType 字段。
 */
class FileOperationErrorFormatPropertyTest {

    private static final String BASE_PATH = "/tmp/test-workspace-error-format";

    // ===== 生成器 =====

    /** 生成随机 SandboxType 值 */
    @Provide
    Arbitrary<SandboxType> runtimeTypes() {
        return Arbitraries.of(SandboxType.values());
    }

    /** 生成不存在的随机文件路径（合法相对路径，但文件不存在） */
    @Provide
    Arbitrary<String> nonExistentPaths() {
        Arbitrary<String> dirPart = Arbitraries.of("", "sub/", "a/b/", "deep/nested/dir/");
        Arbitrary<String> namePart = Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(12);
        Arbitrary<String> extPart = Arbitraries.of(".txt", ".java", ".conf", ".log", ".json");
        return Combinators.combine(dirPart, namePart, extPart)
                .as((dir, name, ext) -> dir + "nonexistent_" + name + ext);
    }

    /**
     * 无效文件操作类型枚举，用于驱动不同的失败场景。
     */
    enum InvalidOperation {
        READ_NON_EXISTENT,
        LIST_NON_EXISTENT,
        DELETE_NON_EXISTENT,
        GET_INFO_NON_EXISTENT
    }

    @Provide
    Arbitrary<InvalidOperation> invalidOperations() {
        return Arbitraries.of(InvalidOperation.values());
    }

    // ===== Property 4: 文件操作错误格式一致性 =====

    /**
     * <b>Validates: Requirements 6.6</b>
     *
     * <p>对于任意运行时类型和任意不存在的文件路径，LocalFileSystemAdapter 的读取操作
     * 抛出的 FileSystemException 应包含非空的 errorType 和与 LOCAL 一致的 runtimeType。
     */
    @Property(tries = 200)
    void readFile_errorContainsErrorTypeAndRuntimeType(@ForAll("nonExistentPaths") String path) {
        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(BASE_PATH);
        FileSystemException ex =
                assertThrows(
                        FileSystemException.class,
                        () -> adapter.readFile(path),
                        "readFile 应对不存在的文件抛出 FileSystemException: " + path);
        assertNotNull(ex.getErrorType(), "errorType 不应为 null");
        assertNotNull(ex.getSandboxType(), "runtimeType 不应为 null");
        assertEquals(SandboxType.LOCAL, ex.getSandboxType());
    }

    /**
     * <b>Validates: Requirements 6.6</b>
     *
     * <p>对于任意运行时类型和任意不存在的目录路径，LocalFileSystemAdapter 的列举目录操作
     * 抛出的 FileSystemException 应包含非空的 errorType 和 runtimeType。
     */
    @Property(tries = 200)
    void listDirectory_errorContainsErrorTypeAndRuntimeType(
            @ForAll("nonExistentPaths") String path) {
        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(BASE_PATH);
        FileSystemException ex =
                assertThrows(
                        FileSystemException.class,
                        () -> adapter.listDirectory(path),
                        "listDirectory 应对不存在的目录抛出 FileSystemException: " + path);
        assertNotNull(ex.getErrorType(), "errorType 不应为 null");
        assertNotNull(ex.getSandboxType(), "runtimeType 不应为 null");
        assertEquals(SandboxType.LOCAL, ex.getSandboxType());
    }

    /**
     * <b>Validates: Requirements 6.6</b>
     *
     * <p>对于任意运行时类型和任意不存在的文件路径，LocalFileSystemAdapter 的删除操作
     * 抛出的 FileSystemException 应包含非空的 errorType 和 runtimeType。
     */
    @Property(tries = 200)
    void delete_errorContainsErrorTypeAndRuntimeType(@ForAll("nonExistentPaths") String path) {
        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(BASE_PATH);
        FileSystemException ex =
                assertThrows(
                        FileSystemException.class,
                        () -> adapter.delete(path),
                        "delete 应对不存在的文件抛出 FileSystemException: " + path);
        assertNotNull(ex.getErrorType(), "errorType 不应为 null");
        assertNotNull(ex.getSandboxType(), "runtimeType 不应为 null");
        assertEquals(SandboxType.LOCAL, ex.getSandboxType());
    }

    /**
     * <b>Validates: Requirements 6.6</b>
     *
     * <p>对于任意运行时类型和任意不存在的文件路径，LocalFileSystemAdapter 的获取文件信息操作
     * 抛出的 FileSystemException 应包含非空的 errorType 和 runtimeType。
     */
    @Property(tries = 200)
    void getFileInfo_errorContainsErrorTypeAndRuntimeType(@ForAll("nonExistentPaths") String path) {
        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(BASE_PATH);
        FileSystemException ex =
                assertThrows(
                        FileSystemException.class,
                        () -> adapter.getFileInfo(path),
                        "getFileInfo 应对不存在的文件抛出 FileSystemException: " + path);
        assertNotNull(ex.getErrorType(), "errorType 不应为 null");
        assertNotNull(ex.getSandboxType(), "runtimeType 不应为 null");
        assertEquals(SandboxType.LOCAL, ex.getSandboxType());
    }

    /**
     * <b>Validates: Requirements 6.6</b>
     *
     * <p>对于任意无效操作类型和任意不存在的文件路径，所有失败的文件操作抛出的
     * FileSystemException 的 errorType 应为 FILE_NOT_FOUND（针对不存在的文件场景）。
     */
    @Property(tries = 200)
    void allFailedOperations_errorTypeIsFileNotFound(
            @ForAll("invalidOperations") InvalidOperation op,
            @ForAll("nonExistentPaths") String path) {
        LocalFileSystemAdapter adapter = new LocalFileSystemAdapter(BASE_PATH);
        FileSystemException ex = executeInvalidOperation(adapter, op, path);
        assertNotNull(ex, "应抛出 FileSystemException");
        assertEquals(
                FileSystemException.ErrorType.FILE_NOT_FOUND,
                ex.getErrorType(),
                "不存在文件的操作应返回 FILE_NOT_FOUND 错误类型");
        assertEquals(SandboxType.LOCAL, ex.getSandboxType());
    }

    /**
     * <b>Validates: Requirements 6.6</b>
     *
     * <p>对于任意 SandboxType 值，直接构造的 FileSystemException 应始终正确保留
     * errorType 和 runtimeType 字段，验证错误格式的结构完整性。
     */
    @Property(tries = 100)
    void fileSystemException_alwaysContainsBothFields(
            @ForAll("runtimeTypes") SandboxType runtimeType) {
        for (FileSystemException.ErrorType errorType : FileSystemException.ErrorType.values()) {
            FileSystemException ex = new FileSystemException(errorType, runtimeType, "测试错误消息");
            assertNotNull(ex.getErrorType(), "errorType 不应为 null");
            assertNotNull(ex.getSandboxType(), "runtimeType 不应为 null");
            assertEquals(errorType, ex.getErrorType());
            assertEquals(runtimeType, ex.getSandboxType());
            assertTrue(
                    ex.getMessage().contains(runtimeType.name()),
                    "错误消息应包含运行时类型: " + ex.getMessage());
            assertTrue(
                    ex.getMessage().contains(errorType.name()), "错误消息应包含错误类型: " + ex.getMessage());
        }
    }

    // ===== 辅助方法 =====

    private FileSystemException executeInvalidOperation(
            LocalFileSystemAdapter adapter, InvalidOperation op, String path) {
        try {
            switch (op) {
                case READ_NON_EXISTENT -> adapter.readFile(path);
                case LIST_NON_EXISTENT -> adapter.listDirectory(path);
                case DELETE_NON_EXISTENT -> adapter.delete(path);
                case GET_INFO_NON_EXISTENT -> adapter.getFileInfo(path);
            }
            fail("应抛出 FileSystemException，操作: " + op + "，路径: " + path);
            return null;
        } catch (FileSystemException e) {
            return e;
        } catch (Exception e) {
            fail("应抛出 FileSystemException 而非 " + e.getClass().getName() + ": " + e.getMessage());
            return null;
        }
    }
}
