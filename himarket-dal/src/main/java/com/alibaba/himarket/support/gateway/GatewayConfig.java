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

package com.alibaba.himarket.support.gateway;

import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.support.enums.GatewayType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayConfig {

    private GatewayType gatewayType;

    private APIGConfig apigConfig;

    private AdpAIGatewayConfig adpAIGatewayConfig;

    private ApsaraGatewayConfig apsaraGatewayConfig;

    private HigressConfig higressConfig;

    private SofaHigressConfig sofaHigressConfig;

    /** 网关实体引用，用于获取gatewayId等信息 */
    private Gateway gateway;
}
