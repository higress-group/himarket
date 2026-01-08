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

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.CacheUtil;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.params.chat.CreateChatParam;
import com.alibaba.himarket.dto.result.chat.ChatAnswerMessage;
import com.alibaba.himarket.dto.result.chat.LlmInvokeResult;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.product.ProductRefResult;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.entity.Chat;
import com.alibaba.himarket.entity.ChatAttachment;
import com.alibaba.himarket.entity.ChatSession;
import com.alibaba.himarket.entity.ProductSubscription;
import com.alibaba.himarket.repository.ChatAttachmentRepository;
import com.alibaba.himarket.repository.ChatRepository;
import com.alibaba.himarket.repository.SubscriptionRepository;
import com.alibaba.himarket.service.*;
import com.alibaba.himarket.support.chat.ChatMessage;
import com.alibaba.himarket.support.chat.attachment.ChatAttachmentConfig;
import com.alibaba.himarket.support.chat.content.*;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import com.alibaba.himarket.support.enums.ChatAttachmentType;
import com.alibaba.himarket.support.enums.ChatRole;
import com.alibaba.himarket.support.enums.ChatStatus;
import com.alibaba.himarket.support.enums.ProductType;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@Slf4j
@AllArgsConstructor
@Deprecated
public class ChatServiceImplLegacy implements ChatServiceLegacy {

    private final ChatSessionService sessionService;

    private final LlmServiceLegacy llmServiceLegacy;

    private final ChatRepository chatRepository;

    private final ChatAttachmentRepository chatAttachmentRepository;

    private final SubscriptionRepository subscriptionRepository;

    private final ContextHolder contextHolder;

    private final ProductService productService;

    private final GatewayService gatewayService;

    private final ConsumerService consumerService;

    private final Cache<String, List<URI>> cache = CacheUtil.newCache(5);

    public Flux<ChatAnswerMessage> chat(CreateChatParam param, HttpServletResponse response) {
        performAllChecks(param);
        //        sessionService.updateStatus(param.getSessionId(), ChatSessionStatus.PROCESSING);

        Chat chat = createChat(param);

        // Current message, contains user message and attachments
        ChatMessage currentMessage = buildUserMessage(chat);

        // History messages, contains user message and assistant message
        List<ChatMessage> historyMessages = buildHistoryMessages(param);

        List<ChatMessage> chatMessages = mergeAndTruncateMessages(currentMessage, historyMessages);

        InvokeModelParamLegacy invokeModelParamLegacy =
                buildInvokeModelParam(param, chatMessages, chat);

        // Invoke LLM
        return llmServiceLegacy.invokeLLM(
                invokeModelParamLegacy, response, r -> updateChatResult(chat.getChatId(), r));
    }

    private Chat createChat(CreateChatParam param) {
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

    private void performAllChecks(CreateChatParam param) {
        ChatSession session = sessionService.findUserSession(param.getSessionId());

        if (!session.getProducts().contains(param.getProductId())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Product not in current session");
        }

        // check mcpServers count is less than 10, and all of them are subscribed
        List<String> mcpProducts = param.getMcpProducts();
        if (CollUtil.isNotEmpty(mcpProducts) && mcpProducts.size() > 10) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "MCP servers count is more than 10, currently max size is 10");
        }

        String consumerId = consumerService.getPrimaryConsumer().getConsumerId();
        // 批量查询提高性能
        List<ProductSubscription> subscriptions =
                subscriptionRepository.findAllByConsumerId(consumerId);
        Set<String> subscribedProductIds =
                subscriptions.stream()
                        .map(ProductSubscription::getProductId)
                        .collect(Collectors.toSet());

        for (String productId : mcpProducts) {
            if (!subscribedProductIds.contains(productId)) {
                //                throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                // Resources.PRODUCT, productId + " mcp is not subscribed, not allowed to use");
                log.warn("Mcp product {} is not subscribed, not allowed to use", productId);
            }
        }

        // chat count

        // check edit

