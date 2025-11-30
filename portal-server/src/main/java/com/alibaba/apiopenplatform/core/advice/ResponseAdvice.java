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

package com.alibaba.apiopenplatform.core.advice;

import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.core.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;

/**
 * 统一响应处理
 * <p>
 * 用于封装接口响应数据为统一格式：
 * {
 * "code": "Success",
 * "message": "操作成功",
 * "data": T
 * }
 * <p>
 * 以下情况不会被包装:
 * 1. 返回值已经是 {@link ResponseEntity}
 * 2. 返回值已经是 {@link Response}
 * 3. ExceptionAdvice 已经处理的异常响应（避免二次包装）
 */
@RestControllerAdvice
@Slf4j
public class ResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 排除Swagger相关路径
        Class<?> declaringClass = returnType.getDeclaringClass();
        if (declaringClass.getName().contains("org.springdoc") ||
                declaringClass.getName().contains("springfox.documentation")) {
            return false;
        }
        Method method = returnType.getMethod();
        if (method == null) {
            return false;
        }

        Class<?> type = returnType.getMethod().getReturnType();
        return !type.equals(ResponseEntity.class) && !type.equals(Response.class) && !type.equals(SseEmitter.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        // 如果body已经是Response对象，说明是ExceptionAdvice处理过的异常响应，直接返回，避免二次包装
        if (body instanceof Response) {
            return body;
        }
        
        // 设置成功响应码
        response.setStatusCode(HttpStatus.OK);

        if (body instanceof String) {
            return JSONUtil.toJsonStr(Response.ok(body));
        }
        return Response.ok(body);
    }
}
