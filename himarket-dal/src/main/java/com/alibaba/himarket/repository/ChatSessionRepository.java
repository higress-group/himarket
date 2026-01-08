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

import com.alibaba.himarket.entity.ChatSession;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatSessionRepository extends BaseRepository<ChatSession, Long> {

    /**
     * Find session by session ID
     *
     * @param sessionId the session ID
     * @return the chat session if found
     */
    Optional<ChatSession> findBySessionId(String sessionId);

    /**
     * Find sessions by user ID with pagination
     *
     * @param userId the user ID
     * @param pageable the pagination information
     * @return the page of chat sessions
     */
    Page<ChatSession> findByUserId(String userId, Pageable pageable);

    /**
     * Count sessions by user ID
     *
     * @param userId the user ID
     * @return the number of sessions
     */
    int countByUserId(String userId);

    /**
     * Find session by session ID and user ID
     *
     * @param sessionId the session ID
     * @param userId the user ID
     * @return the chat session if found
     */
    Optional<ChatSession> findBySessionIdAndUserId(String sessionId, String userId);

    /**
     * Find first session by user ID
     *
     * @param userId the user ID
     * @param sort the sort order
     * @return the first chat session if found
     */
    Optional<ChatSession> findFirstByUserId(String userId, Sort sort);
}
