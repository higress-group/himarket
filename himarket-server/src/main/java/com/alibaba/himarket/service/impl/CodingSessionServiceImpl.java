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

import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.params.coding.CreateCodingSessionParam;
import com.alibaba.himarket.dto.params.coding.UpdateCodingSessionParam;
import com.alibaba.himarket.dto.result.coding.CodingSessionResult;
import com.alibaba.himarket.entity.CodingSession;
import com.alibaba.himarket.repository.CodingSessionRepository;
import com.alibaba.himarket.service.CodingSessionService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CodingSessionServiceImpl implements CodingSessionService {

    private final CodingSessionRepository codingSessionRepository;

    private final ContextHolder contextHolder;

    @Override
    public CodingSessionResult createSession(CreateCodingSessionParam param) {
        CodingSession session = param.convertTo();
        session.setSessionId(IdGenerator.genCodingSessionId());
        session.setUserId(contextHolder.getUser());

        codingSessionRepository.save(session);

        return toResult(session);
    }

    @Override
    public List<CodingSessionResult> listSessions() {
        List<CodingSession> sessions =
                codingSessionRepository.findByUserIdOrderByUpdatedAtDesc(contextHolder.getUser());

        return sessions.stream().map(this::toListResult).collect(Collectors.toList());
    }

    @Override
    public CodingSessionResult getSession(String sessionId) {
        CodingSession session = findUserSession(sessionId);
        return toResult(session);
    }

    @Override
    public CodingSessionResult updateSession(String sessionId, UpdateCodingSessionParam param) {
        CodingSession session = findUserSession(sessionId);
        param.update(session);

        codingSessionRepository.saveAndFlush(session);

        return toResult(session);
    }

    @Override
    public void deleteSession(String sessionId) {
        CodingSession session = findUserSession(sessionId);
        codingSessionRepository.delete(session);
    }

    private CodingSession findUserSession(String sessionId) {
        return codingSessionRepository
                .findBySessionIdAndUserId(sessionId, contextHolder.getUser())
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.CODING_SESSION, sessionId));
    }

    private CodingSessionResult toResult(CodingSession session) {
        return new CodingSessionResult().convertFrom(session);
    }

    /**
     * Convert to list result (without sessionData)
     */
    private CodingSessionResult toListResult(CodingSession session) {
        CodingSessionResult result = new CodingSessionResult().convertFrom(session);
        result.setSessionData(null);
        return result;
    }
}
