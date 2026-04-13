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

package com.alibaba.himarket.dto.vendor;

import lombok.Data;

/** 导入时前端传入的单条 MCP 参数。 */
@Data
public class RemoteMcpItemParam {

    /** 供应商侧唯一标识 */
    private String remoteId;

    /** 转换后的 mcpName（符合平台规范） */
    private String mcpName;

    /** 展示名称 */
    private String displayName;

    /** 描述 */
    private String description;

    /** 协议类型：sse / streamable-http / stdio */
    private String protocolType;

    /** JSON 格式连接配置 */
    private String connectionConfig;

    /** JSON 数组格式标签 */
    private String tags;

    /** JSON 格式图标 */
    private String icon;

    /** 源码仓库地址 */
    private String repoUrl;

    /** JSON 格式额外参数定义（如 env_schema / configSchema） */
    private String extraParams;
}
