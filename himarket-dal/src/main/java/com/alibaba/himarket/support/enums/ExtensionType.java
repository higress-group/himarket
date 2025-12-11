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

package com.alibaba.apiopenplatform.support.enums;

/**
 * 扩展类型枚举
 */
public enum ExtensionType {

    /**
     * 认证鉴权
     */
    AUTH,

    /**
     * 流量限制
     */
    RATE_LIMIT,

    /**
     * 跨域配置
     */
    CORS,

    /**
     * IP 黑白名单
     */
    IP_FILTER,

    /**
     * 请求转换
     */
    REQUEST_TRANSFORM,

    /**
     * 重试策略
     */
    RETRY,

    /**
     * 超时配置
     */
    TIMEOUT,

    /**
     * 熔断降级
     */
    CIRCUIT_BREAKER,

    /**
     * 响应转换
     */
    RESPONSE_TRANSFORM,

    /**
     * 日志记录
     */
    LOGGING,

    /**
     * 自定义
     */
    CUSTOM,

    ;
}
