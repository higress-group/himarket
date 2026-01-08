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
package com.alibaba.himarket.service.hichat.service;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.exception.ChatError;
import com.alibaba.himarket.core.utils.CacheUtil;
import com.alibaba.himarket.dto.result.chat.LlmInvokeResult;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.service.GatewayService;
import com.alibaba.himarket.service.hichat.manager.ChatBotManager;
import com.alibaba.himarket.service.hichat.support.*;
import com.alibaba.himarket.support.product.ModelFeature;
import com.alibaba.himarket.support.product.ProductFeature;
import com.github.benmanes.caffeine.cache.Cache;
import io.agentscope.core.model.Model;
import java.net.URI;
import java.util.*;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractLlmService implements LlmService {

    protected final GatewayService gatewayService;

    protected final ChatBotManager chatBotManager;

    private final Cache<String, List<URI>> gatewayUriCache = CacheUtil.newCache(5 * 60);

    @Override
    public Flux<ChatEvent> invokeLlm(
            InvokeModelParam param, Consumer<LlmInvokeResult> resultHandler) {

        // Create context to collect answer and usage
        ChatContext chatContext = new ChatContext(param.getChatId());

        try {
            LlmChatRequest request = composeRequest(param);
            //            request.tryResolveDns();

            Model chatModel = newChatModel(request);
            ChatBot chatBot = chatBotManager.getOrCreateChatBot(request, chatModel);
            chatContext.setToolMetas(chatBot.getToolMetas());

            ChatFormatter formatter = new ChatFormatter();

            // Start estimate time and collect answer
            chatContext.start();
            return Flux.concat(
                            // Emit START event
                            Flux.just(ChatEvent.start(param.getChatId())),

                            // Stream chat events with error handling
                            applyErrorHandling(
                                    chatBot.chat(param.getUserMessage())
                                            .flatMap(event -> formatter.format(event, chatContext))
                                            // Collect answer content
                                            .doOnNext(chatContext::collect),
                                    param.getChatId(),
                                    chatContext))
                    // Always emit DONE at the end
                    .concatWith(
                            Flux.defer(
                                    () -> {
                                        chatContext.stop();
                                        return Flux.just(
                                                ChatEvent.done(
                                                        param.getChatId(), chatContext.getUsage()));
                                    }))
                    // Unified result handling for all completion scenarios
                    .doFinally(signal -> resultHandler.accept(chatContext.toResult()));

        } catch (Exception e) {
            log.error("Failed to process chat request for chatId: {}", param.getChatId(), e);
            ChatError chatError = ChatError.from(e);
            chatContext.fail();
            chatContext.appendAnswer("[Sorry, something went wrong: " + e.getMessage() + "]");
            resultHandler.accept(chatContext.toResult());

            return Flux.just(
                    ChatEvent.start(param.getChatId()),
                    ChatEvent.error(
                            param.getChatId(),
                            chatError.name(),
                            StrUtil.blankToDefault(e.getMessage(), chatError.getDescription())),
                    ChatEvent.done(param.getChatId(), null));
        }
    }

    private Flux<ChatEvent> applyErrorHandling(
            Flux<ChatEvent> flux, String chatId, ChatContext chatContext) {
        return flux.doOnCancel(
                        () -> {
                            log.warn("Chat stream was canceled by client, chatId: {}", chatId);
                            chatContext.fail();
                        })
                .doOnError(
                        error -> {
                            log.error("Chat stream encountered error, chatId: {}", chatId, error);
                            chatContext.fail();
                            chatContext.appendAnswer(
                                    "\n[Sorry, an error occurred: " + error.getMessage() + "]");
                        })
                .onErrorResume(
                        error -> {
                            ChatError chatError = ChatError.from(error);
                            log.error(
                                    "Chat execution failed, chatId: {}, errorType: {}",
                                    chatId,
                                    chatError,
                                    error);

                            return Flux.just(
                                    ChatEvent.error(
                                            chatId,
                                            chatError.name(),
                                            StrUtil.blankToDefault(
                                                    error.getMessage(),
                                                    chatError.getDescription())));
                        });
    }

    protected LlmChatRequest composeRequest(InvokeModelParam param) {
        ProductResult product = param.getProduct();

        // Get gateway uris for model
        List<URI> gatewayUris =
                gatewayUriCache.get(param.getGatewayId(), gatewayService::fetchGatewayUris);
        CredentialContext credentialContext = param.getCredentialContext();

        return LlmChatRequest.builder()
                .chatId(param.getChatId())
                .sessionId(param.getSessionId())
                .product(product)
                .userMessages(param.getUserMessage())
                .historyMessages(param.getHistoryMessages())
                .apiKey(credentialContext.getApiKey())
                // Clone headers and query params
                .headers(credentialContext.copyHeaders())
                .queryParams(credentialContext.copyQueryParams())
                .gatewayUris(gatewayUris)
                .mcpConfigs(param.getMcpConfigs())
                .build();
    }

    protected ModelFeature getOrDefaultModelFeature(ProductResult product) {
        ModelFeature modelFeature =
                Optional.ofNullable(product)
                        .map(ProductResult::getFeature)
                        .map(ProductFeature::getModelFeature)
                        .orElseGet(() -> ModelFeature.builder().build());

        return ModelFeature.builder()
                .model(StrUtil.blankToDefault(modelFeature.getModel(), "qwen-max"))
                .maxTokens(ObjectUtil.defaultIfNull(modelFeature.getMaxTokens(), 5000))
                .temperature(ObjectUtil.defaultIfNull(modelFeature.getTemperature(), 0.9))
                .streaming(ObjectUtil.defaultIfNull(modelFeature.getStreaming(), true))
                .webSearch(ObjectUtil.defaultIfNull(modelFeature.getWebSearch(), false))
                .build();
    }

    @Override
    public boolean match(String protocol) {
        return getProtocols().stream()
                .anyMatch(p -> StrUtil.equalsIgnoreCase(p.getProtocol(), protocol));
    }

    /**
     * Create a protocol-specific chat model instance
     *
     * @param request request containing model config, credentials, and parameters
     * @return model instance (e.g. DashScopeChatModel, OpenAIChatModel)
     */
    abstract Model newChatModel(LlmChatRequest request);
}
