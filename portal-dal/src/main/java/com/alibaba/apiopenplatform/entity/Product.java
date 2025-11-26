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

import com.alibaba.apiopenplatform.converter.IconConverter;
import com.alibaba.apiopenplatform.converter.ProductFeatureConverter;
import com.alibaba.apiopenplatform.support.enums.ProductStatus;
import com.alibaba.apiopenplatform.support.enums.ProductType;
import com.alibaba.apiopenplatform.support.product.Icon;
import com.alibaba.apiopenplatform.support.product.ProductFeature;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.persistence.*;

@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "product",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"product_id"}, name = "uk_product_id"),
                @UniqueConstraint(columnNames = {"name"}, name = "uk_name")
        })
@Data
public class Product extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", length = 64, nullable = false)
    private String productId;

    @Column(name = "admin_id", length = 64)
    private String adminId;

    @Column(name = "name", length = 64, nullable = false)
    private String name;

    @Column(name = "type", length = 64)
    @Enumerated(EnumType.STRING)
    private ProductType type;

    @Column(name = "description", length = 256)
    private String description;

    @Column(name = "enable_consumer_auth")
    private Boolean enableConsumerAuth;

    @Column(name = "document", columnDefinition = "longtext")
    private String document;

    @Column(name = "icon", columnDefinition = "json")
    @Convert(converter = IconConverter.class)
    private Icon icon;

    @Column(name = "status", length = 64)
    @Enumerated(EnumType.STRING)
    private ProductStatus status = ProductStatus.PENDING;

    @Column(name = "auto_approve")
    private Boolean autoApprove;

    @Column(name = "feature", columnDefinition = "json")
    @Convert(converter = ProductFeatureConverter.class)
    private ProductFeature feature;
}