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

package com.alibaba.himarket.support.chat;

import cn.hutool.core.annotation.Alias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

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
