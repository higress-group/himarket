package com.alibaba.apiopenplatform.dto.converter;

import com.alibaba.apiopenplatform.dto.result.agent.NacosAgentResult;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Nacos Agent 转换器
 * 与 Gateway 的 AgentAPIResult 保持一致，只转换必要字段
 * 
 * @author HiMarket Team
 */
@Component
public class NacosAgentConverter {
    
    /**
     * 批量转换 AgentCardVersionInfo 列表为 NacosAgentResult 列表
     */
    public List<NacosAgentResult> convertToAgentResults(
            List<AgentCardVersionInfo> agentCards, 
            String namespaceId) {
        
        if (agentCards == null || agentCards.isEmpty()) {
            return Collections.emptyList();
        }
        
        return agentCards.stream()
                .map(card -> convertToAgentResult(card, namespaceId))
                .collect(Collectors.toList());
    }
    
    /**
     * 转换单个 AgentCardVersionInfo 为 NacosAgentResult
     */
    public NacosAgentResult convertToAgentResult(
            AgentCardVersionInfo agentCard, 
            String namespaceId) {
        
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

