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

package com.alibaba.himarket.service;

import com.alibaba.himarket.dto.result.api.ToolImportPreviewDTO;
import java.util.List;

/** MCP Tool 服务接口 */
public interface McpToolService {

    /**
     * 从 MCP Server 导入 Tool 预览
     *
     * @param endpoint MCP Server Endpoint
     * @param token 访问 Token
     * @param type 协议类型 (sse/http)
     * @return 工具预览列表
     */
    List<ToolImportPreviewDTO> importFromMcpServer(String endpoint, String token, String type);
}
