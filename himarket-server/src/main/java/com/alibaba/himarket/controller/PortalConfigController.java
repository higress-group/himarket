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

import com.alibaba.himarket.core.annotation.PublicAccess;
import com.alibaba.himarket.service.PortalService;
import com.alibaba.himarket.support.portal.PortalUiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/portal-config")
@Tag(name = "门户配置（公开）")
@PublicAccess
@RequiredArgsConstructor
public class PortalConfigController {

    private final PortalService portalService;

    @Operation(summary = "获取门户 UI 配置")
    @GetMapping("/ui")
    public PortalUiConfig getUiConfig() {
        String portalId = portalService.getDefaultPortal();
        if (portalId == null) {
            return new PortalUiConfig();
        }
        try {
            var portalResult = portalService.getPortal(portalId);
            if (portalResult.getPortalUiConfig() == null) {
                return new PortalUiConfig();
            }
            return portalResult.getPortalUiConfig();
        } catch (Exception e) {
            return new PortalUiConfig();
        }
    }
}
