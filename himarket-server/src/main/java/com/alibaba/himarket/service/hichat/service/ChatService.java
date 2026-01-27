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

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.event.ChatSessionDeletingEvent;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.params.chat.CreateChatParam;
import com.alibaba.himarket.dto.result.chat.LlmInvokeResult;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.product.ProductRefResult;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.dto.result.product.SubscriptionResult;
import com.alibaba.himarket.entity.Chat;
import com.alibaba.himarket.entity.ChatAttachment;
import com.alibaba.himarket.entity.ChatSession;
import com.alibaba.himarket.repository.ChatAttachmentRepository;
import com.alibaba.himarket.repository.ChatRepository;
import com.alibaba.himarket.service.*;
import com.alibaba.himarket.service.hichat.support.ChatEvent;
import com.alibaba.himarket.service.hichat.support.InvokeModelParam;
import com.alibaba.himarket.support.chat.attachment.ChatAttachmentConfig;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import com.alibaba.himarket.support.enums.ChatAttachmentType;
import com.alibaba.himarket.support.enums.ChatStatus;
import com.alibaba.himarket.support.enums.ProductType;
import io.agentscope.core.message.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionService sessionService;

    private final List<LlmService> llmServices;

    private final ChatRepository chatRepository;

    private final ChatAttachmentRepository chatAttachmentRepository;

    private final ContextHolder contextHolder;

    private final ProductService productService;

    private final ConsumerService consumerService;

    public Flux<ChatEvent> chat(CreateChatParam param) {
        performAllChecks(param);

        Chat chat = createChat(param);
        InvokeModelParam invokeModelParam = buildInvokeModelParam(param, chat);

        return getLlmService(invokeModelParam)
                .invokeLlm(invokeModelParam, r -> updateChatResult(chat.getChatId(), r));
    }

    private void updateChatResult(String chatId, LlmInvokeResult result) {
        chatRepository
                .findByChatId(chatId)
                .ifPresent(
                        chat -> {
                            chat.setAnswer(result.getAnswer());
                            chat.setStatus(
                                    result.isSuccess() ? ChatStatus.SUCCESS : ChatStatus.FAILED);
                            chat.setChatUsage(result.getUsage());
                            chatRepository.save(chat);
                        });
    }

    private void performAllChecks(CreateChatParam param) {
        ChatSession session = sessionService.findUserSession(param.getSessionId());

        if (!session.getProducts().contains(param.getProductId())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    StrUtil.format("Product `{}` not in current session", param.getProductId()));
        }

        if (CollUtil.isNotEmpty(param.getMcpProducts())) {
            Set<String> subscribedProductIds =
                    consumerService
                            .listConsumerSubscriptions(
                                    consumerService.getPrimaryConsumer().getConsumerId())
                            .stream()
                            .map(SubscriptionResult::getProductId)
                            .collect(Collectors.toSet());

            Set<String> unsubscribedProducts =
                    param.getMcpProducts().stream()
                            .filter(productId -> !subscribedProductIds.contains(productId))
                            .collect(Collectors.toSet());

            if (!unsubscribedProducts.isEmpty()) {
                log.warn(
                        "MCP products `{}` are not subscribed, which may cause unauthorized access",
                        unsubscribedProducts);
            }
        }

        // chat count

        // check edit

        // check once more
    }

    public Chat createChat(CreateChatParam param) {
        String chatId = IdGenerator.genChatId();
        Chat chat = param.convertTo();
        chat.setChatId(chatId);
        chat.setUserId(contextHolder.getUser());

        // Sequence represent the number of tries for this question
        Integer sequence =
                chatRepository.findCurrentSequence(
                        param.getSessionId(),
                        param.getConversationId(),
                        param.getQuestionId(),
                        param.getProductId());
        chat.setSequence(sequence + 1);

        return chatRepository.save(chat);
    }

    private InvokeModelParam buildInvokeModelParam(CreateChatParam param, Chat chat) {
        // Get product config
        ProductResult productResult = productService.getProduct(param.getProductId());

        // Record target gateway
        ProductRefResult productRef = productService.getProductRef(param.getProductId());
        String gatewayId = productRef.getGatewayId();

        // Get authentication info
        CredentialContext credentialContext =
                consumerService.getDefaultCredential(contextHolder.getUser());

        // Build user msg and history msg list which will be passed to model
        List<Msg> historyMsgList = buildHistoryMsgList(param);
        Msg currentMsg = buildUserMsg(chat);

        return InvokeModelParam.builder()
                .chatId(chat.getChatId())
                .sessionId(param.getSessionId())
                .userMessage(currentMsg)
                .product(productResult)
                .historyMessages(historyMsgList)
                .enableWebSearch(param.getEnableWebSearch())
                .gatewayId(gatewayId)
                .mcpConfigs(buildMCPConfigs(param, credentialContext))
                .credentialContext(credentialContext)
                .build();
    }

    public List<Msg> buildHistoryMsgList(CreateChatParam param) {
        List<Msg> messages = new ArrayList<>();

        // 1. Query successful chat records from database
        List<Chat> chats =
                chatRepository.findBySessionIdAndStatus(
                        param.getSessionId(),
                        ChatStatus.SUCCESS,
                        Sort.by(Sort.Direction.ASC, "createAt"));

        if (CollUtil.isEmpty(chats)) {
            return CollUtil.empty(List.class);
        }

        // 2. Filter and group chats
        Map<String, List<Chat>> chatGroups =
                chats.stream()
                        // Filter valid chats (must have both question and answer)
                        .filter(
                                chat ->
                                        StrUtil.isNotBlank(chat.getQuestion())
                                                && StrUtil.isNotBlank(chat.getAnswer()))
                        // Exclude current conversation
                        .filter(chat -> !param.getConversationId().equals(chat.getConversationId()))
                        // Ensure same product
                        .filter(chat -> StrUtil.equals(chat.getProductId(), param.getProductId()))
                        .collect(Collectors.groupingBy(Chat::getConversationId));

        // 3. Get latest answer for each conversation
        // Note: A conversation may have multiple chats for the same question (retries,
        // regenerations)
        // We need to find the latest question, then get its latest answer
        List<Chat> latestChats =
                chatGroups.values().stream()
                        .map(
                                conversationChats -> {
                                    // 3.1 Find the latest question ID
                                    String latestQuestionId =
                                            conversationChats.stream()
                                                    .max(Comparator.comparing(Chat::getCreateAt))
                                                    .map(Chat::getQuestionId)
                                                    .orElse(null);

                                    if (StrUtil.isBlank(latestQuestionId)) {
                                        return null;
                                    }

                                    // 3.2 Get the latest answer for this question
                                    return conversationChats.stream()
                                            .filter(
                                                    chat ->
                                                            latestQuestionId.equals(
                                                                    chat.getQuestionId()))
                                            .max(Comparator.comparing(Chat::getCreateAt))
                                            .orElse(null);
                                })
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(Chat::getCreateAt))
                        .toList();

        // 4. Build AgentScope Msg objects (user + assistant pairs)
        for (Chat chat : latestChats) {
            // User message (with multimodal support)
            Msg userMsg = buildUserMsg(chat);
            messages.add(userMsg);

            // Assistant message
            Msg assistantMsg = buildAssistantMsg(chat);
            messages.add(assistantMsg);
        }

        // 5. Truncate if too many messages
        messages = truncateMessages(messages);

        log.debug(
                "Built {} AgentScope messages from {} conversations for session: {}",
                messages.size(),
                latestChats.size(),
                param.getSessionId());
        return messages;
    }

    private Msg buildUserMsg(Chat chat) {
        List<ContentBlock> contentBlocks = new ArrayList<>();

        // 1. Prepare text content (question)
        StringBuilder textContent = new StringBuilder();
        if (StrUtil.isNotBlank(chat.getQuestion())) {
            textContent.append(chat.getQuestion());
        }

        // 2. Load and process attachments
        List<ChatAttachmentConfig> attachmentConfigs = chat.getAttachments();
        if (CollUtil.isNotEmpty(attachmentConfigs)) {
            List<String> attachmentIds =
                    attachmentConfigs.stream()
                            .map(ChatAttachmentConfig::getAttachmentId)
                            .filter(StrUtil::isNotBlank)
                            .collect(Collectors.toList());

            if (CollUtil.isNotEmpty(attachmentIds)) {
                List<ChatAttachment> attachments =
                        chatAttachmentRepository.findByAttachmentIdIn(attachmentIds);

                for (ChatAttachment attachment : attachments) {
                    if (attachment == null || ArrayUtil.isEmpty(attachment.getData())) {
                        continue;
                    }

                    // Process attachment based on type
                    if (attachment.getType() == ChatAttachmentType.TEXT) {
                        buildTextContent(attachment, textContent);
                    } else {
                        // IMAGE, AUDIO, VIDEO
                        buildMediaContent(attachment, contentBlocks);
                    }
                }
            }
        }

        // 3. Build content blocks
        // Always add text content first (using correct AgentScope API)
        if (!textContent.isEmpty()) {
            contentBlocks.add(0, TextBlock.builder().text(textContent.toString()).build());
        }

        // 4. Create Msg object with proper content format
        // If no content blocks, use textContent() convenience method for empty string
        if (contentBlocks.isEmpty()) {
            return Msg.builder().role(MsgRole.USER).textContent("").build();
        } else {
            return Msg.builder().role(MsgRole.USER).content(contentBlocks).build();
        }
    }

    private void buildTextContent(ChatAttachment attachment, StringBuilder textContent) {
        String text = new String(attachment.getData(), StandardCharsets.UTF_8);
        textContent.append("\n\n## ").append(attachment.getName()).append("\n").append(text);
    }

    private void buildMediaContent(ChatAttachment attachment, List<ContentBlock> contentBlocks) {

        // Encode to pure Base64 string (no data URL prefix)
        String base64Data = Base64.encode(attachment.getData());

        // Use default mime type if not specified
        String mediaType =
                StrUtil.isBlank(attachment.getMimeType())
                        ? "application/octet-stream"
                        : attachment.getMimeType();

        // Create Base64Source with pure base64 data
        Base64Source source = Base64Source.builder().data(base64Data).mediaType(mediaType).build();

        ContentBlock contentBlock;
        switch (attachment.getType()) {
            case IMAGE:
                contentBlock = ImageBlock.builder().source(source).build();
                break;
            case AUDIO:
                contentBlock = AudioBlock.builder().source(source).build();
                break;
            case VIDEO:
                contentBlock = VideoBlock.builder().source(source).build();
                break;
            default:
                log.warn("Unsupported media attachment type: {}", attachment.getType());
                return;
        }

        contentBlocks.add(contentBlock);
    }

    private Msg buildAssistantMsg(Chat chat) {
        String answer = StrUtil.isBlank(chat.getAnswer()) ? "" : chat.getAnswer();
        // Use textContent() convenience method for simple text messages
        return Msg.builder().role(MsgRole.ASSISTANT).textContent(answer).build();
    }

    private List<Msg> truncateMessages(List<Msg> messages) {
        // Max conversation pairs to keep
        int maxHistoryPairs = 10;
        int maxMessages = maxHistoryPairs * 2;

        if (messages.size() > maxMessages) {
            int startIndex = messages.size() - maxMessages;
            List<Msg> truncated = messages.subList(startIndex, messages.size());
            log.debug("Truncated history to last {} conversation pairs", maxHistoryPairs);
            return truncated;
        }

        return messages;
    }

    private List<MCPTransportConfig> buildMCPConfigs(
            CreateChatParam param, CredentialContext credentialContext) {
        if (CollUtil.isEmpty(param.getMcpProducts())) {
            return CollUtil.empty(List.class);
        }

        return productService.getProducts(param.getMcpProducts()).values().stream()
                .filter(
                        product ->
                                product.getType() == ProductType.MCP_SERVER
                                        || product.getMcpConfig() != null)
                .map(
                        product -> {
                            MCPTransportConfig transportConfig =
                                    product.getMcpConfig().toTransportConfig();

                            // Add authentication credentials
                            transportConfig.setHeaders(credentialContext.copyHeaders());
                            transportConfig.setQueryParams(credentialContext.copyQueryParams());

                            return transportConfig;
                        })
                .collect(Collectors.toList());
    }

    private LlmService getLlmService(InvokeModelParam param) {
        // Get supported protocols from model config (not null)
        List<String> aiProtocols =
                param.getProduct().getModelConfig().getModelAPIConfig().getAiProtocols();

        // Find first matched service by protocol
        return llmServices.stream()
                .filter(service -> aiProtocols.stream().anyMatch(service::match))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "No supported LLM service found for protocols: "
                                                + aiProtocols));
    }

    /**
     * Handle session deletion event - cleanup all related chat records
     *
     * @param event session deletion event
     */
    @EventListener
    @Async("taskExecutor")
    @Transactional
    public void onSessionDeletion(ChatSessionDeletingEvent event) {
        String sessionId = event.getSessionId();

        try {
            log.info("Cleaning chat records and attachments for session: {}", sessionId);

            // Delete all chat records
            chatRepository.deleteAllBySessionId(sessionId);

            log.info("Successfully cleaned chat records for session: {}", sessionId);
        } catch (Exception e) {
            log.error("Failed to cleanup chat records for session: {}", sessionId, e);
        }
    }
}
