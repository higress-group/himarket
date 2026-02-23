package com.alibaba.himarket.service.acp.runtime;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 等待 CLI 进程启动并就绪。
 *
 * <p>K8s/E2B 模式：通过监听 RuntimeAdapter.stdout() 首条消息确认 CLI 已启动。
 *
 * <p>LOCAL 模式：CLI（如 qwen --acp）遵循 ACP 协议，启动后不会主动输出，
 * 而是等待客户端发送 initialize 请求。因此 LOCAL 模式下发送一个 JSON-RPC
 * 探测请求，收到响应即表示 CLI 就绪。
 */
/**
 * 等待 CLI 进程启动并就绪。
 *
 * <p>所有模式（LOCAL/K8s/E2B）统一使用主动探测方式：发送 JSON-RPC ping 请求，
 * 收到任意响应即表示 CLI 已就绪。
 *
 * <p>Qwen Code CLI（qwen --acp）遵循 ACP 协议，启动后不会主动输出，
 * 而是等待客户端发送请求。因此被动等待 stdout 首条消息的方式在所有模式下
 * 都不可靠，统一改为主动探测。
 */
public class CliReadyPhase implements InitPhase {

    private static final Logger logger = LoggerFactory.getLogger(CliReadyPhase.class);
    private static final Duration CLI_READY_TIMEOUT = Duration.ofSeconds(15);

    @Override
    public String name() {
        return "cli-ready";
    }

    @Override
    public int order() {
        return 500;
    }

    @Override
    public boolean shouldExecute(InitContext context) {
        return true;
    }

    @Override
    public void execute(InitContext context) throws InitPhaseException {
        RuntimeAdapter adapter = context.getRuntimeAdapter();
        if (adapter == null) {
            throw new InitPhaseException("cli-ready", "RuntimeAdapter 未初始化", false);
        }

        try {
            executeProbe(adapter, context);
        } catch (InitPhaseException e) {
            throw e;
        } catch (Exception e) {
            throw new InitPhaseException("cli-ready", "等待 CLI 就绪异常: " + e.getMessage(), e, false);
        }
    }

    /**
     * 统一探测逻辑：发送 JSON-RPC ping 请求，等待 CLI 响应。
     * ACP CLI 启动后不会主动输出，需要客户端先发消息触发响应。
     * 对 LOCAL 和 K8s 模式都适用。
     */
    private void executeProbe(RuntimeAdapter adapter, InitContext context)
            throws InitPhaseException {
        String sandboxType =
                context.getSandboxConfig() != null
                        ? context.getSandboxConfig().type().name()
                        : "UNKNOWN";
        logger.info("[CliReadyPhase] {} 模式: 发送探测请求验证 CLI 就绪", sandboxType);

        String probe =
                "{\"jsonrpc\":\"2.0\",\"id\":\"cli-ready-probe\",\"method\":\"ping\",\"params\":{}}";
        try {
            adapter.send(probe);
        } catch (Exception e) {
            throw new InitPhaseException("cli-ready", "发送探测请求失败: " + e.getMessage(), e, false);
        }

        String response = adapter.stdout().blockFirst(CLI_READY_TIMEOUT);
        if (response == null) {
            if (!adapter.isAlive()) {
                throw new InitPhaseException("cli-ready", "CLI 进程已退出", false);
            }
            throw new InitPhaseException(
                    "cli-ready",
                    "CLI 就绪超时: " + CLI_READY_TIMEOUT.toSeconds() + "s 内未收到探测响应",
                    false);
        }

        logger.info(
                "[CliReadyPhase] {} 模式: CLI 已就绪, 收到响应: {}",
                sandboxType,
                response.length() > 200 ? response.substring(0, 200) + "..." : response);
    }

    @Override
    public boolean verify(InitContext context) {
        RuntimeAdapter adapter = context.getRuntimeAdapter();
        return adapter != null && adapter.isAlive();
    }

    @Override
    public RetryPolicy retryPolicy() {
        return RetryPolicy.none();
    }
}
