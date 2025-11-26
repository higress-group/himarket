package com.alibaba.apiopenplatform.dto.result.chat;

import cn.hutool.core.annotation.Alias;
import com.alibaba.apiopenplatform.support.chat.ChatUsage;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * @author zh
 */
@Data
public class OpenAIChatStreamResponse {

    /**
     * Response ID
     */
    private String id;

    /**
     * Response type, currently always "chat.completion.chunk"
     */
    private String object;

    /**
     * Timestamp
     */
    private Long created;

    /**
     * Usage
     */
    private Usage usage;

    /**
     * Model name
     */
    private String model;

    /**
     * System fingerprint
     */
    @JsonProperty("system_fingerprint")
    @Alias("system_fingerprint")
    private String systemFingerprint;

    /**
     * Choices
     */
    private List<Choice> choices;

    @Data
    public static class Choice {
        /**
         * Delta
         */
        private Delta delta;

        /**
         * Index
         */
        private Integer index;

        /**
         * Reason for completion
         */
        @JsonProperty("finish_reason")
        @Alias("finish_reason")
        private String finishReason;
    }

    @Data
    public static class Delta {
        /**
         * Role
         */
        private String role;

        /**
         * Content
         */
        private String content;

        /**
         * Reasoning content, only returned when finish_reason is "reasoning"
         */
        @JsonProperty("reasoning_content")
        @Alias("reasoning_content")
        private String reasoningContent;
    }

    @Data
    public static class Usage {

        @JsonProperty("first_package_time")
        @Alias("first_package_time")
        private Long firstPackageTime;

        /**
         * Tokens used for prompt
         */
        @JsonProperty("prompt_tokens")
        @Alias("prompt_tokens")
        private Integer promptTokens;

        /**
         * Tokens used for completion
         */
        @JsonProperty("completion_tokens")
        @Alias("completion_tokens")
        private Integer completionTokens;

        /**
         * Total tokens used, including prompt and completion
         */
        @JsonProperty("total_tokens")
        @Alias("total_tokens")
        private Integer totalTokens;

        /**
         * Tokens used for prompt details
         */
        @JsonProperty("prompt_tokens_details")
        @Alias("prompt_tokens_details")
        private PromptTokensDetails promptTokensDetails;
    }

    @Data
    public static class PromptTokensDetails {
        /**
         * Cached tokens
         */
        @JsonProperty("cached_tokens")
        @Alias("cached_tokens")
        private Integer cachedTokens;
    }

    public ChatUsage toStandardUsage() {
        return ChatUsage.builder()
                .promptTokens(this.usage.getPromptTokens())
                .completionTokens(this.usage.getCompletionTokens())
                .totalTokens(this.usage.getTotalTokens())
                .promptTokensDetails(ChatUsage.PromptTokensDetails.builder()
                        .cachedTokens(this.usage.getPromptTokensDetails().getCachedTokens())
                        .build())
                .build();
    }
}
