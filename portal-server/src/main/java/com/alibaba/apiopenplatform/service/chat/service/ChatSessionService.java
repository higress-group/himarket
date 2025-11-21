package com.alibaba.apiopenplatform.service.chat.service;

import com.alibaba.apiopenplatform.core.constant.Resources;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.core.security.ContextHolder;
import com.alibaba.apiopenplatform.core.utils.IdGenerator;
import com.alibaba.apiopenplatform.dto.result.common.PageResult;
import com.alibaba.apiopenplatform.entity.ChatSession;
import com.alibaba.apiopenplatform.service.ProductService;
import com.alibaba.apiopenplatform.service.chat.dto.*;
import com.alibaba.apiopenplatform.repository.ChatRepository;
import com.alibaba.apiopenplatform.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.apiopenplatform.entity.Chat;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author HiMarket Team
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChatSessionService {

    private final ChatSessionRepository sessionRepository;

    private final ChatRepository chatRepository;

    private final ProductService productService;

    private final ContextHolder contextHolder;

    /**
     * Allowed number of sessions per user
     */
    private static final int MAX_SESSIONS_PER_USER = 20;

    @Transactional
    public ChatSessionResult createSession(CreateChatSessionParam param) {
        // Check products exist
        productService.existsProducts(param.getProducts());

        String sessionId = IdGenerator.genSessionId();
        ChatSession session = param.convertTo();
        session.setUserId(contextHolder.getUser());
        session.setSessionId(sessionId);

        sessionRepository.save(session);
        cleanupExtraSessions();

        return getSession(sessionId);
    }

    public ChatSessionResult getSession(String sessionId) {
        ChatSession session = findSession(sessionId);
        return new ChatSessionResult().convertFrom(session);
    }

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

    public ChatSession findUserSession(String sessionId) {
        return sessionRepository.findBySessionIdAndUserId(sessionId, contextHolder.getUser())
                .orElseThrow(
                        () -> new BusinessException(ErrorCode.NOT_FOUND, Resources.CHAT_SESSION, sessionId)
                );
    }

    public PageResult<ChatSessionResult> listSessions(Pageable pageable) {
        Page<ChatSession> chatSessions = sessionRepository.findByUserId(contextHolder.getUser(), pageable);

        return new PageResult<ChatSessionResult>().convertFrom(chatSessions, chatSession -> new ChatSessionResult().convertFrom(chatSession));
    }

    public ChatSessionResult updateSession(String sessionId, UpdateChatSessionParam param) {
        ChatSession userSession = findUserSession(sessionId);
        param.update(userSession);

        sessionRepository.saveAndFlush(userSession);

        return getSession(sessionId);
    }

    public void deleteSession(String sessionId) {
        ChatSession session = findUserSession(sessionId);
        // TODO
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

    /**
     * Get all conversations for a session
     */
    public List<ConversationResult> getConversations(String sessionId) {
        // 1. Validate session access
        String userId = contextHolder.getUser();
        if (!chatRepository.existsBySessionIdAndUserId(sessionId, userId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "No access to this session");
        }
        
        // 2. Query data
        List<Chat> chats = chatRepository.findBySessionIdOrderByCreateAtAsc(sessionId);
        if (CollUtil.isEmpty(chats)) {
            return Collections.emptyList();
        }
        
        // 3. Group by conversationId
        Map<String, List<Chat>> conversationMap = chats.stream()
                .collect(Collectors.groupingBy(
                    Chat::getConversationId, 
                    LinkedHashMap::new, 
                    Collectors.toList()
                ));
        
        // 4. Build result list
        List<ConversationResult> results = new ArrayList<>();
        
        for (Map.Entry<String, List<Chat>> conversationEntry : conversationMap.entrySet()) {
            String conversationId = conversationEntry.getKey();
            List<Chat> conversationChats = conversationEntry.getValue();
            
            // Group by questionId
            Map<String, List<Chat>> questionMap = conversationChats.stream()
                    .collect(Collectors.groupingBy(
                        Chat::getQuestionId, 
                        LinkedHashMap::new, 
                        Collectors.toList()
                    ));
            
            // Build question list
            List<QuestionResult> questions = new ArrayList<>();
            for (Map.Entry<String, List<Chat>> questionEntry : questionMap.entrySet()) {
                String questionId = questionEntry.getKey();
                List<Chat> questionChats = questionEntry.getValue();
                Chat firstChat = questionChats.get(0);
                
                // Build answer groups
                List<AnswerGroupResult> answerGroups = buildAnswerGroups(questionChats);
                
                QuestionResult question = QuestionResult.builder()
                        .questionId(questionId)
                        .content(firstChat.getQuestion())
                        .createdAt(firstChat.getCreateAt())
                        .attachments(firstChat.getAttachments() != null ? 
                                   firstChat.getAttachments() : Collections.emptyList())
                        .answers(answerGroups)
                        .build();
                
                questions.add(question);
            }
            
            // Build conversation result
            ConversationResult conversation = ConversationResult.builder()
                    .conversationId(conversationId)
                    .createdAt(conversationChats.get(0).getCreateAt())
                    .updatedAt(conversationChats.get(conversationChats.size() - 1).getUpdatedAt())
                    .questions(questions)
                    .build();
            
            results.add(conversation);
        }
        
        return results;
    }

    /**
     * Build answer groups list (only keep this one helper method)
     */
    private List<AnswerGroupResult> buildAnswerGroups(List<Chat> questionChats) {
        // Group by answerId, filter out records without answers
        Map<String, List<Chat>> answerMap = questionChats.stream()
                .filter(chat -> StrUtil.isNotBlank(chat.getAnswerId()))
                .collect(Collectors.groupingBy(
                    Chat::getAnswerId, 
                    LinkedHashMap::new, 
                    Collectors.toList()
                ));
        
        List<AnswerGroupResult> answerGroups = new ArrayList<>();
        for (List<Chat> answerChats : answerMap.values()) {
            // Each answerId corresponds to all product answers
            List<AnswerResult> results = answerChats.stream()
                    .map(chat -> AnswerResult.builder()
                            .answerId(chat.getAnswerId())
                            .productId(chat.getProductId())
                            .content(chat.getAnswer())
                            .status(chat.getStatus())
                            .createdAt(chat.getCreateAt())
                            .build())
                    .collect(Collectors.toList());
            
            answerGroups.add(AnswerGroupResult.builder()
                    .results(results)
                    .build());
        }
        
        return answerGroups;
    }
}
