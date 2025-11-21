package com.alibaba.apiopenplatform.service.chat.service;

import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.service.chat.dto.InvokeModelParam;
import com.alibaba.apiopenplatform.service.chat.dto.LlmChatRequest;
import com.alibaba.apiopenplatform.service.chat.dto.LlmInvokeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public abstract class LlmService {

    protected final WebClient webClient;

    public SseEmitter invokeLLM(InvokeModelParam param, Consumer<LlmInvokeResult> resultHandler) {
        try {
            LlmChatRequest request = composeRequest(param);

            log.info("zhaoh-test-request: {}", JSONUtil.toJsonStr(request));

            return call(request, resultHandler);
        } catch (Exception e) {
            log.error("Failed to invoke LLM", e);
            return null;
        }
    }

    protected abstract LlmChatRequest composeRequest(InvokeModelParam param);

    private SseEmitter call(LlmChatRequest request, Consumer<LlmInvokeResult> resultHandler) {
        StringBuilder answer = new StringBuilder();
        // Use SseEmitter for streaming
        SseEmitter emitter = new SseEmitter(-1L);

        webClient
                .post()
                .uri(request.getUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .headers(headers -> request.getHeaders().forEach(headers::add))
                .bodyValue(request.getBody())
                .retrieve()
                .bodyToFlux(String.class)
                .delayElements(Duration.ofMillis(100))
                .map(chunk -> processStreamChunk(chunk, answer))
                .filter(Objects::nonNull)
                .doOnNext(chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .doOnComplete(() -> {
                    emitter.complete();
                    // Allow the caller to handle the result
                    resultHandler.accept(LlmInvokeResult.success(answer.toString()));
                })
                .doOnError(error -> {
                    // Handle the error
                    log.error("Model API call failed", error);
                    resultHandler.accept(LlmInvokeResult.failure(
                            error.getMessage(),
                            answer.toString()
                    ));
                }).subscribe();
        return emitter;
    }

    protected abstract String processStreamChunk(String chunk, StringBuilder answerBuilder);

}