package com.alibaba.himarket.service.acp.runtime;

public enum PhaseStatus {
    PENDING,
    EXECUTING,
    VERIFYING,
    COMPLETED,
    SKIPPED,
    FAILED,
    RETRYING
}
