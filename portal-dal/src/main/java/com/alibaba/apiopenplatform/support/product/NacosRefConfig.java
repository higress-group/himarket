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

package com.alibaba.apiopenplatform.support.product;

import lombok.Data;

/**
 * Nacos 引用配置
 * 支持 MCP Server 和 Agent API 两种类型
 */
@Data
public class NacosRefConfig {

    /**
     * MCP Server 名称（用于 MCP Server 类型）
     */
    private String mcpServerName;

    /**
     * Agent 名称（用于 Agent API 类型）
     */
    private String agentName;

    /**
     * 命名空间 ID（MCP Server 和 Agent API 共用）
     */
    private String namespaceId;
} 