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
import com.alibaba.himarket.entity.APIPublishRecord;
import com.alibaba.himarket.support.api.PublishConfig;
import com.alibaba.himarket.support.enums.PublishAction;
import com.alibaba.himarket.support.enums.PublishStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class APIPublishRecordVO implements OutputConverter<APIPublishRecordVO, APIPublishRecord> {

    private String recordId;

    private String apiDefinitionId;

    private String gatewayId;

    private String gatewayName;

    private String version;

    private PublishStatus status;

    private PublishAction action;

    private PublishConfig publishConfig;

    private String publishNote;

    private String operator;

    private Object snapshot;

    private String errorMessage;

    private String lastPublishVersion;

    private LocalDateTime lastPublishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Override
    public APIPublishRecordVO convertFrom(APIPublishRecord domain) {
        OutputConverter.super.convertFrom(domain);
        
        // Manual mapping for fields with different names
        this.createdAt = domain.getCreateAt();

        if (domain.getPublishConfig() != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                this.publishConfig = objectMapper.readValue(domain.getPublishConfig(), PublishConfig.class);
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize publish config", e);
            }
        }

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
