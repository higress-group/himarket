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

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.dto.params.setting.SaveAdminSettingParam;
import com.alibaba.himarket.dto.result.setting.AdminSettingResult;
import com.alibaba.himarket.entity.AdminSetting;
import com.alibaba.himarket.repository.AdminSettingRepository;
import com.alibaba.himarket.service.AdminSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSettingServiceImpl implements AdminSettingService {

    private static final int MAX_SETTING_KEY_LENGTH = 128;

    private final AdminSettingRepository adminSettingRepository;
    private final ContextHolder contextHolder;

    @Override
    @Transactional(readOnly = true)
    public AdminSettingResult getSetting(String settingKey) {
        String normalizedKey = normalizeSettingKey(settingKey);
        return adminSettingRepository
                .findByAdminIdAndSettingKey(contextHolder.getUser(), normalizedKey)
                .map(setting -> new AdminSettingResult().convertFrom(setting))
                .orElseGet(() -> AdminSettingResult.empty(normalizedKey));
    }

    @Override
    @Transactional
    public AdminSettingResult saveSetting(String settingKey, SaveAdminSettingParam param) {
        String normalizedKey = normalizeSettingKey(settingKey);
        String adminId = contextHolder.getUser();
        AdminSetting setting =
                adminSettingRepository
                        .findByAdminIdAndSettingKey(adminId, normalizedKey)
                        .orElseGet(
                                () ->
                                        AdminSetting.builder()
                                                .adminId(adminId)
                                                .settingKey(normalizedKey)
                                                .build());
        setting.setSettingValue(param.getSettingValue());
        return new AdminSettingResult().convertFrom(adminSettingRepository.save(setting));
    }

    private String normalizeSettingKey(String settingKey) {
        if (settingKey == null || settingKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Setting key cannot be blank");
        }
        String normalizedKey = settingKey.trim();
        if (normalizedKey.length() > MAX_SETTING_KEY_LENGTH) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER, "Setting key cannot exceed 128 characters");
        }
        return normalizedKey;
    }
}
