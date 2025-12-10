package com.alibaba.himarket.dto.params.chat;

import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.support.chat.ChatMessage;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * @author zh
 */
@Data
@Builder
public class InvokeModelParam {

    /** Unique ID */
    private String chatId;

    /** Model Product */
    private ProductResult product;

    /** User question */
    private String userQuestion;

    /** Chat messages in OpenAI-compatible format */
    private List<ChatMessage> chatMessages;

    /** If need web search */
    private Boolean enableWebSearch;

    /** Gateway Ips, used to request gatewy */
    private List<String> gatewayIps;

    /** MCP servers with transport config */
    private List<MCPTransportConfig> mcpConfigs;

    /** Credential for invoking the Model and MCP */
    private CredentialContext credentialContext;
}
