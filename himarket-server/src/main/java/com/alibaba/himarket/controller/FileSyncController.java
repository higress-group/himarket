package com.alibaba.himarket.controller;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.service.acp.runtime.FileSyncService;
import com.alibaba.himarket.service.acp.runtime.FileSyncService.FileItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件同步 REST API。
 *
 * <p>提供文件持久化接口：
 * <ul>
 *   <li>POST /api/workspace/sync — 接收文件同步请求，将文件写入服务端</li>
 *   <li>GET /api/workspace/files — 返回用户持久化的文件列表</li>
 * </ul>
 *
 * <p>Requirements: 9.3, 9.5
 */
@Tag(name = "工作空间文件同步", description = "文件持久化接口")
@RestController
@RequestMapping("/api/workspace")
@RequiredArgsConstructor
@Slf4j
public class FileSyncController {

    private final FileSyncService fileSyncService;

    @Operation(summary = "同步文件到服务端")
    @PostMapping("/sync")
    public SyncResponse syncFiles(@RequestBody SyncRequest request) {
        if (request.userId() == null || request.userId().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "userId 不能为空");
        }
        if (request.files() == null || request.files().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "files 不能为空");
        }
        try {
            fileSyncService.syncFiles(request.userId(), request.files());
            return new SyncResponse(true, "同步成功，共 " + request.files().size() + " 个文件");
        } catch (SecurityException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, e, e.getMessage());
        } catch (IOException e) {
            log.error("文件同步失败: userId={}", request.userId(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "文件同步失败");
        }
    }

    @Operation(summary = "获取用户持久化的文件列表")
    @GetMapping("/files")
    public List<FileItem> listFiles(
            @Parameter(description = "用户 ID", required = true) @RequestParam String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "userId 不能为空");
        }
        try {
            return fileSyncService.listFiles(userId);
        } catch (SecurityException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, e, e.getMessage());
        } catch (IOException e) {
            log.error("获取文件列表失败: userId={}", userId, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "获取文件列表失败");
        }
    }

    /** 文件同步请求体 */
    public record SyncRequest(String userId, List<FileItem> files) {}

    /** 文件同步响应体 */
    public record SyncResponse(boolean success, String message) {}
}
