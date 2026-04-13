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

package com.alibaba.himarket.service.vendor;

import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.support.enums.McpVendorType;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 供应商适配器注册表，按 {@link McpVendorType} 查找对应的适配器实例。 */
@Component
public class VendorAdapterRegistry {

    private final Map<McpVendorType, McpVendorAdapter> adapterMap;

    public VendorAdapterRegistry(List<McpVendorAdapter> adapters) {
        this.adapterMap =
                adapters.stream()
                        .collect(Collectors.toMap(McpVendorAdapter::getType, Function.identity()));
    }

    /**
     * 根据供应商类型获取对应的适配器。
     *
     * @param type 供应商类型
     * @return 对应的适配器实例
     * @throws BusinessException 如果该类型未注册
     */
    public McpVendorAdapter getAdapter(McpVendorType type) {
        McpVendorAdapter adapter = adapterMap.get(type);
        if (adapter == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "不支持的供应商类型: " + type);
        }
        return adapter;
    }
}
