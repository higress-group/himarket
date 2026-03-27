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

import com.alibaba.himarket.entity.RevokedToken;
import java.time.LocalDateTime;

public interface RevokedTokenRepository extends BaseRepository<RevokedToken, Long> {

    /**
     * Check if a revoked token exists by its SHA-256 hash
     *
     * @param tokenHash the SHA-256 hash of the token
     * @return true if the token hash exists in the revoked tokens table
     */
    boolean existsByTokenHash(String tokenHash);

    /**
     * Delete all revoked tokens that expired before the given cutoff time
     *
     * @param cutoff the cutoff time; records with expiresAt before this are deleted
     */
    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
