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
import com.alibaba.himarket.dto.params.admin.ResetPasswordParam;
import com.alibaba.himarket.dto.params.developer.*;
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
import org.springframework.web.bind.annotation.*;

@Tag(name = "开发者管理", description = "提供开发者认证、管理等功能")
@RestController
@RequestMapping("/developers")
@RequiredArgsConstructor
@Validated
public class DeveloperController {

    private final DeveloperService developerService;

    @Operation(summary = "开发者注册", description = "注册新开发者账号")
    @PostMapping
    public AuthResult register(@Valid @RequestBody CreateDeveloperParam param) {
        return developerService.registerDeveloper(param);
    }

    @Operation(summary = "开发者登录", description = "开发者账号密码登录")
    @PostMapping("/login")
    public AuthResult login(@Valid @RequestBody DeveloperLoginParam param) {
        return developerService.login(param.getUsername(), param.getPassword());
    }

    @Operation(summary = "开发者登出", description = "登出")
    @PostMapping("/logout")
    @DeveloperAuth
    public void logout(HttpServletRequest request) {
        developerService.logout(request);
    }

    @Operation(summary = "获取门户的开发者列表", description = "管理员功能：获取当前门户下所有开发者的分页列表")
    @GetMapping
    public PageResult<DeveloperResult> listDevelopers(
            QueryDeveloperParam param, Pageable pageable) {
        return developerService.listDevelopers(param, pageable);
    }

    @Operation(summary = "获取当前开发者信息", description = "开发者功能：获取当前登录开发者的个人信息")
    @GetMapping("/profile")
    @DeveloperAuth
    public DeveloperResult getCurrentDeveloperInfo() {
        return developerService.getCurrentDeveloperInfo();
    }

    @Operation(summary = "开发者修改密码", description = "修改当前登录开发者的密码")
    @PatchMapping("/password")
    @DeveloperAuth
    public String changePassword(@RequestBody ResetPasswordParam param) {
        developerService.changeCurrentDeveloperPassword(
                param.getOldPassword(), param.getNewPassword());
        return "修改密码成功";
    }

    @Operation(summary = "开发者更新个人信息", description = "开发者功能：更新当前登录开发者的个人信息")
    @PutMapping("/profile")
    @DeveloperAuth
    public void updateProfile(@Valid @RequestBody UpdateDeveloperParam param) {
        developerService.updateProfile(param);
    }

    @Operation(summary = "设置开发者状态", description = "管理员审核开发者账号，status为APPROVED/PENDING")
    @PatchMapping("/{developerId}/status")
    @AdminAuth
    public void setDeveloperStatus(
            @PathVariable("developerId") String developerId,
            @RequestBody UpdateDeveloperStatusParam param) {
        developerService.setDeveloperStatus(developerId, param.getStatus());
    }

    @Operation(summary = "删除Developer账号")
    @DeleteMapping("/{developerId}")
    @AdminAuth
    public void deleteDeveloper(@PathVariable("developerId") String developerId) {
        developerService.deleteDeveloper(developerId);
    }
}
