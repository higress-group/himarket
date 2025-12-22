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

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Client errors (400-499)

    /** Invalid parameter */
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "无效的请求参数：{}"),

    /** Invalid request */
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "请求无效：{}"),

    /** Unauthorized */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "认证失败：{}"),

    /** Resource not found */
    NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在：{}:{}"),

    /** Resource conflict */
    CONFLICT(HttpStatus.CONFLICT, "资源冲突：{}"),

    // Server errors (500-599)
    /** Internal error */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误：{}"),

    /** Gateway error */
    GATEWAY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "网关错误：{}"),
    ;

    private final HttpStatus status;
    private final String messagePattern;

    public String getMessage(Object... args) {
        try {
            return StrUtil.format(messagePattern, args);
        } catch (Exception e) {
            // Return original pattern if args mismatch
            return messagePattern;
        }
    }
}
