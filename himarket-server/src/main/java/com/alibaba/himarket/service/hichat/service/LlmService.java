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

import com.alibaba.himarket.dto.result.chat.LlmInvokeResult;
import com.alibaba.himarket.service.hichat.support.ChatEvent;
import com.alibaba.himarket.service.hichat.support.InvokeModelParam;
import com.alibaba.himarket.support.enums.AIProtocol;
import java.util.List;
import java.util.function.Consumer;
import reactor.core.publisher.Flux;

public interface LlmService {

    /**
     * Invoke LLM model for chat and return streaming events
     *
     * @param param         model invocation parameters
     * @param resultHandler callback to handle final result (usage, tokens, etc.)
     * @return flux of chat events (START, CONTENT, TOOL_CALL, END, etc.)
     */
    Flux<ChatEvent> invokeLlm(InvokeModelParam param, Consumer<LlmInvokeResult> resultHandler);

    /**
     * Get AI protocols supported by this service
     *
     * @return list of supported protocols (e.g. OPENAI, DASHSCOPE)
     */
    List<AIProtocol> getProtocols();

    /**
     * Check if this service supports the given protocol
     *
     * @param protocol protocol string to match (case-insensitive)
     * @return true if supported
     */
    boolean match(String protocol);
}
