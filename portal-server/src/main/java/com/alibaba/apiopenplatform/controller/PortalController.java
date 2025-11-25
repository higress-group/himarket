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

package com.alibaba.apiopenplatform.controller;

import com.alibaba.apiopenplatform.core.annotation.AdminAuth;
import com.alibaba.apiopenplatform.dto.params.consumer.QuerySubscriptionParam;
import com.alibaba.apiopenplatform.dto.params.portal.*;
import com.alibaba.apiopenplatform.dto.result.common.PageResult;
import com.alibaba.apiopenplatform.dto.result.portal.PortalResult;
import com.alibaba.apiopenplatform.dto.result.product.SubscriptionResult;
import com.alibaba.apiopenplatform.service.PortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/portals")
@Slf4j
@Validated
@Tag(name = "门户管理")
@AdminAuth
@RequiredArgsConstructor
public class PortalController {

    private final PortalService portalService;

    @Operation(summary = "创建门户")
    @PostMapping
    public PortalResult createPortal(@Valid @RequestBody CreatePortalParam param) {
        return portalService.createPortal(param);
    }

    @Operation(summary = "获取门户详情")
    @GetMapping("/{portalId}")
    public PortalResult getPortal(@PathVariable String portalId) {
        return portalService.getPortal(portalId);
    }

    @Operation(summary = "获取门户列表")
    @GetMapping
    public PageResult<PortalResult> listPortals(Pageable pageable) {
        return portalService.listPortals(pageable);
    }

    @Operation(summary = "更新门户信息")
    @PutMapping("/{portalId}")
    public PortalResult updatePortal(@PathVariable String portalId, @Valid @RequestBody UpdatePortalParam param) {
        return portalService.updatePortal(portalId, param);
    }

    @Operation(summary = "删除门户")
    @DeleteMapping("/{portalId}")
    public void deletePortal(@PathVariable String portalId) {
        portalService.deletePortal(portalId);
    }

    @Operation(summary = "绑定域名")
    @PostMapping("/{portalId}/domains")
    public PortalResult bindDomain(@PathVariable String portalId, @Valid @RequestBody BindDomainParam param) {
        return portalService.bindDomain(portalId, param);
    }

    @Operation(summary = "解绑域名")
    @DeleteMapping("/{portalId}/domains/{domain}")
    public PortalResult unbindDomain(@PathVariable String portalId, @PathVariable String domain) {
        return portalService.unbindDomain(portalId, domain);
    }

    @Operation(summary = "获取门户上的API产品订阅列表")
    @GetMapping("/{portalId}/subscriptions")
    public PageResult<SubscriptionResult> listSubscriptions(@PathVariable String portalId,
                                                            QuerySubscriptionParam param,
                                                            Pageable pageable) {
        return portalService.listSubscriptions(portalId, param, pageable);
    }

    @Operation(summary = "获取门户Dashboard监控面板URL")
    @GetMapping("/{portalId}/dashboard")
    public String getDashboard(@PathVariable String portalId,
                               @RequestParam(required = false, defaultValue = "Portal") String type) {
        return portalService.getDashboard(portalId);
    }
}
