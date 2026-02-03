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
import com.alibaba.himarket.dto.result.admin.AdminResult;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.entity.Administrator;
import com.alibaba.himarket.repository.AdministratorRepository;
import com.alibaba.himarket.service.AdministratorService;
import com.alibaba.himarket.utils.IdGenerator;
import com.alibaba.himarket.utils.PasswordVerifier;
import com.alibaba.himarket.utils.TokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AdministratorServiceImpl implements AdministratorService {

    private final AdministratorRepository administratorRepository;

    private final ContextHolder contextHolder;

    @Override
    public AuthResult login(String username, String password) {
        Administrator admin =
                administratorRepository
                        .findByUsername(username)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND,
                                                Resources.ADMINISTRATOR,
                                                username));

        if (!PasswordVerifier.verify(password, admin.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid username or password");
        }

        String token = TokenUtil.generateAdminToken(admin.getAdminId());
        return AuthResult.of(token, TokenUtil.getTokenExpiresIn());
    }

    @Override
    public boolean needInit() {
        return administratorRepository.count() == 0;
    }

    @Override
    public AdminResult initAdmin(String username, String password) {
        Administrator admin =
                Administrator.builder()
                        .adminId(generateAdminId())
                        .username(username)
                        .passwordHash(PasswordVerifier.hash(password))
                        .build();
        administratorRepository.save(admin);
        return new AdminResult().convertFrom(admin);
    }

    @Override
    public AdminResult getAdministrator() {
        Administrator administrator = findAdministrator(contextHolder.getUser());
        return new AdminResult().convertFrom(administrator);
    }

    @Override
    @Transactional
    public void resetPassword(String oldPassword, String newPassword) {
        Administrator admin = findAdministrator(contextHolder.getUser());

        if (!PasswordVerifier.verify(oldPassword, admin.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid username or password");
        }

        admin.setPasswordHash(PasswordVerifier.hash(newPassword));
        administratorRepository.save(admin);
    }

    private String generateAdminId() {
        return IdGenerator.genAdministratorId();
    }

    private Administrator findAdministrator(String adminId) {
        return administratorRepository
                .findByAdminId(adminId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND, Resources.ADMINISTRATOR, adminId));
    }
}
