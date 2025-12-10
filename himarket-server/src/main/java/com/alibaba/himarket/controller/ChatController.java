package com.alibaba.himarket.controller;

import com.alibaba.himarket.core.annotation.AdminOrDeveloperAuth;
import com.alibaba.himarket.dto.result.chat.ChatAnswerMessage;
import com.alibaba.himarket.service.ChatService;
import com.alibaba.himarket.dto.params.chat.CreateChatParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/chats")
@RequiredArgsConstructor
@Validated
@Slf4j
@AdminOrDeveloperAuth
public class ChatController {

    private final ChatService chatService;

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatAnswerMessage> chat(@Valid @RequestBody CreateChatParam param,
                                        HttpServletResponse response) {
        return chatService.chat(param, response);
    }
}
