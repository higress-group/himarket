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

package com.alibaba.apiopenplatform.entity;

import jakarta.persistence.*;

import com.alibaba.apiopenplatform.support.enums.DeveloperAuthType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "developer_external_identity", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"provider", "subject"}, name = "unique_provider_subject")
})
public class DeveloperExternalIdentity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "developer_id", referencedColumnName = "developerId", nullable = false)
    private Developer developer;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 128)
    private String subject;

    @Column(length = 128)
    private String displayName;

    @Column(length = 32)
    @Enumerated(EnumType.STRING)
    private DeveloperAuthType authType;

    @Column(columnDefinition = "json")
    private String rawInfoJson;
} 