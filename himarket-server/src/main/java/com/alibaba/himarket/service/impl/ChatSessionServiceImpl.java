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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.event.ChatSessionDeletingEvent;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.params.chat.CreateChatSessionParam;
import com.alibaba.himarket.dto.params.chat.UpdateChatSessionParam;
import com.alibaba.himarket.dto.result.chat.ChatSessionResult;
import com.alibaba.himarket.dto.result.chat.ConversationResult_V1;
import com.alibaba.himarket.dto.result.chat.ProductConversationResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.entity.Chat;
import com.alibaba.himarket.entity.ChatSession;
import com.alibaba.himarket.repository.ChatRepository;
import com.alibaba.himarket.repository.ChatSessionRepository;
import com.alibaba.himarket.service.ChatSessionService;
import com.alibaba.himarket.service.ProductService;
import com.alibaba.himarket.support.chat.attachment.ChatAttachmentConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionRepository sessionRepository;

    private final ChatRepository chatRepository;

    private final ProductService productService;

    private final ContextHolder contextHolder;

    /**
     * Allowed number of sessions per user
     */
    private static final int MAX_SESSIONS_PER_USER = 20;

    @Override
    public ChatSessionResult createSession(CreateChatSessionParam param) {
        // Check products exist
        productService.existsProducts(param.getProducts());

        // TODO: Check whether the user has subscribed to the product.

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
        sessionRepository
                .findBySessionIdAndUserId(sessionId, contextHolder.getUser())
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.CHAT_SESSION, sessionId));
    }

    private ChatSession findSession(String sessionId) {
        return sessionRepository
                .findBySessionId(sessionId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.CHAT_SESSION, sessionId));
    }

    @Override
    public ChatSession findUserSession(String sessionId) {
        return sessionRepository
                .findBySessionIdAndUserId(sessionId, contextHolder.getUser())
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.CHAT_SESSION, sessionId));
    }

    @Override
    public PageResult<ChatSessionResult> listSessions(Pageable pageable) {
        Page<ChatSession> chatSessions =
                sessionRepository.findByUserId(contextHolder.getUser(), pageable);

        return new PageResult<ChatSessionResult>()
                .convertFrom(
                        chatSessions,
                        chatSession -> new ChatSessionResult().convertFrom(chatSession));
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

        SpringUtil.getApplicationContext().publishEvent(new ChatSessionDeletingEvent(sessionId));
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
            sessionRepository
                    .findFirstByUserId(
                            contextHolder.getUser(), Sort.by(Sort.Direction.ASC, "createAt"))
                    .ifPresent(session -> deleteSession(session.getSessionId()));
        }
    }

    @Override
    public List<ConversationResult_V1> listConversations(String sessionId) {
        List<Chat> chats =
                chatRepository.findAllBySessionIdAndUserId(
                        sessionId,
                        contextHolder.getUser(),
                        Sort.by(Sort.Direction.ASC, "createAt"));
        if (CollUtil.isEmpty(chats)) {
            return Collections.emptyList();
        }

        Map<String, List<Chat>> conversationMap = new LinkedHashMap<>();
        for (Chat chat : chats) {
            String conversationId =
                    Objects.requireNonNull(
                            chat.getConversationId(), "element cannot be mapped to a null key");
            conversationMap.computeIfAbsent(conversationId, key -> new ArrayList<>()).add(chat);
        }

        List<ConversationResult_V1> conversations = new ArrayList<>();
        for (Map.Entry<String, List<Chat>> entry : conversationMap.entrySet()) {
            conversations.add(
                    ConversationResult_V1.builder()
                            .conversationId(entry.getKey())
                            .questions(buildQuestions(entry.getValue()))
                            .build());
        }
        return conversations;
    }

    private List<ConversationResult_V1.QuestionResult> buildQuestions(
            List<Chat> conversationChats) {
        Map<String, List<Chat>> questionGroups = new LinkedHashMap<>();
        for (Chat chat : conversationChats) {
            String questionId =
                    Objects.requireNonNull(
                            chat.getQuestionId(), "element cannot be mapped to a null key");
            questionGroups.computeIfAbsent(questionId, key -> new ArrayList<>()).add(chat);
        }

        List<ConversationResult_V1.QuestionResult> questions = new ArrayList<>();
        for (Map.Entry<String, List<Chat>> e : questionGroups.entrySet()) {
            Chat firstChat = e.getValue().get(0);
            List<ChatAttachmentConfig> attachments = firstChat.getAttachments();
            if (attachments == null) {
                attachments = Collections.emptyList();
            }

            ConversationResult_V1.QuestionResult question =
                    ConversationResult_V1.QuestionResult.builder()
                            .questionId(e.getKey())
                            .content(firstChat.getQuestion())
                            .createdAt(firstChat.getCreateAt())
                            .attachments(attachments)
                            .answers(buildAnswerGroups(e.getValue()))
                            .build();

            questions.add(question);
        }

        return questions;
    }

    private List<ConversationResult_V1.AnswerGroupResult> buildAnswerGroups(
            List<Chat> questionChats) {
        Map<Integer, List<Chat>> sequenceGroups = new TreeMap<>();
        for (Chat chat : questionChats) {
            if (chat.getSequence() == null) {
                continue;
            }
            sequenceGroups.computeIfAbsent(chat.getSequence(), key -> new ArrayList<>()).add(chat);
        }

        List<ConversationResult_V1.AnswerGroupResult> answerGroups = new ArrayList<>();
        for (Map.Entry<Integer, List<Chat>> entry : sequenceGroups.entrySet()) {
            List<ConversationResult_V1.AnswerResult> answers = new ArrayList<>();
            for (Chat chat : entry.getValue()) {
                answers.add(
                        ConversationResult_V1.AnswerResult.builder()
                                .answerId(chat.getAnswerId())
                                .productId(chat.getProductId())
                                .content(chat.getAnswer())
                                .usage(chat.getChatUsage())
                                .build());
            }

            answerGroups.add(
                    ConversationResult_V1.AnswerGroupResult.builder()
                            .sequence(entry.getKey())
                            .results(answers)
                            .build());
        }
        return answerGroups;
    }

    @Override
    public List<ProductConversationResult> listConversationsV2(String sessionId) {
        // 1. Query all chats for the session
        List<Chat> chats =
                chatRepository.findAllBySessionIdAndUserId(
                        sessionId,
                        contextHolder.getUser(),
                        Sort.by(Sort.Direction.ASC, "createAt"));

        if (CollUtil.isEmpty(chats)) {
            return Collections.emptyList();
        }

        Map<String, List<Chat>> productGroups = new LinkedHashMap<>();
        for (Chat chat : chats) {
            String productId =
                    Objects.requireNonNull(
                            chat.getProductId(), "element cannot be mapped to a null key");
            productGroups.computeIfAbsent(productId, key -> new ArrayList<>()).add(chat);
        }

        List<ProductConversationResult> results = new ArrayList<>();
        for (Map.Entry<String, List<Chat>> entry : productGroups.entrySet()) {
            results.add(
                    ProductConversationResult.builder()
                            .productId(entry.getKey())
                            .conversations(buildProductConversations(entry.getValue()))
                            .build());
        }
        return results;
    }

    private List<ProductConversationResult.ConversationResult> buildProductConversations(
            List<Chat> productChats) {
        Map<String, List<Chat>> conversationGroups = new LinkedHashMap<>();
        for (Chat chat : productChats) {
            String conversationId =
                    Objects.requireNonNull(
                            chat.getConversationId(), "element cannot be mapped to a null key");
            conversationGroups.computeIfAbsent(conversationId, key -> new ArrayList<>()).add(chat);
        }

        List<ProductConversationResult.ConversationResult> conversations = new ArrayList<>();
        for (Map.Entry<String, List<Chat>> entry : conversationGroups.entrySet()) {
            conversations.add(
                    ProductConversationResult.ConversationResult.builder()
                            .conversationId(entry.getKey())
                            .questions(buildProductQuestions(entry.getValue()))
                            .build());
        }
        return conversations;
    }

    private List<ProductConversationResult.QuestionResult> buildProductQuestions(
            List<Chat> conversationChats) {
        Map<String, List<Chat>> questionGroups = new LinkedHashMap<>();
        for (Chat chat : conversationChats) {
            String questionId =
                    Objects.requireNonNull(
                            chat.getQuestionId(), "element cannot be mapped to a null key");
            questionGroups.computeIfAbsent(questionId, key -> new ArrayList<>()).add(chat);
        }

        List<ProductConversationResult.QuestionResult> questions = new ArrayList<>();
        for (Map.Entry<String, List<Chat>> entry : questionGroups.entrySet()) {
            Chat firstChat = entry.getValue().get(0);
            List<ChatAttachmentConfig> attachments = firstChat.getAttachments();
            if (attachments == null) {
                attachments = Collections.emptyList();
            }

            ProductConversationResult.QuestionResult question =
                    ProductConversationResult.QuestionResult.builder()
                            .questionId(entry.getKey())
                            .content(firstChat.getQuestion())
                            .createdAt(firstChat.getCreateAt())
                            .attachments(attachments)
                            .answers(buildProductAnswers(entry.getValue()))
                            .build();

            questions.add(question);
        }

        return questions;
    }

    private List<ProductConversationResult.AnswerResult> buildProductAnswers(
            List<Chat> questionChats) {
        Map<Integer, Chat> firstChatBySequence = new TreeMap<>();
        for (Chat chat : questionChats) {
            if (chat.getSequence() == null) {
                continue;
            }
            firstChatBySequence.putIfAbsent(chat.getSequence(), chat);
        }

        List<ProductConversationResult.AnswerResult> answers = new ArrayList<>();
        for (Map.Entry<Integer, Chat> entry : firstChatBySequence.entrySet()) {
            Chat chat = entry.getValue();
            answers.add(
                    ProductConversationResult.AnswerResult.builder()
                            .sequence(entry.getKey())
                            .answerId(chat.getAnswerId())
                            .content(chat.getAnswer())
                            .usage(chat.getChatUsage())
                            .toolCalls(chat.getToolCalls())
                            .build());
        }
        return answers;
    }
}
