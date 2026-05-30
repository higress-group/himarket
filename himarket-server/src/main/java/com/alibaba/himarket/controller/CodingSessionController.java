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

package com.alibaba.himarket.controller;

import com.alibaba.himarket.core.annotation.DeveloperAuth;
import com.alibaba.himarket.dto.params.coding.CreateCodingSessionParam;
import com.alibaba.himarket.dto.params.coding.UpdateCodingSessionParam;
import com.alibaba.himarket.dto.result.coding.CodingSessionResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.service.CodingSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Cloud IDE Session Management", description = "HiCoding session CRUD APIs")
@RestController
@RequestMapping("/coding-sessions")
@RequiredArgsConstructor
@Validated
@DeveloperAuth
public class CodingSessionController {

    private final CodingSessionService codingSessionService;

    @Operation(summary = "Create Cloud IDE session")
    @PostMapping
    public CodingSessionResult createSession(@Valid @RequestBody CreateCodingSessionParam param) {
        return codingSessionService.createSession(param);
    }

    @Operation(summary = "List Cloud IDE sessions")
    @GetMapping
    public PageResult<CodingSessionResult> listSessions(Pageable pageable) {
        return codingSessionService.listSessions(pageable);
    }

    @Operation(summary = "Update Cloud IDE session")
    @PatchMapping("/{sessionId}")
    public CodingSessionResult updateSession(
            @PathVariable String sessionId, @Valid @RequestBody UpdateCodingSessionParam param) {
        return codingSessionService.updateSession(sessionId, param);
    }

    @Operation(summary = "Delete Cloud IDE session")
    @DeleteMapping("/{sessionId}")
    public void deleteSession(@PathVariable String sessionId) {
        codingSessionService.deleteSession(sessionId);
    }
}
