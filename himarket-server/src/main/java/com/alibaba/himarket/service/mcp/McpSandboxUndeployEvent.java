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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sandbox undeploy event that cleans up old CRD resources after transaction commit.
 *
 * <p>This event works with {@link McpSandboxDeployEvent}: when a sandbox is redeployed, old CRD
 * cleanup and new CRD deployment both run asynchronously after transaction commit, avoiding slow
 * K8s operations inside the transaction.
 *
 * <p>This class is a POJO. Spring Boot 3.x wraps it in {@link
 * org.springframework.context.PayloadApplicationEvent}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class McpSandboxUndeployEvent {

    /**
     * Sandbox instance ID.
     */
    private String sandboxId;

    /**
     * MCP Server name used for CRD resource identity.
     */
    private String mcpName;

    /**
     * User ID that performs the operation.
     */
    private String userId;

    /**
     * K8s namespace.
     */
    private String namespace;

    /**
     * CRD resource name used for exact deletion and to avoid name calculation drift.
     */
    private String resourceName;

    /**
     * K8s Secret name, or blank to skip Secret deletion.
     */
    private String secretName;
}
