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

package com.alibaba.himarket.core.exception;

import java.text.MessageFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Client errors (400-499)

    /**
     * Invalid parameter
     */
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "Invalid request parameter: {0}"),

    /**
     * Invalid request
     */
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Invalid request: {0}"),

    /**
     * Unauthorized
     */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication failed: {0}"),

    /**
     * Resource not found
     */
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found: {0}:{1}"),

    /**
     * Resource conflict
     */
    CONFLICT(HttpStatus.CONFLICT, "Resource conflict: {0}"),

    // Server errors (500-599)
    /**
     * Internal error
     */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error: {0}"),

    /**
     * Gateway error
     */
    GATEWAY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Gateway error: {0}"),

    /**
     * Sandbox not ready
     */
    SANDBOX_NOT_READY(HttpStatus.SERVICE_UNAVAILABLE, "Sandbox is not ready: {0}"),

    /**
     * Sandbox connection failed
     */
    SANDBOX_CONNECTION_FAILED(HttpStatus.BAD_GATEWAY, "Sandbox connection failed: {0}"),

    /**
     * Sandbox error
     */
    SANDBOX_ERROR(HttpStatus.BAD_GATEWAY, "{0}"),
    ;

    private final HttpStatus status;
    private final String messagePattern;

    public String getMessage(Object... args) {
        try {
            return MessageFormat.format(messagePattern, args);
        } catch (Exception e) {
            // Return original pattern if args mismatch
            return messagePattern;
        }
    }
}
