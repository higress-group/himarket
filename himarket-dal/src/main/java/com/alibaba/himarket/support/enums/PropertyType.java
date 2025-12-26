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

package com.alibaba.himarket.support.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 属性类型枚举 */
@Getter
@AllArgsConstructor
public enum PropertyType {

    /** 认证鉴权 */
    AUTH("认证鉴权", "配置 API 的认证和鉴权方式"),

    /** 流量限制 */
    RATE_LIMIT("流量限制", "限制 API 的调用频率"),

    /** 跨域配置 */
    CORS("跨域配置", "配置 API 的跨域访问规则"),

    /** IP 黑白名单 */
    IP_FILTER("IP 黑白名单", "限制 API 的访问 IP"),

    /** 请求转换 */
    REQUEST_TRANSFORM("请求转换", "转换 API 的请求参数"),

    /** 重试策略 */
    RETRY("重试策略", "配置 API 的重试机制"),

    /** 超时配置 */
    TIMEOUT("超时配置", "配置 API 的超时时间"),

    /** 熔断降级 */
    CIRCUIT_BREAKER("熔断降级", "配置 API 的熔断策略"),

    /** 响应转换 */
    RESPONSE_TRANSFORM("响应转换", "转换 API 的响应结果"),

    /** 日志记录 */
    LOGGING("日志记录", "配置 API 的日志记录策略"),

    /** 自定义 */
    CUSTOM("自定义", "自定义 API 属性"),
    ;

    private final String label;
    private final String description;
}
