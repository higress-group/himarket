package com.alibaba.himarket.service.hichat.support;

import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import io.agentscope.core.message.Msg;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvokeModelParam {

    /**
     * Chat ID
     */
    private String chatId;

    /**
     * Session ID
     */
    private String sessionId;

    /**
     * Model Product
     */
    private ProductResult product;

    /**
     * User message, contains user question and multimodal
     */
    private Msg userMessage;

    /**
     * History messages for initializing memory
     */
    private List<Msg> historyMessages;

    /**
     * If need web search
     */
    private Boolean enableWebSearch;

    /**
     * Gateway ID
     */
    private String gatewayId;

    /**
     * MCP servers with transport config
     */
    private List<MCPTransportConfig> mcpConfigs;

    /**
     * Credential for invoking the Model and MCP
     */
    private CredentialContext credentialContext;
}
