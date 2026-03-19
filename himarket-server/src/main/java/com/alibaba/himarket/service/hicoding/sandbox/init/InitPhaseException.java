package com.alibaba.himarket.service.hicoding.sandbox.init;

public class InitPhaseException extends RuntimeException {

    private final String phaseName;
    private final boolean retryable;

    public InitPhaseException(String phaseName, String message, boolean retryable) {
        super(message);
        this.phaseName = phaseName;
        this.retryable = retryable;
    }

    public InitPhaseException(
            String phaseName, String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.phaseName = phaseName;
        this.retryable = retryable;
    }

    public String getPhaseName() {
        return phaseName;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
