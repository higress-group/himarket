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

import com.alibaba.himarket.entity.APIDefinition;
import com.alibaba.himarket.entity.APIEndpoint;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.support.api.PublishConfig;
import com.alibaba.himarket.support.enums.APIType;
import com.alibaba.himarket.support.enums.GatewayType;

import java.util.List;

/**
 * 网关发布器接口
 * 定义了将 API Definition 发布到网关的标准操作
 */
public interface GatewayPublisher {

    /**
     * 获取网关类型
     *
     * @return 网关类型
     */
    GatewayType getGatewayType();

    /**
     * 获取支持的 API 类型列表
     *
     * @return 支持的 API 类型
     */
    List<APIType> getSupportedAPITypes();

    /**
     * 检查是否支持指定的 API 类型
     *
     * @param apiType API 类型
     * @return 是否支持
     */
    default boolean supportsAPIType(APIType apiType) {
        return getSupportedAPITypes().contains(apiType);
    }

    /**
     * 发布 API Definition 到网关
     *
     * @param gateway       目标网关
     * @param apiDefinition API Definition
     * @param endpoints     Endpoints 列表
     * @param publishConfig 发布配置
     * @return 发布结果信息
     */
    String publish(Gateway gateway, APIDefinition apiDefinition,
                   List<APIEndpoint> endpoints, PublishConfig publishConfig);

    /**
     * 更新已发布的 API
     *
     * @param gateway       目标网关
     * @param apiDefinition API Definition
     * @param endpoints     Endpoints 列表
     * @param publishConfig 发布配置
     * @return 更新结果信息
     */
    String update(Gateway gateway, APIDefinition apiDefinition,
                  List<APIEndpoint> endpoints, PublishConfig publishConfig);

    /**
     * 从网关下线 API
     *
     * @param gateway       目标网关
     * @param apiDefinition API Definition
     * @param publishConfig 发布配置
     * @return 下线结果信息
     */
    String unpublish(Gateway gateway, APIDefinition apiDefinition, PublishConfig publishConfig);

    /**
     * 检查 API 是否已发布到网关
     *
     * @param gateway       目标网关
     * @param apiDefinition API Definition
     * @return 是否已发布
     */
    boolean isPublished(Gateway gateway, APIDefinition apiDefinition);

    /**
     * 验证发布配置的有效性
     *
     * @param apiDefinition API Definition
     * @param endpoints     Endpoints 列表
     * @param publishConfig 发布配置
     * @throws com.alibaba.himarket.core.exception.BusinessException 如果配置无效
     */
    void validatePublishConfig(APIDefinition apiDefinition,
                               List<APIEndpoint> endpoints,
                               PublishConfig publishConfig);
}
