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

package com.alibaba.himarket.service.impl;

import cn.hutool.core.codec.Base64;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.result.chat.ChatAttachmentDetailResult;
import com.alibaba.himarket.dto.result.chat.ChatAttachmentResult;
import com.alibaba.himarket.entity.ChatAttachment;
import com.alibaba.himarket.repository.ChatAttachmentRepository;
import com.alibaba.himarket.service.ChatAttachmentService;
import com.alibaba.himarket.support.enums.ChatAttachmentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatAttachmentServiceImpl implements ChatAttachmentService {

    private final ContextHolder contextHolder;

    private final ChatAttachmentRepository chatAttachmentRepository;

    @Override
    public ChatAttachmentResult uploadAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "File cannot be empty");
        }

        // Determine attachment type from MIME type
        String mimeType = file.getContentType();
        ChatAttachmentType type = determineAttachmentType(mimeType);

        try {
            // Build attachment entity
            ChatAttachment attachment =
                    ChatAttachment.builder()
                            .attachmentId(IdGenerator.genChatAttachmentId())
                            .userId(contextHolder.getUser())
                            .name(file.getOriginalFilename())
                            .type(type)
                            .mimeType(mimeType)
                            .size(file.getSize())
                            .data(file.getBytes())
                            .build();

            // Save to database
            ChatAttachment chatAttachment = chatAttachmentRepository.save(attachment);

            return new ChatAttachmentResult().convertFrom(chatAttachment);
        } catch (Exception e) {
            log.error("Failed to upload attachment: {}", file.getOriginalFilename(), e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Failed to upload attachment: " + e.getMessage());
        }
    }

    /**
     * Determine attachment type from MIME type
     *
     * @param mimeType MIME type
     * @return Attachment type
     */
    private ChatAttachmentType determineAttachmentType(String mimeType) {
        if (mimeType == null) {
            return ChatAttachmentType.TEXT;
        }

        if (mimeType.startsWith("image/")) {
            return ChatAttachmentType.IMAGE;
        } else if (mimeType.startsWith("video/")) {
            return ChatAttachmentType.VIDEO;
        } else if (mimeType.startsWith("audio/")) {
            return ChatAttachmentType.AUDIO;
        } else {
            return ChatAttachmentType.TEXT;
        }
    }

    @Override
    public ChatAttachmentDetailResult getAttachmentDetail(String attachmentId) {
        ChatAttachment attachment = findAttachment(attachmentId);

        // Encode data to Base64
        String base64Data = Base64.encode(attachment.getData());

        log.debug(
                "Retrieved attachment detail: attachmentId={}, size={}, base64Length={}",
                attachmentId,
                attachment.getSize(),
                base64Data.length());

        return ChatAttachmentDetailResult.builder()
                .attachmentId(attachment.getAttachmentId())
                .name(attachment.getName())
                .type(attachment.getType())
                .mimeType(attachment.getMimeType())
                .size(attachment.getSize())
                .data(base64Data)
                .build();
    }

    private ChatAttachment findAttachment(String attachmentId) {
        return chatAttachmentRepository
                .findByAttachmentId(attachmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, attachmentId));
    }
}
