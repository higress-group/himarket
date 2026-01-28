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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.exception.ChatError;
import com.alibaba.himarket.core.utils.CacheUtil;
import com.alibaba.himarket.dto.result.chat.LlmInvokeResult;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.model.ModelConfigResult;
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
import java.util.function.Function;
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
     * Build URI from model config with flexible path matching.
     *
     * @param modelConfig    model API configuration
     * @param gatewayUris    fallback gateway URIs
     * @param routeKeyword   keyword for route matching (e.g., "/multimodal-generation", "/chat/completions")
     * @param pathProcessor  function to process the matched path (e.g., strip suffix, keep as-is)
     * @return constructed URI, or null if failed
     */
    protected URI buildUri(
            ModelConfigResult modelConfig,
            List<URI> gatewayUris,
            String routeKeyword,
            Function<String, String> pathProcessor) {

        ModelConfigResult.ModelAPIConfig modelAPIConfig = modelConfig.getModelAPIConfig();
        if (modelAPIConfig == null || CollUtil.isEmpty(modelAPIConfig.getRoutes())) {
            log.error("Failed to build URI: model API config is null or contains no routes");
            return null;
        }

        // Find matching route by keyword
        com.alibaba.himarket.dto.result.httpapi.HttpRouteResult route =
                modelAPIConfig.getRoutes().stream()
                        .filter(
                                r ->
                                        java.util.Optional.ofNullable(r.getMatch())
                                                .map(
                                                        com.alibaba.himarket.dto.result.httpapi
                                                                        .HttpRouteResult
                                                                        .RouteMatchResult
                                                                ::getPath)
                                                .map(
                                                        com.alibaba.himarket.dto.result.httpapi
                                                                        .HttpRouteResult
                                                                        .RouteMatchPath
                                                                ::getValue)
                                                .filter(path -> path.contains(routeKeyword))
                                                .isPresent())
                        .findFirst()
                        .orElseGet(() -> modelAPIConfig.getRoutes().get(0));

        // Get and process path
        String path =
                java.util.Optional.ofNullable(route.getMatch())
                        .map(
                                com.alibaba.himarket.dto.result.httpapi.HttpRouteResult
                                                .RouteMatchResult
                                        ::getPath)
                        .map(
                                com.alibaba.himarket.dto.result.httpapi.HttpRouteResult
                                                .RouteMatchPath
                                        ::getValue)
                        .map(pathProcessor) // Apply path processor
                        .orElse(routeKeyword);

        org.springframework.web.util.UriComponentsBuilder builder =
                org.springframework.web.util.UriComponentsBuilder.newInstance();

        // Try to get public domain first, fallback to first domain
        com.alibaba.himarket.dto.result.common.DomainResult domain =
                route.getDomains().stream()
                        .filter(d -> !StrUtil.equalsIgnoreCase(d.getNetworkType(), "intranet"))
                        .findFirst()
                        .orElseGet(
                                () ->
                                        ObjectUtil.isNotEmpty(route.getDomains())
                                                ? route.getDomains().get(0)
                                                : null);

        if (domain != null) {
            String protocol =
                    StrUtil.isNotBlank(domain.getProtocol())
                            ? domain.getProtocol().toLowerCase()
                            : "http";
            builder.scheme(protocol).host(domain.getDomain());
            if (domain.getPort() != null && domain.getPort() > 0) {
                builder.port(domain.getPort());
            }
        } else if (ObjectUtil.isNotEmpty(gatewayUris)) {
            URI uri = gatewayUris.get(0);
            builder.scheme(uri.getScheme() != null ? uri.getScheme() : "http").host(uri.getHost());
            if (uri.getPort() != -1) {
                builder.port(uri.getPort());
            }
        } else {
            log.error("Failed to build URI: no valid domain found and no gateway URIs provided");
            return null;
        }

        builder.path(path);
        URI uri = builder.build().toUri();
        log.debug("Successfully built URI: {}", uri);
        return uri;
    }

    /**
     * Create a protocol-specific chat model instance
     *
     * @param request request containing model config, credentials, and parameters
     * @return model instance (e.g. DashScopeChatModel, OpenAIChatModel)
     */
    abstract Model newChatModel(LlmChatRequest request);
}
