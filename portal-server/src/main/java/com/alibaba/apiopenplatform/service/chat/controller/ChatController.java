package com.alibaba.apiopenplatform.service.chat.controller;

import com.alibaba.apiopenplatform.service.chat.dto.*;
import com.alibaba.apiopenplatform.service.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
@Validated
@Slf4j
public class ChatController {

    private final ChatService chatService;

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody CreateChatParam param,
                           HttpServletResponse response) {
        return chatService.chat(param, response);
    }
}
