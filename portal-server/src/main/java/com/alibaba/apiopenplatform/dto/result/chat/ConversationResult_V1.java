package com.alibaba.apiopenplatform.dto.result.chat;

import com.alibaba.apiopenplatform.support.chat.ChatUsage;
import com.alibaba.apiopenplatform.support.chat.attachment.ChatAttachmentConfig;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ConversationResult_V1 {

    private String conversationId;

    private List<QuestionResult> questions;

    @Data
    @Builder
    public static class QuestionResult {

        private String questionId;

        private String content;

        private LocalDateTime createdAt;

        private List<ChatAttachmentConfig> attachments;

        private List<AnswerGroupResult> answers;
    }

    @Data
    @Builder
    public static class AnswerResult {

        private String answerId;

        private String productId;

        private String content;

        private ChatUsage usage;
    }

    @Data
    @Builder
    public static class AnswerGroupResult {

        private Integer sequence;

        private List<AnswerResult> results;
    }
}
