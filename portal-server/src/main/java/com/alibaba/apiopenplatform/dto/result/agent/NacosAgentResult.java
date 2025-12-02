package com.alibaba.apiopenplatform.dto.result.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Nacos Agent 列表结果 DTO
 * 与 GatewayController 的 AgentAPIResult 保持一致，只返回必要字段
 * 
 * @author HiMarket Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NacosAgentResult {
    
    /**
     * Agent 名称（唯一标识）
     */
    private String agentName;
    
    /**
     * Agent 描述
     */
    private String description;
    
    /**
     * 命名空间 ID
     */
    private String namespaceId;
}

