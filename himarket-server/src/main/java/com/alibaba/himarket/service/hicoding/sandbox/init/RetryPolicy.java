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

    /**
     * Suitable for LB rule propagation: retries up to 10 times with 3s initial delay, exponential
     * backoff, 10s maximum interval, and about 60s total wait.
     */
    public static RetryPolicy lbWarmup() {
        return new RetryPolicy(10, Duration.ofSeconds(3), 1.5, Duration.ofSeconds(10));
    }
}
