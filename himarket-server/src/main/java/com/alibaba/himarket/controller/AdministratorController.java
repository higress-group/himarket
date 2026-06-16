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

import com.alibaba.himarket.core.annotation.AdminAuth;
import com.alibaba.himarket.dto.params.admin.CreateAdministratorParam;
import com.alibaba.himarket.dto.params.admin.ResetPasswordParam;
import com.alibaba.himarket.dto.params.login.LoginParam;
import com.alibaba.himarket.dto.result.admin.AdminResult;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.service.AdministratorService;
import com.alibaba.himarket.service.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Administrator Management",
        description = "Administrator initialization, login, logout, and password management APIs")
@RestController
@RequestMapping("/admins")
@RequiredArgsConstructor
@Validated
public class AdministratorController {

    private final AdministratorService administratorService;

    private final TokenService tokenService;

    @Operation(
            summary = "Log in as administrator",
            description = "Log in with username and password")
    @PostMapping("/login")
    public AuthResult login(@Valid @RequestBody LoginParam param) {
        return administratorService.login(param.getUsername(), param.getPassword());
    }

    @Operation(summary = "Log out administrator", description = "Revoke the current token")
    @PostMapping("/logout")
    @AdminAuth
    public void logout(HttpServletRequest request) {
        tokenService.revokeRequestToken(request);
    }

    @Operation(
            summary = "Check whether administrator initialization is required",
            description = "Returns whether the system needs the initial administrator account")
    @GetMapping("/need-init")
    public Boolean needInit() {
        return administratorService.needInit();
    }

    @Operation(
            summary = "Initialize administrator",
            description = "Creates the first administrator account with username and password")
    @PostMapping("/init")
    public AdminResult initAdmin(@Valid @RequestBody CreateAdministratorParam param) {
        return administratorService.initAdmin(param.getUsername(), param.getPassword());
    }

    @Operation(
            summary = "Change administrator password",
            description = "Change the password of the current administrator")
    @PatchMapping("/password")
    @AdminAuth
    public void resetPassword(@RequestBody ResetPasswordParam param) {
        administratorService.resetPassword(param.getOldPassword(), param.getNewPassword());
    }

    @Operation(
            summary = "Get current administrator",
            description = "Get administrator details from the current token")
    @GetMapping
    @AdminAuth
    public AdminResult getAdministrator() {
        return administratorService.getAdministrator();
    }
}
