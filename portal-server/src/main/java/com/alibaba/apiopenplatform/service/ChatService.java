package com.alibaba.apiopenplatform.service;

import com.alibaba.apiopenplatform.core.event.ChatSessionDeletingEvent;
import com.alibaba.apiopenplatform.dto.params.chat.CreateChatParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;

/**
 * @author zh
 */
public interface ChatService {

    /**
     * Perform a chat
     *
     * @param param
     * @param response
     * @return
     */
    SseEmitter chat(CreateChatParam param, HttpServletResponse response);

    /**
     * Handle session deletion event, such as cleaning up all related chat records
     * @param event
     */
    void handleSessionDeletion(ChatSessionDeletingEvent event);
}
