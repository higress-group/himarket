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

/**
 * Registry for looking up vendor adapters by {@link McpVendorType}.
 */
@Component
public class VendorAdapterRegistry {

    private final Map<McpVendorType, McpVendorAdapter> adapterMap;

    public VendorAdapterRegistry(List<McpVendorAdapter> adapters) {
        this.adapterMap =
                adapters.stream()
                        .collect(Collectors.toMap(McpVendorAdapter::getType, Function.identity()));
    }

    /**
     * Gets the adapter for a vendor type.
     *
     * @param type vendor type
     * @return matching adapter
     * @throws BusinessException when the type is not registered
     */
    public McpVendorAdapter getAdapter(McpVendorType type) {
        McpVendorAdapter adapter = adapterMap.get(type);
        if (adapter == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Unsupported vendor type: " + type);
        }
        return adapter;
    }
}
