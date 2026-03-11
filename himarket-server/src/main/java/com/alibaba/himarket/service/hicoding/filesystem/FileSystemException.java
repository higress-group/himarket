package com.alibaba.himarket.service.hicoding.filesystem;

import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;
import java.io.IOException;

/**
 * 文件系统操作异常，包含结构化的错误信息。
 * <p>
 * 统一错误格式包含 errorType（错误类型）和 sandboxType（沙箱类型），
 * 便于上层业务代码进行统一的错误处理和展示。
 */
public class FileSystemException extends IOException {

    /** 文件系统错误类型枚举 */
    public enum ErrorType {
        FILE_NOT_FOUND,
        PERMISSION_DENIED,
        PATH_TRAVERSAL,
        DISK_FULL,
        NOT_A_DIRECTORY,
        NOT_A_FILE,
        ALREADY_EXISTS,
        IO_ERROR
    }

    private final ErrorType errorType;
    private final SandboxType sandboxType;

    public FileSystemException(ErrorType errorType, SandboxType sandboxType, String message) {
        super(formatMessage(errorType, sandboxType, message));
        this.errorType = errorType;
        this.sandboxType = sandboxType;
    }

    public FileSystemException(
            ErrorType errorType, SandboxType sandboxType, String message, Throwable cause) {
        super(formatMessage(errorType, sandboxType, message), cause);
        this.errorType = errorType;
        this.sandboxType = sandboxType;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public SandboxType getSandboxType() {
        return sandboxType;
    }

    private static String formatMessage(
            ErrorType errorType, SandboxType sandboxType, String message) {
        return "[" + sandboxType + "][" + errorType + "] " + message;
    }
}
