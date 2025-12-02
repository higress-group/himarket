package com.alibaba.apiopenplatform.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.apiopenplatform.core.constant.Resources;
import com.alibaba.apiopenplatform.core.event.ChatSessionDeletingEvent;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.core.security.ContextHolder;
import com.alibaba.apiopenplatform.core.utils.CacheUtil;
import com.alibaba.apiopenplatform.core.utils.IdGenerator;
import com.alibaba.apiopenplatform.dto.result.chat.ChatAnswerMessage;
import com.alibaba.apiopenplatform.dto.result.consumer.CredentialContext;
import com.alibaba.apiopenplatform.dto.result.product.ProductRefResult;
import com.alibaba.apiopenplatform.entity.Chat;
import com.alibaba.apiopenplatform.entity.ChatAttachment;
import com.alibaba.apiopenplatform.entity.ChatSession;
import com.alibaba.apiopenplatform.dto.result.product.ProductResult;
import com.alibaba.apiopenplatform.entity.ProductSubscription;
import com.alibaba.apiopenplatform.repository.ChatAttachmentRepository;
import com.alibaba.apiopenplatform.repository.SubscriptionRepository;
import com.alibaba.apiopenplatform.service.*;
import com.alibaba.apiopenplatform.repository.ChatRepository;
import com.alibaba.apiopenplatform.dto.params.chat.CreateChatParam;
import com.alibaba.apiopenplatform.dto.params.chat.InvokeModelParam;
import com.alibaba.apiopenplatform.dto.result.chat.LlmInvokeResult;
import com.alibaba.apiopenplatform.support.chat.attachment.ChatAttachmentConfig;
import com.alibaba.apiopenplatform.support.chat.ChatMessage;
import com.alibaba.apiopenplatform.support.chat.content.*;
import com.alibaba.apiopenplatform.support.chat.mcp.McpServerConfig;
import com.alibaba.apiopenplatform.support.enums.ChatAttachmentType;
import com.alibaba.apiopenplatform.support.enums.ChatRole;
import com.alibaba.apiopenplatform.support.enums.ChatStatus;
import com.alibaba.apiopenplatform.support.enums.ProductType;
import com.github.benmanes.caffeine.cache.Cache;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@AllArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatSessionService sessionService;

    private final LlmService llmService;

    private final ChatRepository chatRepository;

    private final ChatAttachmentRepository chatAttachmentRepository;

    private final SubscriptionRepository subscriptionRepository;

    private final ContextHolder contextHolder;

    private final ProductService productService;

    private final GatewayService gatewayService;

    private final ConsumerService consumerService;

    private final Cache<String, List<String>> cache = CacheUtil.newCache(5);

    public Flux<ChatAnswerMessage> chat(CreateChatParam param, HttpServletResponse response) {
        performAllChecks(param);
//        sessionService.updateStatus(param.getSessionId(), ChatSessionStatus.PROCESSING);

        Chat chat = createChat(param);

        // Current message, contains user message and attachments
        ChatMessage currentMessage = buildUserMessage(chat);

        // History messages, contains user message and assistant message
        List<ChatMessage> historyMessages = buildHistoryMessages(param);

        List<ChatMessage> chatMessages = mergeAndTruncateMessages(currentMessage, historyMessages);

        List<McpServerConfig> mcpResults = buildMcpServerConfigs(param);

        InvokeModelParam invokeModelParam = buildInvokeModelParam(param, chatMessages, mcpResults, chat);

        // Invoke LLM
        return llmService.invokeLLM(invokeModelParam, response, r -> updateChatResult(chat.getChatId(), r));
    }

    private Chat createChat(CreateChatParam param) {
        String chatId = IdGenerator.genChatId();
        Chat chat = param.convertTo();
        chat.setChatId(chatId);
        chat.setUserId(contextHolder.getUser());

        // Sequence represent the number of tries for this question
        Integer sequence = chatRepository.findCurrentSequence(param.getSessionId(), param.getConversationId(),
                param.getQuestionId(), param.getProductId());
        chat.setSequence(sequence + 1);

        return chatRepository.save(chat);
    }

    private void performAllChecks(CreateChatParam param) {
        ChatSession session = sessionService.findUserSession(param.getSessionId());

        if (!session.getProducts().contains(param.getProductId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Product not in current session");
        }

        // check mcpServers count is less than 10, and all of them are subscribed
        List<String> mcpProducts = param.getMcpProducts();
        if (CollUtil.isNotEmpty(mcpProducts) && mcpProducts.size() > 10) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "MCP servers count is more than 10, currently max size is 10");
        }

        String consumerId = consumerService.getPrimaryConsumer().getConsumerId();
        // 批量查询提高性能
        List<ProductSubscription> subscriptions = subscriptionRepository.findAllByConsumerId(consumerId);
        Set<String> subscribedProductIds = subscriptions.stream()
                .map(ProductSubscription::getProductId)
                .collect(Collectors.toSet());

        for (String productId : mcpProducts) {
            if (!subscribedProductIds.contains(productId)) {
//                throw new BusinessException(ErrorCode.INVALID_PARAMETER, Resources.PRODUCT, productId + " mcp is not subscribed, not allowed to use");
                log.warn("mcp product {} is not subscribed, not allowed to use", productId);
            }
        }

        // chat count

        // check edit

        // check once more
    }


    private List<McpServerConfig> buildMcpServerConfigs(CreateChatParam param) {
        List<String> mcpProducts = param.getMcpProducts();
        if (CollectionUtil.isEmpty(mcpProducts)) {
            return Collections.emptyList();
        }
        List<McpServerConfig> mcpServerConfigs = new ArrayList<>();
        mcpProducts.forEach(productId -> {
            ProductResult product = productService.getProduct(productId);
            if (product.getType() == ProductType.MCP_SERVER && product.getMcpConfig() != null) {
                mcpServerConfigs.add(product.getMcpConfig().toStandardMcpServer());
            }
        });
        return mcpServerConfigs;
    }

    private List<ChatMessage> buildHistoryMessages(CreateChatParam param) {
        // 1. Get successful chat records
        List<Chat> chats = chatRepository.findBySessionIdAndStatus(
                param.getSessionId(),
                ChatStatus.SUCCESS,
                Sort.by(Sort.Direction.ASC, "createAt")
        );

        if (CollUtil.isEmpty(chats)) {
            return CollUtil.empty(List.class);
        }

        // 2. Group by conversation and filter invalid chats and current conversation
        Map<String, List<Chat>> chatGroups = chats.stream()
                .filter(chat -> StrUtil.isNotBlank(chat.getQuestion()) && StrUtil.isNotBlank(chat.getAnswer()))
                .filter(chat -> !param.getConversationId().equals(chat.getConversationId())) // Skip current conversation
                // Ensure the same product
                .filter(chat -> StrUtil.equals(chat.getProductId(), param.getProductId()))
                .collect(Collectors.groupingBy(Chat::getConversationId));


        // 3. Get latest answer for each conversation
        List<Chat> latestChats = chatGroups.values().stream()
                .map(conversationChats -> {
                    // 3.1 Find the latest question ID
                    String latestQuestionId = conversationChats.stream()
                            .max(Comparator.comparing(Chat::getCreateAt))
                            .map(Chat::getQuestionId)
                            .orElse(null);

                    if (StrUtil.isBlank(latestQuestionId)) {
                        return null;
                    }

                    // 3.2 Get the latest answer for this question
                    return conversationChats.stream()
                            .filter(chat -> latestQuestionId.equals(chat.getQuestionId()))
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
        List<ChatAttachment> attachments = chatAttachmentRepository.findByAttachmentIdIn(
                attachmentConfigs.stream()
                        .map(ChatAttachmentConfig::getAttachmentId)
                        .collect(Collectors.toList())
        );

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
                textContent.append("## ")
                        .append(attachment.getName())
                        .append("\n")
                        .append(text)
                        .append("\n\n");
            } else {
                // Handle media files
                String base64String = Base64.encode(attachment.getData());
                String dataString = StrUtil.isBlank(attachment.getMimeType()) ?
                        base64String : String.format("data:%s;base64,%s", attachment.getMimeType(), base64String);

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

        return ChatMessage.builder()
                .role(ChatRole.USER.getRole())
                .content(content)
                .build();
    }

    private ChatMessage buildAssistantMessage(Chat chat) {
        // Assistant message only contains answer
        return ChatMessage.builder()
                .role(ChatRole.ASSISTANT.getRole())
                .content(chat.getAnswer())
                .build();
    }

    private List<ChatMessage> mergeAndTruncateMessages(ChatMessage currentMessage, List<ChatMessage> historyMessages) {
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

    private InvokeModelParam buildInvokeModelParam(CreateChatParam param, List<ChatMessage> chatMessages, List<McpServerConfig> mcpServerConfigs, Chat chat) {
        // Get product config
        ProductResult productResult = productService.getProduct(param.getProductId());

        // Get gateway IPs
        ProductRefResult productRef = productService.getProductRef(param.getProductId());
        String gatewayId = productRef.getGatewayId();
        List<String> gatewayIps = cache.get(gatewayId, gatewayService::fetchGatewayIps);

        // Get authentication info
        CredentialContext credentialContext = consumerService.getDefaultCredential(contextHolder.getUser());

        return InvokeModelParam.builder()
                .chatId(chat.getChatId())
                .userQuestion(param.getQuestion())
                .product(productResult)
                .requestHeaders(credentialContext.getHeaders())
                .queryParams(credentialContext.getQueryParams())
                .chatMessages(chatMessages)
                .stream(param.getStream())
                .enableWebSearch(param.getEnableWebSearch())
                .gatewayIps(gatewayIps)
                .mcpServerConfigs(mcpServerConfigs)
                .credentialContext(credentialContext)
                .build();
    }

    private void updateChatResult(String chatId, LlmInvokeResult result) {
        chatRepository.findByChatId(chatId).ifPresent(chat -> {
            chat.setAnswer(result.getAnswer());
            chat.setStatus(result.isSuccess() ? ChatStatus.SUCCESS : ChatStatus.FAILED);
            chat.setChatUsage(result.getUsage());
            chatRepository.save(chat);
        });
    }

    @EventListener
    @Async("taskExecutor")
    @Override
    public void handleSessionDeletion(ChatSessionDeletingEvent event) {
        String sessionId = event.getSessionId();
        try {
            chatRepository.deleteAllBySessionId(sessionId);

            log.info("Completed cleanup chat records for session {}", sessionId);
        } catch (Exception e) {
            log.error("Failed to cleanup chat records for session {}: {}", sessionId, e.getMessage());
        }
    }
}
