package com.alibaba.apiopenplatform.dto.params.chat;

import com.alibaba.apiopenplatform.dto.result.consumer.CredentialContext;
import com.alibaba.apiopenplatform.dto.result.product.ProductResult;
import com.alibaba.apiopenplatform.support.chat.ChatMessage;
import com.alibaba.apiopenplatform.support.chat.mcp.McpServerConfig;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @author zh
 */
@Data
@Builder
public class InvokeModelParam {

    private String chatId;

    private ProductResult product;

    private Map<String, String> requestHeaders;

    private Map<String, String> queryParams;

    private String userQuestion;

    private List<ChatMessage> chatMessages;

    private Boolean stream;
    
    private Boolean enableWebSearch;

    private List<String> gatewayIps;

    private List<McpServerConfig> mcpServerConfigs;

    private CredentialContext credentialContext;
}
