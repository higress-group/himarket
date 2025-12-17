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

package com.alibaba.himarket.entity;

import com.alibaba.himarket.support.enums.PublishStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * API Publish Record 实体类
 */
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "api_publish_record",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"record_id"}, name = "uk_record_id")
        },
        indexes = {
                @Index(name = "idx_api_definition_id", columnList = "api_definition_id"),
                @Index(name = "idx_gateway_id", columnList = "gateway_id")
        })
@Data
public class APIPublishRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_id", length = 64, nullable = false)
    private String recordId;

    @Column(name = "api_definition_id", length = 64, nullable = false)
    private String apiDefinitionId;

    @Column(name = "gateway_id", length = 64, nullable = false)
    private String gatewayId;

    @Column(name = "gateway_name", length = 255)
    private String gatewayName;

    @Column(name = "gateway_type", length = 32, nullable = false)
    private String gatewayType;

    @Column(name = "status", length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    private PublishStatus status;

    @Column(name = "publish_config", columnDefinition = "json")
    private String publishConfig;

    @Column(name = "gateway_resource_id", length = 255)
    private String gatewayResourceId;

    @Column(name = "access_endpoint", length = 512)
    private String accessEndpoint;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "published_at", columnDefinition = "datetime(3)")
    private LocalDateTime publishedAt;

    @Column(name = "last_sync_at", columnDefinition = "datetime(3)")
    private LocalDateTime lastSyncAt;
}
