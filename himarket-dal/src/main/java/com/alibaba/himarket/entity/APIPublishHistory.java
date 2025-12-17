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

import com.alibaba.himarket.support.enums.PublishAction;
import lombok.Data;

import jakarta.persistence.*;

/**
 * API Publish History 实体类
 */
@Entity
@Table(name = "api_publish_history",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"history_id"}, name = "uk_history_id")
        },
        indexes = {
                @Index(name = "idx_api_definition_id", columnList = "api_definition_id"),
                @Index(name = "idx_publish_record_id", columnList = "publish_record_id")
        })
@Data
public class APIPublishHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "history_id", length = 64, nullable = false)
    private String historyId;

    @Column(name = "api_definition_id", length = 64, nullable = false)
    private String apiDefinitionId;

    @Column(name = "publish_record_id", length = 64)
    private String publishRecordId;

    @Column(name = "action", length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    private PublishAction action;

    @Column(name = "gateway_id", length = 64, nullable = false)
    private String gatewayId;

    @Column(name = "gateway_name", length = 255)
    private String gatewayName;

    @Column(name = "gateway_type", length = 32)
    private String gatewayType;

    @Column(name = "version", length = 32)
    private String version;

    @Column(name = "publish_note", columnDefinition = "text")
    private String publishNote;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "operator", length = 64)
    private String operator;

    @Column(name = "snapshot", columnDefinition = "json")
    private String snapshot;
}
