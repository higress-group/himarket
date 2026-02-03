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

package com.alibaba.himarket.dto.result.api;

import com.alibaba.himarket.support.api.DeploymentConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API Deployment Snapshot
 *
 * <p>Complete snapshot of API deployment operation, including:
 * <ul>
 *   <li>API Definition at the time of deployment</li>
 *   <li>Deployment configuration used for this operation</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class APIDeploymentSnapshot {

    /**
     * API Definition snapshot at deployment time
     */
    private APIDefinitionResult apiDefinition;

    /**
     * Deployment configuration for this operation
     */
    private DeploymentConfig deploymentConfig;
}
