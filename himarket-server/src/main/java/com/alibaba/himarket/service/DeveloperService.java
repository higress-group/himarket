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
     * Registers a new developer.
     *
     * @param param developer registration parameters
     * @return authentication result for the registered developer
     */
    AuthResult registerDeveloper(CreateDeveloperParam param);

    /**
     * Creates a new developer.
     *
     * @param param developer creation parameters
     * @return created developer information
     */
    DeveloperResult createDeveloper(CreateDeveloperParam param);

    /**
     * Authenticates a developer by username and password.
     *
     * @param username developer username
     * @param password developer password
     * @return authentication result with access token
     */
    AuthResult login(String username, String password);

    /**
     * Checks whether a developer exists.
     *
     * @param developerId developer ID
     */
    void existsDeveloper(String developerId);

    /**
     * Gets developer information from an external identity.
     *
     * @param provider identity provider name
     * @param subject unique subject identifier from the provider
     * @return developer information, or {@code null} if no developer matches the external identity
     */
    DeveloperResult getExternalDeveloper(String provider, String subject);

    /**
     * Creates a developer by external identity.
     *
     * @param param external developer creation parameters
     * @return created developer information
     */
    DeveloperResult createExternalDeveloper(CreateExternalDeveloperParam param);

    /**
     * Deletes a developer.
     *
     * @param developerId developer ID
     */
    void deleteDeveloper(String developerId);

    /**
     * Gets a developer.
     *
     * @param developerId developer ID
     * @return developer information
     */
    DeveloperResult getDeveloper(String developerId);

    /**
     * Lists developers.
     *
     * @param param developer query parameters
     * @param pageable pagination parameters
     * @return paged developer results
     */
    PageResult<DeveloperResult> listDevelopers(QueryDeveloperParam param, Pageable pageable);

    /**
     * Sets developer status.
     *
     * @param developerId developer ID
     * @param status developer status
     */
    void setDeveloperStatus(String developerId, DeveloperStatus status);

    /**
     * Resets a developer password.
     *
     * @param developerId developer ID
     * @param oldPassword current password
     * @param newPassword new password
     * @return {@code true} if the password is reset successfully
     */
    boolean resetPassword(String developerId, String oldPassword, String newPassword);

    /**
     * Updates the current developer profile.
     *
     * @param param profile update parameters
     */
    void updateProfile(UpdateDeveloperParam param);

    /**
     * Updates an external developer avatar URL on login.
     *
     * @param provider identity provider name
     * @param subject unique subject identifier from the provider
     * @param avatarUrl latest avatar URL
     */
    void updateExternalDeveloperAvatar(String provider, String subject, String avatarUrl);

    /**
     * Logs out the current developer.
     *
     * @param request HTTP request containing the access token
     */
    void logout(HttpServletRequest request);

    /**
     * Gets the current developer information.
     *
     * @return current developer information
     */
    DeveloperResult getCurrentDeveloperInfo();

    /**
     * Resets the current developer password.
     *
     * @param oldPassword current password
     * @param newPassword new password
     */
    void resetDeveloperPassword(String oldPassword, String newPassword);
}
