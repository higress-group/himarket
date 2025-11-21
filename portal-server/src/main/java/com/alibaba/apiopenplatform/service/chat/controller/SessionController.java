package com.alibaba.apiopenplatform.service.chat.controller;

import com.alibaba.apiopenplatform.core.annotation.AdminOrDeveloperAuth;
import com.alibaba.apiopenplatform.core.response.Response;
import com.alibaba.apiopenplatform.dto.result.common.PageResult;
import com.alibaba.apiopenplatform.service.chat.dto.*;
import com.alibaba.apiopenplatform.service.chat.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * @author zh
 */
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
    public ChatSessionResult updateSession(@PathVariable String sessionId,
                                           @Valid @RequestBody UpdateChatSessionParam param) {
        return sessionService.updateSession(sessionId, param);
    }

    @DeleteMapping("/{sessionId}")
    public void deleteSession(@PathVariable String sessionId) {
        sessionService.deleteSession(sessionId);
    }

    /**
     * Get all conversations for a session
     */
    @GetMapping("/{sessionId}/conversations")
    public List<ConversationResult> getConversations(
            @PathVariable @NotBlank String sessionId) {
        return sessionService.getConversations(sessionId);
    }
}
