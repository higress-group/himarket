package com.alibaba.apiopenplatform.service.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话结果DTO
 *
 * @author HiMarket Team
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConversationResult {
    
    /**
     * 对话ID
     */
    private String conversationId;
    
    /**
     * 对话创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 对话更新时间
     */
    private LocalDateTime updatedAt;
    
    /**
     * 问题列表
     */
    private List<QuestionResult> questions;
}
