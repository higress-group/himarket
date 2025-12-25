/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.dto.result.chat;

import cn.hutool.core.annotation.Alias;
import com.alibaba.himarket.support.chat.ChatUsage;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Optional;
import lombok.Data;

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

        @JsonProperty("first_byte_timeout")
        @Alias("first_byte_timeout")
        private Long firstByteTimeout;

        @JsonProperty("elapsed_time")
        @Alias("elapsed_time")
        private Long elapsedTime;

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
        if (this.usage == null) {
            return null;
        }

        return ChatUsage.builder()
                .promptTokens(this.usage.getPromptTokens())
                .completionTokens(this.usage.getCompletionTokens())
                .totalTokens(this.usage.getTotalTokens())
                .promptTokensDetails(
                        Optional.ofNullable(this.usage.getPromptTokensDetails())
                                .map(
                                        details ->
                                                ChatUsage.PromptTokensDetails.builder()
                                                        .cachedTokens(details.getCachedTokens())
                                                        .build())
                                .orElse(null))
                .build();
    }
}
