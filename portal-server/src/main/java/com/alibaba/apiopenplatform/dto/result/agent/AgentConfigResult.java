package com.alibaba.apiopenplatform.dto.result.agent;

import com.alibaba.apiopenplatform.dto.result.httpapi.HttpRouteResult;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 配置结果
 * 
 * @author zh
 */
@Data
public class AgentConfigResult {

    private AgentAPIConfig agentAPIConfig;
    
    /**
     * 元数据信息（与 agentAPIConfig 同级）
     */
    private AgentMetadata meta;

    /**
     * Agent API 配置
     * 支持 Gateway 和 Nacos 两种来源
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentAPIConfig {
        /**
         * Agent 协议列表（用于判断显示逻辑）
         * 如果包含 "a2a"，则展示 agentCard 字段
         * for AI gateway
         */
        private List<String> agentProtocols;
        
        /**
         * HTTP 路由配置
         */
        private List<HttpRouteResult> routes;
        
        /**
         * Agent Card 信息（可选，仅当 agentProtocols 包含 "a2a" 时存在）
         * 遵循标准 A2A 协议定义
         */
        private AgentCard agentCard;
    }
    
    /**
     * Agent 元数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentMetadata {
        /**
         * 来源
         * AI网关/Higress/Nacos
         */
        private String source;
    }
}

