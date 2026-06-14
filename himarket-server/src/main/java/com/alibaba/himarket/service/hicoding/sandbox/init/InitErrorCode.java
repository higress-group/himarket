package com.alibaba.himarket.service.hicoding.sandbox.init;

/**
 * Initialization error code enum.
 *
 * <p>Provides fine-grained error categories for frontend display and operational diagnostics. Each
 * code maps to a failure scenario in {@link SandboxInitPipeline} and can be derived from the
 * failed phase name through {@link #fromPhaseName(String)}.
 */
public enum InitErrorCode {
    SANDBOX_ACQUIRE_FAILED("SANDBOX_ACQUIRE_FAILED", "Failed to acquire sandbox"),
    FILESYSTEM_NOT_READY("FILESYSTEM_NOT_READY", "Filesystem is not ready"),
    CONFIG_RESOLVE_FAILED("CONFIG_RESOLVE_FAILED", "Failed to resolve configuration"),
    CONFIG_INJECTION_FAILED("CONFIG_INJECTION_FAILED", "Failed to inject configuration"),
    SIDECAR_CONNECT_FAILED("SIDECAR_CONNECT_FAILED", "Failed to connect to Sidecar"),
    CLI_NOT_READY("CLI_NOT_READY", "CLI tool is not ready"),
    PIPELINE_TIMEOUT("PIPELINE_TIMEOUT", "Initialization timed out"),
    UNKNOWN_ERROR("UNKNOWN_ERROR", "Unknown error");

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
     * Maps a failed Pipeline phase name to an error code.
     *
     * @param phaseName Pipeline phase name, such as {@code "sandbox-acquire"} or
     *     {@code "cli-ready"}; see {@link InitPhase#name()} implementations
     * @return matching error code, or {@link #UNKNOWN_ERROR} when no match exists
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
