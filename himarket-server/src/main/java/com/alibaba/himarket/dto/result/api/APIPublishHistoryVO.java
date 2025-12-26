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
import com.alibaba.himarket.entity.APIPublishHistory;
import com.alibaba.himarket.support.api.PublishConfig;
import com.alibaba.himarket.support.enums.PublishAction;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class APIPublishHistoryVO
        implements OutputConverter<APIPublishHistoryVO, APIPublishHistory> {

    private String historyId;

    private String apiDefinitionId;

    private String gatewayId;

    private PublishAction action;

    private String apiVersion;

    private PublishConfig publishConfig;

    private Boolean success;

    private String errorMessage;

    private String comment;

    private Object snapshot;

    private String operatorId;

    private LocalDateTime createdAt;

    @Override
    public APIPublishHistoryVO convertFrom(APIPublishHistory domain) {
        OutputConverter.super.convertFrom(domain);

        if (domain.getSnapshot() != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                this.snapshot = objectMapper.readValue(domain.getSnapshot(), Object.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize snapshot from JSON", e);
                this.snapshot = null;
            }
        }

        return this;
    }
}
