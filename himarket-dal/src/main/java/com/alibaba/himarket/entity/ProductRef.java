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

import com.alibaba.himarket.converter.APIGRefConfigConverter;
import com.alibaba.himarket.converter.HigressRefConfigConverter;
import com.alibaba.himarket.converter.NacosRefConfigConverter;
import com.alibaba.himarket.converter.SofaHigressRefConfigConverter;
import com.alibaba.himarket.support.enums.SourceType;
import com.alibaba.himarket.support.product.APIGRefConfig;
import com.alibaba.himarket.support.product.HigressRefConfig;
import com.alibaba.himarket.support.product.NacosRefConfig;
import com.alibaba.himarket.support.product.SofaHigressRefConfig;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_ref")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    @Column(name = "sofa_higress_ref_config", columnDefinition = "json")
    @Convert(converter = SofaHigressRefConfigConverter.class)
    private SofaHigressRefConfig sofaHigressRefConfig;

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

    @Column(name = "agent_config", columnDefinition = "json")
    private String agentConfig;

    @Column(name = "model_config", columnDefinition = "json")
    private String modelConfig;

    @Column(name = "enabled")
    private Boolean enabled;

    @Column(name = "api_definition_id", length = 64)
    private String apiDefinitionId;

    /** 判断是否为托管的 API */
    public boolean isManaged() {
        return sourceType == SourceType.MANAGED;
    }

    /** 判断是否为导入的 API */
    public boolean isImported() {
        return sourceType != null && sourceType != SourceType.MANAGED;
    }

    /** 判断是否可编辑（仅托管 API 可编辑） */
    public boolean isEditable() {
        return isManaged();
    }
}
