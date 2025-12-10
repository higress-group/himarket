package com.alibaba.himarket.dto.result.chat;

import com.alibaba.himarket.dto.params.chat.ChatContext;
import com.alibaba.himarket.support.chat.ChatUsage;
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
     * Usage, exists only when success
     */
    private ChatUsage usage;

    public static LlmInvokeResult of(ChatContext chatContext) {
        return LlmInvokeResult.builder()
                .success(chatContext.getSuccess())
                .answer(chatContext.getAnswerContent().toString())
                .usage(chatContext.getChatUsage())
                .build();
    }
}
