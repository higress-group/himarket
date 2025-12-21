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
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class NacosAgentConverter {

    /** 批量转换 AgentCardVersionInfo 列表为 NacosAgentResult 列表 */
    public List<NacosAgentResult> convertToAgentResults(
            List<AgentCardVersionInfo> agentCards, String namespaceId) {

        if (agentCards == null || agentCards.isEmpty()) {
            return Collections.emptyList();
        }

        return agentCards.stream()
                .map(card -> convertToAgentResult(card, namespaceId))
                .collect(Collectors.toList());
    }

    /** 转换单个 AgentCardVersionInfo 为 NacosAgentResult */
    public NacosAgentResult convertToAgentResult(
            AgentCardVersionInfo agentCard, String namespaceId) {

        if (agentCard == null) {
            return null;
        }

        // 只返回必要字段，与 Gateway 保持一致
        // 注意：AgentCardVersionInfo 继承自 AgentCardBasicInfo
        // name 和 description 字段来自父类
        return NacosAgentResult.builder()
                .agentName(agentCard.getName())
                .description(agentCard.getDescription())
                .namespaceId(namespaceId)
                .build();
    }
}
