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

import com.alibaba.himarket.entity.CodingSession;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface CodingSessionRepository extends BaseRepository<CodingSession, Long> {

    /**
     * Find coding session by session ID
     */
    Optional<CodingSession> findBySessionId(String sessionId);

    /**
     * Find coding session by session ID and user ID
     */
    Optional<CodingSession> findBySessionIdAndUserId(String sessionId, String userId);

    /**
     * Find all coding sessions by user ID, ordered by updated_at descending
     */
    List<CodingSession> findByUserIdOrderByUpdatedAtDesc(String userId);
}
