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

package com.alibaba.himarket.dto.result.product;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.dto.result.api.APIDefinitionVO;
import com.alibaba.himarket.entity.ProductRef;
import com.alibaba.himarket.support.enums.SourceType;
import com.alibaba.himarket.support.product.APIGRefConfig;
import com.alibaba.himarket.support.product.HigressRefConfig;
import com.alibaba.himarket.support.product.NacosRefConfig;
import java.util.List;
import lombok.Data;

@Data
public class ProductRefResult implements OutputConverter<ProductRefResult, ProductRef> {

    private String productId;

    private SourceType sourceType;

    private String gatewayId;

    private APIGRefConfig apigRefConfig;

    private APIGRefConfig adpAIGatewayRefConfig;

    private APIGRefConfig apsaraGatewayRefConfig;

    private HigressRefConfig higressRefConfig;

    private String nacosId;

    private NacosRefConfig nacosRefConfig;

    private List<String> apiDefinitionIds;

    private List<APIDefinitionVO> apiDefinitions;

    @Override
    public ProductRefResult convertFrom(ProductRef productRef) {
        BeanUtil.copyProperties(productRef, this, configOptions());
        if (StrUtil.isNotBlank(productRef.getApiDefinitionIds())) {
            this.apiDefinitionIds = JSONUtil.toList(productRef.getApiDefinitionIds(), String.class);
        }
        return this;
    }
}
