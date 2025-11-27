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
package com.alibaba.apiopenplatform.dto.params.chat;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * @author shihan
 * @version : McpToolMeta, v0.1 2025年11月26日 19:05 shihan Exp $
 */
@Data
@EqualsAndHashCode
@ToString
public class McpToolMeta {
    /**
     * 工具名称
     */
    private String toolName;
    /**
     * 工具中文名称
     */
    private String toolNameCn;
    /**
     * 对应的mcp server名称
     */
    private String mcpName;
    /**
     * 对应的mcp server中文名称
     */
    private String mcpNameCn;
}