        // check once more
    }

    private List<ChatMessage> buildHistoryMessages(CreateChatParam param) {
        // 1. Get successful chat records
        List<Chat> chats =
                chatRepository.findBySessionIdAndStatus(
                        param.getSessionId(),
                        ChatStatus.SUCCESS,
                        Sort.by(Sort.Direction.ASC, "createAt"));

        if (CollUtil.isEmpty(chats)) {
            return CollUtil.empty(List.class);
        }

        // 2. Group by conversation and filter invalid chats and current conversation
        Map<String, List<Chat>> chatGroups =
                chats.stream()
                        .filter(
                                chat ->
                                        StrUtil.isNotBlank(chat.getQuestion())
                                                && StrUtil.isNotBlank(chat.getAnswer()))
                        .filter(chat -> !param.getConversationId().equals(chat.getConversationId()))
                        // Ensure the same product
                        .filter(chat -> StrUtil.equals(chat.getProductId(), param.getProductId()))
                        .collect(Collectors.groupingBy(Chat::getConversationId));

        // 3. Get latest answer for each conversation
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

        // 4. Convert to chat messages
        List<ChatMessage> chatMessages = new ArrayList<>();
        for (Chat chat : latestChats) {
            // One chat consists of two messages: user message and assistant message
            chatMessages.add(buildUserMessage(chat));
            chatMessages.add(buildAssistantMessage(chat));
        }

        return chatMessages;
    }

    private ChatMessage buildUserMessage(Chat chat) {
        List<ChatAttachmentConfig> attachmentConfigs = chat.getAttachments();
        // Return simple message if no attachments
        if (CollUtil.isEmpty(attachmentConfigs)) {
            return ChatMessage.builder()
                    .role(ChatRole.USER.getRole())
                    .content(chat.getQuestion())
                    .build();
        }

        // All attachments
        List<ChatAttachment> attachments =
                chatAttachmentRepository.findByAttachmentIdIn(
                        attachmentConfigs.stream()
                                .map(ChatAttachmentConfig::getAttachmentId)
                                .collect(Collectors.toList()));

        // Traverse to determine file types
        boolean withTextContent = false, withMediaContent = false;
        for (ChatAttachment attachment : attachments) {
            if (attachment == null || ArrayUtil.isEmpty(attachment.getData())) {
                continue;
            }
            if (attachment.getType() == ChatAttachmentType.TEXT) {
                withTextContent = true;
            } else {
                withMediaContent = true;
            }
            if (withTextContent && withMediaContent) {
                break;
            }
        }

        // Build content with markdown format
        StringBuilder textContent = new StringBuilder("# Question\n").append(chat.getQuestion());
        if (withTextContent) {
            textContent.append("\n\n# Files Content\n");
        }

        List<MessageContent> mediaContents = new ArrayList<>();
        for (ChatAttachment attachment : attachments) {
            if (attachment == null || ArrayUtil.isEmpty(attachment.getData())) {
                continue;
            }

            // Handle text files
            if (attachment.getType() == ChatAttachmentType.TEXT) {
                String text = new String(attachment.getData(), StandardCharsets.UTF_8);
                textContent
                        .append("## ")
                        .append(attachment.getName())
                        .append("\n")
                        .append(text)
                        .append("\n\n");
            } else {
                // Handle media files
                String base64String = Base64.encode(attachment.getData());
                String dataString =
                        StrUtil.isBlank(attachment.getMimeType())
                                ? base64String
                                : String.format(
                                        "data:%s;base64,%s",
                                        attachment.getMimeType(), base64String);

                MessageContent content = null;
                switch (attachment.getType()) {
                    case IMAGE:
                        content = new ImageUrlContent(dataString);
                        break;
                    case AUDIO:
                        content = new AudioUrlContent(dataString);
                        break;
                    case VIDEO:
                        content = new VideoUrlContent(dataString);
                        break;
                    default:
                        log.warn("Unsupported attachment type: {}", attachment.getType());
                }

                if (content != null) {
                    mediaContents.add(content);
                }
            }
        }

        Object content;
        if (withMediaContent) {
            mediaContents.add(0, new TextContent(textContent.toString()));
            content = mediaContents;
        } else {
            content = textContent.toString();
        }

        return ChatMessage.builder().role(ChatRole.USER.getRole()).content(content).build();
    }

    private ChatMessage buildAssistantMessage(Chat chat) {
        // Assistant message only contains answer
        return ChatMessage.builder()
                .role(ChatRole.ASSISTANT.getRole())
                .content(chat.getAnswer())
                .build();
    }

    private List<ChatMessage> mergeAndTruncateMessages(
            ChatMessage currentMessage, List<ChatMessage> historyMessages) {
        List<ChatMessage> messages = new ArrayList<>();

        // Add truncated history messages first
        if (!CollUtil.isEmpty(historyMessages)) {
            int maxHistorySize = 20; // Maximum history messages to keep
            int startIndex = Math.max(0, historyMessages.size() - maxHistorySize);
            messages.addAll(historyMessages.subList(startIndex, historyMessages.size()));
        }

        // Add current message at the end
        messages.add(currentMessage);

        return messages;
    }

    private InvokeModelParamLegacy buildInvokeModelParam(
            CreateChatParam param, List<ChatMessage> chatMessages, Chat chat) {
        // Get product config
        ProductResult productResult = productService.getProduct(param.getProductId());

        // Get gateway uris
        ProductRefResult productRef = productService.getProductRef(param.getProductId());
        String gatewayId = productRef.getGatewayId();
        List<URI> gatewayUris = cache.get(gatewayId, gatewayService::fetchGatewayUris);

        // Get authentication info
        CredentialContext credentialContext =
                consumerService.getDefaultCredential(contextHolder.getUser());

        return InvokeModelParamLegacy.builder()
                .chatId(chat.getChatId())
                .userQuestion(param.getQuestion())
                .product(productResult)
                .chatMessages(chatMessages)
                .enableWebSearch(param.getEnableWebSearch())
                .gatewayUris(gatewayUris)
                .mcpConfigs(buildMCPConfigs(param))
                .credentialContext(credentialContext)
                .build();
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

    private List<MCPTransportConfig> buildMCPConfigs(CreateChatParam param) {
        if (CollectionUtil.isEmpty(param.getMcpProducts())) {
            return CollUtil.empty(List.class);
        }

        return productService.getProducts(param.getMcpProducts(), true).values().stream()
                .filter(
                        product ->
                                product.getType() == ProductType.MCP_SERVER
                                        || product.getMcpConfig() != null)
                .map(product -> product.getMcpConfig().toTransportConfig())
                .collect(Collectors.toList());
    }
}
