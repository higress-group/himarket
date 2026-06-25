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

import com.alibaba.himarket.dto.params.nacos.CreateNacosParam;
import com.alibaba.himarket.dto.params.nacos.QueryNacosParam;
import com.alibaba.himarket.dto.params.nacos.UpdateNacosParam;
import com.alibaba.himarket.dto.result.agent.NacosAgentResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.mcp.NacosMCPServerResult;
import com.alibaba.himarket.dto.result.nacos.MseNacosResult;
import com.alibaba.himarket.dto.result.nacos.NacosNamespaceResult;
import com.alibaba.himarket.dto.result.nacos.NacosResult;
import com.alibaba.himarket.dto.result.nacos.NacosSkillResult;
import com.alibaba.himarket.entity.NacosInstance;
import com.alibaba.himarket.support.product.NacosRefConfig;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import org.springframework.data.domain.Pageable;

/**
 * Service for Nacos instance management and Nacos-backed product configuration.
 */
public interface NacosService {

    /**
     * Lists imported Nacos instances.
     *
     * @param pageable paging parameters
     * @return paged Nacos instances
     */
    PageResult<NacosResult> listNacosInstances(Pageable pageable);

    /**
     * Gets a Nacos instance.
     *
     * @param nacosId Nacos instance ID
     * @return Nacos instance details
     */
    NacosResult getNacosInstance(String nacosId);

    /**
     * Creates an imported Nacos instance.
     *
     * @param param creation parameters
     */
    void createNacosInstance(CreateNacosParam param);

    /**
     * Updates a Nacos instance.
     *
     * @param nacosId Nacos instance ID
     * @param param update parameters
     */
    void updateNacosInstance(String nacosId, UpdateNacosParam param);

    /**
     * Deletes a Nacos instance.
     *
     * @param nacosId Nacos instance ID
     */
    void deleteNacosInstance(String nacosId);

    /**
     * Lists MCP servers from a Nacos instance.
     *
     * @param nacosId Nacos instance ID
     * @param namespaceId namespace ID, or empty to list from all namespaces
     * @param pageable paging parameters
     * @return paged MCP servers
     * @throws Exception when Nacos query fails
     */
    PageResult<NacosMCPServerResult> fetchMcpServers(
            String nacosId, String namespaceId, Pageable pageable) throws Exception;

    /**
     * Fetches MCP server configuration for product binding.
     *
     * @param nacosId Nacos instance ID
     * @param nacosRefConfig Nacos reference configuration
     * @return serialized MCP server configuration
     */
    String fetchMcpConfig(String nacosId, NacosRefConfig nacosRefConfig);

    /**
     * Lists Nacos clusters from Alibaba Cloud MSE.
     *
     * @param param MSE query parameters
     * @param pageable paging parameters
     * @return paged Nacos clusters
     */
    PageResult<MseNacosResult> fetchNacos(QueryNacosParam param, Pageable pageable);

    /**
     * Lists namespaces from an imported Nacos instance.
     *
     * @param nacosId Nacos instance ID
     * @param pageable paging parameters
     * @return paged namespaces
     * @throws Exception when Nacos query fails
     */
    PageResult<NacosNamespaceResult> fetchNamespaces(String nacosId, Pageable pageable)
            throws Exception;

    /**
     * Lists Agents from a Nacos instance.
     *
     * @param nacosId Nacos instance ID
     * @param namespaceId namespace ID, or empty to use the default namespace
     * @param pageable paging parameters
     * @return paged Agents
     * @throws Exception when Nacos query fails
     */
    PageResult<NacosAgentResult> fetchAgents(String nacosId, String namespaceId, Pageable pageable)
            throws Exception;

    /**
     * Lists Skills from a Nacos instance.
     *
     * @param nacosId Nacos instance ID
     * @param namespaceId namespace ID, or empty to use the public namespace
     * @param pageable paging parameters
     * @return paged Skills
     * @throws Exception when Nacos query fails
     */
    PageResult<NacosSkillResult> fetchSkills(String nacosId, String namespaceId, Pageable pageable)
            throws Exception;

    /**
     * Fetches Agent configuration for product binding.
     *
     * <p>This method uses the latest version and is not exposed as a REST endpoint.
     *
     * @param nacosId Nacos instance ID
     * @param nacosRefConfig Nacos reference configuration
     * @return serialized Agent configuration
     */
    String fetchAgentConfig(String nacosId, NacosRefConfig nacosRefConfig);

    /**
     * Gets the cached AiMaintainerService for a Nacos instance.
     *
     * @param nacosId Nacos instance ID
     * @return AiMaintainerService instance
     */
    AiMaintainerService getAiMaintainerService(String nacosId);

    /**
     * Finds the persisted Nacos instance entity.
     *
     * @param nacosId Nacos instance ID
     * @return Nacos instance entity
     */
    NacosInstance findNacosInstanceById(String nacosId);

    /**
     * Gets the default Nacos instance.
     *
     * @return default instance, or null when none exists
     */
    NacosResult getDefaultNacosInstance();

    /**
     * Sets the default Nacos instance and optionally stores a verified default namespace.
     *
     * @param nacosId instance ID to mark as default
     * @param namespaceId namespace ID to store as default, optional
     */
    void setDefaultNacos(String nacosId, String namespaceId);
}
