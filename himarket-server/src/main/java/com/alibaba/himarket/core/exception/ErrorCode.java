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

    // API 生命周期管理相关错误 (400-499)

    /** API Definition 不存在 */
    API_DEFINITION_NOT_FOUND(HttpStatus.NOT_FOUND, "API Definition 不存在：{}"),

    /** Endpoint 不存在 */
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "Endpoint 不存在：{}"),

    /** 网关不存在 */
    GATEWAY_NOT_FOUND(HttpStatus.NOT_FOUND, "网关不存在：{}"),

    /** Registry 不存在 */
    REGISTRY_NOT_FOUND(HttpStatus.NOT_FOUND, "Registry 不存在：{}"),

    /** 不支持的 API 类型 */
    UNSUPPORTED_API_TYPE(HttpStatus.BAD_REQUEST, "不支持的 API 类型：{}"),

    /** 发布目标不支持当前 API 类型 */
    UNSUPPORTED_OPERATION(HttpStatus.BAD_REQUEST, "发布目标不支持当前 API 类型：{}"),

    /** API Definition 当前状态不允许此操作 */
    INVALID_API_STATUS(HttpStatus.CONFLICT, "API Definition 当前状态不允许此操作：{}"),

    /** 存在活跃的发布记录，无法删除 */
    ACTIVE_PUBLISH_EXISTS(HttpStatus.CONFLICT, "存在活跃的发布记录，无法删除"),

    /** API 已发布到该目标 */
    ALREADY_PUBLISHED(HttpStatus.CONFLICT, "API 已发布到该目标：{}"),

    /** API 已发布到其他网关（当前限制只能发布到一个网关） */
    API_ALREADY_PUBLISHED_TO_GATEWAY(HttpStatus.CONFLICT, "API 已发布到网关【{}】，请先取消发布后再发布到其他网关"),

    /** 发布或下线操作正在进行中 */
    PUBLISH_OPERATION_IN_PROGRESS(HttpStatus.CONFLICT, "该 API 在选定网关上的发布或下线操作正在进行中，请稍后再试"),

    // 服务端错误 (500-599)
    /** 非预期错误 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误：{}"),

    /** Gateway error */
    GATEWAY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "网关错误：{}"),

    /** Registry 调用失败 */
    REGISTRY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Registry 调用失败：{}"),

    /** 配置转换失败 */
    CONFIG_CONVERSION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "配置转换失败：{}"),

    /** 网关服务不可用 */
    GATEWAY_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "网关服务不可用：{}"),

    /** Registry 服务不可用 */
    REGISTRY_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Registry 服务不可用：{}"),
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
