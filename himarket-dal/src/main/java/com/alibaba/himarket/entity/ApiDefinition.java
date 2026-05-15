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

import com.alibaba.himarket.converter.APIPoliciesConverter;
import com.alibaba.himarket.converter.ApiDefinitionMetaConverter;
import com.alibaba.himarket.converter.ApiSpecConverter;
import com.alibaba.himarket.support.api.meta.ApiDefinitionMeta;
import com.alibaba.himarket.support.api.policy.ApiPolicy;
import com.alibaba.himarket.support.api.spec.ApiSpec;
import com.alibaba.himarket.support.enums.ApiStatus;
import com.alibaba.himarket.support.enums.ApiType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "api_definition",
        uniqueConstraints = {
            @UniqueConstraint(
                    columnNames = {"api_definition_id"},
                    name = "uk_api_definition_id")
        })
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiDefinition extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_definition_id", length = 64, nullable = false)
    private String apiDefinitionId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "type", length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    private ApiType type;

    @Column(name = "status", length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ApiStatus status = ApiStatus.DRAFT;

    @Column(name = "version", length = 32)
    private String version;

    @Convert(converter = ApiSpecConverter.class)
    @Column(name = "spec", columnDefinition = "json")
    private ApiSpec spec;

    @Convert(converter = ApiDefinitionMetaConverter.class)
    @Column(name = "meta", columnDefinition = "json")
    private ApiDefinitionMeta meta;

    @Convert(converter = APIPoliciesConverter.class)
    @Column(name = "policies", columnDefinition = "json")
    private List<ApiPolicy> policies;
}
