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

import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.entity.Chat;
import com.alibaba.himarket.support.chat.attachment.ChatAttachmentConfig;
import com.alibaba.himarket.support.enums.ChatStatus;
import java.util.List;
import lombok.Data;

@Data
public class ChatResult implements OutputConverter<ChatResult, Chat> {

    private String chatId;

    private String sessionId;

    private String conversationId;

    private ChatStatus status;

    private String productId;

    private String questionId;

    private String question;

    private List<ChatAttachmentConfig> attachments;

    private String answerId;

    private String answer;
}
