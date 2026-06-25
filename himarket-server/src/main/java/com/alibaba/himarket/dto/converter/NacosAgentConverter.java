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

package com.alibaba.himarket.dto.converter;

import com.alibaba.himarket.dto.result.agent.NacosAgentResult;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class NacosAgentConverter {

    /**
     * Converts AgentCardVersionInfo entries into Nacos agent results.
     */
    public List<NacosAgentResult> convertToAgentResults(
            List<AgentCardVersionInfo> agentCards, String namespaceId) {

        if (agentCards == null || agentCards.isEmpty()) {
            return Collections.emptyList();
        }

        return agentCards.stream().map(card -> convertToAgentResult(card, namespaceId)).toList();
    }

    /**
     * Converts one AgentCardVersionInfo entry into a Nacos agent result.
     */
    public NacosAgentResult convertToAgentResult(
            AgentCardVersionInfo agentCard, String namespaceId) {

        if (agentCard == null) {
            return null;
        }

        // Return only the required fields and keep the shape aligned with Gateway.
        // AgentCardVersionInfo extends AgentCardBasicInfo, which owns name and description.
        return NacosAgentResult.builder()
                .agentName(agentCard.getName())
                .description(agentCard.getDescription())
                .namespaceId(namespaceId)
                .build();
    }
}
