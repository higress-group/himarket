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

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
public class McpServerInfo {

    private String mcpServerId;

    private String name;

    private String description;

    private String mcpServerPath;

    private String exposedUriPath;

    private List<DomainInfo> domainInfos;

    private String protocol;

    private String createFromType;

    private String mcpServerConfig;

    private String mcpServerConfigPluginAttachmentId;

    private String apiId;

    private String routeId;

    private String gatewayId;

    private String environmentId;

    private String deployStatus;

    private String type;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DomainInfo {
        private String name;
        private String protocol;
    }
}
