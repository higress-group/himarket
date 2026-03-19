package com.alibaba.himarket.service.hicoding.sandbox.init;

import java.time.Duration;

public record RetryPolicy(
        int maxRetries, Duration initialDelay, double backoffMultiplier, Duration maxDelay) {

    public static RetryPolicy none() {
        return new RetryPolicy(0, Duration.ZERO, 1.0, Duration.ZERO);
    }

    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(3, Duration.ofSeconds(1), 2.0, Duration.ofSeconds(10));
    }

    public static RetryPolicy fileOperation() {
        return new RetryPolicy(2, Duration.ofMillis(500), 2.0, Duration.ofSeconds(3));
    }

    /** 适用于 LB 规则下发场景：最多重试 10 次，初始 3s，指数退避，最大间隔 10s，总等待约 60s。 */
    public static RetryPolicy lbWarmup() {
        return new RetryPolicy(10, Duration.ofSeconds(3), 1.5, Duration.ofSeconds(10));
    }
}
