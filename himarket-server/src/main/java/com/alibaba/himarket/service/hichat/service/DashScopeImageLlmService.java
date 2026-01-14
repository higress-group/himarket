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
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.exception.ChatError;
import com.alibaba.himarket.dto.result.chat.LlmInvokeResult;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.service.GatewayService;
import com.alibaba.himarket.service.hichat.manager.ChatBotManager;
import com.alibaba.himarket.service.hichat.service.dashscope.DashScopeImageChatModel;
import com.alibaba.himarket.service.hichat.support.ChatContext;
import com.alibaba.himarket.service.hichat.support.ChatEvent;
import com.alibaba.himarket.service.hichat.support.InvokeModelParam;
import com.alibaba.himarket.service.hichat.support.LlmChatRequest;
import com.alibaba.himarket.support.chat.ChatUsage;
import com.alibaba.himarket.support.enums.AIProtocol;
import com.alibaba.himarket.support.product.ModelFeature;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.URLSource;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@Slf4j
public class DashScopeImageLlmService extends AbstractLlmService {

    public DashScopeImageLlmService(GatewayService gatewayService, ChatBotManager chatBotManager) {
        super(gatewayService, chatBotManager);
    }

    @Override
    public Flux<ChatEvent> invokeLlm(
            InvokeModelParam param, Consumer<LlmInvokeResult> resultHandler) {

        // Create context to collect answer and usage
        ChatContext chatContext = new ChatContext(param.getChatId());

        try {
            LlmChatRequest request = composeRequest(param);
            Model chatModel = newChatModel(request);
            Msg userMsg = param.getUserMessage();

            // Start estimate time and collect answer
            chatContext.start();
            return Flux.concat(
                            // Emit START event
                            Flux.just(ChatEvent.start(param.getChatId())),

                            // Stream image generation events with error handling
                            applyErrorHandling(
                                    chatModel.stream(List.of(userMsg), null, null)
                                            .next()
                                            .flatMapMany(
                                                    response ->
                                                            convertToChatEvents(
                                                                    response, chatContext))
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
            log.error(
                    "Failed to process image generation request for chatId: {}",
                    param.getChatId(),
                    e);
            ChatError chatError = ChatError.from(e);
            chatContext.fail();
            chatContext.appendAnswer("[Image generation failed: " + e.getMessage() + "]");
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
                            log.warn("Image generation was canceled by client, chatId: {}", chatId);
                            chatContext.fail();
                        })
                .doOnError(
                        error -> {
                            log.error(
                                    "Image generation stream encountered error, chatId: {}",
                                    chatId,
                                    error);
                            chatContext.fail();
                            chatContext.appendAnswer(
                                    "\n[Image generation error: " + error.getMessage() + "]");
                        })
                .onErrorResume(
                        error -> {
                            ChatError chatError = ChatError.from(error);
                            log.error(
                                    "Image generation failed, chatId: {}, errorType: {}",
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

    @Override
    protected LlmChatRequest composeRequest(InvokeModelParam param) {
        LlmChatRequest request = super.composeRequest(param);
        ProductResult product = param.getProduct();

        String generationPath = "/api/v1/services/aigc/multimodal-generation/generation";

        // Set base URL if configured
        if (product.getModelConfig() != null) {
            URI uri =
                    buildUri(
                            product.getModelConfig(),
                            request.getGatewayUris(),
                            generationPath,
                            path -> path);

            request.setUri(uri);
        }

        Map<String, Object> bodyParams = request.getBodyParams();
        if (bodyParams != null && !bodyParams.containsKey("parameters")) {
            // Add default parameters for image generation if not provided
            Map<String, Object> defaultParams =
                    MapUtil.<String, Object>builder()
                            .put("n", 1)
                            .put("prompt_extend", true)
                            .put("watermark", false)
                            .build();
            bodyParams.put("parameters", defaultParams);

            log.debug("Added default parameters for image generation: {}", defaultParams);
        }

        return request;
    }

    @Override
    public Model newChatModel(LlmChatRequest request) {
        // Build GenerateOptions with additional parameters
        GenerateOptions.Builder optionsBuilder =
                GenerateOptions.builder()
                        .additionalHeaders(request.getHeaders())
                        .additionalQueryParams(request.getQueryParams())
                        .additionalBodyParams(request.getBodyParams())
                        .stream(true);

        GenerateOptions options = optionsBuilder.build();

        ModelFeature modelFeature = getOrDefaultModelFeature(request.getProduct());
        String modelName = modelFeature.getModel();
        log.info("Creating DashScopeImageChatModel for image model '{}'", modelName);

        String baseUrl = request.getUri() != null ? request.getUri().toString() : null;

        return DashScopeImageChatModel.builder()
                .apiKey(request.getApiKey())
                .modelName(modelName)
                .defaultOptions(options)
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public List<AIProtocol> getProtocols() {
        return List.of(AIProtocol.DASHSCOPE_IMAGE);
    }

    /**
     * Convert ChatResponse to ChatEvents stream.
     *
     * @param response    ChatResponse containing content and usage
     * @param chatContext Chat context to collect usage and get chat ID
     * @return Flux of ChatEvents
     */
    private Flux<ChatEvent> convertToChatEvents(ChatResponse response, ChatContext chatContext) {

        String chatId = chatContext.getChatId();

        // Extract usage from response and set to context (similar to ChatFormatter.getUsage())
        if (response.getUsage() != null) {
            io.agentscope.core.model.ChatUsage u = response.getUsage();
            ChatUsage usage =
                    ChatUsage.builder()
                            .inputTokens(u.getInputTokens())
                            .outputTokens(u.getOutputTokens())
                            .totalTokens(u.getTotalTokens())
                            .build();

            chatContext.setUsage(usage);
        }

        // Process content (text and images)
        List<ContentBlock> content = response.getContent();
        if (CollUtil.isEmpty(content)) {
            return Flux.just(ChatEvent.text(chatId, "[No content generated]"));
        }

        return Flux.fromIterable(content)
                .flatMap(
                        block -> {
                            if (block instanceof TextBlock textBlock) {
                                // Text content
                                return Flux.just(ChatEvent.text(chatId, textBlock.getText()));
                            } else if (block instanceof ImageBlock imageBlock) {
                                // Image URL - send as markdown format
                                String imageUrl = ((URLSource) imageBlock.getSource()).getUrl();
                                String markdown = String.format("![Generated Image](%s)", imageUrl);
                                log.info("Generated image URL for chatId {}: {}", chatId, imageUrl);
                                return Flux.just(ChatEvent.text(chatId, markdown));
                            } else {
                                log.warn(
                                        "Unsupported content block type: {}",
                                        block.getClass().getName());
                                return Flux.empty();
                            }
                        });
    }
}
