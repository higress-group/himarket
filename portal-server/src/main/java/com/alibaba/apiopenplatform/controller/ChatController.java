package com.alibaba.apiopenplatform.controller;

import com.alibaba.apiopenplatform.core.annotation.AdminOrDeveloperAuth;
import com.alibaba.apiopenplatform.dto.result.chat.ChatAnswerMessage;
import com.alibaba.apiopenplatform.service.ChatService;
import com.alibaba.apiopenplatform.dto.params.chat.CreateChatParam;
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
