package com.alibaba.apiopenplatform.service.impl;

import com.alibaba.apiopenplatform.core.constant.Resources;
import com.alibaba.apiopenplatform.core.event.ChatSessionDeletingEvent;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.core.security.ContextHolder;
import com.alibaba.apiopenplatform.core.utils.IdGenerator;
import com.alibaba.apiopenplatform.dto.result.common.PageResult;
import com.alibaba.apiopenplatform.entity.ChatSession;
import com.alibaba.apiopenplatform.service.ChatSessionService;
import com.alibaba.apiopenplatform.service.ProductService;
import com.alibaba.apiopenplatform.repository.ChatRepository;
import com.alibaba.apiopenplatform.repository.ChatSessionRepository;
import com.alibaba.apiopenplatform.dto.result.chat.ChatSessionResult;
import com.alibaba.apiopenplatform.dto.result.chat.ConversationResult_V1;
import com.alibaba.apiopenplatform.dto.result.chat.ProductConversationResult;
import com.alibaba.apiopenplatform.dto.params.chat.CreateChatSessionParam;
import com.alibaba.apiopenplatform.dto.params.chat.UpdateChatSessionParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.collection.CollUtil;
import com.alibaba.apiopenplatform.entity.Chat;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionRepository sessionRepository;

    private final ChatRepository chatRepository;

    private final ProductService productService;

    private final ContextHolder contextHolder;

    private final ApplicationEventPublisher eventPublisher;

    /**
     * Allowed number of sessions per user
     */
    private static final int MAX_SESSIONS_PER_USER = 20;

    @Override
    public ChatSessionResult createSession(CreateChatSessionParam param) {
        // Check products exist
        productService.existsProducts(param.getProducts());

        // TODO check if the user has subscribed to the product

        String sessionId = IdGenerator.genSessionId();
        ChatSession session = param.convertTo();
        session.setUserId(contextHolder.getUser());
        session.setSessionId(sessionId);

        sessionRepository.save(session);
        cleanupExtraSessions();

        return getSession(sessionId);
    }

    @Override
    public ChatSessionResult getSession(String sessionId) {
        ChatSession session = findSession(sessionId);
        return new ChatSessionResult().convertFrom(session);
    }

    @Override
    public void existsSession(String sessionId) {
        sessionRepository.findBySessionIdAndUserId(sessionId, contextHolder.getUser())
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.NOT_FOUND, Resources.CHAT_SESSION, sessionId)
                );
    }

    private ChatSession findSession(String sessionId) {
        return sessionRepository.findBySessionId(sessionId)
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.NOT_FOUND, Resources.CHAT_SESSION, sessionId)
                );
    }

    @Override
    public ChatSession findUserSession(String sessionId) {
        return sessionRepository.findBySessionIdAndUserId(sessionId, contextHolder.getUser())
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.NOT_FOUND, Resources.CHAT_SESSION, sessionId)
                );
    }

    @Override
    public PageResult<ChatSessionResult> listSessions(Pageable pageable) {
        Page<ChatSession> chatSessions = sessionRepository.findByUserId(contextHolder.getUser(), pageable);

        return new PageResult<ChatSessionResult>().convertFrom(chatSessions, chatSession -> new ChatSessionResult().convertFrom(chatSession));
    }

    @Override
    public ChatSessionResult updateSession(String sessionId, UpdateChatSessionParam param) {
        ChatSession userSession = findUserSession(sessionId);
        param.update(userSession);

        sessionRepository.saveAndFlush(userSession);

        return getSession(sessionId);
    }

    @Override
    public void deleteSession(String sessionId) {
        ChatSession session = findUserSession(sessionId);

        eventPublisher.publishEvent(new ChatSessionDeletingEvent(sessionId));
        sessionRepository.delete(session);
    }

