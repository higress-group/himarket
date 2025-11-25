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

import com.alibaba.apiopenplatform.converter.APIGConfigConverter;
import com.alibaba.apiopenplatform.converter.AdpAIGatewayConfigConverter;
import com.alibaba.apiopenplatform.converter.HigressConfigConverter;
import com.alibaba.apiopenplatform.converter.ApsaraGatewayConfigConverter;
import com.alibaba.apiopenplatform.support.enums.GatewayType;
import com.alibaba.apiopenplatform.support.gateway.APIGConfig;
import com.alibaba.apiopenplatform.support.gateway.AdpAIGatewayConfig;
import com.alibaba.apiopenplatform.support.gateway.HigressConfig;
import com.alibaba.apiopenplatform.support.gateway.ApsaraGatewayConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.persistence.*;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "gateway",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"gateway_id"}, name = "uk_gateway_id"),
        })
@Data
public class Gateway extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gateway_name", length = 64, nullable = false)
    private String gatewayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway_type", length = 32, nullable = false)
    private GatewayType gatewayType;

    @Column(name = "gateway_id", length = 64, nullable = false)
    private String gatewayId;

    @Column(name = "admin_id", length = 64, nullable = false)
    private String adminId;

    @Convert(converter = APIGConfigConverter.class)
    @Column(name = "apig_config", columnDefinition = "json")
    private APIGConfig apigConfig;

    @Convert(converter = AdpAIGatewayConfigConverter.class)
    @Column(name = "adp_ai_gateway_config", columnDefinition = "json")
    private AdpAIGatewayConfig adpAIGatewayConfig;

    @Convert(converter = HigressConfigConverter.class)
    @Column(name = "higress_config", columnDefinition = "json")
    private HigressConfig higressConfig;

    @Convert(converter = ApsaraGatewayConfigConverter.class)
    @Column(name = "apsara_gateway_config", columnDefinition = "json")
    private ApsaraGatewayConfig apsaraGatewayConfig;
}
