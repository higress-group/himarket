package com.alibaba.himarket.service.hicoding.sandbox.init;

import java.time.Instant;

public record InitEvent(Instant timestamp, String phase, EventType type, String message) {

    public enum EventType {
        PHASE_START,
        PHASE_COMPLETE,
        PHASE_SKIP,
        PHASE_RETRY,
        PHASE_FAIL,
        VERIFY_PASS,
        VERIFY_FAIL,
        WARNING
    }
}
