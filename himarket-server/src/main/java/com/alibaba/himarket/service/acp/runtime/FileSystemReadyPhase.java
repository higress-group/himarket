package com.alibaba.himarket.service.acp.runtime;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 验证沙箱文件系统可访问。
 * 通过 SandboxProvider.healthCheck() 统一验证。
 */
/**
 * 验证沙箱文件系统可访问。
 * 通过 SandboxProvider.healthCheck() 统一验证。
 *
 * <p>当健康检查失败时，提供包含 host:port 的友好错误信息，
 * 帮助用户快速定位沙箱连接问题。使用快速失败策略（仅重试 1 次），
 * 避免沙箱不可达时长时间无意义等待。
 */
public class FileSystemReadyPhase implements InitPhase {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemReadyPhase.class);

    @Override
    public String name() {
        return "filesystem-ready";
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public boolean shouldExecute(InitContext context) {
        return true;
    }

    @Override
    public void execute(InitContext context) throws InitPhaseException {
        try {
            SandboxProvider provider = context.getProvider();
            SandboxInfo info = context.getSandboxInfo();
            boolean healthy = provider.healthCheck(info);
            if (!healthy) {
                String detail = buildConnectivityErrorMessage(info);
                throw new InitPhaseException("filesystem-ready", detail, true);
            }
            logger.info("[FileSystemReady] 通过: sandboxId={}", info.sandboxId());
        } catch (InitPhaseException e) {
            throw e;
        } catch (Exception e) {
            throw new InitPhaseException(
                    "filesystem-ready", "文件系统健康检查异常: " + e.getMessage(), e, true);
        }
    }

    @Override
    public boolean verify(InitContext context) {
        try {
            SandboxProvider provider = context.getProvider();
            SandboxInfo info = context.getSandboxInfo();
            String testContent = "health-check-" + System.currentTimeMillis();
            logger.debug(
                    "[FileSystemReady] verify 开始: sandboxId={}, host={}, port={}",
                    info.sandboxId(),
                    info.host(),
                    info.sidecarPort());
            provider.writeFile(info, ".sandbox-health-check", testContent);
            String readBack = provider.readFile(info, ".sandbox-health-check");
            boolean match = testContent.equals(readBack);
            if (!match) {
                logger.warn(
                        "[FileSystemReady] verify 内容不匹配: sandboxId={}, expected={}, actual={}",
                        info.sandboxId(),
                        testContent,
                        readBack);
            }
            return match;
        } catch (IOException e) {
            logger.warn(
                    "[FileSystemReady] verify 异常: sandboxId={}, error={}",
                    context.getSandboxInfo() != null
                            ? context.getSandboxInfo().sandboxId()
                            : "unknown",
                    e.getMessage(),
                    e);
            return false;
        }
    }

    /**
     * 不重试，失败直接返回。
     */
    @Override
    public RetryPolicy retryPolicy() {
        return RetryPolicy.none();
    }

    /**
     * 构建包含连接信息的友好错误消息。
     */
    private String buildConnectivityErrorMessage(SandboxInfo info) {
        String host = info.host();
        int port = info.sidecarPort();
        return String.format(
                "沙箱 %s (%s:%d) 不可达，请检查: 1) sidecar 服务是否已启动 2) 地址和端口是否正确",
                info.sandboxId(), host, port);
    }
}
