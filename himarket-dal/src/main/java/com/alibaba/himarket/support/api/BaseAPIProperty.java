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

import com.alibaba.himarket.support.enums.PropertyPhase;
import com.alibaba.himarket.support.enums.PropertyType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.io.Serializable;
import lombok.Data;

/** API 属性配置基类 用于定义 API 的扩展属性基础信息 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = RateLimitProperty.class, name = "RATE_LIMIT"),
    @JsonSubTypes.Type(value = TimeoutProperty.class, name = "TIMEOUT"),
    @JsonSubTypes.Type(value = CircuitBreakerProperty.class, name = "CIRCUIT_BREAKER"),
    @JsonSubTypes.Type(value = ObservabilityProperty.class, name = "OBSERVABILITY")
})
public class BaseAPIProperty implements Serializable {

    /** 属性类型 */
    private PropertyType type;

    /** 扩展名称（唯一标识） */
    private String name;

    /** 执行阶段 */
    private PropertyPhase phase;

    /** 是否启用 */
    private Boolean enabled;

    /** 优先级（数字越小越先执行） */
    private Integer priority;
}
