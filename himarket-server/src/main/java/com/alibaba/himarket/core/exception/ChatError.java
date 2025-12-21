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

import java.util.concurrent.TimeoutException;
import lombok.Getter;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Getter
public enum ChatError {
    WEB_RESPONSE_ERROR("Web response error"),

    TIMEOUT("Timeout"),

    UNKNOWN_ERROR("Unknown error"),

    LLM_ERROR("LLM error, please check the input"),
    ;

    private final String description;

    ChatError(String description) {
        this.description = description;
    }

    public static ChatError from(Throwable error) {
        if (error instanceof WebClientResponseException) {
            return WEB_RESPONSE_ERROR;
        }
        if (error instanceof TimeoutException) {
            return TIMEOUT;
        }
        return UNKNOWN_ERROR;
    }
}
