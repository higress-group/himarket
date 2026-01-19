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

import com.alibaba.himarket.support.chat.ChatUsage;
import lombok.Builder;
import lombok.Data;

/**
 * Unified streaming chunk structure for chat responses.
 *
 * <p>Structure:
 * <pre>
 * {
 *   "chatId": "chat-id",
 *   "type": "assistant|thinking|tool_call|tool_result|done|error",
 *   "content": ...,  // main content (varies by type)
 *   "usage": {...},  // token usage (optional)
 *   "error": "...",  // error code (optional)
 *   "message": "..." // error message (optional)
 * }
 * </pre>
 */
@Data
@Builder
public class ChatEvent {

    /**
     * Chat ID
     */
    private String chatId;

    /**
     * Chunk type
     */
    private EventType type;

    /**
     * Main content (type varies by chunk type)
     */
    private Object content;

    /**
     * Token usage statistics (optional, mainly for DONE type)
     */
    private ChatUsage usage;

    /**
     * Error code (only for ERROR type)
     */
    private String error;

    /**
     * Error message (only for ERROR type)
     */
    private String message;

    /**
     * Chunk type enumeration.
     *
     * <p>Serialized as lowercase with underscores in JSON (e.g., "assistant", "tool_call").
     */
    public enum EventType {
        /**
         * Stream started
         */
        START,

        /**
         * Assistant response
         */
        ASSISTANT,

        /**
         * Thinking/reasoning process
         */
        THINKING,

        /**
         * Tool call initiated
         */
        TOOL_CALL,

        /**
         * Tool execution result
         */
        TOOL_RESULT,

        /**
         * Stream completed
         */
        DONE,

        /**
         * Error occurred
         */
        ERROR
    }

    /**
     * Create a start chunk (stream started)
     *
     * @param chatId Conversation ID
     */
    public static ChatEvent start(String chatId) {
        return ChatEvent.builder().chatId(chatId).type(EventType.START).build();
    }

    /**
     * Create an assistant response chunk
     *
     * @param chatId Conversation ID
     * @param text   Assistant response text
     */
    public static ChatEvent text(String chatId, String text) {
        return ChatEvent.builder().chatId(chatId).type(EventType.ASSISTANT).content(text).build();
    }

    /**
     * Create a thinking chunk
     *
     * @param chatId  Conversation ID
     * @param thought Thinking content
     */
    public static ChatEvent thinking(String chatId, String thought) {
        return ChatEvent.builder().chatId(chatId).type(EventType.THINKING).content(thought).build();
    }

    /**
     * Create a tool call chunk
     *
     * @param chatId   Conversation ID
     * @param toolCall Tool call details
     */
    public static ChatEvent toolCall(String chatId, ToolCallContent toolCall) {
        return ChatEvent.builder()
                .chatId(chatId)
                .type(EventType.TOOL_CALL)
                .content(toolCall)
                .build();
    }

    /**
     * Create a tool result chunk
     *
     * @param chatId     Conversation ID
     * @param toolResult Tool execution result
     */
    public static ChatEvent toolResult(String chatId, ToolResultContent toolResult) {
        return ChatEvent.builder()
                .chatId(chatId)
                .type(EventType.TOOL_RESULT)
                .content(toolResult)
                .build();
    }

    /**
     * Create a done chunk (stream completed)
     *
     * @param chatId Conversation ID
     * @param usage  Token usage information
     */
    public static ChatEvent done(String chatId, ChatUsage usage) {
        return ChatEvent.builder().chatId(chatId).type(EventType.DONE).usage(usage).build();
    }

    /**
     * Create an error chunk
     *
     * @param chatId       Conversation ID
     * @param code         Error code
     * @param errorMessage Error message
     */
    public static ChatEvent error(String chatId, String code, String errorMessage) {
        return ChatEvent.builder()
                .chatId(chatId)
                .type(EventType.ERROR)
                .error(code)
                .message(errorMessage)
                .build();
    }

    /**
     * Tool call content structure (used in content field when type=tool_call)
     */
    @Data
    @Builder
    public static class ToolCallContent {
        /**
         * Tool call ID
         */
        private String id;

        /**
         * Tool name
         */
        private String name;

        /**
         * Tool arguments (as JSON object)
         */
        private Object arguments;

        /**
         * MCP server name (optional, identifies which MCP server provides this tool)
         */
        private String mcpServerName;
    }

    /**
     * Tool result content structure (used in content field when type=tool_result)
     */
    @Data
    @Builder
    public static class ToolResultContent {
        /**
         * Tool call ID (matches the call)
         */
        private String id;

        /**
         * Tool name
         */
        private String name;

        /**
         * Tool execution result
         */
        private Object result;
    }
}
