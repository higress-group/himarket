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

package com.alibaba.himarket.dto.result.chat;

import com.alibaba.himarket.service.legacy.ChatContextLegacy;
import com.alibaba.himarket.support.chat.ChatUsage;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@Slf4j
public class LlmInvokeResult {

    private boolean success;

    /**
     * Completed answer
     */
    private String answer;

    /**
     * Usage, exists only when success
     */
    private ChatUsage usage;

    @Deprecated
    public static LlmInvokeResult of(ChatContextLegacy chatContext) {
        return LlmInvokeResult.builder()
                .success(chatContext.getSuccess())
                .answer(chatContext.getAnswerContent().toString())
                .usage(chatContext.getChatUsage())
                .build();
    }
}
