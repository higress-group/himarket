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

package com.alibaba.himarket.repository;

import com.alibaba.himarket.entity.Chat;
import com.alibaba.himarket.support.enums.ChatStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatRepository extends BaseRepository<Chat, Long> {

    /**
     * Find chats by session ID and status
     *
     * @param sessionId the session ID
     * @param status the chat status
     * @param sort the sort order
     * @return the list of chats
     */
    List<Chat> findBySessionIdAndStatus(String sessionId, ChatStatus status, Sort sort);

    /**
     * Find chat by chat ID
     *
     * @param chatId the chat ID
     * @return the chat if found
     */
    Optional<Chat> findByChatId(String chatId);

    /**
     * Find all chats by session ID and user ID
     *
     * @param sessionId the session ID
     * @param userId the user ID
     * @param sort the sort order
     * @return the list of chats
     */
    List<Chat> findAllBySessionIdAndUserId(String sessionId, String userId, Sort sort);

    /**
     * Find current sequence number for a conversation
     *
     * @param sessionId the session ID
     * @param conversationId the conversation ID
     * @param questionId the question ID
     * @param productId the product ID
     * @return the current sequence number
     */
    @Query(
            "SELECT COALESCE(MAX(c.sequence), 0) "
                    + "FROM Chat c "
                    + "WHERE c.sessionId = :sessionId "
                    + "AND c.conversationId = :conversationId "
                    + "AND c.questionId = :questionId "
                    + "AND c.productId = :productId")
    Integer findCurrentSequence(
            @Param("sessionId") String sessionId,
            @Param("conversationId") String conversationId,
            @Param("questionId") String questionId,
            @Param("productId") String productId);

    /**
     * Delete all chats by session ID
     *
     * @param sessionId the session ID
     */
    void deleteAllBySessionId(String sessionId);
}
