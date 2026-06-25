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
import com.alibaba.himarket.core.annotation.DeveloperAuth;
import com.alibaba.himarket.core.annotation.PublicAccess;
import com.alibaba.himarket.dto.params.admin.ResetPasswordParam;
import com.alibaba.himarket.dto.params.developer.CreateDeveloperParam;
import com.alibaba.himarket.dto.params.developer.QueryDeveloperParam;
import com.alibaba.himarket.dto.params.developer.UpdateDeveloperParam;
import com.alibaba.himarket.dto.params.developer.UpdateDeveloperStatusParam;
import com.alibaba.himarket.dto.params.login.LoginParam;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.developer.DeveloperResult;
import com.alibaba.himarket.service.DeveloperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Developer Management", description = "Developer authentication and management APIs")
@RestController
@RequestMapping("/developers")
@RequiredArgsConstructor
@Validated
public class DeveloperController {

    private final DeveloperService developerService;

    @Operation(summary = "Register developer", description = "Register a new developer account")
    @PostMapping
    public AuthResult register(@Valid @RequestBody CreateDeveloperParam param) {
        return developerService.registerDeveloper(param);
    }

    @Operation(summary = "Log in as developer", description = "Log in with developer credentials")
    @PostMapping("/login")
    public AuthResult login(@Valid @RequestBody LoginParam param) {
        return developerService.login(param.getUsername(), param.getPassword());
    }

    @Operation(summary = "Log out developer")
    @PostMapping("/logout")
    @DeveloperAuth
    public void logout(HttpServletRequest request) {
        developerService.logout(request);
    }

    @Operation(
            summary = "List portal developers",
            description = "Administrator API for listing developers in the current portal")
    @GetMapping
    @AdminAuth
    public PageResult<DeveloperResult> listDevelopers(
            QueryDeveloperParam param, Pageable pageable) {
        return developerService.listDevelopers(param, pageable);
    }

    @Operation(
            summary = "Get current developer profile",
            description = "Returns the current developer profile, or null when not logged in")
    @GetMapping("/profile")
    @PublicAccess
    public DeveloperResult getCurrentDeveloperInfo() {
        return developerService.getCurrentDeveloperInfo();
    }

    @Operation(
            summary = "Change developer password",
            description = "Change the password of the current developer")
    @PatchMapping("/password")
    @DeveloperAuth
    public void changePassword(@Valid @RequestBody ResetPasswordParam param) {
        developerService.resetDeveloperPassword(param.getOldPassword(), param.getNewPassword());
    }

    @Operation(
            summary = "Update developer profile",
            description = "Update the profile of the current developer")
    @PutMapping("/profile")
    @DeveloperAuth
    public void updateProfile(@Valid @RequestBody UpdateDeveloperParam param) {
        developerService.updateProfile(param);
    }

    @Operation(
            summary = "Update developer status",
            description = "Administrator API for approving or resetting a developer account")
    @PatchMapping("/{developerId}/status")
    @AdminAuth
    public void setDeveloperStatus(
            @PathVariable("developerId") String developerId,
            @Valid @RequestBody UpdateDeveloperStatusParam param) {
        developerService.setDeveloperStatus(developerId, param.getStatus());
    }

    @Operation(summary = "Delete developer account")
    @DeleteMapping("/{developerId}")
    @AdminAuth
    public void deleteDeveloper(@PathVariable("developerId") String developerId) {
        developerService.deleteDeveloper(developerId);
    }
}
