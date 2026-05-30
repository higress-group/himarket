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

package com.alibaba.himarket.service.mcp;

import com.alibaba.himarket.entity.SandboxInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SELF_HOSTED 类型沙箱的部署策略 — 预留接口，暂不实现。
 */
@Component
@Slf4j
public class SelfHostedDeployStrategy implements McpSandboxDeployStrategy {

    @Override
    public String supportedSandboxType() {
        return "SELF_HOSTED";
    }

    @Override
    public String deploy(
            SandboxInstance sandbox,
            String mcpServerId,
            String mcpName,
            String userId,
            String transportType,
            String metaProtocolType,
            String connectionConfig,
            String apiKey,
            String authType,
            String userParams,
            String extraParamsDef,
            String namespace,
            String resourceSpec) {
        // TODO: 实现 SELF_HOSTED 类型沙箱的部署逻辑
        throw new UnsupportedOperationException(
                "SELF_HOSTED 类型沙箱暂不支持 MCP 部署，请使用 AGENT_RUNTIME 类型沙箱");
    }

    @Override
    public void undeploy(SandboxInstance sandbox, String mcpName, String userId, String namespace) {
        // TODO: 实现 SELF_HOSTED 类型沙箱的卸载逻辑
        throw new UnsupportedOperationException(
                "SELF_HOSTED 类型沙箱暂不支持 undeploy，请使用 AGENT_RUNTIME 类型沙箱");
    }
}
