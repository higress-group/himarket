/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.service;

import com.alibaba.himarket.dto.params.chat.CreateChatSessionParam;
import com.alibaba.himarket.dto.params.chat.UpdateChatSessionParam;
import com.alibaba.himarket.dto.result.chat.ChatSessionResult;
import com.alibaba.himarket.dto.result.chat.ConversationResult_V1;
import com.alibaba.himarket.dto.result.chat.ProductConversationResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.entity.ChatSession;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface ChatSessionService {

    /**
     * Creates a chat session.
     *
     * @param param chat session creation parameters
     * @return created chat session information
     */
    ChatSessionResult createSession(CreateChatSessionParam param);

    /**
     * Gets a chat session.
     *
     * @param sessionId chat session ID
     * @return chat session information
     */
    ChatSessionResult getSession(String sessionId);

    /**
     * Checks whether a chat session exists.
     *
     * @param sessionId chat session ID
     */
    void existsSession(String sessionId);

    /**
     * Lists chat sessions for the current user.
     *
     * @param pageable pagination parameters
     * @return paged chat session results
     */
    PageResult<ChatSessionResult> listSessions(Pageable pageable);

    /**
     * Updates a chat session.
     *
     * @param sessionId chat session ID
     * @param param chat session update parameters
     * @return updated chat session information
     */
    ChatSessionResult updateSession(String sessionId, UpdateChatSessionParam param);

    /**
     * Deletes a chat session.
     *
     * @param sessionId chat session ID
     */
    void deleteSession(String sessionId);

    /**
     * Lists conversations for a chat session.
     *
     * @param sessionId chat session ID
     * @return conversation results
     */
    List<ConversationResult_V1> listConversations(String sessionId);

    /**
     * Lists conversations grouped by product for a chat session.
     *
     * @param sessionId chat session ID
     * @return product conversation results
     */
    List<ProductConversationResult> listConversationsV2(String sessionId);

    /**
     * Gets a chat session for the current user.
     *
     * @param sessionId chat session ID
     * @return chat session entity
     */
    ChatSession findUserSession(String sessionId);
}
