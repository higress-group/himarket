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

package com.alibaba.apiopenplatform.service.impl;

import cn.hutool.core.util.EnumUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.apiopenplatform.core.constant.Resources;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.core.security.ContextHolder;
import com.alibaba.apiopenplatform.core.utils.IdGenerator;
import com.alibaba.apiopenplatform.dto.params.gateway.ImportGatewayParam;
import com.alibaba.apiopenplatform.dto.params.gateway.QueryAPIGParam;
import com.alibaba.apiopenplatform.dto.params.gateway.QueryAdpAIGatewayParam;
import com.alibaba.apiopenplatform.dto.params.gateway.QueryGatewayParam;
import com.alibaba.apiopenplatform.dto.params.gateway.QueryApsaraGatewayParam;
import com.alibaba.apiopenplatform.dto.result.*;
import com.alibaba.apiopenplatform.entity.*;
import com.alibaba.apiopenplatform.repository.GatewayRepository;
import com.alibaba.apiopenplatform.repository.ProductRefRepository;
import com.alibaba.apiopenplatform.service.AdpAIGatewayService;
import com.alibaba.apiopenplatform.service.GatewayService;
import com.alibaba.apiopenplatform.service.gateway.GatewayOperator;
import com.alibaba.apiopenplatform.support.consumer.ConsumerAuthConfig;
import com.alibaba.apiopenplatform.support.enums.APIGAPIType;
import com.alibaba.apiopenplatform.support.enums.GatewayType;
import com.alibaba.apiopenplatform.support.gateway.GatewayConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@SuppressWarnings("unchecked")
@Slf4j
public class GatewayServiceImpl implements GatewayService, ApplicationContextAware, AdpAIGatewayService, com.alibaba.apiopenplatform.service.ApsaraGatewayService {

    private final GatewayRepository gatewayRepository;
    private final ProductRefRepository productRefRepository;

    private Map<GatewayType, GatewayOperator> gatewayOperators;

    private final ContextHolder contextHolder;

    @Override
    public PageResult<GatewayResult> fetchGateways(QueryAPIGParam param, int page, int size) {
        return gatewayOperators.get(param.getGatewayType()).fetchGateways(param, page, size);
    }

    @Override
    public PageResult<GatewayResult> fetchGateways(QueryAdpAIGatewayParam param, int page, int size) {
        return gatewayOperators.get(GatewayType.ADP_AI_GATEWAY).fetchGateways(param, page, size);
    }

    @Override
    public PageResult<GatewayResult> fetchGateways(QueryApsaraGatewayParam param, int page, int size) {
        return gatewayOperators.get(GatewayType.APSARA_GATEWAY).fetchGateways(param, page, size);
    }

    public void importGateway(ImportGatewayParam param) {
        gatewayRepository.findByGatewayId(param.getGatewayId())
                .ifPresent(gateway -> {
                    throw new BusinessException(ErrorCode.CONFLICT, StrUtil.format("{}:{}已存在", Resources.GATEWAY, param.getGatewayId()));
                });

        Gateway gateway = param.convertTo();
        if (gateway.getGatewayType().isHigress()) {
            gateway.setGatewayId(IdGenerator.genHigressGatewayId());
        }
        gateway.setAdminId(contextHolder.getUser());
        gatewayRepository.save(gateway);
    }

    @Override
    public GatewayResult getGateway(String gatewayId) {
        Gateway gateway = findGateway(gatewayId);

        return new GatewayResult().convertFrom(gateway);
    }

    @Override
    public PageResult<GatewayResult> listGateways(QueryGatewayParam param, Pageable pageable) {
        Page<Gateway> gateways = gatewayRepository.findAll(buildGatewaySpec(param), pageable);

        return new PageResult<GatewayResult>().convertFrom(gateways, gateway -> new GatewayResult().convertFrom(gateway));
    }

    @Override
    public void deleteGateway(String gatewayId) {
        Gateway gateway = findGateway(gatewayId);
        // 已有Product引用时不允许删除
        if (productRefRepository.existsByGatewayId(gatewayId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "网关已被Product引用");
        }

        gatewayRepository.delete(gateway);
    }

