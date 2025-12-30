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
import com.alibaba.himarket.dto.params.chat.CreateChatSessionParam;
import com.alibaba.himarket.dto.params.chat.UpdateChatSessionParam;
import com.alibaba.himarket.dto.result.chat.ChatSessionResult;
import com.alibaba.himarket.dto.result.chat.ConversationResult_V1;
import com.alibaba.himarket.dto.result.chat.ProductConversationResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.service.ChatSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
@Validated
@Slf4j
@AdminOrDeveloperAuth
public class SessionController {

    private final ChatSessionService sessionService;

    @PostMapping
    public ChatSessionResult createSession(@Valid @RequestBody CreateChatSessionParam param) {
        return sessionService.createSession(param);
    }

    @GetMapping
    public PageResult<ChatSessionResult> listSessions(Pageable pageable) {
        return sessionService.listSessions(pageable);
    }

    @PatchMapping("/{sessionId}")
    public ChatSessionResult updateSession(
            @PathVariable String sessionId, @Valid @RequestBody UpdateChatSessionParam param) {
        return sessionService.updateSession(sessionId, param);
    }

    @DeleteMapping("/{sessionId}")
    public void deleteSession(@PathVariable String sessionId) {
        sessionService.deleteSession(sessionId);
    }

    @GetMapping("/{sessionId}/conversations")
    public List<ConversationResult_V1> listConversations(@PathVariable @NotBlank String sessionId) {
        return sessionService.listConversations(sessionId);
    }

    @GetMapping("/{sessionId}/conversations/v2")
    public List<ProductConversationResult> listConversationsByProduct(
            @PathVariable @NotBlank String sessionId) {
        return sessionService.listConversationsV2(sessionId);
    }
}
