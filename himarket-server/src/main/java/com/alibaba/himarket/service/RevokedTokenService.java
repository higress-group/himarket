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

/** Service for managing revoked JWT tokens with database persistence and local caching. */
public interface RevokedTokenService {

    /**
     * Revoke a token by persisting its SHA-256 hash to the database and caching it locally.
     *
     * @param token the raw JWT token to revoke
     * @param expiresAtMillis the token's expiration time in epoch milliseconds
     */
    void revokeToken(String token, long expiresAtMillis);

    /**
     * Check whether a token has been revoked.
     *
     * <p>Checks the Caffeine local cache first; falls back to the database if not cached.
     *
     * @param token the raw JWT token to check
     * @return true if the token is revoked, false otherwise
     */
    boolean isTokenRevoked(String token);

    /** Delete expired revocation records from the database. */
    void cleanupExpiredTokens();
}
