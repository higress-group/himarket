/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

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
