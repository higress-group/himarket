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

import com.alibaba.apiopenplatform.dto.params.portal.*;
import com.alibaba.apiopenplatform.dto.result.common.PageResult;
import com.alibaba.apiopenplatform.dto.result.portal.PortalResult;
import com.alibaba.apiopenplatform.dto.params.consumer.QuerySubscriptionParam;
import com.alibaba.apiopenplatform.dto.result.product.SubscriptionResult;
import com.alibaba.apiopenplatform.support.enums.SearchEngineType;
import com.alibaba.apiopenplatform.support.portal.SearchEngineConfig;
import org.springframework.data.domain.Pageable;

public interface PortalService {

    /**
     * 创建门户
     *
     * @param param
     * @return
     */
    PortalResult createPortal(CreatePortalParam param);

    /**
     * 查询门户
     *
     * @param portalId
     * @return
     */
    PortalResult getPortal(String portalId);

    /**
     * 检查门户是否存在
     *
     * @param portalId
     */
    void existsPortal(String portalId);

    /**
     * 查询门户列表
     *
     * @param pageable
     * @return
     */
    PageResult<PortalResult> listPortals(Pageable pageable);

    /**
     * 更新门户
     *
     * @param portalId
     * @param param
     * @return
     */
    PortalResult updatePortal(String portalId, UpdatePortalParam param);

    /**
     * 删除门户
     *
     * @param portalId
     */
    void deletePortal(String portalId);

    /**
     * 根据请求域名解析门户
     *
     * @param domain
     * @return
     */
    String resolvePortal(String domain);

    /**
     * 为门户绑定域名
     *
     * @param portalId
     * @param param
     * @return
     */
    PortalResult bindDomain(String portalId, BindDomainParam param);

    /**
     * 删除门户绑定域名
     *
     * @param portalId
     * @param domain
     * @return
     */
    PortalResult unbindDomain(String portalId, String domain);

    /**
     * 获取门户上的API产品订阅列表
     *
     * @param portalId 门户ID
     * @param param    查询参数
     * @param pageable 分页参数
     * @return PageResult of SubscriptionResult
     */
    PageResult<SubscriptionResult> listSubscriptions(String portalId, QuerySubscriptionParam param, Pageable pageable);

    /**
     * 获取默认门户
     *
     * @return
     */
    String getDefaultPortal();

    /**
     * 获取门户的Dashboard监控面板URL
     *
     * @param portalId 门户ID
     * @return Dashboard URL
     */
    String getDashboard(String portalId);

    // ========== 搜索引擎配置查询 ==========

    /**
     * 根据引擎类型获取 API Key（供搜索功能使用）
     * 核心方法：TalkSearchAbilityServiceGoogleImpl 将调用此方法
     *
     * @param portalId   Portal ID
     * @param engineType 搜索引擎类型
     * @return API Key（自动解密）
     * @throws com.alibaba.apiopenplatform.core.exception.BusinessException 如果未配置搜索引擎或搜索引擎未启用
     */
    String getSearchEngineApiKey(String portalId, SearchEngineType engineType);

    /**
     * 获取 Portal 的搜索引擎配置（供开发者查询）
     *
     * @param portalId Portal ID
     * @return 搜索引擎配置，如果未配置则返回 null
     */
    SearchEngineConfig getSearchEngineConfig(String portalId);
}
