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
import com.alibaba.himarket.dto.vendor.RemoteMcpItemResult;
import com.alibaba.himarket.service.vendor.McpVendorService;
import com.alibaba.himarket.support.enums.McpVendorType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "External Vendor Management", description = "External vendor MCP server resource APIs")
@RestController
@RequestMapping("/external-vendors")
@AdminAuth
@RequiredArgsConstructor
public class ExternalVendorController {

    private final McpVendorService mcpVendorService;

    @Operation(
            summary = "List MCP servers from an external marketplace",
            description = "Query remote MCP server resources from the selected external vendor")
    @GetMapping("/{vendorType}/mcp-servers")
    public PageResult<RemoteMcpItemResult> listRemoteMcpItems(
            @PathVariable McpVendorType vendorType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return mcpVendorService.listRemoteMcpItems(vendorType, keyword, page, size);
    }
}
