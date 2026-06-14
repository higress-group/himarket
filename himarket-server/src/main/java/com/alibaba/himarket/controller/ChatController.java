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

package com.alibaba.himarket.controller;

import com.alibaba.himarket.core.annotation.AdminOrDeveloperAuth;
import com.alibaba.himarket.dto.params.chat.CreateChatParam;
import com.alibaba.himarket.service.hichat.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.scheduler.Schedulers;

@Tag(name = "AI Chat", description = "Streaming AI chat APIs")
@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
@Validated
@Slf4j
@AdminOrDeveloperAuth
public class ChatController {

    private final ChatService chatService;

    @Operation(
            summary = "Start AI chat",
            description =
                    "Returns text/event-stream events without the unified JSON response wrapper")
    @ApiResponse(
            responseCode = "200",
            description = "Streaming chat events",
            content =
                    @Content(
                            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                            schema = @Schema(implementation = String.class)))
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody CreateChatParam param) {
        // Use SseEmitter for streaming
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        chatService
                .chat(param)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        event -> {
                            try {
                                emitter.send(event);
                            } catch (Exception e) {
                                log.error(
                                        "Failed to send chat event, sessionId={}",
                                        param.getSessionId(),
                                        e);
                                emitter.completeWithError(e);
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete);

        return emitter;
    }
}
