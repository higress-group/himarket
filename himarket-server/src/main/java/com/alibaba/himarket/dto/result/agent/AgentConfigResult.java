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

package com.alibaba.himarket.dto.result.agent;

import com.alibaba.himarket.dto.result.httpapi.HttpRouteResult;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class AgentConfigResult {

    private AgentAPIConfig agentAPIConfig;

    /**
     * Agent meta
     */
    private AgentMetadata meta;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentAPIConfig {
        /**
         * Agent protocol list
         * If contains "a2a", the agentCard field will be shown for AI gateway
         */
        private List<String> agentProtocols;

        /**
         * HTTP routes
         */
        private List<HttpRouteResult> routes;

        /**
         * Agent Card information (optional, only exists when agentProtocols contains "a2a")
         * Follows standard A2A protocol definition
         */
        private AgentCard agentCard;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentMetadata {
        /**
         * Source, e.g. APIG_AI, HIGRESS, NACOS
         */
        private String source;
    }
}
