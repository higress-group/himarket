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
package com.alibaba.apiopenplatform.service.impl;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * @author shihan
 * @version : McpClientHolder, v0.1 2025年11月26日 21:25 shihan Exp $
 */
@Slf4j
public class McpClientHolder implements Closeable {
    @Getter
    private McpSyncClient mcpSyncClient;

    public McpClientHolder() {
    }

    public McpClientHolder(McpSyncClient mcpSyncClient) {
        this.mcpSyncClient = mcpSyncClient;
    }

    public List<McpSchema.Tool> listTools() {
        if (this.mcpSyncClient == null) {
            return Collections.emptyList();
        }
        McpSchema.ListToolsResult toolsRet;
        try {
            // List available tools
            toolsRet = mcpSyncClient.listTools();
        } catch (Exception e) {
            log.error("mcp tools list error", e);
            return Collections.emptyList();
        }
        if (toolsRet != null) {
            return toolsRet.tools();
        }
        return Collections.emptyList();
    }

    @Override
    public void close() throws IOException {
        if (this.mcpSyncClient != null) {
            this.mcpSyncClient.closeGracefully();
        }
    }
}
