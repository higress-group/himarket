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

package com.alibaba.himarket.controller;

import com.alibaba.himarket.core.annotation.AdminAuth;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.vendor.BatchImportParam;
import com.alibaba.himarket.dto.vendor.BatchImportResult;
import com.alibaba.himarket.dto.vendor.RemoteMcpItemResult;
import com.alibaba.himarket.service.vendor.McpVendorService;
import com.alibaba.himarket.support.enums.McpVendorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** MCP 供应商导入管理接口，仅管理员可访问。 */
@Tag(name = "MCP 供应商导入")
@RestController
@RequestMapping("/admin/mcp-vendor")
@AdminAuth
@RequiredArgsConstructor
public class McpVendorController {

    private final McpVendorService mcpVendorService;

    @Operation(summary = "查询供应商 MCP 列表")
    @GetMapping("/mcp-list")
    public PageResult<RemoteMcpItemResult> listRemoteMcpItems(
            @RequestParam McpVendorType vendorType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return mcpVendorService.listRemoteMcpItems(vendorType, keyword, page, size);
    }

    @Operation(summary = "批量导入选中的 MCP Server")
    @PostMapping("/import")
    public BatchImportResult batchImport(@RequestBody @Valid BatchImportParam param) {
        return mcpVendorService.batchImport(param.getVendorType(), param.getItems());
    }
}
