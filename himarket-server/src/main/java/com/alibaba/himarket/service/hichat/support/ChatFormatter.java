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
import cn.hutool.json.JSONUtil;
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

/**
 * Formats AgentScope events into HiChat events for frontend streaming.
 *
 * <p>Event flow:
 * <pre>
 * 1. REASONING (isLast: false) - streaming chunks (thinking, text, tool call fragments)
 * 2. REASONING (isLast: true)  - final complete message (tool calls with parsed args)
 * 3. TOOL_RESULT               - tool execution results
 * 4. SUMMARY                   - max iterations reached (with usage)
 * 5. AGENT_RESULT              - final result (with usage)
 * </pre>
 */
@Slf4j
public class ChatFormatter {

    public Flux<ChatEvent> format(Event event, ChatContext chatContext) {
        try {
            Msg msg = event.getMessage();
            EventType type = event.getType();

            log.debug(
                    "Converting event - type: {}, isLast: {}, msg: {}",
                    type,
                    event.isLast(),
                    JSONUtil.toJsonStr(msg));

            switch (type) {
                case REASONING:
                    return handleReasoning(msg, event.isLast(), chatContext);

                case TOOL_RESULT:
                    return handleToolResult(msg, chatContext);

                case SUMMARY:
                    return handleSummary(msg, chatContext);

                case AGENT_RESULT:
                    return handleAgentResult(msg, chatContext);

                case HINT:
                    // Skip internal events (RAG context)
                    log.debug("Skipping HINT event (internal)");
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

        // 1. Extract thinking content
        List<ThinkingBlock> thinkingBlocks = msg.getContentBlocks(ThinkingBlock.class);
        for (ThinkingBlock thinking : thinkingBlocks) {
            if (StrUtil.isNotBlank(thinking.getThinking())) {
                chunks.add(ChatEvent.thinking(chatId, thinking.getThinking()));
            }
        }

        // 2. Extract text content (model's response)
        String textContent = msg.getTextContent();
        if (StrUtil.isNotBlank(textContent)) {
            if (isLast) {
                getUsage(msg, chatContext);
                // Skip final complete text to avoid duplication
                log.debug(
                        "Skipping final complete text (isLast=true, length={})",
                        textContent.length());
            } else {
                // Send incremental chunks for streaming
                chunks.add(ChatEvent.text(chatId, textContent));
            }
        }

        // 3. Extract tool calls (only send when isLast=true)
        //
        // Streaming phase (isLast=false):
        //   - input is EMPTY {}, parameters are in content as JSON fragments
        //   - Skip: no useful data to send
        //
        // Final phase (isLast=true):
        //   - input contains COMPLETE PARSED arguments (accumulated by AgentScope)
        //   - Send: complete tool call with full parameters
        //
        List<ToolUseBlock> toolUseBlocks = msg.getContentBlocks(ToolUseBlock.class);
        if (!toolUseBlocks.isEmpty()) {
            if (isLast) {
                // Final complete tool calls with parsed input arguments
                log.debug("Sending {} complete tool call(s)", toolUseBlocks.size());
                for (ToolUseBlock toolUse : toolUseBlocks) {
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
            } else {
                // Skip streaming tool call chunks (input is empty, contains fragments)
                log.debug("Skipping {} streaming tool call chunk(s)", toolUseBlocks.size());
            }
        }

        return Flux.fromIterable(chunks);
    }

    private Flux<ChatEvent> handleToolResult(Msg msg, ChatContext chatContext) {
        List<ChatEvent> chunks = new ArrayList<>();
        String chatId = chatContext.getChatId();

        // Extract and send all tool execution results
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
        // Get usage from SUMMARY (emitted when max iterations reached)
        getUsage(msg, chatContext);

        // Send summary text if available
        if (msg != null && StrUtil.isNotBlank(msg.getTextContent())) {
            return Flux.just(ChatEvent.text(chatContext.getChatId(), msg.getTextContent()));
        }
        return Flux.empty();
    }

    private Flux<ChatEvent> handleAgentResult(Msg msg, ChatContext chatContext) {
        // Get usage from AGENT_RESULT (contains final complete result and usage)
        getUsage(msg, chatContext);

        // Skip this event - complete content already sent via streaming chunks
        log.debug("Skipping AGENT_RESULT event (usage captured)");
        return Flux.empty();
    }

    /**
     * Get usage information from message and saves to context.
     *
     * <p>Usage is only available in certain events:
     * <ul>
     *   <li>SUMMARY - when max iterations reached</li>
     *   <li>AGENT_RESULT - final complete result</li>
     * </ul>
     *
     * @param msg message containing usage info
     * @param chatContext context to save usage to
     */
    private void getUsage(Msg msg, ChatContext chatContext) {
        if (msg == null || msg.getChatUsage() == null) {
            return;
        }

        // Only capture once (first occurrence wins)
        if (chatContext.getUsage() != null) {
            return;
        }

        io.agentscope.core.model.ChatUsage chatUsage = msg.getChatUsage();
        ChatUsage usage =
                ChatUsage.builder()
                        .inputTokens(chatUsage.getInputTokens())
                        .outputTokens(chatUsage.getOutputTokens())
                        .totalTokens(chatUsage.getTotalTokens())
                        // firstByteTimeout and elapsedTime will be set by ChatContext.stop()
                        .build();

        chatContext.setUsage(usage);
        log.debug(
                "Get usage: input={}, output={}, total={}",
                usage.getInputTokens(),
                usage.getOutputTokens(),
                usage.getTotalTokens());
    }
}
