package com.alibaba.apiopenplatform.dto.result.chat;

import com.alibaba.apiopenplatform.support.chat.ChatUsage;
import com.alibaba.apiopenplatform.support.chat.attachment.ChatAttachmentConfig;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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
    }
}

