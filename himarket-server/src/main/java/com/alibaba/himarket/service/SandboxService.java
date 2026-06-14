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

import com.alibaba.himarket.dto.params.sandbox.ImportSandboxParam;
import com.alibaba.himarket.dto.params.sandbox.QuerySandboxParam;
import com.alibaba.himarket.dto.params.sandbox.UpdateSandboxParam;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.sandbox.ClusterInfoResult;
import com.alibaba.himarket.dto.result.sandbox.SandboxResult;
import com.alibaba.himarket.dto.result.sandbox.SandboxSimpleResult;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface SandboxService {

    /**
     * Lists active RUNNING sandboxes that support MCP hosting for portal use.
     */
    List<SandboxSimpleResult> listMcpCapableSandboxes();

    /**
     * Lists active RUNNING sandboxes for portal use, returning only ID and name.
     */
    List<SandboxSimpleResult> listActiveSandboxes();

    /**
     * Lists sandbox instances.
     */
    PageResult<SandboxResult> listSandboxes(QuerySandboxParam param, Pageable pageable);

    /**
     * Gets a sandbox instance by sandboxId.
     */
    SandboxResult getSandbox(String sandboxId);

    /**
     * Imports a sandbox instance.
     */
    void importSandbox(ImportSandboxParam param);

    /**
     * Updates a sandbox instance.
     */
    void updateSandbox(String sandboxId, UpdateSandboxParam param);

    /**
     * Deletes a sandbox instance.
     */
    void deleteSandbox(String sandboxId);

    /**
     * Fetches cluster information by parsing KubeConfig and listing namespaces.
     */
    ClusterInfoResult fetchClusterInfo(String kubeConfig);

    /**
     * Triggers a health check for one sandbox instance and returns the updated status.
     */
    SandboxResult healthCheck(String sandboxId);

    /**
     * Lists namespaces in the specified sandbox cluster.
     */
    List<String> listNamespaces(String sandboxId);

    /**
     * Counts active MCP deployments on a sandbox.
     */
    int countActiveDeployments(String sandboxId);
}
