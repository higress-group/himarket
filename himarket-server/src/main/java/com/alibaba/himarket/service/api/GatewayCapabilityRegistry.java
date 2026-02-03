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

package com.alibaba.himarket.service.api;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.result.api.GatewayCapabilityVO;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.repository.GatewayRepository;
import com.alibaba.himarket.support.enums.APIType;
import com.alibaba.himarket.support.enums.GatewayType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 网关能力注册中心 管理所有可用的网关发布器，并提供能力发现功能 */
@Slf4j
@Component
public class GatewayCapabilityRegistry {

    private final Map<GatewayType, GatewayPublisher> publishers = new ConcurrentHashMap<>();
    private final GatewayRepository gatewayRepository;

    public GatewayCapabilityRegistry(
            GatewayRepository gatewayRepository, List<GatewayPublisher> publisherList) {
        this.gatewayRepository = gatewayRepository;
        publisherList.forEach(this::registerPublisher);
    }

    /**
     * 注册网关发布器
     *
     * @param publisher 网关发布器
     */
    public void registerPublisher(GatewayPublisher publisher) {
        publishers.put(publisher.getGatewayType(), publisher);
        log.info("Registered gateway publisher for type: {}", publisher.getGatewayType());
    }

    /**
     * 获取指定网关类型的发布器
     *
     * @param gatewayType 网关类型
     * @return 网关发布器
     */
    public GatewayPublisher getPublisher(GatewayType gatewayType) {
        GatewayPublisher publisher = publishers.get(gatewayType);
        if (publisher == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_PARAMETER,
                    "No publisher found for gateway type: " + gatewayType);
        }
        return publisher;
    }

    /**
     * 获取指定网关的发布器
     *
     * @param gateway 网关实体
     * @return 网关发布器
     */
    public GatewayPublisher getPublisher(Gateway gateway) {
        return getPublisher(gateway.getGatewayType());
    }

    /**
     * 获取指定网关的能力信息
     *
     * @param gateway 网关实体
     * @return 网关能力信息
     */
    public GatewayCapabilityVO getGatewayCapability(Gateway gateway) {
        GatewayPublisher publisher = getPublisher(gateway);

        GatewayCapabilityVO capability = new GatewayCapabilityVO();
        capability.setGatewayId(gateway.getGatewayId());
        capability.setGatewayName(gateway.getGatewayName());
        capability.setGatewayType(gateway.getGatewayType());
        capability.setSupportedAPITypes(publisher.getSupportedAPITypes());

        // 设置其他能力标识
        capability.setSupportsNacosRegistry(supportsNacosRegistry(gateway.getGatewayType()));
        capability.setSupportsSwaggerImport(true); // 所有网关都支持 Swagger 导入

        return capability;
    }

    /**
     * 获取所有网关的能力信息
     *
     * @return 所有网关能力列表
     */
    public List<GatewayCapabilityVO> getAllGatewayCapabilities() {
        List<Gateway> gateways = gatewayRepository.findAll();
        return gateways.stream().map(this::getGatewayCapability).collect(Collectors.toList());
    }

    /**
     * 检查网关是否支持 Nacos 注册
     *
     * @param gatewayType 网关类型
     * @return 是否支持
     */
    private boolean supportsNacosRegistry(GatewayType gatewayType) {
        // Higress 支持 Nacos 服务发现
        return gatewayType == GatewayType.HIGRESS;
    }

    /**
     * 检查网关是否支持指定的 API 类型
     *
     * @param gateway 网关实体
     * @param apiType API 类型
     * @return 是否支持
     */
    public boolean supportsAPIType(Gateway gateway, APIType apiType) {
        try {
            GatewayPublisher publisher = getPublisher(gateway);
            return publisher.supportsAPIType(apiType);
        } catch (BusinessException e) {
            log.warn("Gateway {} does not have a publisher", gateway.getGatewayId());
            return false;
        }
    }

    /**
     * 获取所有已注册的网关类型
     *
     * @return 网关类型列表
     */
    public List<GatewayType> getRegisteredGatewayTypes() {
        return new ArrayList<>(publishers.keySet());
    }
}
