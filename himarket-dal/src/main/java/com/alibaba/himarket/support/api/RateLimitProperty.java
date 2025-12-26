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

import com.alibaba.himarket.support.annotation.APIField;
import com.alibaba.himarket.support.enums.RateLimitScope;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 限流插件配置 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RateLimitProperty extends BaseAPIProperty {

    /** 限流范围 */
    @APIField(label = "限流范围", description = "限流的作用范围", required = true, defaultValue = "GLOBAL")
    private RateLimitScope scope;

    /** 限流阈值（每秒请求数） */
    @APIField(label = "每秒请求数", description = "每秒允许的最大请求数", required = true)
    private Integer requestsPerSecond;

    /** 突发容量（允许的突发流量） */
    @APIField(label = "突发容量", description = "允许的突发流量")
    private Integer burstCapacity;

    /** 是否启用令牌桶算法 */
    @APIField(label = "启用令牌桶", description = "是否启用令牌桶算法", defaultValue = "true")
    private Boolean useTokenBucket;
}
