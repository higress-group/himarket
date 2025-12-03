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

package com.alibaba.apiopenplatform.dto.params.gateway;

import cn.hutool.core.util.StrUtil;
import com.alibaba.apiopenplatform.dto.converter.InputConverter;
import com.alibaba.apiopenplatform.entity.Gateway;
import com.alibaba.apiopenplatform.support.enums.GatewayType;
import com.alibaba.apiopenplatform.support.gateway.APIGConfig;
import com.alibaba.apiopenplatform.support.gateway.AdpAIGatewayConfig;
import com.alibaba.apiopenplatform.support.gateway.HigressConfig;
import com.alibaba.apiopenplatform.support.gateway.ApsaraGatewayConfig;
import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class ImportGatewayParam implements InputConverter<Gateway> {

    @NotBlank(message = "网关名称不能为空")
    private String gatewayName;

    private String description;

    @NotNull(message = "网关类型不能为空")
    private GatewayType gatewayType;

    private String gatewayId;

    private APIGConfig apigConfig;

    private AdpAIGatewayConfig adpAIGatewayConfig;

    private ApsaraGatewayConfig apsaraGatewayConfig;

    private HigressConfig higressConfig;

    @AssertTrue(message = "网关配置无效")
    private boolean isGatewayConfigValid() {
        return (gatewayType.isAPIG() && !gatewayType.equals(GatewayType.ADP_AI_GATEWAY) && apigConfig != null && StrUtil.isNotBlank(gatewayId))
                || (gatewayType.equals(GatewayType.ADP_AI_GATEWAY) && adpAIGatewayConfig != null && StrUtil.isNotBlank(gatewayId))
                || (gatewayType.isApsaraGateway() && apsaraGatewayConfig != null && StrUtil.isNotBlank(gatewayId))
                || (gatewayType.isHigress() && higressConfig != null);
    }
}
