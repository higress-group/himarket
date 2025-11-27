package com.alibaba.apiopenplatform.service;

import com.alibaba.apiopenplatform.dto.result.common.PageResult;
import com.alibaba.apiopenplatform.entity.ChatSession;
import com.alibaba.apiopenplatform.dto.result.chat.ChatSessionResult;
import com.alibaba.apiopenplatform.dto.result.chat.ConversationResult_V1;
import com.alibaba.apiopenplatform.dto.result.chat.ProductConversationResult;
import com.alibaba.apiopenplatform.dto.params.chat.CreateChatSessionParam;
import com.alibaba.apiopenplatform.dto.params.chat.UpdateChatSessionParam;
import org.springframework.data.domain.Pageable;

import java.util.*;

/**
 * @author zh
 */
public interface ChatSessionService {

    /**
     * Create a new chat session
     *
     * @param param
     * @return
     */
    ChatSessionResult createSession(CreateChatSessionParam param);

    /**
     * Get a chat session
     *
     * @param sessionId
     * @return
     */
    ChatSessionResult getSession(String sessionId);

    /**
     * Check if a chat session exists
     *
     * @param sessionId
     */
    void existsSession(String sessionId);

    /**
     * List all chat sessions for the current user
     *
     * @param pageable
     * @return
     */
    PageResult<ChatSessionResult> listSessions(Pageable pageable);

    /**
     * Update a chat session
     *
     * @param sessionId
     * @param param
     * @return
     */
    ChatSessionResult updateSession(String sessionId, UpdateChatSessionParam param);

    /**
     * Delete a chat session
     *
     * @param sessionId
     */
    void deleteSession(String sessionId);

    /**
     * List all conversations for a chat session
     *
     * @param sessionId
     * @return
     */
    List<ConversationResult_V1> listConversations(String sessionId);

    /**
     * List conversations grouped by product for a chat session
     * Structure: Product[] -> Conversations[] -> Questions[] -> Answers[]
     *
     * @param sessionId
     * @return List of ProductConversationResult
     */
    List<ProductConversationResult> listConversationsV2(String sessionId);

    /**
     * Get a chat session for the current user
     *
     * @param sessionId
     * @return
     */
    ChatSession findUserSession(String sessionId);
}
