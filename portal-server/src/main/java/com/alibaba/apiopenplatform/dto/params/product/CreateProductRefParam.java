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

package com.alibaba.apiopenplatform.dto.params.product;

import com.alibaba.apiopenplatform.dto.converter.InputConverter;
import com.alibaba.apiopenplatform.entity.ProductRef;
import com.alibaba.apiopenplatform.support.enums.SourceType;
import com.alibaba.apiopenplatform.support.product.APIGRefConfig;
import com.alibaba.apiopenplatform.support.product.HigressRefConfig;
import com.alibaba.apiopenplatform.support.product.NacosRefConfig;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
public class CreateProductRefParam implements InputConverter<ProductRef> {

    @NotNull(message = "数据源类型不能为空")
    private SourceType sourceType;

    private String gatewayId;

    private String nacosId;

    private APIGRefConfig apigRefConfig;

    private APIGRefConfig adpAIGatewayRefConfig;

    private APIGRefConfig apsaraGatewayRefConfig;

    private HigressRefConfig higressRefConfig;

    private NacosRefConfig nacosRefConfig;

}