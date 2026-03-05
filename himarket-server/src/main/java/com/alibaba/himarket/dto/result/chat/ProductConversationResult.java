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

import com.alibaba.himarket.support.chat.ChatUsage;
import com.alibaba.himarket.support.chat.ToolCallInfo;
import com.alibaba.himarket.support.chat.attachment.ChatAttachmentConfig;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductConversationResult {

    private String productId;

    private List<ConversationResult> conversations;

    @Data
    @Builder
    public static class ConversationResult {
        private String conversationId;
        private List<QuestionResult> questions;
    }

    @Data
    @Builder
    public static class QuestionResult {
        private String questionId;
        private String content;
        private LocalDateTime createdAt;
        private List<ChatAttachmentConfig> attachments;
        private List<AnswerResult> answers;
    }

    @Data
    @Builder
    public static class AnswerResult {
        private Integer sequence;
        private String answerId;
        private String content;
        private ChatUsage usage;
        private List<ToolCallInfo> toolCalls;
    }
}
