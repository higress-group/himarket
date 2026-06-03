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
import com.alibaba.himarket.dto.params.setting.SaveAdminSettingParam;
import com.alibaba.himarket.dto.result.setting.AdminSettingResult;
import com.alibaba.himarket.service.AdminSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Settings", description = "Administrator personal setting APIs")
@RestController
@RequestMapping("/admin-settings")
@RequiredArgsConstructor
@AdminAuth
public class AdminSettingController {

    private final AdminSettingService adminSettingService;

    @Operation(summary = "Get administrator setting")
    @GetMapping("/{settingKey}")
    public AdminSettingResult getSetting(@PathVariable String settingKey) {
        return adminSettingService.getSetting(settingKey);
    }

    @Operation(summary = "Save administrator setting")
    @PutMapping("/{settingKey}")
    public AdminSettingResult saveSetting(
            @PathVariable String settingKey, @RequestBody @Valid SaveAdminSettingParam param) {
        return adminSettingService.saveSetting(settingKey, param);
    }
}
