package com.alibaba.himarket.service.acp.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.himarket.config.AcpProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 单元测试：验证 FileSyncService 的文件同步和读取逻辑。
 */
class FileSyncServiceTest {

    @TempDir Path tempDir;

    private FileSyncService service;

    @BeforeEach
    void setUp() {
        AcpProperties props = new AcpProperties();
        props.setWorkspaceRoot(tempDir.toString());
        service = new FileSyncService(props);
    }

    // --- syncFiles ---

    @Test
    void syncFiles_writesFilesToUserDirectory() throws IOException {
        List<FileSyncService.FileItem> files =
                List.of(
                        new FileSyncService.FileItem("hello.txt", "Hello World"),
                        new FileSyncService.FileItem("src/main.js", "console.log('hi')"));

        service.syncFiles("user1", files);

        Path hello = tempDir.resolve("user1/hello.txt");
        Path main = tempDir.resolve("user1/src/main.js");
        assertTrue(Files.exists(hello));
        assertTrue(Files.exists(main));
        assertEquals("Hello World", Files.readString(hello));
        assertEquals("console.log('hi')", Files.readString(main));
    }

    @Test
    void syncFiles_overwritesExistingFile() throws IOException {
        service.syncFiles("user1", List.of(new FileSyncService.FileItem("a.txt", "v1")));
        service.syncFiles("user1", List.of(new FileSyncService.FileItem("a.txt", "v2")));

        assertEquals("v2", Files.readString(tempDir.resolve("user1/a.txt")));
    }

    @Test
    void syncFiles_pathTraversal_throwsSecurityException() {
        List<FileSyncService.FileItem> files =
                List.of(new FileSyncService.FileItem("../escape.txt", "bad"));

        assertThrows(SecurityException.class, () -> service.syncFiles("user1", files));
    }

    @Test
    void syncFiles_absolutePath_throwsSecurityException() {
        List<FileSyncService.FileItem> files =
                List.of(new FileSyncService.FileItem("/etc/passwd", "bad"));

        assertThrows(SecurityException.class, () -> service.syncFiles("user1", files));
    }

    @Test
    void syncFiles_maliciousUserId_throwsSecurityException() {
        List<FileSyncService.FileItem> files = List.of(new FileSyncService.FileItem("a.txt", "ok"));

        assertThrows(SecurityException.class, () -> service.syncFiles("../evil", files));
    }

    // --- listFiles ---

    @Test
    void listFiles_returnsAllFilesInUserDirectory() throws IOException {
        service.syncFiles(
                "user2",
                List.of(
                        new FileSyncService.FileItem("index.html", "<h1>Hi</h1>"),
                        new FileSyncService.FileItem("css/style.css", "body{}")));

        List<FileSyncService.FileItem> result = service.listFiles("user2");

        assertEquals(2, result.size());
        // 按路径排序以便断言
        List<FileSyncService.FileItem> sorted =
                result.stream()
                        .sorted(Comparator.comparing(FileSyncService.FileItem::path))
                        .toList();
        assertEquals("css/style.css", sorted.get(0).path());
        assertEquals("body{}", sorted.get(0).content());
        assertEquals("index.html", sorted.get(1).path());
        assertEquals("<h1>Hi</h1>", sorted.get(1).content());
    }

    @Test
    void listFiles_nonExistentUser_returnsEmptyList() throws IOException {
        List<FileSyncService.FileItem> result = service.listFiles("nobody");

        assertTrue(result.isEmpty());
    }

    @Test
    void listFiles_emptyDirectory_returnsEmptyList() throws IOException {
        Files.createDirectories(tempDir.resolve("emptyuser"));

        List<FileSyncService.FileItem> result = service.listFiles("emptyuser");

        assertTrue(result.isEmpty());
    }

    @Test
    void listFiles_maliciousUserId_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> service.listFiles("../../etc"));
    }

    // --- round-trip ---

    @Test
    void syncThenList_roundTrip_contentMatches() throws IOException {
        List<FileSyncService.FileItem> original =
                List.of(
                        new FileSyncService.FileItem("app.js", "const x = 42;"),
                        new FileSyncService.FileItem("lib/util.js", "export default {}"));

        service.syncFiles("user3", original);
        List<FileSyncService.FileItem> restored = service.listFiles("user3");

        assertEquals(original.size(), restored.size());
        for (FileSyncService.FileItem orig : original) {
            FileSyncService.FileItem found =
                    restored.stream()
                            .filter(f -> f.path().equals(orig.path()))
                            .findFirst()
                            .orElseThrow();
            assertEquals(orig.content(), found.content());
        }
    }
}
