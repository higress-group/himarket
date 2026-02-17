package com.alibaba.himarket.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alibaba.himarket.controller.FileSyncController.SyncRequest;
import com.alibaba.himarket.controller.FileSyncController.SyncResponse;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.service.acp.runtime.FileSyncService;
import com.alibaba.himarket.service.acp.runtime.FileSyncService.FileItem;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 单元测试：验证 FileSyncController 的 REST API 逻辑和错误处理。
 */
@ExtendWith(MockitoExtension.class)
class FileSyncControllerTest {

    @Mock private FileSyncService fileSyncService;

    private FileSyncController controller;

    @BeforeEach
    void setUp() {
        controller = new FileSyncController(fileSyncService);
    }

    // --- POST /api/workspace/sync ---

    @Test
    void syncFiles_validRequest_returnsSuccess() throws IOException {
        List<FileItem> files = List.of(new FileItem("a.txt", "hello"));
        SyncRequest request = new SyncRequest("user1", files);

        SyncResponse response = controller.syncFiles(request);

        assertTrue(response.success());
        assertNotNull(response.message());
        verify(fileSyncService).syncFiles("user1", files);
    }

    @Test
    void syncFiles_nullUserId_throwsBadRequest() {
        SyncRequest request = new SyncRequest(null, List.of(new FileItem("a.txt", "x")));

        BusinessException ex =
                assertThrows(BusinessException.class, () -> controller.syncFiles(request));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void syncFiles_blankUserId_throwsBadRequest() {
        SyncRequest request = new SyncRequest("  ", List.of(new FileItem("a.txt", "x")));

        BusinessException ex =
                assertThrows(BusinessException.class, () -> controller.syncFiles(request));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void syncFiles_nullFiles_throwsBadRequest() {
        SyncRequest request = new SyncRequest("user1", null);

        BusinessException ex =
                assertThrows(BusinessException.class, () -> controller.syncFiles(request));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void syncFiles_emptyFiles_throwsBadRequest() {
        SyncRequest request = new SyncRequest("user1", List.of());

        BusinessException ex =
                assertThrows(BusinessException.class, () -> controller.syncFiles(request));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void syncFiles_pathTraversal_throwsBadRequest() throws IOException {
        List<FileItem> files = List.of(new FileItem("../evil.txt", "bad"));
        SyncRequest request = new SyncRequest("user1", files);
        doThrow(new SecurityException("路径包含非法的遍历模式"))
                .when(fileSyncService)
                .syncFiles("user1", files);

        BusinessException ex =
                assertThrows(BusinessException.class, () -> controller.syncFiles(request));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void syncFiles_ioError_throwsInternalError() throws IOException {
        List<FileItem> files = List.of(new FileItem("a.txt", "x"));
        SyncRequest request = new SyncRequest("user1", files);
        doThrow(new IOException("磁盘写入失败")).when(fileSyncService).syncFiles("user1", files);

        BusinessException ex =
                assertThrows(BusinessException.class, () -> controller.syncFiles(request));
        assertEquals(500, ex.getStatus().value());
    }

    // --- GET /api/workspace/files ---

    @Test
    void listFiles_validUserId_returnsFileList() throws IOException {
        List<FileItem> files =
                List.of(new FileItem("a.txt", "hello"), new FileItem("b.txt", "world"));
        when(fileSyncService.listFiles("user1")).thenReturn(files);

        List<FileItem> result = controller.listFiles("user1");

        assertEquals(2, result.size());
        assertEquals("a.txt", result.get(0).path());
        assertEquals("hello", result.get(0).content());
    }

    @Test
    void listFiles_nullUserId_throwsBadRequest() {
        BusinessException ex =
                assertThrows(BusinessException.class, () -> controller.listFiles(null));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void listFiles_blankUserId_throwsBadRequest() {
        BusinessException ex =
                assertThrows(BusinessException.class, () -> controller.listFiles("  "));
        assertEquals(400, ex.getStatus().value());
    }

    @Test
    void listFiles_noFiles_returnsEmptyList() throws IOException {
        when(fileSyncService.listFiles("empty-user")).thenReturn(List.of());

        List<FileItem> result = controller.listFiles("empty-user");

        assertTrue(result.isEmpty());
    }

    @Test
    void listFiles_ioError_throwsInternalError() throws IOException {
        when(fileSyncService.listFiles("user1")).thenThrow(new IOException("读取失败"));

        BusinessException ex =
                assertThrows(BusinessException.class, () -> controller.listFiles("user1"));
        assertEquals(500, ex.getStatus().value());
    }

    @Test
    void listFiles_securityException_throwsBadRequest() throws IOException {
        when(fileSyncService.listFiles("../../evil")).thenThrow(new SecurityException("非法的用户 ID"));

        BusinessException ex =
                assertThrows(BusinessException.class, () -> controller.listFiles("../../evil"));
        assertEquals(400, ex.getStatus().value());
    }
}
