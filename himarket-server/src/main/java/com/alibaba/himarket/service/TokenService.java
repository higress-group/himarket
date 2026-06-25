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

import com.alibaba.himarket.support.common.User;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Token service.
 */
public interface TokenService {

    /**
     * Generates an administrator token.
     *
     * @param userId administrator ID
     * @return JWT token
     */
    String generateAdminToken(String userId);

    /**
     * Generates a developer token.
     *
     * @param userId developer ID
     * @return JWT token
     */
    String generateDeveloperToken(String userId);

    /**
     * Parses user information from a token.
     *
     * @param token JWT token
     * @return user information
     */
    User parseUser(String token);

    /**
     * Gets a token from the request authorization header or cookie.
     *
     * @param request HTTP request
     * @return token, or null when missing
     */
    String getTokenFromRequest(HttpServletRequest request);

    /**
     * Revokes a token.
     *
     * @param token JWT token
     */
    void revokeToken(String token);

    /**
     * Revokes the token carried by a request.
     *
     * @param request HTTP request
     */
    void revokeRequestToken(HttpServletRequest request);

    /**
     * Checks whether a token has been revoked.
     *
     * @param token JWT token
     * @return true if the token has been revoked
     */
    boolean isTokenRevoked(String token);

    /**
     * Gets token expiration duration in seconds.
     *
     * @return expiration duration in seconds
     */
    long getTokenExpiresIn();
}
