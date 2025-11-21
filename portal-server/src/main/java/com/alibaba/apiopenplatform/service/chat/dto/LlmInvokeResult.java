package com.alibaba.apiopenplatform.service.chat.dto;

import lombok.Builder;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author HiMarket Team
 */
@Data
@Builder
public class LlmInvokeResult {

    private static final Logger log = LoggerFactory.getLogger(LlmInvokeResult.class);
    /**
     * 是否调用成功
     */
    private boolean success;

    /**
     * 完整的AI回答
     */
    private String answer;

    /**
     * 错误信息（如果失败）
     */
    private String errorMessage;

    /**
     * 处理耗时（毫秒）
     */
    private long elapsedTime;

    /**
     * 答案长度
     */
    private int answerLength;

    /**
     * 创建成功结果
     */
    public static LlmInvokeResult success(String answer) {
        log.info("zhaoh-test-answer: {}", answer);
        return LlmInvokeResult.builder()
                .success(true)
                .answer(answer)
//                .elapsedTime(elapsedTime)
                .answerLength(answer != null ? answer.length() : 0)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static LlmInvokeResult failure(String errorMessage, String answer) {
        return LlmInvokeResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .answer(answer)
//                .elapsedTime(elapsedTime)
                .answerLength(0)
                .build();
    }
}
