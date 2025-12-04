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
package com.alibaba.apiopenplatform.dto.params.chat;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.apiopenplatform.service.impl.McpClientHolder;
import com.alibaba.apiopenplatform.support.chat.ChatUsage;
import com.google.common.base.Stopwatch;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author shihan
 * @version : ChatContext, v0.1 2025年11月26日 15:49 shihan Exp $
 */
@Data
@Builder
@Slf4j
public class ChatContext {

    private String chatId;

    @Builder.Default
    private StringBuilder answerContent = new StringBuilder();

    @Builder.Default
    Stopwatch stopWatch = Stopwatch.createUnstarted();;

    private Long firstByteTimeout;

    private ChatUsage chatUsage;

    @Builder.Default
    private Boolean success = true;

    private ChatOptions chatOptions;

    private ChatClient chatClient;

    private ToolContext toolContext;

    private List<McpClientHolder> mcpClientHolders;

    public void start() {
        stopWatch.start();
    }

    public void recordFirstByteTimeout() {
        if (firstByteTimeout == null) {
            firstByteTimeout = stopWatch.elapsed(TimeUnit.MILLISECONDS);;
        }
    }

    public void stop() {
        if (stopWatch.isRunning()) {
            stopWatch.stop();
        }

        if (chatUsage != null) {
            chatUsage.setElapsedTime(stopWatch.elapsed(TimeUnit.MILLISECONDS));
            if (firstByteTimeout != null) {
                chatUsage.setFirstByteTimeout(firstByteTimeout);
            }
        }
    }

    public void appendAnswer(String content) {
        answerContent.append(content);
    }

    public void close() {
        if (CollUtil.isNotEmpty(mcpClientHolders)) {
            mcpClientHolders.forEach(mcpClientHolder -> {
                try {
                    mcpClientHolder.close();
                } catch (IOException e) {
                    log.warn("Close mcp client error", e);
                }
            });
        }
    }
}