    @Override
    public PageResult<APIResult> fetchAPIs(String gatewayId, String apiType, int page, int size) {
        Gateway gateway = findGateway(gatewayId);
        GatewayType gatewayType = gateway.getGatewayType();

        if (gatewayType.isAPIG()) {
            APIGAPIType type = EnumUtil.fromString(APIGAPIType.class, apiType);
            switch (type) {
                case REST:
                    return fetchRESTAPIs(gatewayId, page, size);
                case HTTP:
                    return fetchHTTPAPIs(gatewayId, page, size);
                default:
            }
        }

        if (gatewayType.isHigress()) {
            return fetchRoutes(gatewayId, page, size);
        }

        throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                String.format("Gateway type %s does not support API type %s", gatewayType, apiType));
    }

    @Override
    public PageResult<APIResult> fetchHTTPAPIs(String gatewayId, int page, int size) {
        Gateway gateway = findGateway(gatewayId);
        return getOperator(gateway).fetchHTTPAPIs(gateway, page, size);
    }

    @Override
    public PageResult<APIResult> fetchRESTAPIs(String gatewayId, int page, int size) {
        Gateway gateway = findGateway(gatewayId);
        return getOperator(gateway).fetchRESTAPIs(gateway, page, size);
    }

    @Override
    public PageResult<APIResult> fetchRoutes(String gatewayId, int page, int size) {
        return null;
    }

    @Override
    public PageResult<GatewayMCPServerResult> fetchMcpServers(String gatewayId, int page, int size) {
        Gateway gateway = findGateway(gatewayId);
        return getOperator(gateway).fetchMcpServers(gateway, page, size);
    }

    @Override
    public String fetchAPIConfig(String gatewayId, Object config) {
        Gateway gateway = findGateway(gatewayId);
        return getOperator(gateway).fetchAPIConfig(gateway, config);
    }

    @Override
    public String fetchMcpConfig(String gatewayId, Object conf) {
        Gateway gateway = findGateway(gatewayId);
        return getOperator(gateway).fetchMcpConfig(gateway, conf);
    }

    @Override
    public String createConsumer(Consumer consumer, ConsumerCredential credential, GatewayConfig config) {
        return gatewayOperators.get(config.getGatewayType()).createConsumer(consumer, credential, config);
    }

    @Override
    public void updateConsumer(String gwConsumerId, ConsumerCredential credential, GatewayConfig config) {
        gatewayOperators.get(config.getGatewayType()).updateConsumer(gwConsumerId, credential, config);
    }

    @Override
    public void deleteConsumer(String gwConsumerId, GatewayConfig config) {
        gatewayOperators.get(config.getGatewayType()).deleteConsumer(gwConsumerId, config);
    }

    @Override
    public boolean isConsumerExists(String gwConsumerId, GatewayConfig config) {
        return gatewayOperators.get(config.getGatewayType()).isConsumerExists(gwConsumerId, config);
    }

    @Override
    public ConsumerAuthConfig authorizeConsumer(String gatewayId, String gwConsumerId, ProductRefResult productRef) {
        Gateway gateway = findGateway(gatewayId);
        Object refConfig;
        if (gateway.getGatewayType().isHigress()) {
            refConfig = productRef.getHigressRefConfig();
        } else if (gateway.getGatewayType().isAdpAIGateway()) {
            refConfig = productRef.getAdpAIGatewayRefConfig();
        } else if (gateway.getGatewayType().isApsaraGateway()) {
            refConfig = productRef.getApsaraGatewayRefConfig();
        } else {
            refConfig = productRef.getApigRefConfig();
        }
        return getOperator(gateway).authorizeConsumer(gateway, gwConsumerId, refConfig);
    }

    @Override
    public void revokeConsumerAuthorization(String gatewayId, String gwConsumerId, ConsumerAuthConfig config) {
        Gateway gateway = findGateway(gatewayId);

        getOperator(gateway).revokeConsumerAuthorization(gateway, gwConsumerId, config);
    }

    @Override
    public GatewayConfig getGatewayConfig(String gatewayId) {
        Gateway gateway = findGateway(gatewayId);

        return GatewayConfig.builder()
                .gatewayType(gateway.getGatewayType())
                .apigConfig(gateway.getApigConfig())
                .higressConfig(gateway.getHigressConfig())
                .adpAIGatewayConfig(gateway.getAdpAIGatewayConfig())
                .apsaraGatewayConfig(gateway.getApsaraGatewayConfig())
                .gateway(gateway)  // 添加Gateway实体引用
                .build();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        Map<String, GatewayOperator> operators = applicationContext.getBeansOfType(GatewayOperator.class);

        gatewayOperators = operators.values().stream()
                .collect(Collectors.toMap(
                        operator -> operator.getGatewayType(),
                        operator -> operator,
                        (existing, replacement) -> existing));
    }

    private Gateway findGateway(String gatewayId) {
        return gatewayRepository.findByGatewayId(gatewayId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, Resources.GATEWAY, gatewayId));
    }

    private GatewayOperator getOperator(Gateway gateway) {
        GatewayOperator gatewayOperator = gatewayOperators.get(gateway.getGatewayType());
        if (gatewayOperator == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "No gateway operator found for gateway type: " + gateway.getGatewayType());
        }
        return gatewayOperator;
    }

    @Override
    public String getDashboard(String gatewayId,String type) {
        Gateway gateway = findGateway(gatewayId);
        return getOperator(gateway).getDashboard(gateway,type); //type: Portal,MCP,API
    }

    private Specification<Gateway> buildGatewaySpec(QueryGatewayParam param) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (param != null && param.getGatewayType() != null) {
                predicates.add(cb.equal(root.get("gatewayType"), param.getGatewayType()));
            }

            String adminId = contextHolder.getUser();
            if (StrUtil.isNotBlank(adminId)) {
                predicates.add(cb.equal(root.get("adminId"), adminId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}