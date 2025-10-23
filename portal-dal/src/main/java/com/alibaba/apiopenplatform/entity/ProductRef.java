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

import com.alibaba.apiopenplatform.converter.APIGRefConfigConverter;
import com.alibaba.apiopenplatform.converter.HigressRefConfigConverter;
import com.alibaba.apiopenplatform.converter.NacosRefConfigConverter;
import com.alibaba.apiopenplatform.support.enums.SourceType;
import com.alibaba.apiopenplatform.support.product.APIGRefConfig;
import com.alibaba.apiopenplatform.support.product.HigressRefConfig;
import com.alibaba.apiopenplatform.support.product.NacosRefConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "product_ref")
@Data
public class ProductRef extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", length = 64, nullable = false)
    private String productId;

    @Column(name = "gateway_id", length = 64)
    private String gatewayId;

    @Column(name = "apig_ref_config", columnDefinition = "json")
    @Convert(converter = APIGRefConfigConverter.class)
    private APIGRefConfig apigRefConfig;

    @Column(name = "adp_ai_gateway_ref_config", columnDefinition = "json")
    @Convert(converter = APIGRefConfigConverter.class)
    private APIGRefConfig adpAIGatewayRefConfig;

    @Column(name = "apsara_gateway_ref_config", columnDefinition = "json")
    @Convert(converter = APIGRefConfigConverter.class)
    private APIGRefConfig apsaraGatewayRefConfig;

    @Column(name = "higress_ref_config", columnDefinition = "json")
    @Convert(converter = HigressRefConfigConverter.class)
    private HigressRefConfig higressRefConfig;

    @Column(name = "nacos_id", length = 64)
    private String nacosId;

    @Column(name = "nacos_ref_config", columnDefinition = "json")
    @Convert(converter = NacosRefConfigConverter.class)
    private NacosRefConfig nacosRefConfig;

    @Column(name = "source_type", length = 32)
    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    @Column(name = "api_config", columnDefinition = "json")
    private String apiConfig;

    @Column(name = "mcp_config", columnDefinition = "json")
    private String mcpConfig;

    @Column(name = "enabled")
    private Boolean enabled;
}
