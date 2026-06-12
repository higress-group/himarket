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

package com.alibaba.himarket.dto.params.mcp;

import lombok.Data;

/**
 * MCP 订阅请求参数。
 *
 * <p>SSE/HTTP 直连场景：不需要传任何参数。
 * <p>Remote 沙箱场景：需要传 sandboxId + transportType，可选 params。
 */
@Data
public class SubscribeMcpParam {

    /** 沙箱 ID（Remote 场景必填） */
    private String sandboxId;

    /** 传输类型：sse / http（Remote 场景必填） */
    private String transportType;

    /** 鉴权方式：none / bearer（Remote 场景，默认 none） */
    private String authType;

    /** 用户填写的额外参数（JSON 格式），可选 */
    private String params;
}
