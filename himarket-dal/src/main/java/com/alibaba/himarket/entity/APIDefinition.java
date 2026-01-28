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

import com.alibaba.himarket.support.enums.APIStatus;
import com.alibaba.himarket.support.enums.APIType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** API Definition 实体类 */
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "api_definition",
        uniqueConstraints = {
            @UniqueConstraint(
                    columnNames = {"api_definition_id"},
                    name = "uk_api_definition_id")
        },
        indexes = {
            @Index(name = "idx_type", columnList = "type"),
            @Index(name = "idx_status", columnList = "status"),
            @Index(name = "idx_deleted_at", columnList = "deleted_at")
        })
@Data
public class APIDefinition extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_definition_id", length = 64, nullable = false)
    private String apiDefinitionId;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "type", length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    private APIType type;

    @Column(name = "status", length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    private APIStatus status = APIStatus.DRAFT;

    @Column(name = "version", length = 32)
    private String version;

    @Column(name = "base_path", length = 255)
    private String basePath;

    @Column(name = "properties", columnDefinition = "json")
    private String properties;

    @Column(name = "metadata", columnDefinition = "json")
    private String metadata;

    @Column(name = "deleted_at", columnDefinition = "datetime(3)")
    private LocalDateTime deletedAt;
}
