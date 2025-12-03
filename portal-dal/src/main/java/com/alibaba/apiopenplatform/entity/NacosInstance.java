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

import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.persistence.*;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "nacos_instance",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"nacos_id"}, name = "uk_nacos_id"),
        })
@Data
public class NacosInstance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nacos_name", length = 64, nullable = false)
    private String nacosName;

    @Column(name = "nacos_id", length = 64, nullable = false)
    private String nacosId;

    @Column(name = "admin_id", length = 64, nullable = false)
    private String adminId;

    @Column(name = "server_url", length = 256, nullable = false)
    private String serverUrl;


    @Column(name = "username", length = 64)
    private String username;

    @Column(name = "password", length = 128)
    private String password;

    @Column(name = "access_key", length = 128)
    private String accessKey;

    @Column(name = "secret_key", length = 256)
    private String secretKey;

    @Column(name = "description", length = 512)
    private String description;
} 