package com.alibaba.apiopenplatform.support.chat;

import cn.hutool.core.annotation.Alias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
* @author zh
*/
@Data
@Builder
public class ChatUsage {

    @JsonProperty("elapsed_time")
    @Alias("elapsed_time")
    private Long elapsedTime;

    @JsonProperty("first_byte_timeout")
    @Alias("first_byte_timeout")
    private Long firstByteTimeout;

    @JsonProperty("prompt_tokens")
    @Alias("prompt_tokens")
    private Integer promptTokens;

    @JsonProperty("completion_tokens")
    @Alias("completion_tokens")
    private Integer completionTokens;

    @JsonProperty("total_tokens")
    @Alias("total_tokens")
    private Integer totalTokens;

    @JsonProperty("prompt_tokens_details")
    @Alias("prompt_tokens_details")
    private PromptTokensDetails promptTokensDetails;

    @Data
    @Builder
    public static class PromptTokensDetails {
        @JsonProperty("cached_tokens")
        @Alias("cached_tokens")
        private Integer cachedTokens;
    }
}
