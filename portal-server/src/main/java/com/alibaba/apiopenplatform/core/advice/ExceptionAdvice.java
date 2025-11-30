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

import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.core.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理
 * <p>
 * 处理三类异常:
 * 1. 业务异常 {@link BusinessException}: 业务异常
 * 2. 参数校验异常 {@link MethodArgumentNotValidException}: 请求参数校验不通过
 * 3. 系统异常 {@link Exception}: 非预期的系统异常
 * <p>
 * 所有异常都会被转换为统一的响应格式:
 * {
 * "code": "错误码",
 * "message": "错误信息",
 * "data": null
 * }
 *
 */
@Slf4j
@RestControllerAdvice
public class ExceptionAdvice {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Response<Void>> handleBusinessException(BusinessException e) {
        log.warn("[Business Exception] code: {}, message: {}", e.getCode(), e.getMessage());
        return ResponseEntity
                .status(e.getStatus())
                .body(Response.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response<Void>> handleParamVerifyException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        // 参数校验失败属于用户行为，不打印堆栈
        log.warn("[Validation Exception] invalid parameters: {}", message);
        return ResponseEntity
                .status(ErrorCode.INVALID_PARAMETER.getStatus())
                .body(Response.fail(ErrorCode.INVALID_PARAMETER.name(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleSystemException(Exception e) {
        // 完整打印异常堆栈信息，包括异常类型、消息和堆栈跟踪
        log.error("[System Exception] type: {}, message: {}", 
                e.getClass().getSimpleName(), e.getMessage(), e);
        
        return ResponseEntity
                .status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(Response.fail(
                        ErrorCode.INTERNAL_ERROR.name(),
                        ErrorCode.INTERNAL_ERROR.getMessage(e.getMessage())
                ));
    }
}
