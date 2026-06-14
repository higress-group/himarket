package com.alibaba.himarket.service.hicoding.runtime;

/**
 * Runtime instance status.
 */
public enum RuntimeStatus {

    /**
     * Creating.
     */
    CREATING,

    /**
     * Running.
     */
    RUNNING,

    /**
     * WebSocket is disconnected, but the Sidecar session may still be alive.
     */
    DETACHED,

    /**
     * Stopped.
     */
    STOPPED,

    /**
     * Error state.
     */
    ERROR
}
