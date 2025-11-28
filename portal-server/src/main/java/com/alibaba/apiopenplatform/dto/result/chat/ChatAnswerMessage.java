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
package com.alibaba.apiopenplatform.dto.result.chat;

import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.dto.params.chat.McpToolMeta;
import com.alibaba.apiopenplatform.support.chat.ChatUsage;
import lombok.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

/**
 * @author shihan
 * @version : ChatAnswerMessage, v0.1 2025年11月26日 17:30 shihan Exp $
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAnswerMessage {
    private String      chatId;
    private MessageType msgType;
    private Object      content;
    private ChatUsage   chatUsage;
    private String      error;
    private String      message;

    public enum MessageType {
        USER,
        ANSWER,
        TOOL_CALL,
        TOOL_RESPONSE,
        STOP,
        ERROR,
    }

    @SuppressWarnings("unchecked")
    public <T> T convertContent() {
        if (msgType == MessageType.TOOL_CALL) {
            return (T) JSONUtil.toBean(JSONUtil.toJsonStr(content), ToolCall.class);
        } else if (msgType == MessageType.TOOL_RESPONSE) {
            return (T) JSONUtil.toBean(JSONUtil.toJsonStr(content), ToolResponse.class);
        }
        return (T) content;
    }

    public static ChatAnswerMessage ofError(String error, String message) {
        return ChatAnswerMessage.builder()
                .msgType(MessageType.ERROR)
                .error(error)
                .message(message)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall {
        private McpToolMeta toolMeta;
        private Object      inputSchema;
        // 根据ToolCall做json解析后的结果
        private Object      input;
        /**
         * 以下字段来自:
         * {@link org.springframework.ai.chat.messages.AssistantMessage.ToolCall}
         */
        private String      id;
        private String      type;
        private String      name;
        private String      arguments;

        public AssistantMessage.ToolCall toToolCall() {
            return new AssistantMessage.ToolCall(id, type, name, arguments);
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolResponse {
        private McpToolMeta toolMeta;
        // 根据ToolResponse的responseData做json解析后的结果
        private Object      output;
        private Long        costMillis;
        /**
         * 以下字段来自:
         * {@link org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse}
         */
        private String      id;
        private String      name;
        private String      responseData;

        public ToolResponseMessage.ToolResponse toolResponse() {
            return new ToolResponseMessage.ToolResponse(id, name, responseData);
        }
    }
}
