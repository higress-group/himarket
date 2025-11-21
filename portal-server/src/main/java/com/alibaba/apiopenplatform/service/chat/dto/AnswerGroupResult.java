package com.alibaba.apiopenplatform.service.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 答案组结果DTO
 * 支持多产品并行回答或"再来一次"的多个回答版本
 *
 * @author HiMarket Team
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnswerGroupResult {
    
    /**
     * 答案结果列表（支持多产品并行回答）
     */
    private List<AnswerResult> results;
}
