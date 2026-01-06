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
     * Find by sessionId and status
     *
     * @param sessionId
     * @param status
     * @param sort
     * @return
     */
    List<Chat> findBySessionIdAndStatus(String sessionId, ChatStatus status, Sort sort);

    /**
     * Find by chatId
     *
     * @param chatId
     * @return
     */
    Optional<Chat> findByChatId(String chatId);

    /**
     * Find all chats for given sessionId and userId
     *
     * @param sessionId
     * @param userId
     * @param sort
     * @return
     */
    List<Chat> findAllBySessionIdAndUserId(String sessionId, String userId, Sort sort);

    /**
     * Find next sequence for given conversationId and questionId
     *
     * @param conversationId
     * @param questionId
     * @return
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
     * Delete all chats for given sessionId
     *
     * @param sessionId
     */
    void deleteAllBySessionId(String sessionId);

    /**
     * Count chats grouped by productId
     *
     * @return List of Object[] where index 0 is productId and index 1 is count
     */
    @Query(
            "SELECT c.productId, COUNT(c) FROM Chat c WHERE c.productId IS NOT NULL GROUP BY"
                    + " c.productId")
    List<Object[]> countChatsGroupedByProductId();

    /**
     * Count chats for specific productId
     *
     * @param productId
     * @return count of chats for the product
     */
    @Query("SELECT COUNT(c) FROM Chat c WHERE c.productId = :productId")
    Long countChatsByProductId(@Param("productId") String productId);
}
