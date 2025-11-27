package com.alibaba.apiopenplatform.dto.params.chat;

import com.alibaba.apiopenplatform.support.chat.ChatMessage;
import com.alibaba.apiopenplatform.support.chat.mcp.McpServerConfig;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * @author zh
 */
@Data
@Builder
public class ChatRequestBody {

    private String model;

    private Boolean stream;

    private Integer maxTokens;

    private Double topP;

    private Double temperature;

    private String userQuestion;

    private List<ChatMessage> messages;

    private List<McpServerConfig> mcpServerConfigs;
}