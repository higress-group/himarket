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

import com.alibaba.himarket.dto.params.consumer.QuerySubscriptionParam;
import com.alibaba.himarket.dto.params.portal.BindDomainParam;
import com.alibaba.himarket.dto.params.portal.CreatePortalParam;
import com.alibaba.himarket.dto.params.portal.UpdatePortalParam;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.portal.PortalResult;
import com.alibaba.himarket.dto.result.product.ProductPublicationResult;
import com.alibaba.himarket.dto.result.product.SubscriptionResult;
import com.alibaba.himarket.support.enums.SearchEngineType;
import com.alibaba.himarket.support.portal.SearchEngineConfig;
import org.springframework.data.domain.Pageable;

public interface PortalService {

    /**
     * Create a portal
     *
     * @param param the portal creation parameters
     * @return the created portal result
     */
    PortalResult createPortal(CreatePortalParam param);

    /**
     * Get a portal
     *
     * @param portalId the portal ID
     * @return the portal result
     */
    PortalResult getPortal(String portalId);

    /**
     * Check if portal exists
     *
     * @param portalId the portal ID
     * @throws com.alibaba.himarket.core.exception.BusinessException if portal not found
     */
    void existsPortal(String portalId);

    /**
     * List portals with pagination
     *
     * @param pageable the pagination parameters
     * @return page result of portals
     */
    PageResult<PortalResult> listPortals(Pageable pageable);

    /**
     * Update a portal
     *
     * @param portalId the portal ID
     * @param param    the portal update parameters
     * @return the updated portal result
     */
    PortalResult updatePortal(String portalId, UpdatePortalParam param);

    /**
     * Delete a portal
     *
     * @param portalId the portal ID
     */
    void deletePortal(String portalId);

    /**
     * Resolve portal by domain
     *
     * @param domain the domain name
     * @return the portal ID or null if not found
     */
    String resolvePortal(String domain);

    /**
     * Bind domain to portal
     *
     * @param portalId the portal ID
     * @param param    the domain binding parameters
     * @return the updated portal result
     */
    PortalResult bindDomain(String portalId, BindDomainParam param);

    /**
     * Unbind domain from portal
     *
     * @param portalId the portal ID
     * @param domain   the domain name to unbind
     * @return the updated portal result
     */
    PortalResult unbindDomain(String portalId, String domain);

    /**
     * Get API product subscription list for portal
     *
     * @param portalId the portal ID
     * @param param    the query parameters
     * @param pageable the pagination parameters
     * @return page result of subscriptions
     */
    PageResult<SubscriptionResult> listSubscriptions(
            String portalId, QuerySubscriptionParam param, Pageable pageable);

    /**
     * Get published product list for portal
     *
     * @param portalId the portal ID
     * @param pageable the pagination parameters
     * @return page result of product publications
     */
    PageResult<ProductPublicationResult> getPublications(String portalId, Pageable pageable);

    /**
     * Get default portal
     *
     * @return the default portal ID or null if not found
     */
    String getDefaultPortal();

    /**
     * Get API Key by engine type for search functionality.
     *
     * @param portalId   the portal ID
     * @param engineType the search engine type
     * @return the API Key (automatically decrypted)
     * @throws com.alibaba.himarket.core.exception.BusinessException if search engine is not configured or disabled
     */
    String getSearchEngineApiKey(String portalId, SearchEngineType engineType);

    /**
     * Get search engine configuration for portal (for developer queries)
     *
     * @param portalId the portal ID
     * @return the search engine configuration, or null if not configured
     */
    SearchEngineConfig getSearchEngineConfig(String portalId);
}
