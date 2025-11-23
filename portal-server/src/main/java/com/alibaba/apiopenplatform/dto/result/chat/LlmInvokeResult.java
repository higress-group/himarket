package com.alibaba.apiopenplatform.dto.result.chat;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@Slf4j
public class LlmInvokeResult {

    private boolean success;

    /**
     * Completed answer
     */
    private String answer;

    /**
     * If failed, error message
     */
    private String errorMessage;

    /**
     * Elapsed time, in milliseconds
     */
    private long elapsedTime;

    /**
     * Answer length
     */
    private int answerLength;

    public static LlmInvokeResult of(boolean success, String answer, long elapsedTime) {
        return LlmInvokeResult.builder()
                .success(success)
                .answer(answer)
                .elapsedTime(elapsedTime)
                .answerLength(answer != null ? answer.length() : 0)
                .build();
    }
}
