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

import com.alibaba.himarket.converter.EncryptedStringConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Entity
@Table(
        name = "sandbox_instance",
        uniqueConstraints = {
            @UniqueConstraint(
                    columnNames = {"sandbox_id"},
                    name = "uk_sandbox_id"),
            @UniqueConstraint(
                    columnNames = {"admin_id", "sandbox_name"},
                    name = "uk_admin_sandbox_name"),
            @UniqueConstraint(
                    columnNames = {"api_server"},
                    name = "uk_api_server"),
        })
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxInstance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sandbox_id", length = 64, nullable = false)
    private String sandboxId;

    @Column(name = "admin_id", length = 64, nullable = false)
    private String adminId;

    @Column(name = "sandbox_name", length = 64, nullable = false)
    private String sandboxName;

    @Column(name = "sandbox_type", length = 32, nullable = false)
    private String sandboxType;

    @Column(name = "cluster_attribute", columnDefinition = "json")
    private String clusterAttribute;

    @Column(name = "api_server", length = 256)
    private String apiServer;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "kube_config", columnDefinition = "text")
    private String kubeConfig;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "extra_config", columnDefinition = "json")
    private String extraConfig;

    @Column(name = "status", length = 32, nullable = false)
    private String status;

    @Column(name = "status_message", length = 512)
    private String statusMessage;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;
}
