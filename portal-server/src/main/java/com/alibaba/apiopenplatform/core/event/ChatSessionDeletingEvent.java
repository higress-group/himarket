package com.alibaba.apiopenplatform.core.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * @author zh
 */
@Getter
public class ChatSessionDeletingEvent extends ApplicationEvent {

    private final String sessionId;

    public ChatSessionDeletingEvent(String sessionId) {
        super(sessionId);
        this.sessionId = sessionId;
    }
}
