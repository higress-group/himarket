package com.alibaba.apiopenplatform.service.chat.dto;

import com.alibaba.apiopenplatform.support.enums.ChatStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 答案结果DTO
 *
 * @author HiMarket Team
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnswerResult {
    
    /**
     * 答案ID
     */
    private String answerId;
    
    /**
     * 产品ID
     */
    private String productId;
    
    /**
     * 答案内容
     */
    private String content;
    
    /**
     * 答案状态
     */
    private ChatStatus status;
    
    /**
     * 答案创建时间
     */
    private LocalDateTime createdAt;
}
