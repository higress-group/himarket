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

package com.alibaba.himarket.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.constant.Resources;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.core.security.ContextHolder;
import com.alibaba.himarket.core.utils.IdGenerator;
import com.alibaba.himarket.dto.params.mcp.RegisterMcpParam;
import com.alibaba.himarket.dto.params.mcp.SaveMcpEndpointParam;
import com.alibaba.himarket.dto.params.mcp.SaveMcpMetaParam;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.result.consumer.ConsumerResult;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.mcp.McpEndpointResult;
import com.alibaba.himarket.dto.result.mcp.McpMetaResult;
import com.alibaba.himarket.dto.result.mcp.MyEndpointResult;
import com.alibaba.himarket.entity.McpServerEndpoint;
import com.alibaba.himarket.entity.McpServerMeta;
import com.alibaba.himarket.entity.ProductRef;
import com.alibaba.himarket.entity.ProductSubscription;
import com.alibaba.himarket.repository.McpServerEndpointRepository;
import com.alibaba.himarket.repository.McpServerMetaRepository;
import com.alibaba.himarket.repository.ProductPublicationRepository;
import com.alibaba.himarket.repository.ProductRefRepository;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.repository.SubscriptionRepository;
import com.alibaba.himarket.service.ConsumerService;
import com.alibaba.himarket.service.GatewayService;
import com.alibaba.himarket.service.McpSandboxDeployService;
import com.alibaba.himarket.service.McpServerService;
import com.alibaba.himarket.service.NacosService;
import com.alibaba.himarket.service.SandboxService;
import com.alibaba.himarket.service.hichat.manager.ToolManager;
import com.alibaba.himarket.service.mcp.McpConnectionConfig;
import com.alibaba.himarket.service.mcp.McpProtocolUtils;
import com.alibaba.himarket.service.mcp.McpToolsConfigParser;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import com.alibaba.himarket.support.enums.MCPTransportMode;
import com.alibaba.himarket.support.enums.ProductStatus;
import com.alibaba.himarket.support.enums.SourceType;
import com.alibaba.himarket.support.enums.SubscriptionStatus;
import com.alibaba.himarket.support.product.NacosRefConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class McpServerServiceImpl implements McpServerService {

    private final McpServerMetaRepository metaRepository;
    private final McpServerEndpointRepository endpointRepository;
    private final ProductRefRepository productRefRepository;
    private final ProductRepository productRepository;
    private final ProductPublicationRepository publicationRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ContextHolder contextHolder;
    private final ConsumerService consumerService;
    private final GatewayService gatewayService;
    private final NacosService nacosService;
    private final SandboxService sandboxService;
    private final McpSandboxDeployService mcpSandboxDeployService;
    private final ToolManager toolManager;
    private final ObjectMapper objectMapper;

    @Resource(name = "taskExecutor")
    private Executor taskExecutor;

    @Override
    @Transactional
    public McpMetaResult saveMeta(SaveMcpMetaParam param) {
        // 标准化协议类型：统一为 stdio / sse / streamableHttp
        param.setProtocolType(McpProtocolUtils.normalize(param.getProtocolType()));

        // 自动推断 sandboxRequired：
        // 1. stdio 协议强制需要沙箱托管
        // 2. 网关/Nacos 导入默认不需要
        // 3. 其他类型默认需要
        String protocol = StrUtil.blankToDefault(param.getProtocolType(), "");
        if (protocol.toLowerCase().contains("stdio")) {
            param.setSandboxRequired(true);
        } else if (param.getSandboxRequired() == null) {
            String paramOrigin = StrUtil.blankToDefault(param.getOrigin(), "ADMIN");
            param.setSandboxRequired(
                    !"GATEWAY".equalsIgnoreCase(paramOrigin)
                            && !"NACOS".equalsIgnoreCase(paramOrigin));
        }

        // 查找是否已存在同 productId + mcpName 的记录
        McpServerMeta meta =
                metaRepository
                        .findByProductIdAndMcpName(param.getProductId(), param.getMcpName())
                        .orElse(null);

        if (meta == null) {
            // 新建
            meta =
                    McpServerMeta.builder()
                            .mcpServerId(IdGenerator.genMcpServerId())
                            .productId(param.getProductId())
                            .mcpName(param.getMcpName())
                            .displayName(param.getDisplayName())
                            .description(param.getDescription())
                            .repoUrl(param.getRepoUrl())
                            .sourceType(param.getSourceType())
                            .origin(StrUtil.blankToDefault(param.getOrigin(), "ADMIN"))
                            .tags(param.getTags())
                            .icon(param.getIcon())
                            .protocolType(param.getProtocolType())
                            .connectionConfig(param.getConnectionConfig())
                            .extraParams(param.getExtraParams())
                            .serviceIntro(param.getServiceIntro())
                            .visibility(StrUtil.blankToDefault(param.getVisibility(), "PUBLIC"))
                            .publishStatus(
                                    StrUtil.blankToDefault(param.getPublishStatus(), "DRAFT"))
                            .toolsConfig(McpToolsConfigParser.normalize(param.getToolsConfig()))
                            .sandboxRequired(param.getSandboxRequired())
                            .createdBy(
                                    StrUtil.blankToDefault(
                                            param.getCreatedBy(), getCreatedByOrDefault()))
                            .build();
        } else {
            // 更新前先标准化 toolsConfig，避免 YAML/空字符串写入 JSON 列
            param.setToolsConfig(McpToolsConfigParser.normalize(param.getToolsConfig()));
            // 更新：只覆盖非 null 字段
            BeanUtil.copyProperties(
                    param,
                    meta,
                    CopyOptions.create()
                            .ignoreNullValue()
                            .setIgnoreProperties("productId", "mcpName"));
        }

        metaRepository.save(meta);

        // 同步创建/更新 ProductRef，使 MCP 配置在产品关联中可见
        syncProductRef(meta, param);

        // 非沙箱 MCP：从 connectionConfig 提取 URL，同步创建/更新公共 endpoint（userId=*）
        // 沙箱托管的 MCP 由 doDeploySandbox() 负责创建 endpoint
        if (!Boolean.TRUE.equals(meta.getSandboxRequired())) {
            syncPublicEndpoint(meta);
        }

        return new McpMetaResult().convertFrom(meta);
    }

    @Override
    @Transactional
    public McpMetaResult registerMcp(RegisterMcpParam param) {
        // 0. mcpName 全局唯一性校验：防止同名 MCP 被重复注册
        metaRepository
                .findByMcpName(param.getMcpName())
                .ifPresent(
                        existing -> {
                            throw new BusinessException(
                                    ErrorCode.INVALID_REQUEST,
                                    "MCP 名称「" + param.getMcpName() + "」已被注册，请更换名称");
                        });

        // 1. 非 stdio 协议校验：connectionConfig 必须包含可提取的连接地址
        String protocol = param.getProtocolType();
        if (!"stdio".equalsIgnoreCase(protocol)) {
            String connCfg = param.getConnectionConfig();
            if (StrUtil.isBlank(connCfg)) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST, "非 stdio 协议必须提供 connectionConfig（包含连接地址）");
            }
            try {
                cn.hutool.json.JSONObject connJson = JSONUtil.parseObj(connCfg);
                String url = extractEndpointUrl(connJson, param.getMcpName(), protocol);
                if (StrUtil.isBlank(url)) {
                    throw new BusinessException(
                            ErrorCode.INVALID_REQUEST, "connectionConfig 中未找到有效的连接地址（url）");
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST,
                        "connectionConfig 格式错误或缺少连接地址: " + e.getMessage());
            }
        }

        // 1. 自动创建 Product（优先用 displayName 作为产品名，fallback 到 mcpName）
        String productId = IdGenerator.genApiProductId();
        String productName = StrUtil.blankToDefault(param.getDisplayName(), param.getMcpName());
        com.alibaba.himarket.entity.Product product =
                com.alibaba.himarket.entity.Product.builder()
                        .productId(productId)
                        .name(productName)
                        .type(com.alibaba.himarket.support.enums.ProductType.MCP_SERVER)
                        .description(param.getDescription())
                        .status(ProductStatus.PENDING)
                        .enableConsumerAuth(false)
                        .autoApprove(true)
                        .build();

        // 解析 icon JSON
        if (StrUtil.isNotBlank(param.getIcon())) {
            try {
                product.setIcon(
                        JSONUtil.toBean(
                                param.getIcon(), com.alibaba.himarket.support.product.Icon.class));
            } catch (Exception e) {
                log.warn("解析 icon JSON 失败: {}", e.getMessage());
            }
        }

        productRepository.save(product);

        // 2. 构建 SaveMcpMetaParam 并调用 saveMeta
        SaveMcpMetaParam metaParam = new SaveMcpMetaParam();
        metaParam.setProductId(productId);
        metaParam.setMcpName(param.getMcpName());
        metaParam.setDisplayName(param.getDisplayName());
        metaParam.setDescription(param.getDescription());
        metaParam.setRepoUrl(param.getRepoUrl());
        metaParam.setSourceType("config");
        metaParam.setOrigin(StrUtil.blankToDefault(param.getOrigin(), "OPEN_API"));
        metaParam.setTags(param.getTags());
        metaParam.setIcon(param.getIcon());
        metaParam.setProtocolType(param.getProtocolType());
        metaParam.setConnectionConfig(param.getConnectionConfig());
        metaParam.setExtraParams(param.getExtraParams());
        metaParam.setServiceIntro(param.getServiceIntro());
        metaParam.setVisibility(StrUtil.blankToDefault(param.getVisibility(), "PUBLIC"));
        metaParam.setPublishStatus(StrUtil.blankToDefault(param.getPublishStatus(), "PENDING"));
        metaParam.setToolsConfig(McpToolsConfigParser.normalize(param.getToolsConfig()));
        metaParam.setCreatedBy(param.getCreatedBy());
        metaParam.setSandboxRequired(param.getSandboxRequired());
        metaParam.setSandboxId(param.getSandboxId());
        metaParam.setTransportType(param.getTransportType());
        metaParam.setAuthType(param.getAuthType());
        metaParam.setParamValues(param.getParamValues());

        return saveMeta(metaParam);
    }

    @Override
    public McpMetaResult getMeta(String mcpServerId) {
        McpServerMeta meta = findMeta(mcpServerId);
        return new McpMetaResult().convertFrom(meta);
    }

    @Override
    public McpMetaResult getMetaByName(String mcpName) {
        McpServerMeta meta =
                metaRepository
                        .findByMcpName(mcpName)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND,
                                                Resources.MCP_SERVER_META,
                                                mcpName));
        return new McpMetaResult().convertFrom(meta);
    }

    @Override
    public PageResult<McpMetaResult> listMetaByOrigin(String origin, Pageable pageable) {
        Page<McpServerMeta> page = metaRepository.findByOrigin(origin, pageable);
        return new PageResult<McpMetaResult>()
                .convertFrom(page, m -> new McpMetaResult().convertFrom(m));
    }

    @Override
    public PageResult<McpMetaResult> listAllMeta(Pageable pageable) {
        Page<McpServerMeta> page = metaRepository.findAll(pageable);
        return new PageResult<McpMetaResult>()
                .convertFrom(page, m -> new McpMetaResult().convertFrom(m));
    }

    @Override
    public List<McpMetaResult> listMetaByProduct(String productId) {
        return metaRepository.findByProductId(productId).stream()
                .map(
                        m -> {
                            McpMetaResult result = new McpMetaResult().convertFrom(m);
                            // 查询公共 endpoint（userId=*）以获取沙箱托管后的连接地址
                            McpServerEndpoint ep =
                                    endpointRepository
                                            .findByMcpServerIdAndUserIdInAndStatus(
                                                    m.getMcpServerId(), List.of("*"), "ACTIVE")
                                            .stream()
                                            .findFirst()
                                            .orElse(null);
                            if (ep != null) {
                                result.setEndpointUrl(ep.getEndpointUrl());
                                result.setEndpointProtocol(ep.getProtocol());
                                result.setEndpointStatus(ep.getStatus());
                                result.setSubscribeParams(ep.getSubscribeParams());
                                result.setEndpointHostingType(ep.getHostingType());
                            }
                            fillResolvedConfig(result, m, ep);
                            return result;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public List<McpMetaResult> listMetaByProductIds(List<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }
        List<McpServerMeta> metas = metaRepository.findByProductIdIn(productIds);
        if (metas.isEmpty()) {
            return List.of();
        }

        // 批量查询所有公共 endpoint，避免 N+1
        List<String> mcpServerIds =
                metas.stream().map(McpServerMeta::getMcpServerId).collect(Collectors.toList());
        Map<String, McpServerEndpoint> endpointMap =
                endpointRepository
                        .findByMcpServerIdInAndUserIdInAndStatus(
                                mcpServerIds, List.of("*"), "ACTIVE")
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        McpServerEndpoint::getMcpServerId, ep -> ep, (a, b) -> a));

        return metas.stream()
                .map(
                        m -> {
                            McpMetaResult result = new McpMetaResult().convertFrom(m);
                            McpServerEndpoint ep = endpointMap.get(m.getMcpServerId());
                            if (ep != null) {
                                result.setEndpointUrl(ep.getEndpointUrl());
                                result.setEndpointProtocol(ep.getProtocol());
                                result.setEndpointStatus(ep.getStatus());
                                result.setSubscribeParams(ep.getSubscribeParams());
                                result.setEndpointHostingType(ep.getHostingType());
                            }
                            fillResolvedConfig(result, m, ep);
                            return result;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public McpMetaResult refreshTools(String mcpServerId) {
        McpServerMeta meta = findMeta(mcpServerId);

        // 优先使用热数据 endpoint URL
        McpServerEndpoint activeEndpoint =
                endpointRepository
                        .findByMcpServerIdAndUserIdInAndStatus(mcpServerId, List.of("*"), "ACTIVE")
                        .stream()
                        .findFirst()
                        .orElse(null);

        String endpointUrl = activeEndpoint != null ? activeEndpoint.getEndpointUrl() : null;
        String transportType =
                activeEndpoint != null
                        ? StrUtil.blankToDefault(activeEndpoint.getProtocol(), "sse")
                        : "sse";

        // 没有热数据时，从冷数据 connectionConfig 解析
        if (endpointUrl == null && StrUtil.isNotBlank(meta.getConnectionConfig())) {
            try {
                McpConnectionConfig cfg = McpConnectionConfig.parse(meta.getConnectionConfig());
                if (cfg.isMcpServersFormat()) {
                    McpConnectionConfig.McpServerEntry entry =
                            cfg.getMcpServers().values().iterator().next();
                    Object url = entry.getExtra().get("url");
                    if (url != null) {
                        endpointUrl = url.toString();
                        Object type = entry.getExtra().get("type");
                        if (type != null && "sse".equalsIgnoreCase(type.toString())) {
                            transportType = "sse";
                        } else {
                            transportType = "http";
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("解析 connectionConfig 失败: {}", e.getMessage());
            }
        }

        if (endpointUrl == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无可用的连接地址，请先配置连接点或部署沙箱");
        }

        fetchAndSaveToolsListOrThrow(meta, endpointUrl, transportType);
        return new McpMetaResult().convertFrom(meta);
    }

    @Override
    @Transactional
    public McpMetaResult updateServiceIntro(String mcpServerId, String serviceIntro) {
        McpServerMeta meta = findMeta(mcpServerId);
        meta.setServiceIntro(serviceIntro);
        metaRepository.save(meta);
        return new McpMetaResult().convertFrom(meta);
    }

    @Override
    @Transactional
    public McpMetaResult deploySandbox(String mcpServerId, SaveMcpMetaParam param) {
        McpServerMeta meta = findMeta(mcpServerId);
        if (!Boolean.TRUE.equals(meta.getSandboxRequired())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "该 MCP 配置未启用沙箱托管");
        }
        if (StrUtil.isBlank(param.getSandboxId())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "请选择沙箱实例");
        }
        // 将 meta 上的字段补充到 param 中，doDeploySandbox 需要用到
        param.setProductId(meta.getProductId());
        param.setMcpName(meta.getMcpName());
        doDeploySandbox(meta, param);
        return new McpMetaResult().convertFrom(meta);
    }

    @Override
    @Transactional
    public McpMetaResult undeploySandbox(String mcpServerId) {
        McpServerMeta meta = findMeta(mcpServerId);
        if (!Boolean.TRUE.equals(meta.getSandboxRequired())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "该 MCP 配置未启用沙箱托管");
        }
        // 删除沙箱 CRD
        undeploySandboxEndpoints(meta);
        // 删除沙箱类型的公共 endpoint 记录
        List<McpServerEndpoint> endpoints =
                endpointRepository.findByMcpServerId(meta.getMcpServerId());
        for (McpServerEndpoint ep : endpoints) {
            if ("SANDBOX".equalsIgnoreCase(ep.getHostingType())) {
                endpointRepository.delete(ep);
            }
        }
        log.info("管理员取消沙箱托管: mcpName={}, mcpServerId={}", meta.getMcpName(), mcpServerId);
        return new McpMetaResult().convertFrom(meta);
    }

    @Override
    @Transactional
    public void deleteMeta(String mcpServerId) {
        McpServerMeta meta = findMeta(mcpServerId);
        String productId = meta.getProductId();

        // 已发布的产品不允许删除 MCP 配置
        if (publicationRepository.existsByProductId(productId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "产品已发布，请先下架后再删除 MCP 配置");
        }

        // 对沙箱托管的 endpoint 执行 undeploy
        undeploySandboxEndpoints(meta);

        // 级联删除所有 endpoint
        endpointRepository.deleteByMcpServerId(mcpServerId);
        metaRepository.delete(meta);

        // 如果该产品下没有其他 MCP meta 了，删除 ProductRef 并重置产品状态
        List<McpServerMeta> remaining = metaRepository.findByProductId(productId);
        if (remaining.isEmpty()) {
            productRefRepository.deleteByProductId(productId);
            productRepository
                    .findByProductId(productId)
                    .ifPresent(
                            product -> {
                                product.setStatus(ProductStatus.PENDING);
                                productRepository.save(product);
                            });
        }
    }

    @Override
    @Transactional
    public void deleteMetaByProduct(String productId) {
        List<McpServerMeta> metas = metaRepository.findByProductId(productId);
        if (metas.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "该产品下没有 MCP 配置");
        }

        // 已发布的产品不允许删除 MCP 配置
        if (publicationRepository.existsByProductId(productId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "产品已发布，请先下架后再删除 MCP 配置");
        }

        // 级联删除所有 endpoint 和 meta
        for (McpServerMeta meta : metas) {
            // 对沙箱托管的 endpoint 执行 undeploy
            undeploySandboxEndpoints(meta);

            endpointRepository.deleteByMcpServerId(meta.getMcpServerId());
            metaRepository.delete(meta);
        }

        // 删除 ProductRef
        productRefRepository.deleteByProductId(productId);

        // 重置产品状态
        productRepository
                .findByProductId(productId)
                .ifPresent(
                        product -> {
                            product.setStatus(ProductStatus.PENDING);
                            productRepository.save(product);
                        });
    }

    @Override
    @Transactional
    public McpEndpointResult saveEndpoint(SaveMcpEndpointParam param) {
        // 校验 mcpServerId 存在
        McpServerMeta meta = findMeta(param.getMcpServerId());

        McpServerEndpoint endpoint =
                McpServerEndpoint.builder()
                        .endpointId(IdGenerator.genEndpointId())
                        .mcpServerId(param.getMcpServerId())
                        .mcpName(meta.getMcpName())
                        .endpointUrl(param.getEndpointUrl())
                        .hostingType(param.getHostingType())
                        .protocol(param.getProtocol())
                        .userId(StrUtil.blankToDefault(param.getUserId(), "*"))
                        .hostingInstanceId(param.getHostingInstanceId())
                        .hostingIdentifier(param.getHostingIdentifier())
                        .status("ACTIVE")
                        .build();

        endpointRepository.save(endpoint);
        return new McpEndpointResult().convertFrom(endpoint);
    }

    @Override
    public List<McpEndpointResult> listEndpoints(String mcpServerId) {
        return endpointRepository.findByMcpServerId(mcpServerId).stream()
                .map(e -> new McpEndpointResult().convertFrom(e))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteEndpoint(String endpointId) {
        McpServerEndpoint endpoint =
                endpointRepository
                        .findByEndpointId(endpointId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND,
                                                Resources.MCP_SERVER_ENDPOINT,
                                                endpointId));
        endpointRepository.delete(endpoint);
    }

    @Override
    public PageResult<McpMetaResult> listPublishedMcpServers(Pageable pageable) {
        Page<McpServerMeta> page =
                metaRepository.findByPublishStatusAndVisibility("PUBLISHED", "PUBLIC", pageable);
        return new PageResult<McpMetaResult>()
                .convertFrom(page, m -> new McpMetaResult().convertFrom(m));
    }

    @Override
    public List<MyEndpointResult> listMyEndpoints() {
        String userId = contextHolder.getUser();
        List<McpServerEndpoint> endpoints = endpointRepository.findByUserIdIn(List.of(userId, "*"));

        List<String> mcpServerIds =
                endpoints.stream()
                        .map(McpServerEndpoint::getMcpServerId)
                        .distinct()
                        .collect(Collectors.toList());

        Map<String, McpServerMeta> metaMap =
                mcpServerIds.stream()
                        .map(id -> metaRepository.findByMcpServerId(id).orElse(null))
                        .filter(m -> m != null)
                        .collect(Collectors.toMap(McpServerMeta::getMcpServerId, m -> m));

        return endpoints.stream()
                .map(
                        ep -> {
                            McpServerMeta meta = metaMap.get(ep.getMcpServerId());
                            return MyEndpointResult.builder()
                                    .endpointId(ep.getEndpointId())
                                    .mcpServerId(ep.getMcpServerId())
                                    .endpointUrl(ep.getEndpointUrl())
                                    .hostingType(ep.getHostingType())
                                    .protocol(ep.getProtocol())
                                    .hostingInstanceId(ep.getHostingInstanceId())
                                    .subscribeParams(ep.getSubscribeParams())
                                    .status(ep.getStatus())
                                    .endpointCreatedAt(ep.getCreateAt())
                                    .productId(meta != null ? meta.getProductId() : null)
                                    .displayName(
                                            meta != null ? meta.getDisplayName() : ep.getMcpName())
                                    .mcpName(ep.getMcpName())
                                    .description(meta != null ? meta.getDescription() : null)
                                    .icon(meta != null ? meta.getIcon() : null)
                                    .tags(meta != null ? meta.getTags() : null)
                                    .protocolType(meta != null ? meta.getProtocolType() : null)
                                    .origin(meta != null ? meta.getOrigin() : null)
                                    .toolsConfig(meta != null ? meta.getToolsConfig() : null)
                                    .build();
                        })
                .collect(Collectors.toList());
    }

    // ==================== 解析 MCP 传输配置 ====================

    @Override
    public List<MCPTransportConfig> resolveTransportConfigs(
            List<String> productIds, String userId) {
        List<MCPTransportConfig> configs = new ArrayList<>();

        // 获取用户的 primary consumer，用于校验订阅关系
        String consumerId = null;
        try {
            ConsumerResult primaryConsumer = consumerService.getPrimaryConsumer(userId);
            consumerId = primaryConsumer.getConsumerId();
        } catch (Exception e) {
            log.warn("[resolveTransportConfigs] 用户 {} 无 consumer，无法校验订阅，返回空列表", userId);
            return configs;
        }

        for (String productId : productIds) {
            // 校验用户是否已订阅该产品
            Optional<ProductSubscription> subscription =
                    subscriptionRepository.findByConsumerIdAndProductId(consumerId, productId);
            if (subscription.isEmpty()
                    || subscription.get().getStatus() != SubscriptionStatus.APPROVED) {
                log.debug("[resolveTransportConfigs] 用户 {} 未订阅产品 {} 或订阅未生效，跳过", userId, productId);
                continue;
            }

            List<McpServerMeta> metas = metaRepository.findByProductId(productId);
            if (metas.isEmpty()) {
                log.warn("[resolveTransportConfigs] 产品 {} 无 MCP meta，跳过", productId);
                continue;
            }

            McpServerMeta meta = metas.get(0);

            // 查找用户的 ACTIVE endpoint（包括 userId=* 的公共 endpoint）
            List<McpServerEndpoint> endpoints =
                    endpointRepository.findByMcpServerIdAndUserIdInAndStatus(
                            meta.getMcpServerId(), List.of(userId, "*"), "ACTIVE");

            if (endpoints.isEmpty()) {
                log.debug(
                        "[resolveTransportConfigs] 产品 {} 用户 {} 无订阅 endpoint，跳过", productId, userId);
                continue;
            }

            // 优先取用户自己的 endpoint，其次取公共的
            McpServerEndpoint endpoint =
                    endpoints.stream()
                            .filter(ep -> userId.equals(ep.getUserId()))
                            .findFirst()
                            .orElse(endpoints.get(0));

            if (StrUtil.isBlank(endpoint.getEndpointUrl())) {
                log.debug(
                        "[resolveTransportConfigs] endpoint {} URL 为空，跳过",
                        endpoint.getEndpointId());
                continue;
            }

            // 根据 protocol 确定 transportMode
            String protocol =
                    StrUtil.blankToDefault(endpoint.getProtocol(), meta.getProtocolType());
            MCPTransportMode transportMode =
                    ("HTTP".equalsIgnoreCase(protocol)
                                    || "StreamableHTTP".equalsIgnoreCase(protocol))
                            ? MCPTransportMode.STREAMABLE_HTTP
                            : MCPTransportMode.SSE;

            String url = endpoint.getEndpointUrl();
            if (transportMode == MCPTransportMode.SSE && !url.endsWith("/sse")) {
                url = url.endsWith("/") ? url + "sse" : url + "/sse";
            }

            configs.add(
                    MCPTransportConfig.builder()
                            .mcpServerName(endpoint.getMcpName())
                            .productId(productId)
                            .description(meta.getDescription())
                            .transportMode(transportMode)
                            .url(url)
                            .headers(resolveAuthHeaders(endpoint, meta, userId))
                            .build());
        }

        return configs;
    }

    // ==================== 私有方法 ====================

    private McpServerMeta findMeta(String mcpServerId) {
        return metaRepository
                .findByMcpServerId(mcpServerId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.NOT_FOUND,
                                        Resources.MCP_SERVER_META,
                                        mcpServerId));
    }

    /**
     * 根据 endpoint 来源解析 auth headers。
     * 网关/Nacos 来源：使用用户的 consumer credential（API Key）。
     * 沙箱来源：沙箱代理层处理鉴权，不需要额外 headers。
     */
    private Map<String, String> resolveAuthHeaders(
            McpServerEndpoint endpoint, McpServerMeta meta, String userId) {
        String hostingType = endpoint.getHostingType();
        if ("GATEWAY".equalsIgnoreCase(hostingType) || "NACOS".equalsIgnoreCase(hostingType)) {
            try {
                CredentialContext credential = consumerService.getDefaultCredential(userId);
                Map<String, String> headers = credential.copyHeaders();
                return headers.isEmpty() ? null : headers;
            } catch (Exception e) {
                log.warn("[resolveAuthHeaders] 获取用户 credential 失败: {}", e.getMessage());
            }
        }
        return null;
    }

    /**
     * 将 MCP meta 数据同步到 ProductRef 表，使产品详情页能展示 MCP 配置。
     * 支持三种来源：CUSTOM（自定义）、GATEWAY（网关导入）、NACOS（Nacos导入）。
     * 与 saveMeta 在同一事务中，任一失败则全部回滚。
     */
    private void syncProductRef(McpServerMeta meta, SaveMcpMetaParam param) {
        String productId = meta.getProductId();
        String origin = StrUtil.blankToDefault(param.getOrigin(), "ADMIN");

        // 确定 SourceType
        SourceType refSourceType;
        if ("GATEWAY".equalsIgnoreCase(origin) && StrUtil.isNotBlank(param.getGatewayId())) {
            refSourceType = SourceType.GATEWAY;
        } else if ("NACOS".equalsIgnoreCase(origin) && StrUtil.isNotBlank(param.getNacosId())) {
            refSourceType = SourceType.NACOS;
        } else {
            refSourceType = SourceType.CUSTOM;
        }

        // 获取或构建 mcpConfig JSON
        String mcpConfigStr;
        if (refSourceType == SourceType.GATEWAY) {
            // 从网关拉取完整 MCP 配置，需要将 refConfig 转换为正确的类型
            Object refConfigObj = null;
            if (StrUtil.isNotBlank(param.getRefConfig())) {
                cn.hutool.json.JSONObject refJson = JSONUtil.parseObj(param.getRefConfig());
                String fromGatewayType = refJson.getStr("fromGatewayType");
                if ("HIGRESS".equals(fromGatewayType)) {
                    refConfigObj =
                            JSONUtil.toBean(
                                    param.getRefConfig(),
                                    com.alibaba.himarket.support.product.HigressRefConfig.class);
                } else {
                    refConfigObj =
                            JSONUtil.toBean(
                                    param.getRefConfig(),
                                    com.alibaba.himarket.support.product.APIGRefConfig.class);
                }
            }
            mcpConfigStr = gatewayService.fetchMcpConfig(param.getGatewayId(), refConfigObj);
        } else if (refSourceType == SourceType.NACOS) {
            // 从 Nacos 拉取完整 MCP 配置
            NacosRefConfig nacosRef =
                    StrUtil.isNotBlank(param.getRefConfig())
                            ? JSONUtil.toBean(param.getRefConfig(), NacosRefConfig.class)
                            : null;
            mcpConfigStr = nacosService.fetchMcpConfig(param.getNacosId(), nacosRef);
        } else {
            // 自定义：手动构建 MCPConfigResult 兼容的 JSON
            cn.hutool.json.JSONObject mcpServerConfig = JSONUtil.createObj();
            if (StrUtil.isNotBlank(meta.getConnectionConfig())) {
                mcpServerConfig.set("rawConfig", JSONUtil.parse(meta.getConnectionConfig()));
            }

            cn.hutool.json.JSONObject metaObj = JSONUtil.createObj();
            metaObj.set("source", "CUSTOM");
            metaObj.set("protocol", meta.getProtocolType());

            cn.hutool.json.JSONObject mcpConfigJson = JSONUtil.createObj();
            mcpConfigJson.set("mcpServerName", meta.getMcpName());
            mcpConfigJson.set("mcpServerConfig", mcpServerConfig);
            mcpConfigJson.set("tools", meta.getToolsConfig());
            mcpConfigJson.set("meta", metaObj);

            mcpConfigStr = mcpConfigJson.toString();
        }

        // 网关/Nacos 导入：将拉取到的完整配置回写到 meta，供前端展示连接信息和工具列表
        if (refSourceType != SourceType.CUSTOM && StrUtil.isNotBlank(mcpConfigStr)) {
            try {
                cn.hutool.json.JSONObject mcpJson = JSONUtil.parseObj(mcpConfigStr);
                // 同步协议类型
                String protocol = mcpJson.getByPath("meta.protocol", String.class);
                if (StrUtil.isNotBlank(protocol)) {
                    meta.setProtocolType(McpProtocolUtils.normalize(protocol));
                }
                // 同步工具配置
                String tools = mcpJson.getStr("tools");
                if (StrUtil.isNotBlank(tools) && StrUtil.isBlank(meta.getToolsConfig())) {
                    meta.setToolsConfig(McpToolsConfigParser.normalize(tools));
                }
                // 将网关 domains 格式转换为标准 mcpServers 格式存入 connectionConfig
                String standardConfig =
                        convertToStandardConnectionConfig(mcpJson, meta.getMcpName(), protocol);
                meta.setConnectionConfig(
                        StrUtil.isNotBlank(standardConfig) ? standardConfig : mcpConfigStr);
            } catch (Exception e) {
                log.warn("解析网关配置失败，保留原始格式: {}", e.getMessage());
                meta.setConnectionConfig(mcpConfigStr);
            }
            metaRepository.save(meta);
        }

        // 创建或更新 ProductRef
        ProductRef ref = productRefRepository.findByProductId(productId).orElse(null);

        if (ref == null) {
            ref =
                    ProductRef.builder()
                            .productId(productId)
                            .sourceType(refSourceType)
                            .mcpConfig(mcpConfigStr)
                            .enabled(true)
                            .build();
        } else {
            ref.setSourceType(refSourceType);
            ref.setMcpConfig(mcpConfigStr);
            ref.setEnabled(true);
        }

        // 设置网关/Nacos 关联信息
        if (refSourceType == SourceType.GATEWAY) {
            ref.setGatewayId(param.getGatewayId());
            // 解析 refConfig 设置到对应字段
            if (StrUtil.isNotBlank(param.getRefConfig())) {
                cn.hutool.json.JSONObject refJson = JSONUtil.parseObj(param.getRefConfig());
                String fromGatewayType = refJson.getStr("fromGatewayType");
                if ("HIGRESS".equals(fromGatewayType)) {
                    ref.setHigressRefConfig(
                            JSONUtil.toBean(
                                    param.getRefConfig(),
                                    com.alibaba.himarket.support.product.HigressRefConfig.class));
                } else if ("ADP_AI_GATEWAY".equals(fromGatewayType)) {
                    ref.setAdpAIGatewayRefConfig(
                            JSONUtil.toBean(
                                    param.getRefConfig(),
                                    com.alibaba.himarket.support.product.APIGRefConfig.class));
                } else if ("APSARA_GATEWAY".equals(fromGatewayType)) {
                    ref.setApsaraGatewayRefConfig(
                            JSONUtil.toBean(
                                    param.getRefConfig(),
                                    com.alibaba.himarket.support.product.APIGRefConfig.class));
                } else {
                    ref.setApigRefConfig(
                            JSONUtil.toBean(
                                    param.getRefConfig(),
                                    com.alibaba.himarket.support.product.APIGRefConfig.class));
                }
            }
        } else if (refSourceType == SourceType.NACOS) {
            ref.setNacosId(param.getNacosId());
            if (StrUtil.isNotBlank(param.getRefConfig())) {
                ref.setNacosRefConfig(JSONUtil.toBean(param.getRefConfig(), NacosRefConfig.class));
            }
        }

        productRefRepository.save(ref);

        // 更新产品状态为 READY（已发布的产品不变）
        productRepository
                .findByProductId(productId)
                .ifPresent(
                        product -> {
                            if (product.getStatus() != ProductStatus.PUBLISHED) {
                                product.setStatus(ProductStatus.READY);
                                productRepository.save(product);
                            }
                        });
    }

    /**
     * 非沙箱 MCP：从 meta.connectionConfig 提取 endpoint URL，创建/更新公共 endpoint（userId=*）。
     * 使 resolveTransportConfigs 能统一从 endpoint 表获取连接信息，无需 fallback 到冷数据。
     * 默认生成 SSE 协议的 endpoint，URL 自动拼接 /sse 后缀。
     */
    private void syncPublicEndpoint(McpServerMeta meta) {
        String connectionConfig = meta.getConnectionConfig();
        if (StrUtil.isBlank(connectionConfig)) {
            return;
        }

        String endpointUrl;
        try {
            cn.hutool.json.JSONObject connJson = JSONUtil.parseObj(connectionConfig);
            endpointUrl = extractEndpointUrl(connJson, meta.getMcpName(), meta.getProtocolType());
        } catch (Exception e) {
            log.debug(
                    "[syncPublicEndpoint] 无法从 connectionConfig 提取 URL，跳过: mcpServerId={}, error={}",
                    meta.getMcpServerId(),
                    e.getMessage());
            return;
        }

        if (StrUtil.isBlank(endpointUrl)) {
            return;
        }

        // 确定协议：明确指定 StreamableHTTP 的保持原样，其余统一走 SSE 并拼接 /sse 后缀
        String protocol = StrUtil.blankToDefault(meta.getProtocolType(), "");
        boolean isStreamableHttp =
                "StreamableHTTP".equalsIgnoreCase(protocol) || "HTTP".equalsIgnoreCase(protocol);

        if (!isStreamableHttp) {
            // SSE 或未指定协议：标准化 URL，确保以 /sse 结尾且不重复
            String normalized = endpointUrl.replaceAll("/+$", ""); // 去掉尾部斜杠
            if (!normalized.endsWith("/sse")) {
                endpointUrl = normalized + "/sse";
            } else {
                endpointUrl = normalized;
            }
            protocol = "sse";
        }

        String origin = StrUtil.blankToDefault(meta.getOrigin(), "ADMIN");
        String hostingType;
        if ("GATEWAY".equalsIgnoreCase(origin)) {
            hostingType = "GATEWAY";
        } else if ("NACOS".equalsIgnoreCase(origin)) {
            hostingType = "NACOS";
        } else {
            hostingType = "DIRECT";
        }

        upsertEndpoint(
                meta.getMcpServerId(),
                meta.getMcpName(),
                endpointUrl,
                hostingType,
                protocol,
                "*",
                "public",
                null,
                null);

        log.info(
                "[syncPublicEndpoint] 公共 endpoint 已同步: mcpServerId={}, protocol={}, url={}",
                meta.getMcpServerId(),
                protocol,
                endpointUrl);
    }

    /**
     * 管理员预部署沙箱：在 saveMeta 时自动向沙箱集群下发 CRD，创建公共 endpoint（userId=*）。
     * 前台用户订阅时直接复用此公共 endpoint，无需再选沙箱。
     */
    private void doDeploySandbox(McpServerMeta meta, SaveMcpMetaParam param) {
        String sandboxId = param.getSandboxId();
        // transportType 只影响热数据（endpoint protocol + URL 拼接），与冷数据 meta.protocolType 无关
        String transportType = StrUtil.blankToDefault(param.getTransportType(), "sse");
        String authType = StrUtil.blankToDefault(param.getAuthType(), "none");
        String paramValues = param.getParamValues();
        String adminUserId = getCreatedByOrDefault();

        // 先清理旧的公共 endpoint 和 CRD
        List<McpServerEndpoint> existingPublic =
                endpointRepository.findByMcpServerIdAndUserIdInAndStatus(
                        meta.getMcpServerId(), List.of("*"), "ACTIVE");
        for (McpServerEndpoint existing : existingPublic) {
            if ("SANDBOX".equalsIgnoreCase(existing.getHostingType())
                    && StrUtil.isNotBlank(existing.getHostingInstanceId())) {
                try {
                    mcpSandboxDeployService.undeploy(
                            existing.getHostingInstanceId(),
                            existing.getMcpName(),
                            adminUserId,
                            extractNamespace(existing));
                } catch (Exception e) {
                    log.warn("清理旧公共 CRD 失败: {}", e.getMessage());
                }
            }
            endpointRepository.delete(existing);
        }

        // 分步部署，任何一步失败都回滚前面的工作
        String endpointUrl = null;
        McpServerEndpoint createdEndpoint = null;
        String currentStep = "部署沙箱";
        try {
            // Step 1: 部署 CRD 到沙箱
            var sandbox = sandboxService.getSandbox(sandboxId);
            endpointUrl =
                    mcpSandboxDeployService.deploy(
                            sandboxId,
                            meta.getMcpServerId(),
                            meta.getMcpName(),
                            adminUserId,
                            transportType,
                            meta.getProtocolType(),
                            meta.getConnectionConfig(),
                            "",
                            authType,
                            paramValues,
                            meta.getExtraParams(),
                            param.getNamespace(),
                            param.getResourceSpec());

            // SSE 类型需要 /sse 后缀
            if ("sse".equalsIgnoreCase(transportType)
                    && endpointUrl != null
                    && !endpointUrl.endsWith("/sse")) {
                endpointUrl = endpointUrl + "/sse";
            }

            // Step 2: 创建公共 endpoint
            currentStep = "创建连接";
            cn.hutool.json.JSONObject subParams =
                    JSONUtil.createObj()
                            .set("sandboxId", sandboxId)
                            .set("transportType", transportType)
                            .set("authType", authType)
                            .set(
                                    "namespace",
                                    StrUtil.blankToDefault(param.getNamespace(), "default"));
            if (StrUtil.isNotBlank(paramValues)) {
                subParams.set("extraParams", JSONUtil.parse(paramValues));
            }
            createdEndpoint =
                    upsertEndpoint(
                            meta.getMcpServerId(),
                            meta.getMcpName(),
                            endpointUrl,
                            "SANDBOX",
                            transportType,
                            "*",
                            sandboxId,
                            sandbox.getSandboxName(),
                            subParams.toString());

            log.info(
                    "管理员预部署沙箱成功: mcpName={}, sandboxId={}, endpoint={}",
                    meta.getMcpName(),
                    sandboxId,
                    endpointUrl);

            // Step 3: 异步获取工具列表（不阻塞部署响应）
            final String asyncEndpointUrl = endpointUrl;
            final String asyncMcpServerId = meta.getMcpServerId();
            final String asyncMcpName = meta.getMcpName();
            final String asyncTransportType = transportType;
            taskExecutor.execute(
                    () -> {
                        try {
                            fetchAndSaveToolsListOrThrow(
                                    asyncMcpServerId, asyncEndpointUrl, asyncTransportType);
                        } catch (Exception toolEx) {
                            log.warn(
                                    "沙箱部署后异步获取工具列表失败（不影响部署）: mcpName={}, error={}",
                                    asyncMcpName,
                                    toolEx.getMessage());
                        }
                    });

        } catch (Exception e) {
            log.error(
                    "管理员预部署沙箱失败[{}]: mcpName={}, sandboxId={}",
                    currentStep,
                    meta.getMcpName(),
                    sandboxId,
                    e);

            // 回滚：删除已创建的 endpoint
            if (createdEndpoint != null) {
                try {
                    endpointRepository.delete(createdEndpoint);
                } catch (Exception re) {
                    log.warn("回滚删除 endpoint 失败: {}", re.getMessage());
                }
            }
            // 回滚：删除已部署的 CRD
            if (endpointUrl != null) {
                try {
                    mcpSandboxDeployService.undeploy(
                            sandboxId,
                            meta.getMcpName(),
                            adminUserId,
                            StrUtil.blankToDefault(param.getNamespace(), "default"));
                } catch (Exception re) {
                    log.warn("回滚删除 CRD 失败: {}", re.getMessage());
                }
            }

            String errMsg =
                    e instanceof BusinessException
                            ? e.getMessage()
                            : (e.getMessage() != null
                                    ? e.getMessage()
                                    : e.getClass().getSimpleName());
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "沙箱预部署失败[" + currentStep + "]: " + errMsg);
        }
    }

    /**
     * 异步版本：通过 mcpServerId 重新加载 meta，避免跨线程使用 detached entity。
     */
    private void fetchAndSaveToolsListOrThrow(
            String mcpServerId, String endpointUrl, String transportType) {
        McpServerMeta meta = metaRepository.findByMcpServerId(mcpServerId).orElse(null);
        if (meta == null) {
            log.warn("异步获取工具列表时 meta 已不存在: mcpServerId={}", mcpServerId);
            return;
        }
        fetchAndSaveToolsListOrThrow(meta, endpointUrl, transportType);
    }

    /**
     * 与 fetchAndSaveToolsList 相同逻辑，但失败时抛出异常而非吞掉。
     * 用于管理员预部署流程，需要确保工具列表获取成功。
     */
    private void fetchAndSaveToolsListOrThrow(
            McpServerMeta meta, String endpointUrl, String transportType) {
        MCPTransportMode mode =
                "http".equalsIgnoreCase(transportType)
                        ? MCPTransportMode.STREAMABLE_HTTP
                        : MCPTransportMode.SSE;

        MCPTransportConfig config =
                MCPTransportConfig.builder()
                        .mcpServerName(meta.getMcpName())
                        .transportMode(mode)
                        .url(endpointUrl)
                        .build();

        // 沙箱刚部署完可能还没就绪，最多重试 3 次，每次间隔 10 秒（总计约 30s）
        McpClientWrapper client = null;
        int maxRetries = 3;
        long retryIntervalMs = 10000;
        for (int i = 0; i < maxRetries; i++) {
            client = toolManager.createClient(config);
            if (client != null) {
                break;
            }
            log.info("MCP 客户端创建失败，等待重试 ({}/{}): mcpName={}", i + 1, maxRetries, meta.getMcpName());
            try {
                Thread.sleep(retryIntervalMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR, "获取工具列表被中断: mcpName=" + meta.getMcpName());
            }
        }
        if (client == null) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "创建 MCP 客户端失败（已重试 " + maxRetries + " 次）: mcpName=" + meta.getMcpName());
        }

        List<McpSchema.Tool> tools = client.listTools().block();
        if (tools != null && !tools.isEmpty()) {
            try {
                String toolsJson = objectMapper.writeValueAsString(tools);
                meta.setToolsConfig(toolsJson);
            } catch (Exception e) {
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR, "序列化工具列表失败: " + e.getMessage());
            }
            metaRepository.save(meta);
            log.info("自动查询工具列表成功: mcpName={}, toolCount={}", meta.getMcpName(), tools.size());
        } else {
            log.info("工具列表为空: mcpName={}", meta.getMcpName());
        }
    }

    /**
     * Upsert endpoint：按 mcpServerId + userId + hostingInstanceId 唯一约束更新或新建。
     */
    private McpServerEndpoint upsertEndpoint(
            String mcpServerId,
            String mcpName,
            String endpointUrl,
            String hostingType,
            String protocol,
            String userId,
            String hostingInstanceId,
            String hostingIdentifier,
            String subscribeParams) {
        McpServerEndpoint endpoint =
                endpointRepository
                        .findByMcpServerIdAndUserIdAndHostingInstanceId(
                                mcpServerId, userId, hostingInstanceId)
                        .orElse(null);

        if (endpoint == null) {
            endpoint =
                    McpServerEndpoint.builder()
                            .endpointId(IdGenerator.genEndpointId())
                            .mcpServerId(mcpServerId)
                            .mcpName(mcpName)
                            .endpointUrl(endpointUrl)
                            .hostingType(hostingType)
                            .protocol(protocol)
                            .userId(userId)
                            .hostingInstanceId(hostingInstanceId)
                            .hostingIdentifier(hostingIdentifier)
                            .subscribeParams(subscribeParams)
                            .status("ACTIVE")
                            .build();
        } else {
            endpoint.setEndpointUrl(endpointUrl);
            endpoint.setProtocol(protocol);
            endpoint.setHostingIdentifier(hostingIdentifier);
            endpoint.setSubscribeParams(subscribeParams);
            endpoint.setStatus("ACTIVE");
        }
        return endpointRepository.save(endpoint);
    }

    /**
     * 将网关/Nacos 返回的原始配置转换为标准 mcpServers 格式。
     * 网关格式：{ mcpServerConfig: { domains: [...], path: "..." }, meta: { protocol: "sse" } }
     * Nacos 格式：{ mcpServerConfig: { rawConfig: {...} } }
     * 转换后：{ "mcpServers": { "name": { "url": "...", "type": "sse" } } }
     *
     * @return 标准格式 JSON 字符串，无法转换时返回 null
     */
    private String convertToStandardConnectionConfig(
            cn.hutool.json.JSONObject mcpJson, String mcpName, String protocol) {
        String serverName =
                StrUtil.blankToDefault(mcpName, "mcp-server")
                        .toLowerCase()
                        .replaceAll("[^a-z0-9-]", "-");

        // Nacos rawConfig：已经是标准格式，直接包装
        cn.hutool.json.JSONObject serverConfig = mcpJson.getJSONObject("mcpServerConfig");
        if (serverConfig != null && serverConfig.get("rawConfig") != null) {
            Object rawConfig = serverConfig.get("rawConfig");
            cn.hutool.json.JSONObject rawJson;
            try {
                rawJson =
                        rawConfig instanceof cn.hutool.json.JSONObject
                                ? (cn.hutool.json.JSONObject) rawConfig
                                : JSONUtil.parseObj(rawConfig.toString());
            } catch (Exception e) {
                return null;
            }
            // rawConfig 本身可能已经是 mcpServers 格式
            if (rawJson.containsKey("mcpServers")) {
                return rawJson.toString();
            }
            // 单 server 格式（有 command 或 url）
            return JSONUtil.createObj()
                    .set("mcpServers", JSONUtil.createObj().set(serverName, rawJson))
                    .toString();
        }

        // 网关 domains 格式：解析 domains 拼接 URL
        if (serverConfig != null && serverConfig.getJSONArray("domains") != null) {
            cn.hutool.json.JSONArray domains = serverConfig.getJSONArray("domains");
            if (domains.isEmpty()) return null;

            // 优先取非 intranet 的 domain
            cn.hutool.json.JSONObject domain = null;
            for (int i = 0; i < domains.size(); i++) {
                cn.hutool.json.JSONObject d = domains.getJSONObject(i);
                if (!"intranet".equalsIgnoreCase(d.getStr("networkType"))) {
                    domain = d;
                    break;
                }
            }
            if (domain == null) domain = domains.getJSONObject(0);

            String scheme = StrUtil.blankToDefault(domain.getStr("protocol"), "https");
            String host = domain.getStr("domain");
            Integer port = domain.getInt("port");
            String path = serverConfig.getStr("path", "");

            if (StrUtil.isBlank(host)) return null;

            StringBuilder urlBuilder = new StringBuilder(scheme).append("://").append(host);
            if (port != null && port > 0 && port != 443 && port != 80) {
                urlBuilder.append(":").append(port);
            }
            if (StrUtil.isNotBlank(path)) {
                if (!path.startsWith("/")) urlBuilder.append("/");
                urlBuilder.append(path);
            }

            String url = urlBuilder.toString();
            boolean isSse = "sse".equalsIgnoreCase(protocol);
            if (isSse && !url.endsWith("/sse")) {
                url = url.endsWith("/") ? url + "sse" : url + "/sse";
            }

            cn.hutool.json.JSONObject serverEntry = JSONUtil.createObj().set("url", url);
            if (isSse) serverEntry.set("type", "sse");

            return JSONUtil.createObj()
                    .set("mcpServers", JSONUtil.createObj().set(serverName, serverEntry))
                    .toString();
        }

        return null;
    }

    /**
     * 获取 createdBy：优先从 SecurityContext 取当前用户，无登录态时返回 "open-api"。
     */
    private String getCreatedByOrDefault() {
        try {
            return contextHolder.getUser();
        } catch (Exception e) {
            return "open-api";
        }
    }

    /**
     * 从 endpoint 的 subscribeParams JSON 中提取部署时使用的 namespace。
     * 如果解析失败或不存在，返回 "default"。
     */
    private String extractNamespace(McpServerEndpoint endpoint) {
        if (endpoint == null || StrUtil.isBlank(endpoint.getSubscribeParams())) {
            return "default";
        }
        try {
            cn.hutool.json.JSONObject params = JSONUtil.parseObj(endpoint.getSubscribeParams());
            return StrUtil.blankToDefault(params.getStr("namespace"), "default");
        } catch (Exception e) {
            return "default";
        }
    }

    /**
     * 对 meta 关联的所有沙箱托管 endpoint 执行 undeploy（删除沙箱中的 CRD 资源）。
     * undeploy 失败不阻塞删除流程，仅记录日志。
     */
    private void undeploySandboxEndpoints(McpServerMeta meta) {
        List<McpServerEndpoint> endpoints =
                endpointRepository.findByMcpServerId(meta.getMcpServerId());
        for (McpServerEndpoint ep : endpoints) {
            if (!"SANDBOX".equalsIgnoreCase(ep.getHostingType())
                    || StrUtil.isBlank(ep.getHostingInstanceId())) {
                continue;
            }
            try {
                String namespace = extractNamespace(ep);
                mcpSandboxDeployService.undeploy(
                        ep.getHostingInstanceId(), meta.getMcpName(), ep.getUserId(), namespace);
                log.info(
                        "沙箱 undeploy 成功: mcpName={}, sandboxId={}, namespace={}",
                        meta.getMcpName(),
                        ep.getHostingInstanceId(),
                        namespace);
            } catch (Exception e) {
                log.warn(
                        "沙箱 undeploy 失败（不阻塞删除）: mcpName={}, sandboxId={}, error={}",
                        meta.getMcpName(),
                        ep.getHostingInstanceId(),
                        e.getMessage());
            }
        }
    }

    /**
     * 从 connectionConfig JSON 中提取 endpoint URL。
     * 支持多种格式：直接 url 字段、mcpServers 格式、domains 格式。
     */
    private String extractEndpointUrl(
            cn.hutool.json.JSONObject connJson, String mcpName, String protocolType) {
        // 格式1: { "url": "..." }
        String url = connJson.getStr("url");
        if (StrUtil.isNotBlank(url)) return url;

        // 格式2: { "mcpServers": { "name": { "url": "..." } } }
        cn.hutool.json.JSONObject mcpServers = connJson.getJSONObject("mcpServers");
        if (mcpServers != null) {
            for (String key : mcpServers.keySet()) {
                cn.hutool.json.JSONObject server = mcpServers.getJSONObject(key);
                if (server != null && StrUtil.isNotBlank(server.getStr("url"))) {
                    return server.getStr("url");
                }
            }
        }

        // 格式3: { "mcpServerConfig": { "domains": [...] } }
        cn.hutool.json.JSONObject serverConfig = connJson.getJSONObject("mcpServerConfig");
        if (serverConfig != null) {
            cn.hutool.json.JSONArray domains = serverConfig.getJSONArray("domains");
            if (domains != null && !domains.isEmpty()) {
                cn.hutool.json.JSONObject domain = domains.getJSONObject(0);
                String protocol = domain.getStr("protocol", "https");
                String domainName = domain.getStr("domain");
                Integer port = domain.getInt("port");
                String path = serverConfig.getStr("path", "");
                String portStr = (port != null && port != 443 && port != 80) ? ":" + port : "";
                return protocol + "://" + domainName + portStr + path;
            }
        }

        throw new BusinessException(ErrorCode.INVALID_REQUEST, "无法从连接配置中提取 endpoint URL");
    }

    /**
     * 为 McpMetaResult 填充 resolvedConfig：后端统一解析的连接配置 JSON。
     * 热数据优先（endpoint URL），冷数据 fallback（meta.connectionConfig 解析）。
     */
    private void fillResolvedConfig(
            McpMetaResult result, McpServerMeta meta, McpServerEndpoint endpoint) {
        try {
            String serverName =
                    StrUtil.blankToDefault(meta.getMcpName(), "mcp-server")
                            .toLowerCase()
                            .replaceAll("[^a-z0-9-]", "-");

            // 热数据优先：有 ACTIVE endpoint 时用 endpoint URL
            if (endpoint != null && StrUtil.isNotBlank(endpoint.getEndpointUrl())) {
                String url = endpoint.getEndpointUrl();
                String protocol = StrUtil.blankToDefault(endpoint.getProtocol(), "sse");
                boolean isSse = "SSE".equalsIgnoreCase(protocol);

                cn.hutool.json.JSONObject serverEntry = JSONUtil.createObj().set("url", url);
                if (isSse) {
                    serverEntry.set("type", "sse");
                } else {
                    serverEntry.set("type", "streamable-http");
                }
                result.setResolvedConfig(
                        JSONUtil.createObj()
                                .set(
                                        "mcpServers",
                                        JSONUtil.createObj().set(serverName, serverEntry))
                                .toString());
                return;
            }

            // 冷数据 fallback：解析 meta.connectionConfig
            if (StrUtil.isBlank(meta.getConnectionConfig())) return;

            cn.hutool.json.JSONObject connJson;
            try {
                connJson = JSONUtil.parseObj(meta.getConnectionConfig());
            } catch (Exception e) {
                return;
            }

            String resolved =
                    convertToStandardConnectionConfig(
                            connJson, meta.getMcpName(), meta.getProtocolType());
            if (StrUtil.isNotBlank(resolved)) {
                result.setResolvedConfig(resolved);
            }
        } catch (Exception e) {
            log.debug(
                    "[fillResolvedConfig] 解析失败 mcpServerId={}: {}",
                    meta.getMcpServerId(),
                    e.getMessage());
        }
    }
}
