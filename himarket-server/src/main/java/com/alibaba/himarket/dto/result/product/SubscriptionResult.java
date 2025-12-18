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

import com.alibaba.himarket.dto.converter.OutputConverter;
import com.alibaba.himarket.entity.ProductSubscription;
import com.alibaba.himarket.support.enums.ProductType;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class SubscriptionResult
        implements OutputConverter<SubscriptionResult, ProductSubscription> {

    private String subscriptionId;

    private String productId;

    private String consumerId;

    private String status;

    private ProductType productType;

    private String productName;

    private String consumerName;

    private LocalDateTime createAt;

    private LocalDateTime updatedAt;
}
