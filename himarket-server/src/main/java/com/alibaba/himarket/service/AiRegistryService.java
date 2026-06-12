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

import com.alibaba.himarket.dto.params.airegistry.CreateAiRegistryParam;
import com.alibaba.himarket.dto.params.airegistry.UpdateAiRegistryParam;
import com.alibaba.himarket.dto.result.airegistry.AiRegistryResult;
import com.alibaba.himarket.dto.result.common.PageResult;
import org.springframework.data.domain.Pageable;

public interface AiRegistryService {

    PageResult<AiRegistryResult> listAiRegistryInstances(Pageable pageable);

    AiRegistryResult getAiRegistryInstance(String airegistryId);

    AiRegistryResult getDefaultAiRegistryInstance();

    void createAiRegistryInstance(CreateAiRegistryParam param);

    void updateAiRegistryInstance(String airegistryId, UpdateAiRegistryParam param);

    void deleteAiRegistryInstance(String airegistryId);

    void setDefaultAiRegistry(String airegistryId, String namespaceId);

    void validateAiRegistry(String airegistryId, String namespaceId);
}
