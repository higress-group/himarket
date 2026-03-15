package com.alibaba.himarket.controller;

import com.alibaba.himarket.core.annotation.DeveloperAuth;
import com.alibaba.himarket.dto.params.coding.CreateCodingSessionParam;
import com.alibaba.himarket.dto.params.coding.UpdateCodingSessionParam;
import com.alibaba.himarket.dto.result.coding.CodingSessionResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.service.CodingSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/coding-sessions")
@RequiredArgsConstructor
@Validated
@DeveloperAuth
public class CodingSessionController {

    private final CodingSessionService codingSessionService;

    @PostMapping
    public CodingSessionResult createSession(@Valid @RequestBody CreateCodingSessionParam param) {
        return codingSessionService.createSession(param);
    }

    @GetMapping
    public PageResult<CodingSessionResult> listSessions(Pageable pageable) {
        return codingSessionService.listSessions(pageable);
    }

    @PatchMapping("/{sessionId}")
    public CodingSessionResult updateSession(
            @PathVariable String sessionId, @Valid @RequestBody UpdateCodingSessionParam param) {
        return codingSessionService.updateSession(sessionId, param);
    }

    @DeleteMapping("/{sessionId}")
    public void deleteSession(@PathVariable String sessionId) {
        codingSessionService.deleteSession(sessionId);
    }
}
