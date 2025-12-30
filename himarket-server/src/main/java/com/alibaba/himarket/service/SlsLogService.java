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

import com.alibaba.himarket.dto.params.sls.GenericSlsQueryRequest;
import com.alibaba.himarket.dto.params.sls.GenericSlsQueryResponse;
import com.alibaba.himarket.dto.params.sls.SlsCheckLogstoreRequest;
import com.alibaba.himarket.dto.params.sls.SlsCheckProjectRequest;
import com.alibaba.himarket.dto.params.sls.SlsCommonQueryRequest;

/** 通用SLS日志查询服务 支持多种认证方式和场景化查询 */
public interface SlsLogService {

    /**
     * 执行通用SQL查询
     *
     * @param request 查询请求
     * @return 查询结果
     */
    GenericSlsQueryResponse executeQuery(GenericSlsQueryRequest request);

    /**
     * 执行通用SQL查询
     *
     * @param request 查询请求
     * @return 查询结果
     */
    GenericSlsQueryResponse executeQuery(SlsCommonQueryRequest request);

    /**
     * 检查Project是否存在
     *
     * @param request 查询请求（包含认证信息）
     * @return 是否存在
     */
    Boolean checkProjectExists(SlsCheckProjectRequest request);

    /**
     * 检查Logstore是否存在
     *
     * @param request 查询请求（包含认证信息）
     * @return 是否存在
     */
    Boolean checkLogstoreExists(SlsCheckLogstoreRequest request);

    /**
     * 为全局日志的 logstore 更新索引 使用配置中心的 project 和 logstore
     *
     * @param userId 用户ID（用于STS认证）
     */
    void updateLogIndex(String userId);
}
