package com.alibaba.apiopenplatform.dto.params.chat;

import com.alibaba.apiopenplatform.support.chat.ChatMessage;
import com.alibaba.apiopenplatform.support.chat.mcp.McpServerConfig;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.List;

/**
 * @author zh
 */
@Data
@Builder
public class ChatRequestBody {

    private String model;

    private Boolean stream;
    
    @JsonProperty("max_tokens")
    private Integer maxTokens;
    
    @JsonProperty("top_p")
    private Double topP;

    private Double temperature;
    
    @JsonProperty("web_search_options")
    private OpenAiApi.ChatCompletionRequest.WebSearchOptions webSearchOptions;

    private String userQuestion;

    private List<ChatMessage> messages;

    private List<McpServerConfig> mcpServerConfigs;
}