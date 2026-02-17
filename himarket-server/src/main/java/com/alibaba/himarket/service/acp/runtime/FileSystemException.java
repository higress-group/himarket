package com.alibaba.himarket.service.acp.runtime;

import java.io.IOException;

/**
 * 文件系统操作异常，包含结构化的错误信息。
 * <p>
 * 统一错误格式包含 errorType（错误类型）和 runtimeType（运行时类型），
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
    private final RuntimeType runtimeType;

    public FileSystemException(ErrorType errorType, RuntimeType runtimeType, String message) {
        super(formatMessage(errorType, runtimeType, message));
        this.errorType = errorType;
        this.runtimeType = runtimeType;
    }

    public FileSystemException(
            ErrorType errorType, RuntimeType runtimeType, String message, Throwable cause) {
        super(formatMessage(errorType, runtimeType, message), cause);
        this.errorType = errorType;
        this.runtimeType = runtimeType;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public RuntimeType getRuntimeType() {
        return runtimeType;
    }

    private static String formatMessage(
            ErrorType errorType, RuntimeType runtimeType, String message) {
        return "[" + runtimeType + "][" + errorType + "] " + message;
    }
}
