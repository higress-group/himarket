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

package com.alibaba.himarket.service.hicoding.session;

import java.util.List;
import lombok.Data;

/**
 * CLI 会话配置，前端传入的纯标识符 DTO。
 * 承载 modelProductId、mcpServers[].productId、skills[].productId、认证凭据等，
 * 通过 WebSocket 消息传递给后端。
 */
@Data
public class CliSessionConfig {

    /** 市场模型产品 ID（可选） */
    private String modelProductId;

    /** 选中的 MCP Server 列表（简化为标识符） */
    private List<McpServerEntry> mcpServers;

    /** 选中的 Skill 列表（简化为标识符） */
    private List<SkillEntry> skills;

    /** 认证凭据（PAT / API Key），用于注入到 CLI 进程环境变量中（可选） */
    private String authToken;

    @Data
    public static class McpServerEntry {
        /** MCP 产品 ID */
        private String productId;

        /** MCP 服务名称 */
        private String name;
    }

    @Data
    public static class SkillEntry {
        /** Skill 产品 ID */
        private String productId;

        /** Skill 名称 */
        private String name;
    }
}
