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
 * Sandbox deployment event that triggers K8s CRD deployment after transaction commit.
 *
 * <p>This prevents K8s resource leaks: when the database transaction rolls back, the CRD is not
 * deployed. K8s operations run only after the transaction commits successfully.
 *
 * <p>This class is a POJO and does not extend {@link
 * org.springframework.context.ApplicationEvent}. Spring Boot 3.x wraps POJO events in {@link
 * org.springframework.context.PayloadApplicationEvent}, so they work with {@code
 * @TransactionalEventListener}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class McpSandboxDeployEvent {

    private String sandboxId;
    private String mcpServerId;
    private String mcpName;
    private String adminUserId;
    private String transportType;
    private String metaProtocolType;
    private String connectionConfig;
    private String authType;
    private String paramValues;
    private String extraParams;
    private String namespace;
    private String resourceSpec;

    /**
     * Pre-created endpoint ID. Its URL is updated after success and cleaned up after failure.
     */
    private String endpointId;

    /**
     * Generated API key, present when authType is "apikey".
     */
    private String apiKey;
}
