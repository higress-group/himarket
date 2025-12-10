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

package com.alibaba.himarket.dto.result.mcp;

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import com.alibaba.himarket.support.enums.MCPTransportMode;
import java.util.List;
import java.util.Random;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class MCPConfigResult {

    protected String mcpServerName;

    protected MCPServerConfig mcpServerConfig;

    protected String tools;

    protected McpMetadata meta;

    public void convertDomainToGatewayIp(List<String> gatewayIps) {

        List<DomainResult> domains = this.mcpServerConfig.getDomains();
        for (DomainResult domain : domains) {
            if (StringUtils.equals(domain.getDomain(), "<higress-gateway-ip>")) {
                if (gatewayIps.isEmpty()) {
                    throw new BusinessException(
                            ErrorCode.GATEWAY_ERROR,
                            Resources.GATEWAY,
                            "no available ip to replace <higress-gateway-ip>");
                }
                String randomGatewayIp = gatewayIps.get(new Random().nextInt(gatewayIps.size()));
                domain.setDomain(randomGatewayIp);
            }
        }
    }

    public MCPTransportConfig toTransportConfig() {
        List<DomainResult> domains = mcpServerConfig.getDomains();
        DomainResult domain =
                domains.stream()
                        .filter(d -> !StrUtil.equalsIgnoreCase(d.getNetworkType(), "intranet"))
                        .findFirst()
                        .orElse(null);
        if (domain == null) {
            return null;
        }

        String url = StrUtil.format("{}://{}", domain.getProtocol(), domain.getDomain());

        String path = mcpServerConfig.getPath();
        if (StrUtil.isNotBlank(path)) {
            url = path.startsWith("/") ? url + path : url + "/" + path;
        }
        // Default: StreamableHTTP
        MCPTransportMode transportMode =
                "sse".equalsIgnoreCase(meta.getProtocol())
                        ? MCPTransportMode.SSE
                        : MCPTransportMode.STREAMABLE_HTTP;

        if (transportMode == MCPTransportMode.SSE) {
            if (!url.endsWith("/sse")) {
                url = url.endsWith("/") ? url + "sse" : url + "/sse";
            }
        }

        return MCPTransportConfig.builder()
                .mcpServerName(mcpServerName)
                .transportMode(transportMode)
                .url(url)
                .build();
    }

    @Data
    public static class McpMetadata {

        /** 来源 AI网关/Higress/Nacos */
        private String source;

        /**
         * 服务类型 AI网关：HTTP（HTTP转MCP）/MCP（MCP直接代理）
         * Higress：OPEN_API（OpenAPI转MCP）/DIRECT_ROUTE（直接路由）/DATABASE（数据库）
         */
        private String createFromType;

        /** HTTP/SSE */
        private String protocol;
    }

    @Data
    public static class MCPServerConfig {
        /** for gateway */
        private String path;

        private List<DomainResult> domains;

        /** for nacos */
        private Object rawConfig;
    }
}
