package com.alibaba.himarket.service.hicoding.filesystem;

import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;
import java.io.IOException;

/**
 * File system operation exception with structured error details.
 *
 * <p>The formatted error contains errorType and sandboxType so callers can handle and display file
 * system failures consistently.
 */
public class FileSystemException extends IOException {

    /**
     * File system error type.
     */
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
