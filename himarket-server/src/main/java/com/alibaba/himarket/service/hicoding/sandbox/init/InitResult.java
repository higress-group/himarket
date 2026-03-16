package com.alibaba.himarket.service.hicoding.sandbox.init;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public record InitResult(
        boolean success,
        String failedPhase,
        String errorMessage,
        Duration totalDuration,
        Map<String, Duration> phaseDurations,
        List<InitEvent> events) {

    public static InitResult success(
            Duration duration, Map<String, Duration> phases, List<InitEvent> events) {
        return new InitResult(true, null, null, duration, phases, events);
    }

    public static InitResult failure(
            String phase,
            String error,
            Duration duration,
            Map<String, Duration> phases,
            List<InitEvent> events) {
        return new InitResult(false, phase, error, duration, phases, events);
    }
}
