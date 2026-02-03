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
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "api_deployment",
        uniqueConstraints = {
            @UniqueConstraint(
                    columnNames = {"deployment_id"},
                    name = "uk_deployment_id")
        },
        indexes = {
            @Index(name = "idx_api_definition_id", columnList = "api_definition_id"),
            @Index(name = "idx_gateway_id", columnList = "gateway_id")
        })
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class APIDeployment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deployment_id", length = 64, nullable = false)
    private String deploymentId;

    @Column(name = "api_definition_id", length = 64, nullable = false)
    private String apiDefinitionId;

    @Column(name = "gateway_id", length = 64, nullable = false)
    private String gatewayId;

    @Column(name = "version", length = 32)
    private String version;

    @Column(name = "status", length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    private PublishStatus status;

    @Column(name = "gateway_resource_config", columnDefinition = "json")
    private String gatewayResourceConfig;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "snapshot", columnDefinition = "json")
    private String snapshot;
}
