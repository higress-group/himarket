package com.alibaba.himarket.service.hicoding.sandbox.init;

/**
 * 初始化错误码枚举。
 * 提供细粒度的错误分类，便于前端展示和运维排查。
 *
 * 每个错误码对应 {@link SandboxInitPipeline} 中某个阶段的失败场景，
 * 可通过 {@link #fromPhaseName(String)} 从 Pipeline 失败阶段名称自动映射。
 */
public enum InitErrorCode {
    SANDBOX_ACQUIRE_FAILED("SANDBOX_ACQUIRE_FAILED", "沙箱获取失败"),
    FILESYSTEM_NOT_READY("FILESYSTEM_NOT_READY", "文件系统未就绪"),
    CONFIG_RESOLVE_FAILED("CONFIG_RESOLVE_FAILED", "配置解析失败"),
    CONFIG_INJECTION_FAILED("CONFIG_INJECTION_FAILED", "配置注入失败"),
    SIDECAR_CONNECT_FAILED("SIDECAR_CONNECT_FAILED", "Sidecar 连接失败"),
    CLI_NOT_READY("CLI_NOT_READY", "CLI 工具未就绪"),
    PIPELINE_TIMEOUT("PIPELINE_TIMEOUT", "初始化超时"),
    UNKNOWN_ERROR("UNKNOWN_ERROR", "未知错误");

    private final String code;
    private final String defaultMessage;

    InitErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * 根据 Pipeline 失败阶段名称映射到错误码。
     *
     * @param phaseName Pipeline 阶段名称（如 "sandbox-acquire"、"cli-ready"），
     *                  参见各 {@link InitPhase#name()} 实现
     * @return 对应的错误码，无法匹配时返回 {@link #UNKNOWN_ERROR}
     */
    public static InitErrorCode fromPhaseName(String phaseName) {
        if (phaseName == null) {
            return UNKNOWN_ERROR;
        }
        return switch (phaseName) {
            case "sandbox-acquire" -> SANDBOX_ACQUIRE_FAILED;
            case "filesystem-ready" -> FILESYSTEM_NOT_READY;
            case "config-injection" -> CONFIG_INJECTION_FAILED;
            case "sidecar-connect" -> SIDECAR_CONNECT_FAILED;
            case "cli-ready" -> CLI_NOT_READY;
            default -> UNKNOWN_ERROR;
        };
    }
}
