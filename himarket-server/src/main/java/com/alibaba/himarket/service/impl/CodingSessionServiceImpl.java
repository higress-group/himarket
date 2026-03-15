package com.alibaba.himarket.service.impl;

import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.params.coding.CreateCodingSessionParam;
import com.alibaba.himarket.dto.params.coding.UpdateCodingSessionParam;
import com.alibaba.himarket.dto.result.coding.CodingSessionResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.entity.CodingSession;
import com.alibaba.himarket.repository.CodingSessionRepository;
import com.alibaba.himarket.service.CodingSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CodingSessionServiceImpl implements CodingSessionService {

    private final CodingSessionRepository sessionRepository;
    private final ContextHolder contextHolder;

    private static final int MAX_SESSIONS_PER_USER = 50;

    @Override
    public CodingSessionResult createSession(CreateCodingSessionParam param) {
        String sessionId = IdGenerator.genSessionId();
        CodingSession session = param.convertTo();
        session.setUserId(contextHolder.getUser());
        session.setSessionId(sessionId);

        sessionRepository.save(session);
        cleanupExtraSessions();

        return new CodingSessionResult().convertFrom(session);
    }

    @Override
    public PageResult<CodingSessionResult> listSessions(Pageable pageable) {
        Page<CodingSession> sessions =
                sessionRepository.findByUserIdOrderByUpdatedAtDesc(
                        contextHolder.getUser(), pageable);

        return new PageResult<CodingSessionResult>()
                .convertFrom(sessions, session -> new CodingSessionResult().convertFrom(session));
    }

    @Override
    public CodingSessionResult updateSession(String sessionId, UpdateCodingSessionParam param) {
        CodingSession session = findUserSession(sessionId);
        param.update(session);

        sessionRepository.saveAndFlush(session);

        return new CodingSessionResult().convertFrom(session);
    }

    @Override
    public void deleteSession(String sessionId) {
        CodingSession session = findUserSession(sessionId);
        sessionRepository.delete(session);
    }

    private CodingSession findUserSession(String sessionId) {
        return sessionRepository
                .findBySessionIdAndUserId(sessionId, contextHolder.getUser())
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.CHAT_SESSION, sessionId));
    }

    private void cleanupExtraSessions() {
        int count = sessionRepository.countByUserId(contextHolder.getUser());
        if (count > MAX_SESSIONS_PER_USER) {
            sessionRepository
                    .findByUserIdOrderByUpdatedAtDesc(
                            contextHolder.getUser(),
                            Pageable.ofSize(1).withPage(MAX_SESSIONS_PER_USER))
                    .forEach(session -> sessionRepository.delete(session));
        }
    }
}
