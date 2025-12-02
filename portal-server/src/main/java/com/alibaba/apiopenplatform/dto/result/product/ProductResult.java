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

package com.alibaba.apiopenplatform.dto.result.product;

import com.alibaba.apiopenplatform.dto.converter.OutputConverter;
import com.alibaba.apiopenplatform.dto.result.ProductCategoryResult;
import com.alibaba.apiopenplatform.dto.result.httpapi.APIConfigResult;
import com.alibaba.apiopenplatform.dto.result.agent.AgentConfigResult;
import com.alibaba.apiopenplatform.dto.result.mcp.MCPConfigResult;
import com.alibaba.apiopenplatform.dto.result.model.ModelConfigResult;
import com.alibaba.apiopenplatform.entity.Product;
import com.alibaba.apiopenplatform.support.enums.ProductStatus;
import com.alibaba.apiopenplatform.support.enums.ProductType;
import com.alibaba.apiopenplatform.support.product.Icon;
import com.alibaba.apiopenplatform.support.product.ProductFeature;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductResult implements OutputConverter<ProductResult, Product> {

    private String productId;

    private String name;

    private String description;

    private ProductStatus status = ProductStatus.PENDING;

    private Boolean enableConsumerAuth = false;

    private ProductType type;

    private String document;

    private Icon icon;

    private List<ProductCategoryResult> categories;

    private Boolean autoApprove;

    private LocalDateTime createAt;

    private LocalDateTime updatedAt;

    private APIConfigResult apiConfig;

    private MCPConfigResult mcpConfig;

    private AgentConfigResult agentConfig;

    private ModelConfigResult modelConfig;

    private Boolean enabled;

    private ProductFeature feature;

    private Boolean isSubscribed;
}
