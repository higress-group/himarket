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

package com.alibaba.himarket.core.advice;

import com.alibaba.himarket.core.response.Response;
import com.alibaba.himarket.utils.JsonUtil;
import java.lang.reflect.Method;
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
import reactor.core.publisher.Flux;

/**
 * Unified response wrapper
 *
 * <p>Wraps API responses into standard format: { "code": "Success", "message": "Operation
 * successful", "data": T }
 *
 * <p>Skips wrapping for: 1. {@link ResponseEntity} 2. {@link Response} 3. Exception responses
 * handled by ExceptionAdvice (avoid double wrapping)
 */
@RestControllerAdvice
@Slf4j
public class ResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(
            MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Exclude Swagger endpoints
        Class<?> declaringClass = returnType.getDeclaringClass();
        if (declaringClass.getName().contains("org.springdoc")
                || declaringClass.getName().contains("springfox.documentation")) {
            return false;
        }
        Method method = returnType.getMethod();
        if (method == null) {
            return false;
        }

        Class<?> type = returnType.getMethod().getReturnType();
        return !type.equals(ResponseEntity.class)
                && !type.equals(Response.class)
                && !type.equals(SseEmitter.class)
                && !type.equals(Flux.class);
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        // Skip wrapping if already wrapped by ExceptionAdvice
        if (body instanceof Response) {
            return body;
        }

        // Set success status
        response.setStatusCode(HttpStatus.OK);

        if (body instanceof String) {
            return JsonUtil.toJson(Response.ok(body));
        }
        return Response.ok(body);
    }
}
