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

import com.alibaba.apiopenplatform.support.enums.DomainType;
import com.alibaba.apiopenplatform.support.enums.ProtocolType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.persistence.*;

@Entity
@Table(name = "portal_domain",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"domain"}, name = "uk_domain")
        }
)
@Data
@EqualsAndHashCode(callSuper = true)
public class PortalDomain extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portal_id", length = 64, nullable = false)
    private String portalId;

    @Column(name = "domain", length = 128, nullable = false)
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 32, nullable = false)
    private DomainType type = DomainType.DEFAULT;

    @Column(name = "protocol", length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    private ProtocolType protocol = ProtocolType.HTTP;
}