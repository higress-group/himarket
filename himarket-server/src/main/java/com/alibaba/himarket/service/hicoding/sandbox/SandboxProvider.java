package com.alibaba.himarket.service.hicoding.sandbox;

import com.alibaba.himarket.service.hicoding.runtime.RuntimeAdapter;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeConfig;
import java.io.IOException;
import java.net.URI;

/**
 * 统一沙箱提供者接口。
 *
 * <p>抽象不同沙箱环境（本地 Mac、K8s Pod、E2B）的差异， 为 SandboxInitPipeline 提供统一的操作契约。
 *
 * <h3>OpenSandbox 对接说明</h3>
 * <p>若需对接 <a href="https://github.com/alibaba/OpenSandbox">OpenSandbox</a>，
 * 创建 {@code OpenSandboxProvider} 实现本接口，关键适配点：
 * <ul>
 *   <li><b>acquire()</b>：调用 OpenSandbox Python FastAPI Server（POST /sandboxes）创建沙箱实例，
 *       返回的 sandboxId 和 host 封装为 {@link SandboxInfo}</li>
 *   <li><b>release()</b>：调用 DELETE /sandboxes/{id} 销毁沙箱</li>
 *   <li><b>writeFile / readFile / healthCheck / exec</b>：
 *       OpenSandbox 的 execd 组件提供兼容的 /files/* HTTP API，
 *       可直接复用 {@link SandboxHttpClient}，无需重复实现</li>
 *   <li><b>connectSidecar()</b>：OpenSandbox 使用 HTTP + SSE 而非 WebSocket 桥接 CLI，
 *       需要适配 {@link RuntimeAdapter} 的 stdout() 流为 SSE 事件流，
 *       send() 方法改为 HTTP POST /command 调用</li>
 * </ul>
 *
 * @see SandboxHttpClient 可复用的 HTTP 客户端（兼容 OpenSandbox execd /files/* API）
 * @see SandboxType 沙箱类型枚举（需新增 OPEN_SANDBOX 值）
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
     * 在沙箱内执行命令。
     * 默认抛出 UnsupportedOperationException。
     *
     * @param info    沙箱信息
     * @param command 要执行的命令
     * @param args    命令参数列表
     * @param timeout 超时时间
     * @return 命令执行结果
     */
    default ExecResult exec(
            SandboxInfo info,
            String command,
            java.util.List<String> args,
            java.time.Duration timeout)
            throws java.io.IOException {
        throw new UnsupportedOperationException("exec not implemented");
    }

    /**
     * 建立到 Sidecar 的 WebSocket 连接。 所有沙箱类型都通过 Sidecar WebSocket 桥接 CLI。
     */
    RuntimeAdapter connectSidecar(SandboxInfo info, RuntimeConfig config);

    /** 获取 Sidecar WebSocket URI（不带环境变量）。 */
    default URI getSidecarUri(SandboxInfo info, String command, String args) {
        return getSidecarUri(info, command, args, null);
    }

    /** 获取 Sidecar WebSocket URI（支持环境变量）。 */
    default URI getSidecarUri(
            SandboxInfo info, String command, String args, java.util.Map<String, String> env) {
        return info.sidecarWsUri(command, args, env);
    }
}
