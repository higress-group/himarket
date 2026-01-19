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

package com.alibaba.himarket.support.api.property;

import com.alibaba.himarket.support.annotation.APIField;
import com.alibaba.himarket.support.annotation.SupportedAPITypes;
import com.alibaba.himarket.support.enums.APIType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 超时插件配置 */
@Data
@EqualsAndHashCode(callSuper = true)
@SupportedAPITypes({APIType.REST_API, APIType.MCP_SERVER, APIType.AGENT_API, APIType.MODEL_API})
public class TimeoutProperty extends BaseAPIProperty {

    /** 超时时间数值 */
    @APIField(label = "超时时间", description = "超时时间数值", required = true)
    private Long unitNum;

    /** 时间单位 */
    @APIField(label = "时间单位", description = "超时时间单位（仅支持 s=秒）", required = true, defaultValue = "s")
    private String timeUnit = "s";
}
