package com.alibaba.apiopenplatform.service.chat.service;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.core.security.ContextHolder;
import com.alibaba.apiopenplatform.core.utils.IdGenerator;
import com.alibaba.apiopenplatform.entity.Chat;
import com.alibaba.apiopenplatform.entity.ChatSession;
import com.alibaba.apiopenplatform.dto.result.product.ProductResult;
import com.alibaba.apiopenplatform.service.ProductService;
import com.alibaba.apiopenplatform.service.chat.dto.*;
import com.alibaba.apiopenplatform.repository.ChatRepository;
import com.alibaba.apiopenplatform.support.chat.attachment.AttachmentConfig;
import com.alibaba.apiopenplatform.support.chat.ChatMessage;
import com.alibaba.apiopenplatform.support.chat.attachment.ChatAttachmentData;
import com.alibaba.apiopenplatform.support.chat.content.*;
import com.alibaba.apiopenplatform.support.enums.ChatRole;
import com.alibaba.apiopenplatform.support.enums.ChatStatus;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@AllArgsConstructor
public class ChatService {

    private final ChatSessionService sessionService;

    private final ChatAttachmentService chatAttachmentService;

    private final LlmService llmService;

    private final ChatRepository chatRepository;

    private final ContextHolder contextHolder;

    private final ProductService productService;

    public SseEmitter chat(CreateChatParam param, HttpServletResponse response) {
        performAllChecks(param);
//        sessionService.updateStatus(param.getSessionId(), ChatSessionStatus.PROCESSING);

        Chat chat = createChat(param);

        // Current message, contains user message and attachments
        ChatMessage currentMessage = buildUserMessage(chat);

        // History messages, contains user message and assistant message
        List<ChatMessage> historyMessages = buildHistoryMessages(param);

        List<ChatMessage> chatMessages = mergeAndTruncateMessages(currentMessage, historyMessages);

        InvokeModelParam invokeModelParam = buildInvokeModelParam(param, chatMessages, chat);

        // Invoke LLM
        return llmService.invokeLLM(invokeModelParam, r -> updateChatResult(chat.getChatId(), r));
    }

    private Chat createChat(CreateChatParam param) {
        String chatId = IdGenerator.genChatId();
        Chat chat = param.convertTo();
        chat.setChatId(chatId);
        chat.setUserId(contextHolder.getUser());

        return chatRepository.save(chat);
    }

    private void performAllChecks(CreateChatParam param) {
        ChatSession session = sessionService.findUserSession(param.getSessionId());

        if (!session.getProducts().contains(param.getProductId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Product not in current session");
        }

        // chat count

        // check edit

        // check once more
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
                .collect(Collectors.toList());

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
        List<AttachmentConfig> attachments = chat.getAttachments();
        // If no attachments, build a simple user message
        if (CollUtil.isEmpty(attachments)) {
            return ChatMessage.builder()
                    .role(ChatRole.USER.getRole())
                    .content(chat.getQuestion())
                    .build();
        }

        // Build multimodal message contents
        List<MessageContent> messageContents = new ArrayList<>();
        messageContents.add(new TextContent(chat.getQuestion()));

        for (AttachmentConfig a : attachments) {
            ChatAttachmentDetailResult attachmentDetail = chatAttachmentService.getAttachmentDetail(a.getAttachmentId());
            if (attachmentDetail == null) {
                continue;
            }

            ChatAttachmentData attachmentData = attachmentDetail.getData();
            String mimeType = attachmentDetail.getMimeType();

            byte[] data = attachmentData.getData();
            if (ArrayUtil.isEmpty(data)) {
                continue;
            }

            // Convert to base64 format
            String base64String = Base64.encode(data);
            String dataString = StrUtil.isBlank(mimeType) ?
                    base64String : String.format("data:%s;base64,%s", attachmentDetail.getMimeType(), base64String);

            MessageContent content = null;
            switch (attachmentDetail.getType()) {
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
                    log.warn("Unsupported attachment type: {}", attachmentDetail.getType());
            }
            messageContents.add(content);
        }

        return ChatMessage.builder()
                .role(ChatRole.USER.getRole())
                .content(messageContents)
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

    private InvokeModelParam buildInvokeModelParam(CreateChatParam param, List<ChatMessage> chatMessages, Chat chat) {
        // Get product config
        ProductResult productResult = productService.getProduct(param.getProductId());

        return InvokeModelParam.builder()
                .modelConfig(productResult.getModelConfig())
                .chatMessages(chatMessages)
                .stream(param.getStream())
                .build();
    }

    private void updateChatResult(String chatId, LlmInvokeResult result) {
        chatRepository.findByChatId(chatId).ifPresent(chat -> {
            chat.setAnswer(result.getAnswer());
            chat.setStatus(result.isSuccess() ? ChatStatus.SUCCESS : ChatStatus.FAILED);
            chatRepository.save(chat);
        });
    }

}
