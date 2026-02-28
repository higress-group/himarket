package com.alibaba.himarket.service.acp.runtime;

import java.io.IOException;
import java.net.URI;

/**
 * 统一沙箱提供者接口。
 *
 * <p>抽象不同沙箱环境（本地 Mac、K8s Pod、E2B）的差异， 为 SandboxInitPipeline 提供统一的操作契约。
 */
public interface SandboxProvider {

    /** 沙箱类型标识 */
    SandboxType getType();

    /**
     * 获取或创建沙箱实例。
     */
    SandboxInfo acquire(SandboxConfig config);

    /**
     * 释放沙箱资源。
     */
    void release(SandboxInfo info);

    /**
     * 文件系统健康检查。 通过 Sidecar HTTP API 验证沙箱文件系统可读写。
     */
    boolean healthCheck(SandboxInfo info);

    /**
     * 写入文件到沙箱工作空间。 所有沙箱类型统一通过 Sidecar HTTP API（POST /files/write）写入。
     */
    void writeFile(SandboxInfo info, String relativePath, String content) throws IOException;

    /**
     * 从沙箱工作空间读取文件。 所有沙箱类型统一通过 Sidecar HTTP API（POST /files/read）读取。
     */
    String readFile(SandboxInfo info, String relativePath) throws IOException;

    /**
     * 将 tar.gz 压缩包解压到沙箱工作空间。 通过 Sidecar HTTP API（POST /files/extract）上传并解压。
     * 用于批量注入配置文件，替代逐个 writeFile 调用。
     *
     * @param info 沙箱信息
     * @param tarGzBytes tar.gz 压缩包的字节内容
     * @return 解压的文件数量
     */
    default int extractArchive(SandboxInfo info, byte[] tarGzBytes) throws IOException {
        throw new UnsupportedOperationException("extractArchive not implemented");
    }

    /**
     * 建立到 Sidecar 的 WebSocket 连接。 所有沙箱类型都通过 Sidecar WebSocket 桥接 CLI。
     */
    RuntimeAdapter connectSidecar(SandboxInfo info, RuntimeConfig config);

    /** 获取 Sidecar WebSocket URI。 */
    URI getSidecarUri(SandboxInfo info, String command, String args);
}
