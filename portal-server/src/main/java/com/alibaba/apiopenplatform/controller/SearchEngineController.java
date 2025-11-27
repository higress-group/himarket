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

import com.alibaba.apiopenplatform.core.annotation.DeveloperAuth;
import com.alibaba.apiopenplatform.core.security.ContextHolder;
import com.alibaba.apiopenplatform.dto.result.search.AvailableSearchEngineResult;
import com.alibaba.apiopenplatform.service.PortalService;
import com.alibaba.apiopenplatform.support.portal.SearchEngineConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 搜索引擎控制器
 * 提供开发者可用的搜索引擎查询功能
 */
@Tag(name = "搜索引擎", description = "开发者可用的搜索引擎查询")
@RestController
@RequestMapping("/search-engines")
@RequiredArgsConstructor
@Slf4j
public class SearchEngineController {

    private final PortalService portalService;
    private final ContextHolder contextHolder;

    /**
     * 获取当前 Portal 可用的搜索引擎配置
     * 供开发者在使用 talk 功能时查询是否有可用的联网搜索
     *
     * @return 可用的搜索引擎配置，如果未配置或已禁用则返回 null
     */
    @Operation(summary = "获取当前Portal可用的搜索引擎配置",
            description = "返回当前Portal已配置且启用的搜索引擎，供开发者使用。不包含敏感信息（API Key）")
    @GetMapping("/available")
    @DeveloperAuth
    public AvailableSearchEngineResult getAvailableSearchEngine() {
        // 从上下文中获取当前 Portal ID
        String portalId = contextHolder.getPortal();
        
        log.debug("Fetching available search engine for portal: {}", portalId);
        
        // 获取当前 Portal 的搜索引擎配置
        SearchEngineConfig config = portalService.getSearchEngineConfig(portalId);
        
        // 如果未配置或已禁用，返回 null
        if (config == null || !config.isEnabled()) {
            log.debug("No available search engine for portal: {}", portalId);
            return null;
        }
        
        log.debug("Found available search engine for portal: {}, type: {}", 
                portalId, config.getEngineType());
        
        return new AvailableSearchEngineResult(
                config.getEngineType(),
                config.getEngineName()
        );
    }
}

