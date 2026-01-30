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

import com.fasterxml.jackson.core.type.TypeReference;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.formatter.dashscope.DashScopeResponseParser;
import io.agentscope.core.formatter.dashscope.dto.DashScopeChoice;
import io.agentscope.core.formatter.dashscope.dto.DashScopeContentPart;
import io.agentscope.core.formatter.dashscope.dto.DashScopeMessage;
import io.agentscope.core.formatter.dashscope.dto.DashScopeOutput;
import io.agentscope.core.formatter.dashscope.dto.DashScopeResponse;
import io.agentscope.core.formatter.dashscope.dto.DashScopeUsage;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.URLSource;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.ChatUsage;
import io.agentscope.core.util.JsonUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * DashScope formatter for image generation models.
 *
 * <p>Extends {@link DashScopeChatFormatter} to parse responses from DashScope
 * multimodal APIs, which return image URLs alongside text content.
 */
@Slf4j
public class DashScopeImageFormatter extends DashScopeChatFormatter {

    private final ImageResponseParser imageResponseParser;

    public DashScopeImageFormatter() {
        super();
        this.imageResponseParser = new ImageResponseParser();
    }

    /**
     * Parse DashScope response using image-aware parser.
     *
     * @param result    DashScope API response
     * @param startTime Request start time
     * @return Parsed ChatResponse with ImageBlocks for images
     */
    @Override
    public ChatResponse parseResponse(DashScopeResponse result, Instant startTime) {
        return imageResponseParser.parseResponse(result, startTime);
    }

    /**
     * Response parser for image generation models.
     *
     * <p>Extends {@link DashScopeResponseParser} to handle image generation responses
     * which return image URLs in the {@code content} field alongside text content.
     */
    @Slf4j
    static class ImageResponseParser extends DashScopeResponseParser {

        /**
         * Parse DashScope response to ChatResponse, handling both text and image content.
         *
         * @param response  DashScope API response
         * @param startTime Request start time for calculating duration
         * @return Parsed ChatResponse with ImageBlocks for images and TextBlocks for text
         */
        @Override
        public ChatResponse parseResponse(DashScopeResponse response, Instant startTime) {
            List<ContentBlock> blocks = new ArrayList<>();
            String finishReason = null;

            DashScopeOutput output = response.getOutput();
            if (output != null) {
                DashScopeChoice choice = output.getFirstChoice();
                if (choice != null) {
                    DashScopeMessage message = choice.getMessage();
                    if (message != null) {
                        // Parse content - could be String or List<DashScopeContentPart>
                        Object content = message.getContent();
                        if (content instanceof String text) {
                            // Plain text content
                            if (!text.isEmpty()) {
                                blocks.add(TextBlock.builder().text(text).build());
                            }
                        } else if (content instanceof List) {
                            // Multimodal content (text + image)
                            List<DashScopeContentPart> contentParts =
                                    JsonUtils.getJsonCodec()
                                            .convertValue(content, new TypeReference<>() {});
                            if (contentParts != null) {
                                for (DashScopeContentPart part : contentParts) {
                                    // Extract text
                                    if (part.getText() != null && !part.getText().isEmpty()) {
                                        blocks.add(
                                                TextBlock.builder().text(part.getText()).build());
                                    }
                                    // Extract image
                                    if (part.getImage() != null && !part.getImage().isEmpty()) {
                                        log.debug("Extracted image URL: {}", part.getImage());
                                        blocks.add(
                                                ImageBlock.builder()
                                                        .source(
                                                                URLSource.builder()
                                                                        .url(part.getImage())
                                                                        .build())
                                                        .build());
                                    }
                                }
                            }
                        }
                    }
                    finishReason = choice.getFinishReason();
                }

                // Fallback to output-level finish reason
                if (finishReason == null) {
                    finishReason = output.getFinishReason();
                }
            }

            // Parse usage
            ChatUsage usage = null;
            DashScopeUsage u = response.getUsage();
            if (u != null) {
                usage =
                        ChatUsage.builder()
                                .inputTokens(u.getInputTokens() != null ? u.getInputTokens() : 0)
                                .outputTokens(u.getOutputTokens() != null ? u.getOutputTokens() : 0)
                                .time(
                                        Duration.between(startTime, Instant.now()).toMillis()
                                                / 1000.0)
                                .build();
            }

            return ChatResponse.builder()
                    .id(response.getRequestId())
                    .content(blocks)
                    .usage(usage)
                    .finishReason(finishReason)
                    .build();
        }
    }
}
