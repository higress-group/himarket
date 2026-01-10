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

import io.agentscope.core.model.ModelException;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import lombok.Getter;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Getter
public enum ChatError {

    /**
     * Unknown error
     */
    UNKNOWN_ERROR("Unknown error"),

    /**
     * Network connection error
     */
    NETWORK_ERROR("Network connection error"),

    /**
     * Request timeout
     */
    TIMEOUT("Request timeout"),

    /**
     * Request failed
     */
    REQUEST_ERROR("Request failed"),
    ;

    private final String description;

    ChatError(String description) {
        this.description = description;
    }

    /**
     * Determine ChatError type from exception.
     *
     * @param error The throwable to classify
     * @return Corresponding ChatError enum value
     */
    public static ChatError from(Throwable error) {
        if (error instanceof TimeoutException) {
            return TIMEOUT;
        }

        // Network connection errors (network unreachable, connection refused, etc.)
        if (error instanceof IOException) {
            return NETWORK_ERROR;
        }

        // HTTP errors from model (401, 403, 500, etc.)
        if (error instanceof WebClientResponseException || error instanceof ModelException) {
            return REQUEST_ERROR;
        }

        return UNKNOWN_ERROR;
    }
}
