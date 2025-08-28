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
import com.alibaba.apiopenplatform.entity.Product;
import com.alibaba.apiopenplatform.support.enums.ProductType;
import com.alibaba.apiopenplatform.support.enums.SourceType;
import com.alibaba.apiopenplatform.support.product.ProductIcon;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class CreateProductParam implements InputConverter<Product> {

    @NotBlank(message = "API产品名称不能为空")
    @Size(max = 50, message = "API产品名称长度不能超过50个字符")
    private String name;

    @Size(max = 256, message = "API产品描述长度不能超过256个字符")
    private String description;

    @NotNull(message = "API产品类型不能为空")
    private ProductType type;

    private String document;

    private ProductIcon icon;

    private String category;
}
