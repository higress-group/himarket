package com.alibaba.apiopenplatform.dto.params.chat;

import cn.hutool.core.date.StopWatch;
import com.alibaba.apiopenplatform.support.chat.ChatUsage;
import lombok.Data;

/**
 * @author zh
 * @description Save current chunk and append to answer content
 */
@Data
public class ChatContent {

    private StringBuilder answerContent = new StringBuilder();

    private StringBuilder unexpectedContent = new StringBuilder();

    private StopWatch stopWatch = new StopWatch();

    private ChatUsage usage;

    private String currentContent;

    public boolean success() {
        return unexpectedContent.length() == 0;
    }

    public void start() {
        stopWatch.start();
    }

    public void stop() {
        stopWatch.stop();
        if (usage != null) {
            usage.setElapsedTime(stopWatch.getTotalTimeMillis());
        }
    }
}
