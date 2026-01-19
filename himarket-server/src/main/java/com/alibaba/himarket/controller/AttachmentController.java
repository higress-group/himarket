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

package com.alibaba.himarket.controller;

import com.alibaba.himarket.core.annotation.AdminOrDeveloperAuth;
import com.alibaba.himarket.dto.result.chat.ChatAttachmentDetailResult;
import com.alibaba.himarket.dto.result.chat.ChatAttachmentResult;
import com.alibaba.himarket.service.ChatAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "聊天附件管理", description = "提供聊天附件的上传功能，支持图片、视频、音频等多模态内容")
@RestController
@RequestMapping("/attachments")
@RequiredArgsConstructor
@Slf4j
@AdminOrDeveloperAuth
public class AttachmentController {

    private final ChatAttachmentService chatAttachmentService;

    @Operation(summary = "上传附件")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChatAttachmentResult uploadAttachment(@RequestParam("file") MultipartFile file) {
        return chatAttachmentService.uploadAttachment(file);
    }

    @Operation(summary = "获取附件内容")
    @GetMapping("/{attachmentId}")
    public ChatAttachmentDetailResult getAttachment(@PathVariable String attachmentId) {
        return chatAttachmentService.getAttachmentDetail(attachmentId);
    }
}