//    public void updateStatus(String sessionId, ChatSessionStatus status) {
//        ChatSession session = findUserSession(sessionId);
//        session.setStatus(status);
//        sessionRepository.saveAndFlush(session);
//    }

    /**
     * Clean up extra sessions
     */
    private void cleanupExtraSessions() {
        long count = sessionRepository.countByUserId(contextHolder.getUser());
        if (count > MAX_SESSIONS_PER_USER) {
            // Delete the first session
            sessionRepository.findFirstByUserId(contextHolder.getUser(), Sort.by(Sort.Direction.ASC, "createAt"))
                    .ifPresent(session -> deleteSession(session.getSessionId()));
        }
    }

    @Override
    public List<ConversationResult_V1> listConversations(String sessionId) {
        List<Chat> chats = chatRepository.findAllBySessionIdAndUserId(
                sessionId, contextHolder.getUser(),
                Sort.by(Sort.Direction.ASC, "createAt")
        );
        if (CollUtil.isEmpty(chats)) {
            return Collections.emptyList();
        }

        // Group by conversation ID
        Map<String, List<Chat>> conversationMap = chats.stream()
                .collect(Collectors.groupingBy(
                        Chat::getConversationId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return conversationMap.entrySet().stream()
                .map(entry -> ConversationResult_V1.builder()
                        .conversationId(entry.getKey())
                        .questions(buildQuestions(entry.getValue()))
                        .build())
                .collect(Collectors.toList());
    }

    private List<ConversationResult_V1.QuestionResult> buildQuestions(List<Chat> conversationChats) {
        // Group by question ID
        Map<String, List<Chat>> questionGroups = conversationChats.stream()
                .collect(Collectors.groupingBy(
                        Chat::getQuestionId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // Build question results
        List<ConversationResult_V1.QuestionResult> questions = new ArrayList<>();
        for (Map.Entry<String, List<Chat>> e : questionGroups.entrySet()) {
            Chat firstChat = e.getValue().get(0);

            ConversationResult_V1.QuestionResult question = ConversationResult_V1.QuestionResult.builder()
                    .questionId(e.getKey())
                    .content(firstChat.getQuestion())
                    .createdAt(firstChat.getCreateAt())
                    .attachments(Optional.ofNullable(firstChat.getAttachments())
                            .orElse(Collections.emptyList()))
                    .answers(buildAnswerGroups(e.getValue()))
                    .build();

            questions.add(question);
        }

        return questions;
    }

    private List<ConversationResult_V1.AnswerGroupResult> buildAnswerGroups(List<Chat> questionChats) {
        // Group by sequence
        Map<Integer, List<Chat>> sequenceGroups = questionChats.stream()
                .filter(chat -> chat.getSequence() != null)
                .collect(Collectors.groupingBy(
                        Chat::getSequence,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // Build answer groups sorted by sequence
        return sequenceGroups.keySet().stream()
                .sorted()
                .map(sequence -> {
                    // Build answers for current sequence
                    List<ConversationResult_V1.AnswerResult> answers = sequenceGroups.get(sequence).stream()
                            .map(chat -> ConversationResult_V1.AnswerResult.builder()
                                    .answerId(chat.getAnswerId())
                                    .productId(chat.getProductId())
                                    .content(chat.getAnswer())
                                    .usage(chat.getChatUsage())
                                    .build())
                            .collect(Collectors.toList());

                    return ConversationResult_V1.AnswerGroupResult.builder()
                            .sequence(sequence)
                            .results(answers)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductConversationResult> listConversationsV2(String sessionId) {
        // 1. Query all chats for the session
        List<Chat> chats = chatRepository.findAllBySessionIdAndUserId(
                sessionId, contextHolder.getUser(),
                Sort.by(Sort.Direction.ASC, "createAt")
        );
        
        if (CollUtil.isEmpty(chats)) {
            return Collections.emptyList();
        }

        // 2. Group by productId
        Map<String, List<Chat>> productGroups = chats.stream()
                .collect(Collectors.groupingBy(
                        Chat::getProductId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // 3. Build result list for each product
        return productGroups.entrySet().stream()
                .map(entry -> {
                    String productId = entry.getKey();
                    List<Chat> productChats = entry.getValue();
                    
                    return ProductConversationResult.builder()
                            .productId(productId)
                            .conversations(buildProductConversations(productChats))
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<ProductConversationResult.ConversationResult> buildProductConversations(List<Chat> productChats) {
        // Group by conversationId
        Map<String, List<Chat>> conversationGroups = productChats.stream()
                .collect(Collectors.groupingBy(
                        Chat::getConversationId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return conversationGroups.entrySet().stream()
                .map(entry -> ProductConversationResult.ConversationResult.builder()
                        .conversationId(entry.getKey())
                        .questions(buildProductQuestions(entry.getValue()))
                        .build())
                .collect(Collectors.toList());
    }

    private List<ProductConversationResult.QuestionResult> buildProductQuestions(List<Chat> conversationChats) {
        // Group by questionId
        Map<String, List<Chat>> questionGroups = conversationChats.stream()
                .collect(Collectors.groupingBy(
                        Chat::getQuestionId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<ProductConversationResult.QuestionResult> questions = new ArrayList<>();
        for (Map.Entry<String, List<Chat>> entry : questionGroups.entrySet()) {
            Chat firstChat = entry.getValue().get(0);

            ProductConversationResult.QuestionResult question = ProductConversationResult.QuestionResult.builder()
                    .questionId(entry.getKey())
                    .content(firstChat.getQuestion())
                    .createdAt(firstChat.getCreateAt())
                    .attachments(Optional.ofNullable(firstChat.getAttachments())
                            .orElse(Collections.emptyList()))
                    .answers(buildProductAnswers(entry.getValue()))
                    .build();

            questions.add(question);
        }

        return questions;
    }

    private List<ProductConversationResult.AnswerResult> buildProductAnswers(List<Chat> questionChats) {
        // Group by sequence
        Map<Integer, List<Chat>> sequenceGroups = questionChats.stream()
                .filter(chat -> chat.getSequence() != null)
                .collect(Collectors.groupingBy(
                        Chat::getSequence,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // Build answers sorted by sequence
        return sequenceGroups.keySet().stream()
                .sorted()
                .map(sequence -> {
                    // Since we're grouping by product already, there should be only one chat per sequence
                    Chat chat = sequenceGroups.get(sequence).get(0);
                    
                    return ProductConversationResult.AnswerResult.builder()
                            .sequence(sequence)
                            .answerId(chat.getAnswerId())
                            .content(chat.getAnswer())
                            .usage(chat.getChatUsage())
                            .build();
                })
                .collect(Collectors.toList());
    }
}
