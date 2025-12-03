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

package com.alibaba.apiopenplatform.service;

import com.alibaba.apiopenplatform.core.event.PortalDeletingEvent;
import com.alibaba.apiopenplatform.dto.params.developer.CreateDeveloperParam;
import com.alibaba.apiopenplatform.dto.params.developer.CreateExternalDeveloperParam;
import com.alibaba.apiopenplatform.dto.params.developer.QueryDeveloperParam;
import com.alibaba.apiopenplatform.dto.params.developer.UpdateDeveloperParam;
import com.alibaba.apiopenplatform.dto.result.common.AuthResult;
import com.alibaba.apiopenplatform.dto.result.developer.DeveloperResult;
import com.alibaba.apiopenplatform.dto.result.common.PageResult;
import com.alibaba.apiopenplatform.support.enums.DeveloperStatus;
import org.springframework.data.domain.Pageable;

import jakarta.servlet.http.HttpServletRequest;

public interface DeveloperService {

    /**
     * 开发者注册
     *
     * @param param
     * @return
     */
    AuthResult registerDeveloper(CreateDeveloperParam param);

    /**
     * 创建开发者
     *
     * @param param
     * @return
     */
    DeveloperResult createDeveloper(CreateDeveloperParam param);

    /**
     * 开发者登录
     *
     * @param username
     * @param password
     * @return
     */
    AuthResult login(String username, String password);

    /**
     * 校验Developer
     *
     * @param developerId
     */
    void existsDeveloper(String developerId);

    /**
     * 获取外部开发者详情
     *
     * @param provider
     * @param subject
     * @return
     */
    DeveloperResult getExternalDeveloper(String provider, String subject);

    /**
     * 外部账号创建开发者
     *
     * @param param
     * @return
     */
    DeveloperResult createExternalDeveloper(CreateExternalDeveloperParam param);

    /**
     * 删除开发者账号（删除账号及所有外部身份）
     *
     * @param developerId
     */
    void deleteDeveloper(String developerId);

    /**
     * 查询开发者详情
     *
     * @param developerId
     * @return
     */
    DeveloperResult getDeveloper(String developerId);

    /**
     * 查询门户下的开发者列表
     *
     * @param param
     * @param pageable
     * @return
     */
    PageResult<DeveloperResult> listDevelopers(QueryDeveloperParam param, Pageable pageable);

    /**
     * 设置开发者状态
     *
     * @param developerId
     * @param status
     * @return
     */
    void setDeveloperStatus(String developerId, DeveloperStatus status);

    /**
     * 开发者修改密码
     *
     * @param developerId
     * @param oldPassword
     * @param newPassword
     * @return
     */
    boolean resetPassword(String developerId, String oldPassword, String newPassword);

    /**
     * 开发者更新个人信息
     *
     * @param param
     * @return
     */
    boolean updateProfile(UpdateDeveloperParam param);

    /**
     * 清理门户资源
     *
     * @param event
     */
    void handlePortalDeletion(PortalDeletingEvent event);

    /**
     * 开发者登出
     *
     * @param request HTTP请求
     */
    void logout(HttpServletRequest request);

    /**
     * 获取当前登录开发者信息
     *
     * @return 开发者信息
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