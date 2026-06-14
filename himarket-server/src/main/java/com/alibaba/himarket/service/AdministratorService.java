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

import com.alibaba.himarket.dto.result.admin.AdminResult;
import com.alibaba.himarket.dto.result.common.AuthResult;

/**
 * Administrator service.
 */
public interface AdministratorService {

    /**
     * Logs in an administrator.
     *
     * @param username administrator username
     * @param password administrator password
     * @return authentication result
     */
    AuthResult login(String username, String password);

    /**
     * Resets the administrator password.
     *
     * @param oldPassword current password
     * @param newPassword new password
     */
    void resetPassword(String oldPassword, String newPassword);

    /**
     * Checks whether the administrator needs initialization.
     *
     * @return true if initialization is required
     */
    boolean needInit();

    /**
     * Initializes the administrator. This is only allowed for the first setup.
     *
     * @param username administrator username
     * @param password administrator password
     * @return initialized administrator information
     */
    AdminResult initAdmin(String username, String password);

    /**
     * Gets the current administrator information.
     *
     * @return administrator information
     */
    AdminResult getAdministrator();
}
