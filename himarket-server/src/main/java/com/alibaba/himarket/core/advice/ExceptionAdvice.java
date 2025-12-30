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

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.response.Response;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler
 *
 * <p>Handles three types of exceptions:
 * 1. {@link BusinessException}: Business errors
 * 2. {@link MethodArgumentNotValidException}: Request validation errors
 * 3. {@link Exception}: Unexpected system errors
 *
 * <p>All exceptions are converted to unified response:
 * { "code": "error_code", "message": "error_message", "data": null }
 */
@Slf4j
@RestControllerAdvice
public class ExceptionAdvice {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Response<Void>> handleBusinessException(BusinessException e) {
        log.warn("[Business Exception] code: {}, message: {}", e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getStatus())
                .body(Response.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response<Void>> handleParamVerifyException(
            MethodArgumentNotValidException e) {
        String message =
                e.getBindingResult().getFieldErrors().stream()
                        .map(
                                fieldError ->
                                        fieldError.getField()
                                                + ": "
                                                + fieldError.getDefaultMessage())
                        .collect(Collectors.joining("; "));
        // Validation error is user behavior, no stack trace needed
        log.warn("[Validation Exception] invalid parameters: {}", message);
        return ResponseEntity.status(ErrorCode.INVALID_PARAMETER.getStatus())
                .body(Response.fail(ErrorCode.INVALID_PARAMETER.name(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleSystemException(Exception e) {
        // Log full stack trace for system errors
        log.error(
                "[System Exception] type: {}, message: {}",
                e.getClass().getSimpleName(),
                e.getMessage(),
                e);

        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(
                        Response.fail(
                                ErrorCode.INTERNAL_ERROR.name(),
                                ErrorCode.INTERNAL_ERROR.getMessage(e.getMessage())));
    }
}
