package com.alibaba.apiopenplatform.service;

import com.alibaba.apiopenplatform.core.event.ChatSessionDeletingEvent;
import com.alibaba.apiopenplatform.dto.params.chat.CreateChatParam;
import com.alibaba.apiopenplatform.dto.result.chat.ChatAnswerMessage;

import jakarta.servlet.http.HttpServletResponse;
import reactor.core.publisher.Flux;

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
    Flux<ChatAnswerMessage> chat(CreateChatParam param, HttpServletResponse response);

    /**
     * Handle session deletion event, such as cleaning up all related chat records
     * @param event
     */
    void handleSessionDeletion(ChatSessionDeletingEvent event);
}
