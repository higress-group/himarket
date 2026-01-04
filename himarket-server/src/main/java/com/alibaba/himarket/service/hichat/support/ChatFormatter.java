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
package com.alibaba.himarket.service.hichat.support;

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.support.chat.ChatUsage;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolUseBlock;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class ChatFormatter {

    public Flux<ChatEvent> format(Event event, ChatContext chatContext) {
        try {
            Msg msg = event.getMessage();
            EventType type = event.getType();

            log.debug("Converting event - type: {}, isLast: {}", type, event.isLast());

            switch (type) {
                case REASONING:
                    return handleReasoning(msg, event.isLast(), chatContext);

                case TOOL_RESULT:
                    return handleToolResult(msg, chatContext);

                case SUMMARY:
                    return handleSummary(msg, chatContext);

                case HINT:
                case AGENT_RESULT:
                    // Skip internal events (RAG context, duplicate final result)
                    log.debug("Skipping {} event (internal)", type);
                    return Flux.empty();

                default:
                    log.debug("Skipping unknown event type: {}", type);
                    return Flux.empty();
            }

        } catch (Exception e) {
            log.error("Error converting event to ChatEvent", e);
            return Flux.just(
                    ChatEvent.error(chatContext.getChatId(), "CONVERSION_ERROR", e.getMessage()));
        }
    }

    private Flux<ChatEvent> handleReasoning(Msg msg, boolean isLast, ChatContext chatContext) {
        List<ChatEvent> chunks = new ArrayList<>();
        String chatId = chatContext.getChatId();

        // Extract thinking content
        List<ThinkingBlock> thinkingBlocks = msg.getContentBlocks(ThinkingBlock.class);
        for (ThinkingBlock thinking : thinkingBlocks) {
            if (StrUtil.isNotBlank(thinking.getThinking())) {
                chunks.add(ChatEvent.thinking(chatId, thinking.getThinking()));
            }
        }

        // Extract text content (model's response)
        String textContent = msg.getTextContent();
        if (StrUtil.isNotBlank(textContent)) {
            if (isLast) {
                // Skip final complete text to avoid duplication and large data transfer
                String preview =
                        textContent.length() > 20
                                ? textContent.substring(0, 20) + "..."
                                : textContent;
                log.debug(
                        "Skipping final REASONING text (isLast=true, length={}): {}",
                        textContent.length(),
                        preview);
            } else {
                // Send incremental chunks
                chunks.add(ChatEvent.text(chatId, textContent));
            }
        }

        // Extract tool calls (tool invocation requests)
        List<ToolUseBlock> toolUseBlocks = msg.getContentBlocks(ToolUseBlock.class);
        for (ToolUseBlock toolUse : toolUseBlocks) {
            // Query tool metadata from mapping
            ToolMeta toolMeta = chatContext.getToolMeta(toolUse.getName());
            String mcpServerName = toolMeta != null ? toolMeta.getMcpServerName() : null;

            ChatEvent.ToolCallContent tc =
                    ChatEvent.ToolCallContent.builder()
                            .id(toolUse.getId())
                            .name(toolUse.getName())
                            .arguments(toolUse.getInput())
                            .mcpServerName(mcpServerName)
                            .build();
            chunks.add(ChatEvent.toolCall(chatId, tc));
        }

        // Extract usage from the last REASONING event and save to context
        if (msg.getChatUsage() != null) {
            io.agentscope.core.model.ChatUsage chatUsage = msg.getChatUsage();
            ChatUsage usage =
                    ChatUsage.builder()
                            .inputTokens(chatUsage.getInputTokens())
                            .outputTokens(chatUsage.getOutputTokens())
                            .totalTokens(chatUsage.getTotalTokens())
                            .firstByteTimeout(chatContext.getFirstByteTimeout())
                            // elapsedTime will be set by ChatContext.stop()
                            .build();

            chatContext.setUsage(usage);
            log.debug("Extracted and saved usage to context from final REASONING: {}", usage);
        }

        return Flux.fromIterable(chunks);
    }

    private Flux<ChatEvent> handleToolResult(Msg msg, ChatContext chatContext) {
        List<ChatEvent> chunks = new ArrayList<>();
        String chatId = chatContext.getChatId();

        List<ToolResultBlock> toolResults = msg.getContentBlocks(ToolResultBlock.class);
        for (ToolResultBlock toolResult : toolResults) {
            ChatEvent.ToolResultContent tr =
                    ChatEvent.ToolResultContent.builder()
                            .id(toolResult.getId())
                            .name(toolResult.getName())
                            .result(toolResult.getOutput())
                            .build();
            chunks.add(ChatEvent.toolResult(chatId, tr));
        }

        return Flux.fromIterable(chunks);
    }

    private Flux<ChatEvent> handleSummary(Msg msg, ChatContext chatContext) {
        if (msg != null && StrUtil.isNotBlank(msg.getTextContent())) {
            return Flux.just(ChatEvent.text(chatContext.getChatId(), msg.getTextContent()));
        }
        return Flux.empty();
    }
}
