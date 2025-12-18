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

package com.alibaba.himarket.support.api;

import com.alibaba.himarket.support.enums.RateLimitScope;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 限流插件配置 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RateLimitProperty extends BaseAPIProperty {

    /** 限流范围 */
    private RateLimitScope scope;

    /** 限流阈值（每秒请求数） */
    private Integer requestsPerSecond;

    /** 突发容量（允许的突发流量） */
    private Integer burstCapacity;

    /** 是否启用令牌桶算法 */
    private Boolean useTokenBucket;
}
