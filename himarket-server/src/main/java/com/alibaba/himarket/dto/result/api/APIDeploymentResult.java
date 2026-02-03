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

import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.entity.APIDeployment;
import com.alibaba.himarket.support.enums.PublishStatus;
import com.alibaba.himarket.utils.JsonUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class APIDeploymentResult implements OutputConverter<APIDeploymentResult, APIDeployment> {

    private String deploymentId;

    private String apiDefinitionId;

    private String gatewayId;

    private String gatewayName;

    private String version;

    private PublishStatus status;

    private String gatewayResourceConfig;

    private String description;

    private Object snapshot;

    private String errorMessage;

    @Override
    public APIDeploymentResult convertFrom(APIDeployment domain) {
        OutputConverter.super.convertFrom(domain);

        if (domain.getSnapshot() != null) {
            try {
                this.snapshot = JsonUtil.parse(domain.getSnapshot(), Object.class);
            } catch (Exception e) {
                log.error("Failed to deserialize snapshot from JSON", e);
                this.snapshot = null;
            }
        }

        return this;
    }
}
