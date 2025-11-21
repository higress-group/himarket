package com.alibaba.apiopenplatform.service.chat.dto;

import com.alibaba.apiopenplatform.support.chat.attachment.AttachmentConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 问题结果DTO
 *
 * @author HiMarket Team
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QuestionResult {
    
    /**
     * 问题ID
     */
    private String questionId;
    
    /**
     * 问题内容
     */
    private String content;
    
    /**
     * 问题创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 附件列表
     */
    private List<AttachmentConfig> attachments;
    
    /**
     * 答案组列表
     */
    private List<AnswerGroupResult> answers;
}
