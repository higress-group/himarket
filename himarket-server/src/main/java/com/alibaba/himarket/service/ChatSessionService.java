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
     * List conversations grouped by product for a chat session Structure: Product[] ->
     * Conversations[] -> Questions[] -> Answers[]
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
