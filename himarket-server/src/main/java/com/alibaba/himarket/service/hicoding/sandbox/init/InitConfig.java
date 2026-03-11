package com.alibaba.himarket.service.hicoding.sandbox.init;

import java.time.Duration;

public record InitConfig(
        Duration totalTimeout,
        boolean failFast,
        boolean enableVerification,
        boolean enableProgressNotify) {

    public static InitConfig defaults() {
        return new InitConfig(Duration.ofSeconds(120), true, false, true);
    }
}
