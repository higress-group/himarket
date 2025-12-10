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

/** 管理员服务接口，定义管理员相关的核心操作方法 */
public interface AdministratorService {

    /**
     * 管理员登录
     *
     * @param username
     * @param password
     * @return
     */
    AuthResult login(String username, String password);

    /**
     * 管理员修改密码
     *
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    void resetPassword(String oldPassword, String newPassword);

    /**
     * 检查指定portalId下是否需要初始化管理员
     *
     * @return 是否需要初始化管理员
     */
    boolean needInit();

    /**
     * 初始化管理员，仅允许首次调用
     *
     * @param username 管理员用户名
     * @param password 管理员密码
     * @return 初始化成功的管理员信息
     */
    AdminResult initAdmin(String username, String password);

    /**
     * 获取当前登录管理员信息
     *
     * @return
     */
    AdminResult getAdministrator();
}
