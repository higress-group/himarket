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

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 熔断降级插件配置 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CircuitBreakerProperty extends BaseAPIProperty {

    /** 失败阈值（触发熔断的失败次数或比例） */
    private Integer failureThreshold;

    /** 成功阈值（从半开状态恢复到关闭状态的成功次数） */
    private Integer successThreshold;

    /** 超时时间（毫秒，判定为失败的超时时长） */
    private Long timeout;

    /** 熔断持续时间（毫秒，熔断后多久尝试恢复） */
    private Long openDuration;

    /** 半开状态允许的请求数 */
    private Integer halfOpenRequests;

    /** 降级响应内容 */
    private String fallbackResponse;
}
