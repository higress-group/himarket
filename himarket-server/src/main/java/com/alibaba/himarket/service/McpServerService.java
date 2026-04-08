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

package com.alibaba.himarket.service;

import com.alibaba.himarket.dto.params.mcp.RegisterMcpParam;
import com.alibaba.himarket.dto.params.mcp.SaveMcpEndpointParam;
import com.alibaba.himarket.dto.params.mcp.SaveMcpMetaParam;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.mcp.McpEndpointResult;
import com.alibaba.himarket.dto.result.mcp.McpMetaResult;
import com.alibaba.himarket.dto.result.mcp.MyEndpointResult;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface McpServerService {

    /** 保存 MCP 元信息（创建或更新） */
    McpMetaResult saveMeta(SaveMcpMetaParam param);

    /** 注册 MCP Server：自动创建 Product + McpMeta + ProductRef */
    McpMetaResult registerMcp(RegisterMcpParam param);

    /** 根据 mcpServerId 获取元信息 */
    McpMetaResult getMeta(String mcpServerId);

    /** 根据 productId 获取该产品下所有 MCP 元信息 */
    List<McpMetaResult> listMetaByProduct(String productId);

    /** 批量获取多个产品的 MCP 元信息（含公共 endpoint 热数据） */
    List<McpMetaResult> listMetaByProductIds(List<String> productIds);

    /** 删除 MCP 元信息及其关联的所有 endpoint */
    void deleteMeta(String mcpServerId);

    /** 删除产品下所有 MCP 元信息、endpoint、ProductRef，并重置产品状态 */
    void deleteMetaByProduct(String productId);

    /**
     * 强制删除产品下所有 MCP 资源（跳过发布检查）。
     * 供 ProductService 删除产品时调用，此时 publication 已被清理。
     */
    void forceDeleteMetaByProduct(String productId);

    /** 保存 endpoint（创建或更新） */
    McpEndpointResult saveEndpoint(SaveMcpEndpointParam param);

    /** 获取指定 MCP Server 的所有 endpoint */
    List<McpEndpointResult> listEndpoints(String mcpServerId);

    /** 删除单个 endpoint */
    void deleteEndpoint(String endpointId);

    /** 市场列表：已发布且公开的 MCP Server（分页） */
    PageResult<McpMetaResult> listPublishedMcpServers(Pageable pageable);

    /** 查询当前用户拥有的所有 MCP endpoint（我的MCP） */
    List<MyEndpointResult> listMyEndpoints();

    /**
     * 根据 productId 列表解析当前用户的 MCP 传输配置。
     * 优先使用用户订阅的 endpoint（热数据），找不到则返回空（由调用方 fallback）。
     */
    List<MCPTransportConfig> resolveTransportConfigs(List<String> productIds, String userId);

    /** 根据 mcpName 获取元信息 */
    McpMetaResult getMetaByName(String mcpName);

    /** 分页查询指定来源的 MCP 元信息 */
    PageResult<McpMetaResult> listMetaByOrigin(String origin, Pageable pageable);

    /** 分页查询所有 MCP 元信息 */
    PageResult<McpMetaResult> listAllMeta(Pageable pageable);

    /** 分页查询指定来源的已发布 MCP 元信息（Open API 用，只返回关联产品已发布的记录） */
    PageResult<McpMetaResult> listPublishedMetaByOrigin(String origin, Pageable pageable);

    /** 分页查询所有已发布 MCP 元信息（Open API 用，只返回关联产品已发布的记录） */
    PageResult<McpMetaResult> listAllPublishedMeta(Pageable pageable);

    /**
     * 根据 mcpServerId 获取已发布的元信息（Open API 用）。
     * 如果关联产品未发布，抛出 NOT_FOUND 异常。
     */
    McpMetaResult getPublishedMeta(String mcpServerId);

    /**
     * 根据 mcpName 获取已发布的元信息（Open API 用）。
     * 如果关联产品未发布，抛出 NOT_FOUND 异常。
     */
    McpMetaResult getPublishedMetaByName(String mcpName);

    /** 刷新工具列表：连接 endpoint 获取 tools/list，保存到 meta.toolsConfig */
    McpMetaResult refreshTools(String mcpServerId);

    /** 更新服务介绍 */
    McpMetaResult updateServiceIntro(String mcpServerId, String serviceIntro);

    /** 更新工具配置（手动编辑） */
    McpMetaResult updateToolsConfig(String mcpServerId, String toolsConfig);

    /** 管理员手动部署沙箱：为已保存的 MCP 配置部署沙箱 endpoint */
    McpMetaResult deploySandbox(String mcpServerId, SaveMcpMetaParam param);

    /** 管理员取消沙箱托管：删除沙箱 CRD 和 endpoint */
    McpMetaResult undeploySandbox(String mcpServerId);
}
