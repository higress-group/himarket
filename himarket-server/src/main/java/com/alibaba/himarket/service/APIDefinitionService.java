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

import com.alibaba.himarket.dto.params.api.CreateAPIDefinitionParam;
import com.alibaba.himarket.dto.params.api.PublishAPIParam;
import com.alibaba.himarket.dto.params.api.QueryAPIDefinitionParam;
import com.alibaba.himarket.dto.params.api.UpdateAPIDefinitionParam;
import com.alibaba.himarket.dto.result.api.APIDefinitionVO;
import com.alibaba.himarket.dto.result.api.APIEndpointVO;
import com.alibaba.himarket.dto.result.api.APIPublishHistoryVO;
import com.alibaba.himarket.dto.result.api.APIPublishRecordVO;
import com.alibaba.himarket.dto.result.common.PageResult;
import java.util.List;
import org.springframework.data.domain.Pageable;

/** API Definition 服务接口 */
public interface APIDefinitionService {

    /**
     * 创建 API Definition
     *
     * @param param 创建参数
     * @return API Definition 详情
     */
    APIDefinitionVO createAPIDefinition(CreateAPIDefinitionParam param);

    /**
     * 获取 API Definition 详情
     *
     * @param apiDefinitionId API Definition ID
     * @return API Definition 详情
     */
    APIDefinitionVO getAPIDefinition(String apiDefinitionId);

    /**
     * 查询 API Definition 列表
     *
     * @param param 查询参数
     * @param pageable 分页参数
     * @return API Definition 列表
     */
    PageResult<APIDefinitionVO> listAPIDefinitions(
            QueryAPIDefinitionParam param, Pageable pageable);

    /**
     * 更新 API Definition
     *
     * @param apiDefinitionId API Definition ID
     * @param param 更新参数
     * @return 更新后的 API Definition
     */
    APIDefinitionVO updateAPIDefinition(String apiDefinitionId, UpdateAPIDefinitionParam param);

    /**
     * 删除 API Definition
     *
     * @param apiDefinitionId API Definition ID
     */
    void deleteAPIDefinition(String apiDefinitionId);

    /**
     * 获取 API Definition 的端点列表
     *
     * @param apiDefinitionId API Definition ID
     * @return 端点列表
     */
    List<APIEndpointVO> listEndpoints(String apiDefinitionId);

    /**
     * 获取发布记录列表
     *
     * @param apiDefinitionId API Definition ID
     * @param pageable 分页参数
     * @return 发布记录列表
     */
    PageResult<APIPublishRecordVO> listPublishRecords(String apiDefinitionId, Pageable pageable);

    /**
     * 发布 API
     *
     * @param apiDefinitionId API Definition ID
     * @param param 发布参数
     * @return 发布记录
     */
    APIPublishRecordVO publishAPI(String apiDefinitionId, PublishAPIParam param);

    /**
     * 取消发布
     *
     * @param apiDefinitionId API Definition ID
     * @param recordId 发布记录 ID
     */
    void unpublishAPI(String apiDefinitionId, String recordId);

    /**
     * 获取发布历史列表
     *
     * @param apiDefinitionId API Definition ID
     * @param pageable 分页参数
     * @return 发布历史列表
     */
    PageResult<APIPublishHistoryVO> listPublishHistory(String apiDefinitionId, Pageable pageable);
}
