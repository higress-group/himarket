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
package com.alibaba.himarket.service.hichat.service.dashscope;

import cn.hutool.core.util.BooleanUtil;
import io.agentscope.core.formatter.dashscope.dto.DashScopeMessage;
import io.agentscope.core.formatter.dashscope.dto.DashScopeRequest;
import io.agentscope.core.formatter.dashscope.dto.DashScopeResponse;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.DashScopeHttpClient;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ModelUtils;
import io.agentscope.core.model.ToolChoice;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.model.transport.HttpTransport;
import io.agentscope.core.model.transport.HttpTransportFactory;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * DashScope chat model for image generation.
 *
 * <p>This class is specifically designed for DashScope text-to-image models (Qwen/Wanx series).
 * Unlike standard text models, image generation models require:
 * <ul>
 *   <li>Multimodal API endpoint ({@code /multimodal-generation/generation})
 *   <li>Specialized response parsing to extract image URLs from {@code content} field
 *   <li>Model name validation against supported image models whitelist
 * </ul>
 *
 * <p>For text-only models, use {@code io.agentscope.core.model.DashScopeChatModel} instead.
 *
 * @see DashScopeImageFormatter
 */
@Slf4j
public class DashScopeImageChatModel extends ChatModelBase {

    private static final Set<String> SUPPORTED_MODELS =
            Set.of(
                    // Qwen Text-to-Image series
                    "z-image-turbo",
                    "qwen-image-max",
                    "qwen-image-max-2025-12-30",
                    "qwen-image-plus",
                    "qwen-image-plus-2026-01-09",
                    "qwen-image",
                    // Wanx v2 series
                    "wan2.6-t2i",
                    "wan2.5-t2i-preview",
                    "wan2.2-t2i-flash",
                    "wan2.2-t2i-plus",
                    "wanx2.1-t2i-turbo",
                    "wanx2.1-t2i-plus",
                    "wanx2.0-t2i-turbo");

    private final String modelName;
    private final Boolean enableSearch; // nullable
    private final GenerateOptions defaultOptions;
    private final DashScopeImageFormatter formatter;
    private final DashScopeHttpClient httpClient;

    /**
     * Create a new DashScope image generation model.
     *
     * @param apiKey         DashScope API key
     * @param modelName      model name (must be in the supported image models list)
     * @param enableSearch   whether search enhancement should be enabled
     * @param defaultOptions default generation options
     * @param baseUrl        full API URL
     * @param httpTransport  optional HTTP transport
     */
    public DashScopeImageChatModel(
            String apiKey,
            String modelName,
            Boolean enableSearch,
            GenerateOptions defaultOptions,
            String baseUrl,
            HttpTransport httpTransport) {

        if (!SUPPORTED_MODELS.contains(modelName)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Model '%s' is not a supported image generation model. "
                                    + "Supported models: %s. "
                                    + "Please use DashScopeChatModel for text-only models.",
                            modelName, getSupportedModels()));
        }

        this.modelName = modelName;
        this.enableSearch = enableSearch;
        this.defaultOptions =
                defaultOptions != null ? defaultOptions : GenerateOptions.builder().build();

        // Image generation will use DashScopeImageFormatter
        this.formatter = new DashScopeImageFormatter();

        // Use ImageHttpClient with full URL from baseUrl
        HttpTransport transport =
                httpTransport != null ? httpTransport : HttpTransportFactory.getDefault();
        this.httpClient = new DashScopeImageHttpClient(transport, apiKey, baseUrl);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    protected Flux<ChatResponse> doStream(
            List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        Flux<ChatResponse> responseFlux = streamWithHttpClient(messages, tools, options);

        return ModelUtils.applyTimeoutAndRetry(
                responseFlux, options, defaultOptions, modelName, "dashscope");
    }

    private Flux<ChatResponse> streamWithHttpClient(
            List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        Instant start = Instant.now();

        GenerateOptions effectiveOptions = options != null ? options : defaultOptions;
        ToolChoice toolChoice = effectiveOptions.getToolChoice();

        // Use multimodal format for image generation
        List<DashScopeMessage> dashScopeMessages = formatter.formatMultiModal(messages);

        // Build request
        DashScopeRequest request =
                formatter.buildRequest(
                        modelName,
                        dashScopeMessages,
                        true,
                        options,
                        defaultOptions,
                        tools,
                        toolChoice);

        if (BooleanUtil.isTrue(enableSearch)) {
            request.getParameters().setEnableSearch(true);
        }

        return Flux.defer(
                () -> {
                    try {
                        DashScopeResponse response =
                                httpClient.call(
                                        request,
                                        effectiveOptions.getAdditionalHeaders(),
                                        effectiveOptions.getAdditionalBodyParams(),
                                        effectiveOptions.getAdditionalQueryParams());
                        log.debug("Received HTTP response: {}", response);
                        ChatResponse chatResponse = formatter.parseResponse(response, start);
                        return Flux.just(chatResponse);
                    } catch (Exception e) {
                        log.error(
                                "DashScope image generation HTTP client error: {}",
                                e.getMessage(),
                                e);
                        return Flux.error(
                                new RuntimeException(
                                        "DashScope image generation API call failed: "
                                                + e.getMessage(),
                                        e));
                    }
                });
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    /**
     * Get the set of supported image generation models.
     *
     * @return unmodifiable set of supported model names
     */
    public Set<String> getSupportedModels() {
        return SUPPORTED_MODELS;
    }

    /**
     * Custom HTTP client for image generation models.
     * Returns empty endpoint to use the full URL from baseUrl directly.
     */
    public static class DashScopeImageHttpClient extends DashScopeHttpClient {

        public DashScopeImageHttpClient(HttpTransport transport, String apiKey, String baseUrl) {
            super(transport, apiKey, baseUrl);
        }

        /**
         * Return empty endpoint to use the full URL from baseUrl directly.
         *
         * @param modelName the model name (ignored)
         * @return empty string to use baseUrl as-is
         */
        @Override
        public String selectEndpoint(String modelName) {
            return "";
        }
    }

    public static class Builder {
        private String apiKey;
        private String modelName;
        private Boolean enableSearch;
        private GenerateOptions defaultOptions = null;
        private String baseUrl;
        private HttpTransport httpTransport;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Enable or disable search enhancement for image generation.
         *
         * <p>When enabled, the model can leverage internet search to enhance
         * image generation with up-to-date visual references and information.
         *
         * @param enableSearch true to enable search mode, false to disable, null for default (disabled)
         * @return this builder instance
         */
        public Builder enableSearch(Boolean enableSearch) {
            this.enableSearch = enableSearch;
            return this;
        }

        public Builder defaultOptions(GenerateOptions options) {
            this.defaultOptions = options;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder httpTransport(HttpTransport httpTransport) {
            this.httpTransport = httpTransport;
            return this;
        }

        /**
         * Build the DashScopeImageChatModel instance.
         *
         * @return configured image generation model
         * @throws IllegalArgumentException if model name is not in supported models list
         */
        public DashScopeImageChatModel build() {
            GenerateOptions effectiveOptions =
                    ModelUtils.ensureDefaultExecutionConfig(defaultOptions);

            return new DashScopeImageChatModel(
                    apiKey, modelName, enableSearch, effectiveOptions, baseUrl, httpTransport);
        }
    }
}
