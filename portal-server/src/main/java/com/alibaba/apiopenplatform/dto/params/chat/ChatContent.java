package com.alibaba.apiopenplatform.dto.params.chat;

import com.alibaba.apiopenplatform.support.chat.ChatUsage;
import lombok.Getter;
import lombok.Setter;

/**
 * @author zh
 * @description Save current chunk and append to answer content
 */
@Getter
@Setter
public class ChatContent {

    private StringBuilder answerContent = new StringBuilder();

    private StringBuilder unexpectedContent = new StringBuilder();

    private Long startTime;

    private Long firstByteTimeout;

    private ChatUsage usage;

    public boolean success() {
        return unexpectedContent.isEmpty();
    }

    public void start() {
        startTime = System.currentTimeMillis();
    }

    public void recordFirstByteTimeout() {
        if (firstByteTimeout == null && startTime != null) {
            firstByteTimeout = System.currentTimeMillis() - startTime;
        }
    }

    public void stop() {
        if (usage != null) {
            usage.setElapsedTime(System.currentTimeMillis() - startTime);
            if (firstByteTimeout != null) {
                usage.setFirstByteTimeout(firstByteTimeout);
            }
        }
    }
}
