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

import com.alibaba.apiopenplatform.support.enums.ConsumerStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.ColumnDefault;

import jakarta.persistence.*;

@Entity
@Table(name = "consumer",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"consumer_id"}, name = "uk_consumer_id"),
                @UniqueConstraint(columnNames = {"name", "portal_id", "developer_id"},
                        name = "uk_name_portal_developer")
        })
@Data
@EqualsAndHashCode(callSuper = true)
public class Consumer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consumer_id", length = 64, nullable = false)
    private String consumerId;

    @Column(name = "name", length = 64, nullable = false)
    private String name;

    @Column(name = "description", length = 256)
    private String description;

//    @Enumerated(EnumType.STRING)
//    @Column(name = "status", length = 32, nullable = false)
//    private ConsumerStatus status;

    @Column(name = "portal_id", length = 64, nullable = false)
    private String portalId;

    @Column(name = "developer_id", length = 64, nullable = false)
    private String developerId;

    @Column(name = "is_primary")
    private Boolean isPrimary;
}
