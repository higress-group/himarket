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

package com.alibaba.apiopenplatform.controller;

import com.alibaba.apiopenplatform.core.annotation.AdminAuth;
import com.alibaba.apiopenplatform.core.utils.TokenUtil;
import com.alibaba.apiopenplatform.dto.params.admin.AdminCreateParam;
import com.alibaba.apiopenplatform.dto.params.admin.AdminLoginParam;
import com.alibaba.apiopenplatform.dto.params.admin.ResetPasswordParam;
import com.alibaba.apiopenplatform.dto.result.admin.AdminResult;
import com.alibaba.apiopenplatform.dto.result.common.AuthResult;
import com.alibaba.apiopenplatform.service.AdministratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

import jakarta.servlet.http.HttpServletRequest;

@Tag(name = "管理员管理", description = "管理员初始化、登录、修改密码等相关接口")
@RestController
@RequestMapping("/admins")
@RequiredArgsConstructor
@Validated
public class AdministratorController {

    private final AdministratorService administratorService;

    @Operation(summary = "管理员登录", description = "管理员登录，只需用户名和密码")
    @PostMapping("/login")
    public AuthResult login(@Valid @RequestBody AdminLoginParam param) {
        return administratorService.login(param.getUsername(), param.getPassword());
    }

    @Operation(summary = "管理员登出", description = "管理员登出，将当前token加入黑名单")
    @PostMapping("/logout")
    @AdminAuth
    public void logout(HttpServletRequest request) {
        TokenUtil.revokeToken(request);
    }

    @Operation(summary = "检查是否需要初始化管理员", description = "检查系统是否需要初始化管理员")
    @GetMapping("/need-init")
    public Boolean needInit() {
        return administratorService.needInit();
    }

    @Operation(summary = "初始化管理员", description = "仅允许首次调用，前端需传username和password")
    @PostMapping("/init")
    public AdminResult initAdmin(@Valid @RequestBody AdminCreateParam param) {
        return administratorService.initAdmin(param.getUsername(), param.getPassword());
    }

    @Operation(summary = "管理员修改密码", description = "修改当前登录管理员的密码")
    @PatchMapping("/password")
    @AdminAuth
    public void resetPassword(@RequestBody ResetPasswordParam param) {
        administratorService.resetPassword(param.getOldPassword(), param.getNewPassword());
    }

    @Operation(summary = "获取当前登录管理员信息", description = "根据token自动获取当前登录管理员的详细信息")
    @GetMapping
    @AdminAuth
    public AdminResult getAdministrator() {
        return administratorService.getAdministrator();
    }
} 