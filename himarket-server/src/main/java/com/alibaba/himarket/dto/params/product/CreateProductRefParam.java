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

package com.alibaba.himarket.dto.params.product;

import com.alibaba.himarket.dto.converter.InputConverter;
import com.alibaba.himarket.entity.ProductRef;
import com.alibaba.himarket.support.enums.SourceType;
import com.alibaba.himarket.support.product.APIGRefConfig;
import com.alibaba.himarket.support.product.HigressRefConfig;
import com.alibaba.himarket.support.product.NacosRefConfig;
import com.alibaba.himarket.support.product.SofaHigressRefConfig;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateProductRefParam implements InputConverter<ProductRef> {

    @NotNull(message = "Source type cannot be null")
    private SourceType sourceType;

    private String gatewayId;

    private String nacosId;

    private APIGRefConfig apigRefConfig;

    private APIGRefConfig adpAIGatewayRefConfig;

    private APIGRefConfig apsaraGatewayRefConfig;

    private HigressRefConfig higressRefConfig;

    private SofaHigressRefConfig sofaHigressRefConfig;

    private NacosRefConfig nacosRefConfig;

    /** API Definition ID (for MANAGED source type) 当前版本：只包含一个元素 */
    private String apiDefinitionId;
}
