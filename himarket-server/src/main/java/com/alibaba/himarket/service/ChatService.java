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

package com.alibaba.himarket.service;

import com.alibaba.himarket.core.event.ChatSessionDeletingEvent;
import com.alibaba.himarket.dto.params.chat.CreateChatParam;
import com.alibaba.himarket.dto.result.chat.ChatAnswerMessage;
import jakarta.servlet.http.HttpServletResponse;
import reactor.core.publisher.Flux;

public interface ChatService {

    /**
     * Perform a chat
     *
     * @param param
     * @param response
     * @return
     */
    Flux<ChatAnswerMessage> chat(CreateChatParam param, HttpServletResponse response);

    /**
     * Handle session deletion event, such as cleaning up all related chat records
     *
     * @param event
     */
    void handleSessionDeletion(ChatSessionDeletingEvent event);
}
