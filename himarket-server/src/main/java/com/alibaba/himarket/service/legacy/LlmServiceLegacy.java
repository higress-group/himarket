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

package com.alibaba.himarket.service.legacy;

import com.alibaba.himarket.dto.result.chat.ChatAnswerMessage;
import com.alibaba.himarket.dto.result.chat.LlmInvokeResult;
import com.alibaba.himarket.support.enums.AIProtocol;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Consumer;
import reactor.core.publisher.Flux;

@Deprecated
public interface LlmServiceLegacy {

    /**
     * Chat with LLM
     *
     * @param param
     * @param response
     * @param resultHandler
     * @return
     */
    Flux<ChatAnswerMessage> invokeLLM(
            InvokeModelParamLegacy param,
            HttpServletResponse response,
            Consumer<LlmInvokeResult> resultHandler);

    /**
     * Supported protocol
     *
     * @return
     */
    AIProtocol getProtocol();
}
