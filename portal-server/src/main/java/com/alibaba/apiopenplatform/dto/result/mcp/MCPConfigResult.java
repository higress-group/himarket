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

package com.alibaba.apiopenplatform.dto.result.mcp;

import cn.hutool.core.util.StrUtil;
import com.alibaba.apiopenplatform.support.chat.mcp.McpServerConfig;
import com.alibaba.apiopenplatform.dto.result.common.DomainResult;
import lombok.Data;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;

@Data
public class MCPConfigResult {

    protected String mcpServerName;

    protected MCPServerConfig mcpServerConfig;

    protected String tools;

    protected McpMetadata meta;

    public McpServerConfig toStandardMcpServer() {
        McpServerConfig mcpServerConfig = new McpServerConfig();
        McpServerConfig.McpServer mcpServer = new McpServerConfig.McpServer();

        mcpServer.setType(meta.getProtocol().toLowerCase());
        List<DomainResult> domains = this.mcpServerConfig.getDomains();
        DomainResult domainResult = domains.get(0);
        String url = String.format("%s://%s", domainResult.getProtocol(), domainResult.getDomain());

        String path = this.getMcpServerConfig().getPath();
        if (StrUtil.isNotBlank(path)) {
            url = path.startsWith("/") ? url + path : url + "/" + path;
        }

        if (StringUtils.equalsIgnoreCase(mcpServer.getType(), "sse")) {
            if (!url.endsWith("/sse")) {
                url = url.endsWith("/") ? url + "sse" : url + "/sse";
            }
        }
        mcpServer.setUrl(url);

        mcpServerConfig.setMcpServers(Collections.singletonMap(mcpServerName, mcpServer));
        return mcpServerConfig;
    }

    @Data
    public static class McpMetadata {

        /**
         * 来源
         * AI网关/Higress/Nacos
         */
        private String source;

        /**
         * 服务类型
         * AI网关：HTTP（HTTP转MCP）/MCP（MCP直接代理）
         * Higress：OPEN_API（OpenAPI转MCP）/DIRECT_ROUTE（直接路由）/DATABASE（数据库）
         */
        private String createFromType;

        /**
         * HTTP/SSE
         */
        private String protocol;
    }

    @Data
    public static class MCPServerConfig {
        /**
         * for gateway
         */
        private String path;
        private List<DomainResult> domains;

        /**
         * for nacos
         */
        private Object rawConfig;

        private String transportMode = MCPTransportMode.REMOTE.getMode();
    }

    @Getter
    public enum MCPTransportMode {
        LOCAL("Local"),
        REMOTE("Remote");

        private final String mode;

        MCPTransportMode(String mode) {
            this.mode = mode;
        }
    }
}
