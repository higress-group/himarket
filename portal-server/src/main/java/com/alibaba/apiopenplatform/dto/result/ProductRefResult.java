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

package com.alibaba.apiopenplatform.dto.result;

import com.alibaba.apiopenplatform.dto.converter.OutputConverter;
import com.alibaba.apiopenplatform.entity.ProductRef;
import com.alibaba.apiopenplatform.support.enums.SourceType;
import com.alibaba.apiopenplatform.support.product.APIGRefConfig;
import com.alibaba.apiopenplatform.support.product.HigressRefConfig;
import com.alibaba.apiopenplatform.support.product.NacosRefConfig;
import lombok.Data;

@Data
public class ProductRefResult implements OutputConverter<ProductRefResult, ProductRef> {

    private String productId;

    private SourceType sourceType;

    private String gatewayId;

    private APIGRefConfig apigRefConfig;

    // 新增：ADP AI 网关引用配置（与 APIGRefConfig 结构一致）
    private APIGRefConfig adpAIGatewayRefConfig;

    // 新增：Apsara 网关引用配置（与 APIGRefConfig 结构一致）
    private APIGRefConfig apsaraGatewayRefConfig;

    private HigressRefConfig higressRefConfig;

    private String nacosId;

    private NacosRefConfig nacosRefConfig;
}