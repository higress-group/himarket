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

import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.vendor.RemoteMcpItem;
import com.alibaba.himarket.dto.vendor.RemoteMcpItemResult;
import com.alibaba.himarket.entity.ApiDefinition;
import com.alibaba.himarket.repository.APIDefinitionRepository;
import com.alibaba.himarket.support.enums.ApiType;
import com.alibaba.himarket.support.enums.McpVendorType;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** {@link McpVendorService} 默认实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpVendorServiceImpl implements McpVendorService {

    private final VendorAdapterRegistry vendorAdapterRegistry;
    private final APIDefinitionRepository apiDefinitionRepository;

    @Override
    public PageResult<RemoteMcpItemResult> listRemoteMcpItems(
            McpVendorType vendorType, String keyword, int page, int size) {

        McpVendorAdapter adapter = vendorAdapterRegistry.getAdapter(vendorType);
        PageResult<RemoteMcpItem> remotePage = adapter.listMcpServers(keyword, page, size);

        // 收集本页所有 mcpName，批量查询平台已有记录
        Set<String> allNames =
                remotePage.getContent().stream()
                        .map(RemoteMcpItem::getMcpName)
                        .filter(name -> name != null && !name.isBlank())
                        .collect(Collectors.toSet());
        Set<String> existingNames =
                allNames.isEmpty()
                        ? Set.of()
                        : apiDefinitionRepository
                                .findByTypeAndNameIn(ApiType.MCP_SERVER, allNames)
                                .stream()
                                .map(ApiDefinition::getName)
                                .collect(Collectors.toSet());

        List<RemoteMcpItemResult> results =
                remotePage.getContent().stream()
                        .map(item -> toResult(item, existingNames))
                        .collect(Collectors.toList());

        return PageResult.of(
                results,
                remotePage.getNumber(),
                remotePage.getSize(),
                remotePage.getTotalElements());
    }

    private RemoteMcpItemResult toResult(RemoteMcpItem item, Set<String> existingNames) {
        RemoteMcpItemResult result = new RemoteMcpItemResult();
        result.setRemoteId(item.getRemoteId());
        result.setMcpName(item.getMcpName());
        result.setDisplayName(item.getDisplayName());
        result.setDescription(item.getDescription());
        result.setProtocolType(item.getProtocolType());
        result.setConnectionConfig(item.getConnectionConfig());
        result.setTags(item.getTags());
        result.setIcon(item.getIcon());
        result.setRepoUrl(item.getRepoUrl());
        result.setExtraParams(item.getExtraParams());
        result.setExistsInPlatform(existingNames.contains(item.getMcpName()));
        return result;
    }
}
