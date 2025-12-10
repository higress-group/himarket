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

package com.alibaba.himarket.service;

import com.alibaba.himarket.core.event.PortalDeletingEvent;
import com.alibaba.himarket.dto.params.developer.CreateDeveloperParam;
import com.alibaba.himarket.dto.params.developer.CreateExternalDeveloperParam;
import com.alibaba.himarket.dto.params.developer.QueryDeveloperParam;
import com.alibaba.himarket.dto.params.developer.UpdateDeveloperParam;
import com.alibaba.himarket.dto.result.common.AuthResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.developer.DeveloperResult;
import com.alibaba.himarket.support.enums.DeveloperStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;

public interface DeveloperService {

    /**
     * Register a new developer
     *
     * @param param
     * @return
     */
    AuthResult registerDeveloper(CreateDeveloperParam param);

    /**
     * Create a new developer
     *
     * @param param
     * @return
     */
    DeveloperResult createDeveloper(CreateDeveloperParam param);

    /**
     * Login by username and password
     *
     * @param username
     * @param password
     * @return
     */
    AuthResult login(String username, String password);

    /**
     * If the developer exists
     *
     * @param developerId
     */
    void existsDeveloper(String developerId);

    /**
     * Get external developer info
     *
     * @param provider
     * @param subject
     * @return
     */
    DeveloperResult getExternalDeveloper(String provider, String subject);

    /**
     * Create a new developer by external identity
     *
     * @param param
     * @return
     */
    DeveloperResult createExternalDeveloper(CreateExternalDeveloperParam param);

    /**
     * Delete a developer
     *
     * @param developerId
     */
    void deleteDeveloper(String developerId);

    /**
     * Get a developer
     *
     * @param developerId
     * @return
     */
    DeveloperResult getDeveloper(String developerId);

    /**
     * List developers in a portal
     *
     * @param param
     * @param pageable
     * @return
     */
    PageResult<DeveloperResult> listDevelopers(QueryDeveloperParam param, Pageable pageable);

    /**
     * Set a developer status
     *
     * @param developerId
     * @param status
     * @return
     */
    void setDeveloperStatus(String developerId, DeveloperStatus status);

    /**
     * Reset password of a developer
     *
     * @param developerId
     * @param oldPassword
     * @param newPassword
     * @return
     */
    boolean resetPassword(String developerId, String oldPassword, String newPassword);

    /**
     * Update user info of a developer
     *
     * @param param
     */
    void updateProfile(UpdateDeveloperParam param);

    /**
     * Clean developer resources when portal is deleted
     *
     * @param event
     */
    void handlePortalDeletion(PortalDeletingEvent event);

    /**
     * Logout
     *
     * @param request
     */
    void logout(HttpServletRequest request);

    /**
     * Get current developer info
     *
     * @return
     */
    DeveloperResult getCurrentDeveloperInfo();

    /**
     * 当前开发者修改密码
     *
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 是否成功
     */
    boolean changeCurrentDeveloperPassword(String oldPassword, String newPassword);
}
