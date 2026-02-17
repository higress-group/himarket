package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * LocalFileSystemAdapter 单元测试。
 */
class LocalFileSystemAdapterTest {

    @TempDir Path tempDir;

    private LocalFileSystemAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LocalFileSystemAdapter(tempDir.toString());
    }

    // ===== 构造函数测试 =====

    @Test
    void constructor_nullBasePath_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new LocalFileSystemAdapter(null));
    }

    @Test
    void constructor_emptyBasePath_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new LocalFileSystemAdapter(""));
    }

    // ===== readFile 测试 =====

    @Test
    void readFile_existingFile_returnsContent() throws IOException {
        Files.writeString(tempDir.resolve("hello.txt"), "你好世界");
        assertEquals("你好世界", adapter.readFile("hello.txt"));
    }

    @Test
    void readFile_nestedFile_returnsContent() throws IOException {
        Path nested = tempDir.resolve("sub/dir");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("data.txt"), "nested content");
        assertEquals("nested content", adapter.readFile("sub/dir/data.txt"));
    }

    @Test
    void readFile_nonExistent_throwsFileNotFound() {
        FileSystemException ex =
                assertThrows(FileSystemException.class, () -> adapter.readFile("missing.txt"));
        assertEquals(FileSystemException.ErrorType.FILE_NOT_FOUND, ex.getErrorType());
        assertEquals(RuntimeType.LOCAL, ex.getRuntimeType());
    }

    @Test
    void readFile_directory_throwsNotAFile() throws IOException {
        Files.createDirectory(tempDir.resolve("adir"));
        FileSystemException ex =
                assertThrows(FileSystemException.class, () -> adapter.readFile("adir"));
        assertEquals(FileSystemException.ErrorType.NOT_A_FILE, ex.getErrorType());
        assertEquals(RuntimeType.LOCAL, ex.getRuntimeType());
    }

    @Test
    void readFile_pathTraversal_throwsPathTraversal() {
        FileSystemException ex =
                assertThrows(FileSystemException.class, () -> adapter.readFile("../etc/passwd"));
        assertEquals(FileSystemException.ErrorType.PATH_TRAVERSAL, ex.getErrorType());
        assertEquals(RuntimeType.LOCAL, ex.getRuntimeType());
    }

    // ===== writeFile 测试 =====

    @Test
    void writeFile_newFile_createsFile() throws IOException {
        adapter.writeFile("new.txt", "content");
        assertEquals("content", Files.readString(tempDir.resolve("new.txt")));
    }

    @Test
    void writeFile_existingFile_overwritesContent() throws IOException {
        Files.writeString(tempDir.resolve("exist.txt"), "old");
        adapter.writeFile("exist.txt", "new");
        assertEquals("new", Files.readString(tempDir.resolve("exist.txt")));
    }

    @Test
    void writeFile_nestedPath_createsParentDirs() throws IOException {
        adapter.writeFile("a/b/c/file.txt", "deep");
        assertEquals("deep", Files.readString(tempDir.resolve("a/b/c/file.txt")));
    }

    @Test
    void writeFile_pathTraversal_throwsPathTraversal() {
        FileSystemException ex =
                assertThrows(
                        FileSystemException.class,
                        () -> adapter.writeFile("../../evil.txt", "bad"));
        assertEquals(FileSystemException.ErrorType.PATH_TRAVERSAL, ex.getErrorType());
    }

    // ===== writeFile + readFile round-trip =====

    @Test
    void writeAndRead_roundTrip_contentMatches() throws IOException {
        String content = "Round-trip 测试内容 \n 包含换行和特殊字符 !@#$%";
        adapter.writeFile("roundtrip.txt", content);
        assertEquals(content, adapter.readFile("roundtrip.txt"));
    }

    // ===== listDirectory 测试 =====

    @Test
    void listDirectory_withFiles_returnsEntries() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "a");
        Files.writeString(tempDir.resolve("b.txt"), "b");
        Files.createDirectory(tempDir.resolve("subdir"));

        List<FileEntry> entries = adapter.listDirectory(".");
        assertEquals(3, entries.size());

        List<String> names = entries.stream().map(FileEntry::name).sorted().toList();
        assertEquals(List.of("a.txt", "b.txt", "subdir"), names);

        FileEntry dirEntry =
                entries.stream().filter(e -> e.name().equals("subdir")).findFirst().orElseThrow();
        assertTrue(dirEntry.isDirectory());

        FileEntry fileEntry =
                entries.stream().filter(e -> e.name().equals("a.txt")).findFirst().orElseThrow();
        assertFalse(fileEntry.isDirectory());
    }

    @Test
    void listDirectory_emptyDir_returnsEmptyList() throws IOException {
        Files.createDirectory(tempDir.resolve("empty"));
        List<FileEntry> entries = adapter.listDirectory("empty");
        assertTrue(entries.isEmpty());
    }

    @Test
    void listDirectory_nonExistent_throwsFileNotFound() {
        FileSystemException ex =
                assertThrows(FileSystemException.class, () -> adapter.listDirectory("nodir"));
        assertEquals(FileSystemException.ErrorType.FILE_NOT_FOUND, ex.getErrorType());
    }

    @Test
    void listDirectory_onFile_throwsNotADirectory() throws IOException {
        Files.writeString(tempDir.resolve("file.txt"), "x");
        FileSystemException ex =
                assertThrows(FileSystemException.class, () -> adapter.listDirectory("file.txt"));
        assertEquals(FileSystemException.ErrorType.NOT_A_DIRECTORY, ex.getErrorType());
    }

    // ===== createDirectory 测试 =====

    @Test
    void createDirectory_simple_createsDir() throws IOException {
        adapter.createDirectory("newdir");
        assertTrue(Files.isDirectory(tempDir.resolve("newdir")));
    }

    @Test
    void createDirectory_nested_createsAllDirs() throws IOException {
        adapter.createDirectory("x/y/z");
        assertTrue(Files.isDirectory(tempDir.resolve("x/y/z")));
    }

    @Test
    void createDirectory_alreadyExists_noError() throws IOException {
        Files.createDirectory(tempDir.resolve("existing"));
        assertDoesNotThrow(() -> adapter.createDirectory("existing"));
    }

    @Test
    void createDirectory_pathTraversal_throwsPathTraversal() {
        FileSystemException ex =
                assertThrows(
                        FileSystemException.class, () -> adapter.createDirectory("../outside"));
        assertEquals(FileSystemException.ErrorType.PATH_TRAVERSAL, ex.getErrorType());
    }

    // ===== delete 测试 =====

    @Test
    void delete_file_removesFile() throws IOException {
        Files.writeString(tempDir.resolve("todelete.txt"), "bye");
        adapter.delete("todelete.txt");
        assertFalse(Files.exists(tempDir.resolve("todelete.txt")));
    }

    @Test
    void delete_emptyDirectory_removesDir() throws IOException {
        Files.createDirectory(tempDir.resolve("emptydir"));
        adapter.delete("emptydir");
        assertFalse(Files.exists(tempDir.resolve("emptydir")));
    }

    @Test
    void delete_nonEmptyDirectory_removesRecursively() throws IOException {
        Path dir = tempDir.resolve("nonempty");
        Files.createDirectories(dir.resolve("sub"));
        Files.writeString(dir.resolve("file.txt"), "x");
        Files.writeString(dir.resolve("sub/nested.txt"), "y");

        adapter.delete("nonempty");
        assertFalse(Files.exists(dir));
    }

    @Test
    void delete_nonExistent_throwsFileNotFound() {
        FileSystemException ex =
                assertThrows(FileSystemException.class, () -> adapter.delete("ghost"));
        assertEquals(FileSystemException.ErrorType.FILE_NOT_FOUND, ex.getErrorType());
    }

    // ===== getFileInfo 测试 =====

    @Test
    void getFileInfo_file_returnsCorrectInfo() throws IOException {
        Files.writeString(tempDir.resolve("info.txt"), "hello");
        FileInfo info = adapter.getFileInfo("info.txt");

        assertEquals("info.txt", info.path());
        assertFalse(info.isDirectory());
        assertEquals(5, info.size());
        assertTrue(info.lastModified() > 0);
        assertTrue(info.readable());
        assertTrue(info.writable());
    }

    @Test
    void getFileInfo_directory_returnsIsDirectory() throws IOException {
        Files.createDirectory(tempDir.resolve("infodir"));
        FileInfo info = adapter.getFileInfo("infodir");

        assertEquals("infodir", info.path());
        assertTrue(info.isDirectory());
    }

    @Test
    void getFileInfo_nonExistent_throwsFileNotFound() {
        FileSystemException ex =
                assertThrows(FileSystemException.class, () -> adapter.getFileInfo("nope.txt"));
        assertEquals(FileSystemException.ErrorType.FILE_NOT_FOUND, ex.getErrorType());
    }

    // ===== 错误格式一致性测试 =====

    @Test
    void allErrors_containErrorTypeAndRuntimeType() {
        // 验证所有 FileSystemException 都包含 errorType 和 runtimeType
        FileSystemException ex =
                assertThrows(FileSystemException.class, () -> adapter.readFile("nonexistent.txt"));
        assertNotNull(ex.getErrorType());
        assertNotNull(ex.getRuntimeType());
        assertTrue(ex.getMessage().contains("LOCAL"));
        assertTrue(ex.getMessage().contains("FILE_NOT_FOUND"));
    }

    @Test
    void pathTraversalError_containsCorrectFields() {
        FileSystemException ex =
                assertThrows(
                        FileSystemException.class, () -> adapter.readFile("../../../etc/shadow"));
        assertEquals(FileSystemException.ErrorType.PATH_TRAVERSAL, ex.getErrorType());
        assertEquals(RuntimeType.LOCAL, ex.getRuntimeType());
        assertTrue(ex.getMessage().contains("LOCAL"));
        assertTrue(ex.getMessage().contains("PATH_TRAVERSAL"));
    }
}
