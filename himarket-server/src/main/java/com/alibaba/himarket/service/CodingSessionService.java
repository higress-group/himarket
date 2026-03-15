package com.alibaba.himarket.service;

import com.alibaba.himarket.dto.params.coding.CreateCodingSessionParam;
import com.alibaba.himarket.dto.params.coding.UpdateCodingSessionParam;
import com.alibaba.himarket.dto.result.coding.CodingSessionResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import org.springframework.data.domain.Pageable;

public interface CodingSessionService {

    CodingSessionResult createSession(CreateCodingSessionParam param);

    PageResult<CodingSessionResult> listSessions(Pageable pageable);

    CodingSessionResult updateSession(String sessionId, UpdateCodingSessionParam param);

    void deleteSession(String sessionId);
}
