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

import com.alibaba.apiopenplatform.converter.ApiKeyConfigConverter;
import com.alibaba.apiopenplatform.converter.HmacConfigConverter;
import com.alibaba.apiopenplatform.converter.JwtConfigConverter;
import com.alibaba.apiopenplatform.support.consumer.ApiKeyConfig;
import com.alibaba.apiopenplatform.support.consumer.HmacConfig;
import com.alibaba.apiopenplatform.support.consumer.JwtConfig;
import lombok.Data;

import jakarta.persistence.*;

@Entity
@Table(name = "consumer_credential",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"consumer_id"}, name = "uk_consumer_id")
        })
@Data
public class ConsumerCredential extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "consumer_id", length = 64, nullable = false)
    private String consumerId;

    @Column(name = "apikey_config", columnDefinition = "json")
    @Convert(converter = ApiKeyConfigConverter.class)
    private ApiKeyConfig apiKeyConfig;

    @Column(name = "hmac_config", columnDefinition = "json")
    @Convert(converter = HmacConfigConverter.class)
    private HmacConfig hmacConfig;

    @Column(name = "jwt_config", columnDefinition = "json")
    @Convert(converter = JwtConfigConverter.class)
    private JwtConfig jwtConfig;
}
