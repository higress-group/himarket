package com.alibaba.himarket.service.acp.runtime;

import java.io.IOException;
import java.net.URI;

/**
 * E2B 云沙箱提供者骨架类。
 *
 * <p>本类是未来集成 E2B 云沙箱的扩展点示例，当前所有方法均抛出 {@link UnsupportedOperationException}。
 * E2B 沙箱通过 E2B SDK 管理远程云沙箱实例，沙箱内运行 Sidecar Server + CLI，
 * 与本地沙箱和 K8s 沙箱共享相同的 SandboxProvider 接口契约。
 *
 * <p>注意：本类不注册为 Spring Bean（不加 @Component），仅作为扩展点示例。
 * 未来实现时需添加 @Component 注解并引入 E2B SDK 依赖。
 */
public class E2BSandboxProvider implements SandboxProvider {

    /** TODO: 未来直接返回 SandboxType.E2B */
    @Override
    public SandboxType getType() {
        throw new UnsupportedOperationException("E2B 沙箱尚未实现");
    }

    /** TODO: 通过 E2B SDK 创建沙箱实例，返回包含 E2B host 和端口的 SandboxInfo */
    @Override
    public SandboxInfo acquire(SandboxConfig config) {
        throw new UnsupportedOperationException("E2B 沙箱尚未实现");
    }

    /** TODO: 通过 E2B SDK 销毁沙箱实例，释放云端资源 */
    @Override
    public void release(SandboxInfo info) {
        throw new UnsupportedOperationException("E2B 沙箱尚未实现");
    }

    /** TODO: 通过 E2B SDK 检查沙箱状态，验证沙箱实例存活且 Sidecar 可响应 */
    @Override
    public boolean healthCheck(SandboxInfo info) {
        throw new UnsupportedOperationException("E2B 沙箱尚未实现");
    }

    /** TODO: 通过 E2B SDK 或 Sidecar HTTP API 写文件到云沙箱工作空间 */
    @Override
    public void writeFile(SandboxInfo info, String relativePath, String content)
            throws IOException {
        throw new UnsupportedOperationException("E2B 沙箱尚未实现");
    }

    /** TODO: 通过 E2B SDK 或 Sidecar HTTP API 从云沙箱工作空间读取文件 */
    @Override
    public String readFile(SandboxInfo info, String relativePath) throws IOException {
        throw new UnsupportedOperationException("E2B 沙箱尚未实现");
    }

    /** TODO: 建立到 E2B 沙箱内 Sidecar 的 WebSocket 连接，桥接 CLI 进程 */
    @Override
    public RuntimeAdapter connectSidecar(SandboxInfo info, RuntimeConfig config) {
        throw new UnsupportedOperationException("E2B 沙箱尚未实现");
    }

    /** TODO: 构建 E2B 沙箱的 Sidecar WebSocket URI，需包含 E2B 特有的认证参数 */
    @Override
    public URI getSidecarUri(SandboxInfo info, String command, String args) {
        throw new UnsupportedOperationException("E2B 沙箱尚未实现");
    }
}
