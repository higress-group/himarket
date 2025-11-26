package com.alibaba.apiopenplatform.service.impl;

import cn.hutool.core.map.MapUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.dto.params.chat.ChatContent;
import com.alibaba.apiopenplatform.service.LlmService;
import com.alibaba.apiopenplatform.dto.params.chat.InvokeModelParam;
import com.alibaba.apiopenplatform.dto.result.chat.LlmChatRequest;
import com.alibaba.apiopenplatform.dto.result.chat.LlmInvokeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractLlmService implements LlmService {

    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();

    @Override
    public SseEmitter invokeLLM(InvokeModelParam param, HttpServletResponse response, Consumer<LlmInvokeResult> resultHandler) {
       // ResultHandler is mainly used to record answer and usage
        try {
            LlmChatRequest request = composeRequest(param);

            return call(request, response, resultHandler);
        } catch (Exception e) {
            log.error("Failed to invoke LLM", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    protected abstract LlmChatRequest composeRequest(InvokeModelParam param);

    private SseEmitter call(LlmChatRequest request, HttpServletResponse response, Consumer<LlmInvokeResult> resultHandler) {
        // Use SseEmitter for streaming
        SseEmitter emitter = new SseEmitter(-1L);

        request.tryResolveDns();
        log.info("zhaoh-test-request: {}", JSONUtil.toJsonStr(request));

        ChatContent chatContent = new ChatContent();
        chatContent.start();
        webClient
                .post()
                .uri(request.getUrl().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .headers(headers -> request.getHeaders().forEach(headers::add))
                .bodyValue(request.getBody())
                .exchangeToFlux(clientResponse -> {
                    // Set response headers which are from the gateway
                    HttpHeaders responseHeaders = clientResponse.headers().asHttpHeaders();

                    // Skip Transfer-Encoding header to avoid duplicated headers
                    responseHeaders.forEach((key, values) -> {
                        if (key != null && !key.equalsIgnoreCase("transfer-encoding")) {
                            values.forEach(value -> response.addHeader(key, value));
                        }
                    });

                    // Handle response body
                    return clientResponse.bodyToFlux(String.class)
                            .delayElements(Duration.ofMillis(100))
                            .handle((chunk, sink) -> {
                                // Record first token time
                                chatContent.recordFirstPackageTime();

                                String s = processStreamChunk(chunk, chatContent);
                                // Only send the chunk if there is no error and unexpected content is empty
                                if (chatContent.success()) {
                                    sink.next(s);
                                }
                            });
                })
//                .filter(Objects::nonNull)
                .doOnNext(chunk -> {
                    try {
                        emitter.send(SseEmitter.event().data(chunk));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .doOnComplete(() -> {

                    if (!chatContent.success()) {
                        sendError(emitter, chatContent.getUnexpectedContent().toString());
                    }
                    emitter.complete();
                    // Allow the caller to handle the result
                    resultHandler.accept(LlmInvokeResult.of(chatContent));
                })
                .doOnError(error -> {
                    // Handle the error
                    log.error("Model API call failed", error);
                    sendError(emitter, error.getMessage());
                    emitter.complete();
                    chatContent.setAnswerContent(chatContent.getAnswerContent().append(error.getMessage()));
                    resultHandler.accept(LlmInvokeResult.of(chatContent));
                }).subscribe();
        return emitter;
    }

    protected abstract String processStreamChunk(String chunk, ChatContent chatContent);

    private void sendError(SseEmitter emitter, String message) {
        try {
            Map<String, String> errEvent = MapUtil.<String, String>builder()
                    .put("status", "error")
                    .put("message", message)
                    .build();
            emitter.send(SseEmitter.event()
                    .data(JSONUtil.toJsonStr(errEvent)));
        } catch (IOException e) {
            log.error("Failed to send error event", e);
        }
    }

}