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
import com.alibaba.apiopenplatform.dto.result.PageResult;
import com.alibaba.apiopenplatform.dto.result.PortalResult;
import com.alibaba.apiopenplatform.dto.params.consumer.QuerySubscriptionParam;
import com.alibaba.apiopenplatform.dto.result.SubscriptionResult;
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
}
