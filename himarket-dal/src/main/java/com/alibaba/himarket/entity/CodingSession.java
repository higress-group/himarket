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

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "coding_session",
        uniqueConstraints = {
            @UniqueConstraint(
                    columnNames = {"session_id"},
                    name = "uk_session_id")
        })
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodingSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true, length = 64)
    private String sessionId;

    @Column(name = "cli_session_id", nullable = false, length = 128)
    private String cliSessionId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "provider_key", length = 64)
    private String providerKey;

    @Column(name = "cwd", length = 512)
    private String cwd;

    @Column(name = "model_product_id", length = 64)
    private String modelProductId;

    @Column(name = "model_name", length = 128)
    private String modelName;
}
