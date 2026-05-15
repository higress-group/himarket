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

package com.alibaba.himarket.support.api.spec;

import com.alibaba.himarket.support.enums.McpFromType;
import com.alibaba.himarket.support.enums.McpProtocolType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerSpec extends ApiSpec {

    @NotNull(message = "FromType cannot be null")
    private McpFromType fromType;

    @NotNull(message = "Protocol cannot be null")
    private McpProtocolType protocol;

    private ToolsConfig toolsConfig;

    @NotNull(message = "Connection cannot be null")
    @Valid
    private McpConnection connection;

    @AssertTrue(message = "ToolsConfig is required for HTTP_TO_MCP and must be null for NATIVE_MCP")
    private boolean isToolsConfigValid() {
        if (McpFromType.HTTP_TO_MCP == fromType) {
            return toolsConfig != null;
        }
        if (McpFromType.NATIVE_MCP == fromType) {
            return toolsConfig == null;
        }
        return true;
    }

    @AssertTrue(
            message =
                    "Connection must be HttpConnection for HTTP_TO_MCP and must not be"
                            + " HttpConnection for NATIVE_MCP")
    private boolean isConnectionTypeValid() {
        if (McpFromType.HTTP_TO_MCP == fromType) {
            return connection instanceof HttpConnection;
        }
        if (McpFromType.NATIVE_MCP == fromType) {
            return !(connection instanceof HttpConnection);
        }
        return true;
    }
}
