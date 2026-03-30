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

import com.alibaba.himarket.dto.params.mcp.RegisterMcpParam;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.vendor.BatchImportResult;
import com.alibaba.himarket.dto.vendor.BatchImportResult.ImportItemStatus;
import com.alibaba.himarket.dto.vendor.RemoteMcpItem;
import com.alibaba.himarket.dto.vendor.RemoteMcpItemParam;
import com.alibaba.himarket.dto.vendor.RemoteMcpItemResult;
import com.alibaba.himarket.repository.McpServerMetaRepository;
import com.alibaba.himarket.service.McpServerService;
import com.alibaba.himarket.support.enums.McpOrigin;
import com.alibaba.himarket.support.enums.McpVendorType;
import java.util.ArrayList;
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
    private final McpServerService mcpServerService;
    private final McpServerMetaRepository metaRepository;

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
                        : metaRepository.findByMcpNameIn(allNames).stream()
                                .map(meta -> meta.getMcpName())
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

    @Override
    public BatchImportResult batchImport(McpVendorType vendorType, List<RemoteMcpItemParam> items) {

        McpVendorAdapter adapter = vendorAdapterRegistry.getAdapter(vendorType);

        // 批量预查询已存在的 mcpName，避免循环内 N+1
        Set<String> allMcpNames =
                items.stream()
                        .map(RemoteMcpItemParam::getMcpName)
                        .filter(name -> name != null && !name.isBlank())
                        .collect(Collectors.toSet());
        Set<String> existingMcpNames =
                allMcpNames.isEmpty()
                        ? Set.of()
                        : metaRepository.findByMcpNameIn(allMcpNames).stream()
                                .map(meta -> meta.getMcpName())
                                .collect(Collectors.toSet());

        int successCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        List<ImportItemStatus> details = new ArrayList<>();

        for (RemoteMcpItemParam item : items) {
            String mcpName = item.getMcpName();

            // 检查 mcpName 是否已存在
            if (existingMcpNames.contains(mcpName)) {
                skippedCount++;
                details.add(
                        ImportItemStatus.builder()
                                .mcpName(mcpName)
                                .status("SKIPPED")
                                .message("已存在，已跳过")
                                .build());
                log.info("批量导入跳过已存在的 MCP: {}", mcpName);
                continue;
            }

            try {
                // 导入前调详情 API 补充完整数据（connectionConfig、extraParams 等）
                RemoteMcpItem enriched = adapter.enrichForImport(toRemoteMcpItem(item));
                RegisterMcpParam param = buildRegisterParam(enriched);
                mcpServerService.registerMcp(param);
                successCount++;
                details.add(
                        ImportItemStatus.builder()
                                .mcpName(mcpName)
                                .status("SUCCESS")
                                .message(null)
                                .build());
                log.info("批量导入成功: {}", mcpName);
            } catch (Exception e) {
                failedCount++;
                details.add(
                        ImportItemStatus.builder()
                                .mcpName(mcpName)
                                .status("FAILED")
                                .message(e.getMessage())
                                .build());
                log.warn("批量导入失败: {}, 原因: {}", mcpName, e.getMessage(), e);
            }
        }

        return BatchImportResult.builder()
                .successCount(successCount)
                .skippedCount(skippedCount)
                .failedCount(failedCount)
                .details(details)
                .build();
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

    private RemoteMcpItem toRemoteMcpItem(RemoteMcpItemParam item) {
        return RemoteMcpItem.builder()
                .remoteId(item.getRemoteId())
                .mcpName(item.getMcpName())
                .displayName(item.getDisplayName())
                .description(item.getDescription())
                .protocolType(item.getProtocolType())
                .connectionConfig(item.getConnectionConfig())
                .tags(item.getTags())
                .icon(item.getIcon())
                .repoUrl(item.getRepoUrl())
                .extraParams(item.getExtraParams())
                .serviceIntro(null)
                .build();
    }

    private RegisterMcpParam buildRegisterParam(RemoteMcpItem item) {
        RegisterMcpParam param = new RegisterMcpParam();
        param.setMcpName(item.getMcpName());
        param.setDisplayName(item.getDisplayName());
        param.setDescription(item.getDescription());
        param.setProtocolType(item.getProtocolType());
        param.setConnectionConfig(item.getConnectionConfig());
        param.setTags(item.getTags());
        param.setIcon(item.getIcon());
        param.setRepoUrl(item.getRepoUrl());
        param.setExtraParams(item.getExtraParams());
        param.setServiceIntro(item.getServiceIntro());
        param.setOrigin(McpOrigin.VENDOR_IMPORT.name());
        param.setVisibility("PUBLIC");
        param.setPublishStatus("DRAFT");
        // 第三方导入的 MCP 默认需要沙箱托管
        param.setSandboxRequired(true);
        return param;
    }
}
