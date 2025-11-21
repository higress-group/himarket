package com.alibaba.apiopenplatform.service.chat.dto;

import cn.hutool.core.annotation.Alias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * OpenAI聊天流式响应结构
 * 
 * @author zh
 */
@Data
public class OpenAIChatStreamResponse {

    /**
     * 响应ID
     */
    private String id;

    /**
     * 响应对象类型，通常为 "chat.completion.chunk"
     */
    private String object;

    /**
     * 响应创建时间戳
     */
    private Long created;

    // Usage
    private Usage usage;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 系统指纹
     */
    @JsonProperty("system_fingerprint")
    @Alias("system_fingerprint")
    private String systemFingerprint;

    /**
     * 选择列表
     */
    private List<Choice> choices;

    /**
     * 选择项
     */
    @Data
    public static class Choice {
        /**
         * 增量内容
         */
        private Delta delta;

        /**
         * 选择索引
         */
        private Integer index;

        /**
         * 结束原因
         */
        @JsonProperty("finish_reason")
        @Alias("finish_reason")
        private String finishReason;
    }

    /**
     * 增量内容
     */
    @Data
    public static class Delta {
        /**
         * 角色
         */
        private String role;

        /**
         * 内容
         */
        private String content;

        /**
         * 推理内容（某些模型支持）
         */
        @JsonProperty("reasoning_content")
        @Alias("reasoning_content")
        private String reasoningContent;
    }

    @Data
    public static class Usage {
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
    }

    @Data
    public static class PromptTokensDetails {
        @JsonProperty("cached_tokens")
        @Alias("cached_tokens")
        private Integer cachedTokens;
    }
}
