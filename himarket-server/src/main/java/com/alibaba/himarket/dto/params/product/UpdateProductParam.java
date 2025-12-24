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

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.dto.converter.InputConverter;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.product.Icon;
import com.alibaba.himarket.support.product.ProductFeature;
import jakarta.validation.constraints.AssertTrue;
import java.util.List;
import lombok.Data;

@Data
public class UpdateProductParam implements InputConverter<Product> {

    private String name;

    private String description;

    private ProductType type;

    private Boolean enableConsumerAuth;

    private String document;

    private Icon icon;

    private Boolean autoApprove;

    private List<String> categories;

    private ProductFeature feature;

    @AssertTrue(message = "Icon size cannot exceed 16KB")
    public boolean checkIcon() {
        if (icon == null || StrUtil.isBlank(icon.getValue())) {
            return true;
        }
        return icon.getValue().length() < 16 * 1024;
    }
}
