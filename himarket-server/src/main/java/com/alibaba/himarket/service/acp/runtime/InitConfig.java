package com.alibaba.himarket.service.acp.runtime;

import java.time.Duration;

public record InitConfig(
        Duration totalTimeout,
        boolean failFast,
        boolean enableVerification,
        boolean enableProgressNotify) {

    public static InitConfig defaults() {
        return new InitConfig(Duration.ofSeconds(120), true, true, true);
    }
}
