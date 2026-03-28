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
import com.alibaba.himarket.entity.Product;
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
import com.alibaba.himarket.service.mcp.AgentRuntimeDeployStrategy;
import com.alibaba.himarket.service.mcp.McpConnectionConfig;
import com.alibaba.himarket.service.mcp.McpProtocolUtils;
import com.alibaba.himarket.service.mcp.McpSandboxDeployEvent;
import com.alibaba.himarket.service.mcp.McpSandboxUndeployEvent;
import com.alibaba.himarket.service.mcp.McpToolsConfigParser;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import com.alibaba.himarket.support.enums.MCPTransportMode;
import com.alibaba.himarket.support.enums.McpEndpointStatus;
import com.alibaba.himarket.support.enums.McpHostingType;
import com.alibaba.himarket.support.enums.McpOrigin;
import com.alibaba.himarket.support.enums.McpProtocolType;
import com.alibaba.himarket.support.enums.ProductStatus;
import com.alibaba.himarket.support.enums.SourceType;
import com.alibaba.himarket.support.enums.SubscriptionStatus;
import com.alibaba.himarket.support.product.NacosRefConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Resource;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
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

    @Lazy @Resource private ConsumerService consumerService;

    private final GatewayService gatewayService;
    private final NacosService nacosService;
    private final SandboxService sandboxService;
    private final McpSandboxDeployService mcpSandboxDeployService;
    private final ToolManager toolManager;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

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
        McpProtocolType protocolEnum = McpProtocolType.fromString(param.getProtocolType());
        if (protocolEnum != null && protocolEnum.isStdio()) {
            param.setSandboxRequired(true);
        } else if (param.getSandboxRequired() == null) {
            McpOrigin paramOrigin = McpOrigin.fromString(param.getOrigin());
            param.setSandboxRequired(
                    paramOrigin != McpOrigin.GATEWAY && paramOrigin != McpOrigin.NACOS);
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
                            .repoUrl(param.getRepoUrl())
                            .sourceType(param.getSourceType())
                            .origin(McpOrigin.fromString(param.getOrigin()).name())
                            .tags(param.getTags())
                            .protocolType(param.getProtocolType())
                            .connectionConfig(param.getConnectionConfig())
                            .extraParams(param.getExtraParams())
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

        // 同步展示字段到 Product（Product 是展示信息的唯一主人）
        syncDisplayFieldsToProduct(meta.getProductId(), param);

        // 同步创建/更新 ProductRef，使 MCP 配置在产品关联中可见
        syncProductRef(meta, param);

        // 非沙箱 MCP：从 connectionConfig 提取 URL，同步创建/更新公共 endpoint（userId=*）
        // 沙箱托管的 MCP 由 doDeploySandbox() 负责创建 endpoint
        if (!Boolean.TRUE.equals(meta.getSandboxRequired())) {
            syncPublicEndpoint(meta);
        }

        return enrichedResult(meta);
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
        McpProtocolType regProtocol = McpProtocolType.fromString(param.getProtocolType());
        if (regProtocol == null || !regProtocol.isStdio()) {
            String connCfg = param.getConnectionConfig();
            if (StrUtil.isBlank(connCfg)) {
                throw new BusinessException(
                        ErrorCode.INVALID_REQUEST, "非 stdio 协议必须提供 connectionConfig（包含连接地址）");
            }
            try {
                cn.hutool.json.JSONObject connJson = JSONUtil.parseObj(connCfg);
                String url =
                        extractEndpointUrl(connJson, param.getMcpName(), param.getProtocolType());
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
                        .adminId(
                                StrUtil.blankToDefault(
                                        param.getCreatedBy(), getCreatedByOrDefault()))
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
        metaParam.setSourceType("config"); // TODO: consider SourceType enum
        metaParam.setOrigin(StrUtil.blankToDefault(param.getOrigin(), McpOrigin.OPEN_API.name()));
        metaParam.setTags(param.getTags());
        metaParam.setIcon(param.getIcon());
        metaParam.setProtocolType(param.getProtocolType());
        metaParam.setConnectionConfig(param.getConnectionConfig());
        metaParam.setExtraParams(param.getExtraParams());
        metaParam.setServiceIntro(param.getServiceIntro());
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
        McpMetaResult result = new McpMetaResult().convertFrom(meta);
        enrichFromProduct(result, meta.getProductId());
        // 查询公共 endpoint（userId=*）以获取热数据（与 listMetaByProduct 保持一致）
        McpServerEndpoint ep =
                endpointRepository
                        .findByMcpServerIdAndUserIdInAndStatus(
                                mcpServerId,
                                List.of(McpEndpointStatus.PUBLIC_USER_ID),
                                McpEndpointStatus.ACTIVE.name())
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
        fillResolvedConfig(result, meta, ep);
        return result;
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
        McpMetaResult result = new McpMetaResult().convertFrom(meta);
        enrichFromProduct(result, meta.getProductId());
        return result;
    }

    @Override
    public PageResult<McpMetaResult> listMetaByOrigin(String origin, Pageable pageable) {
        Page<McpServerMeta> page = metaRepository.findByOrigin(origin, pageable);
        return new PageResult<McpMetaResult>()
                .convertFrom(
                        page,
                        m -> {
                            McpMetaResult r = new McpMetaResult().convertFrom(m);
                            enrichFromProduct(r, m.getProductId());
                            return r;
                        });
    }

    @Override
    public PageResult<McpMetaResult> listAllMeta(Pageable pageable) {
        Page<McpServerMeta> page = metaRepository.findAll(pageable);
        return new PageResult<McpMetaResult>()
                .convertFrom(
                        page,
                        m -> {
                            McpMetaResult r = new McpMetaResult().convertFrom(m);
                            enrichFromProduct(r, m.getProductId());
                            return r;
                        });
    }

    @Override
    public PageResult<McpMetaResult> listPublishedMetaByOrigin(String origin, Pageable pageable) {
        // 1. 获取所有已发布 MCP 产品的 productId
        List<String> publishedProductIds =
                productRepository.findProductIdsByTypeAndStatus(
                        com.alibaba.himarket.support.enums.ProductType.MCP_SERVER,
                        ProductStatus.PUBLISHED);
        if (publishedProductIds.isEmpty()) {
            return PageResult.of(
                    List.of(), pageable.getPageNumber() + 1, pageable.getPageSize(), 0);
        }

        // 2. 在 meta 层按 origin 分页查询（分页粒度是 meta 而非 product）
        Page<McpServerMeta> metaPage =
                metaRepository.findByProductIdInAndOrigin(publishedProductIds, origin, pageable);
        if (metaPage.isEmpty()) {
            return PageResult.of(List.of(), metaPage.getNumber() + 1, metaPage.getSize(), 0);
        }

        // 3. 批量查询关联 Product，避免 N+1
        List<String> pageProductIds =
                metaPage.getContent().stream()
                        .map(McpServerMeta::getProductId)
                        .distinct()
                        .collect(Collectors.toList());
        Map<String, Product> productMap =
                productRepository.findByProductIdIn(pageProductIds).stream()
                        .collect(Collectors.toMap(Product::getProductId, p -> p, (a, b) -> a));

        List<McpMetaResult> results =
                metaPage.getContent().stream()
                        .map(
                                m -> {
                                    McpMetaResult r = new McpMetaResult().convertFrom(m);
                                    enrichFromProduct(r, productMap.get(m.getProductId()));
                                    return r;
                                })
                        .collect(Collectors.toList());
        return PageResult.of(
                results, metaPage.getNumber() + 1, metaPage.getSize(), metaPage.getTotalElements());
    }

    @Override
    public PageResult<McpMetaResult> listAllPublishedMeta(Pageable pageable) {
        // 1. 获取所有已发布 MCP 产品的 productId
        List<String> publishedProductIds =
                productRepository.findProductIdsByTypeAndStatus(
                        com.alibaba.himarket.support.enums.ProductType.MCP_SERVER,
                        ProductStatus.PUBLISHED);
        if (publishedProductIds.isEmpty()) {
            return PageResult.of(
                    List.of(), pageable.getPageNumber() + 1, pageable.getPageSize(), 0);
        }

        // 2. 在 meta 层分页查询
        Page<McpServerMeta> metaPage =
                metaRepository.findByProductIdIn(publishedProductIds, pageable);
        if (metaPage.isEmpty()) {
            return PageResult.of(List.of(), metaPage.getNumber() + 1, metaPage.getSize(), 0);
        }

        // 3. 批量查询关联 Product，避免 N+1
        List<String> pageProductIds =
                metaPage.getContent().stream()
                        .map(McpServerMeta::getProductId)
                        .distinct()
                        .collect(Collectors.toList());
        Map<String, Product> productMap =
                productRepository.findByProductIdIn(pageProductIds).stream()
                        .collect(Collectors.toMap(Product::getProductId, p -> p, (a, b) -> a));

        List<McpMetaResult> results =
                metaPage.getContent().stream()
                        .map(
                                m -> {
                                    McpMetaResult r = new McpMetaResult().convertFrom(m);
                                    enrichFromProduct(r, productMap.get(m.getProductId()));
                                    return r;
                                })
                        .collect(Collectors.toList());
        return PageResult.of(
                results, metaPage.getNumber() + 1, metaPage.getSize(), metaPage.getTotalElements());
    }

    @Override
    public McpMetaResult getPublishedMeta(String mcpServerId) {
        McpServerMeta meta = findMeta(mcpServerId);
        requirePublished(meta.getProductId(), mcpServerId);
        return getMeta(mcpServerId);
    }

    @Override
    public McpMetaResult getPublishedMetaByName(String mcpName) {
        McpServerMeta meta =
                metaRepository
                        .findByMcpName(mcpName)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND,
                                                Resources.MCP_SERVER_META,
                                                mcpName));
        requirePublished(meta.getProductId(), mcpName);
        // 委托给 getMeta 以获取完整的热数据（endpoint + resolvedConfig）
        return getMeta(meta.getMcpServerId());
    }

    /** 校验关联产品是否已发布，未发布则抛 NOT_FOUND（对外部调用方隐藏未发布资源的存在） */
    private void requirePublished(String productId, String identifier) {
        Product product =
                productRepository
                        .findByProductId(productId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.NOT_FOUND,
                                                Resources.MCP_SERVER_META,
                                                identifier));
        if (product.getStatus() != ProductStatus.PUBLISHED) {
            throw new BusinessException(ErrorCode.NOT_FOUND, Resources.MCP_SERVER_META, identifier);
        }
    }

    @Override
    public List<McpMetaResult> listMetaByProduct(String productId) {
        Product product = productRepository.findByProductId(productId).orElse(null);
        return metaRepository.findByProductId(productId).stream()
                .map(
                        m -> {
                            McpMetaResult result = new McpMetaResult().convertFrom(m);
                            enrichFromProduct(result, product);
                            // 查询公共 endpoint（userId=*）以获取沙箱托管后的连接地址
                            McpServerEndpoint ep =
                                    endpointRepository
                                            .findByMcpServerIdAndUserIdInAndStatus(
                                                    m.getMcpServerId(),
                                                    List.of(McpEndpointStatus.PUBLIC_USER_ID),
                                                    McpEndpointStatus.ACTIVE.name())
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

        // 批量查询所有关联 Product，避免 N+1
        Map<String, Product> productMap =
                productRepository.findByProductIdIn(productIds).stream()
                        .collect(Collectors.toMap(Product::getProductId, p -> p, (a, b) -> a));

        // 批量查询所有公共 endpoint，避免 N+1
        List<String> mcpServerIds =
                metas.stream().map(McpServerMeta::getMcpServerId).collect(Collectors.toList());
        Map<String, McpServerEndpoint> endpointMap =
                endpointRepository
                        .findByMcpServerIdInAndUserIdInAndStatus(
                                mcpServerIds,
                                List.of(McpEndpointStatus.PUBLIC_USER_ID),
                                McpEndpointStatus.ACTIVE.name())
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        McpServerEndpoint::getMcpServerId, ep -> ep, (a, b) -> a));

        return metas.stream()
                .map(
                        m -> {
                            McpMetaResult result = new McpMetaResult().convertFrom(m);
                            enrichFromProduct(result, productMap.get(m.getProductId()));
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
                        .findByMcpServerIdAndUserIdInAndStatus(
                                mcpServerId,
                                List.of(McpEndpointStatus.PUBLIC_USER_ID),
                                McpEndpointStatus.ACTIVE.name())
                        .stream()
                        .findFirst()
                        .orElse(null);

        String endpointUrl = activeEndpoint != null ? activeEndpoint.getEndpointUrl() : null;
        String transportType =
                activeEndpoint != null
                        ? StrUtil.blankToDefault(
                                activeEndpoint.getProtocol(), McpProtocolType.SSE.getValue())
                        : McpProtocolType.SSE.getValue();

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
                        transportType =
                                McpProtocolType.normalize(type != null ? type.toString() : "sse");
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
        McpMetaResult result = new McpMetaResult().convertFrom(meta);
        enrichFromProduct(result, meta.getProductId());
        return result;
    }

    @Override
    @Transactional
    public McpMetaResult updateServiceIntro(String mcpServerId, String serviceIntro) {
        McpServerMeta meta = findMeta(mcpServerId);
        // 服务介绍存储在 Product.document 中（Product 是展示信息的唯一主人）
        productRepository
                .findByProductId(meta.getProductId())
                .ifPresent(
                        product -> {
                            product.setDocument(serviceIntro);
                            productRepository.save(product);
                        });
        McpMetaResult result = new McpMetaResult().convertFrom(meta);
        enrichFromProduct(result, meta.getProductId());
        return result;
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
        McpMetaResult result = new McpMetaResult().convertFrom(meta);
        enrichFromProduct(result, meta.getProductId());
        return result;
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
            if (McpHostingType.SANDBOX.name().equalsIgnoreCase(ep.getHostingType())) {
                endpointRepository.delete(ep);
            }
        }
        log.info("管理员取消沙箱托管: mcpName={}, mcpServerId={}", meta.getMcpName(), mcpServerId);
        McpMetaResult result = new McpMetaResult().convertFrom(meta);
        enrichFromProduct(result, meta.getProductId());
        return result;
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
    public void forceDeleteMetaByProduct(String productId) {
        List<McpServerMeta> metas = metaRepository.findByProductId(productId);
        if (metas.isEmpty()) {
            return;
        }

        for (McpServerMeta meta : metas) {
            undeploySandboxEndpoints(meta);
            endpointRepository.deleteByMcpServerId(meta.getMcpServerId());
            metaRepository.delete(meta);
        }

        productRefRepository.deleteByProductId(productId);
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
                        .userId(
                                StrUtil.blankToDefault(
                                        param.getUserId(), McpEndpointStatus.PUBLIC_USER_ID))
                        .hostingInstanceId(param.getHostingInstanceId())
                        .hostingIdentifier(param.getHostingIdentifier())
                        .status(McpEndpointStatus.ACTIVE.name())
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
        // 展示字段已移至 Product，通过已发布的 MCP 产品查询
        Page<Product> productPage =
                productRepository.findByTypeAndStatus(
                        com.alibaba.himarket.support.enums.ProductType.MCP_SERVER,
                        ProductStatus.PUBLISHED,
                        pageable);
        List<String> productIds =
                productPage.getContent().stream()
                        .map(Product::getProductId)
                        .collect(Collectors.toList());
        if (productIds.isEmpty()) {
            return PageResult.of(List.of(), productPage.getNumber() + 1, productPage.getSize(), 0);
        }
        Map<String, Product> productMap =
                productPage.getContent().stream()
                        .collect(Collectors.toMap(Product::getProductId, p -> p, (a, b) -> a));
        // 批量查询 meta
        List<McpServerMeta> metas = metaRepository.findByProductIdIn(productIds);
        List<McpMetaResult> results =
                metas.stream()
                        .map(
                                m -> {
                                    McpMetaResult r = new McpMetaResult().convertFrom(m);
                                    enrichFromProduct(r, productMap.get(m.getProductId()));
                                    r.setPublishStatus("PUBLISHED");
                                    r.setVisibility("PUBLIC");
                                    return r;
                                })
                        .collect(Collectors.toList());
        return PageResult.of(
                results,
                productPage.getNumber() + 1,
                productPage.getSize(),
                productPage.getTotalElements());
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

        // 批量查询 meta，避免 N+1
        Map<String, McpServerMeta> metaMap =
                metaRepository.findByMcpServerIdIn(mcpServerIds).stream()
                        .collect(
                                Collectors.toMap(
                                        McpServerMeta::getMcpServerId, m -> m, (a, b) -> a));

        // 批量查询关联 Product，获取展示字段
        List<String> productIds =
                metaMap.values().stream()
                        .map(McpServerMeta::getProductId)
                        .distinct()
                        .collect(Collectors.toList());
        Map<String, Product> productMap =
                productRepository.findByProductIdIn(productIds).stream()
                        .collect(Collectors.toMap(Product::getProductId, p -> p, (a, b) -> a));

        return endpoints.stream()
                .map(
                        ep -> {
                            McpServerMeta meta = metaMap.get(ep.getMcpServerId());
                            Product product =
                                    meta != null ? productMap.get(meta.getProductId()) : null;
                            String iconStr = null;
                            if (product != null && product.getIcon() != null) {
                                try {
                                    iconStr = JSONUtil.toJsonStr(product.getIcon());
                                } catch (Exception e) {
                                    // ignore
                                }
                            }
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
                                            product != null ? product.getName() : ep.getMcpName())
                                    .mcpName(ep.getMcpName())
                                    .description(product != null ? product.getDescription() : null)
                                    .icon(iconStr)
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
        if (productIds == null || productIds.isEmpty()) {
            return List.of();
        }

        // 获取用户的 primary consumer，用于校验订阅关系
        String consumerId;
        try {
            ConsumerResult primaryConsumer = consumerService.getPrimaryConsumer(userId);
            consumerId = primaryConsumer.getConsumerId();
        } catch (Exception e) {
            log.warn("[resolveTransportConfigs] 用户 {} 无 consumer，无法校验订阅，返回空列表", userId);
            return List.of();
        }

        // 批量查询订阅关系，过滤出已生效的 productId
        Map<String, ProductSubscription> subscriptionMap =
                subscriptionRepository
                        .findByConsumerIdAndProductIdIn(consumerId, productIds)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        ProductSubscription::getProductId, s -> s, (a, b) -> a));
        List<String> approvedProductIds =
                productIds.stream()
                        .filter(
                                pid -> {
                                    ProductSubscription sub = subscriptionMap.get(pid);
                                    return sub != null
                                            && sub.getStatus() == SubscriptionStatus.APPROVED;
                                })
                        .collect(Collectors.toList());
        if (approvedProductIds.isEmpty()) {
            return List.of();
        }

        // 批量查询 meta
        List<McpServerMeta> allMetas = metaRepository.findByProductIdIn(approvedProductIds);
        // 每个 product 取第一个 meta
        Map<String, McpServerMeta> metaByProduct =
                allMetas.stream()
                        .collect(
                                Collectors.toMap(McpServerMeta::getProductId, m -> m, (a, b) -> a));
        if (metaByProduct.isEmpty()) {
            return List.of();
        }

        // 批量查询 endpoint
        List<String> mcpServerIds =
                metaByProduct.values().stream()
                        .map(McpServerMeta::getMcpServerId)
                        .collect(Collectors.toList());
        Map<String, List<McpServerEndpoint>> endpointsByServer =
                endpointRepository
                        .findByMcpServerIdInAndUserIdInAndStatus(
                                mcpServerIds,
                                List.of(userId, McpEndpointStatus.PUBLIC_USER_ID),
                                McpEndpointStatus.ACTIVE.name())
                        .stream()
                        .collect(Collectors.groupingBy(McpServerEndpoint::getMcpServerId));

        // 批量查询 product（用于 description）
        Map<String, Product> productMap =
                productRepository.findByProductIdIn(approvedProductIds).stream()
                        .collect(Collectors.toMap(Product::getProductId, p -> p, (a, b) -> a));

        // 组装结果
        List<MCPTransportConfig> configs = new ArrayList<>();
        for (String productId : approvedProductIds) {
            McpServerMeta meta = metaByProduct.get(productId);
            if (meta == null) {
                continue;
            }

            List<McpServerEndpoint> endpoints =
                    endpointsByServer.getOrDefault(meta.getMcpServerId(), List.of());
            if (endpoints.isEmpty()) {
                log.debug(
                        "[resolveTransportConfigs] 产品 {} 用户 {} 无可用 endpoint，跳过", productId, userId);
                continue;
            }

            // 优先取用户自己的 endpoint，其次取公共的
            McpServerEndpoint endpoint =
                    endpoints.stream()
                            .filter(ep -> userId.equals(ep.getUserId()))
                            .findFirst()
                            .orElse(endpoints.get(0));

            if (StrUtil.isBlank(endpoint.getEndpointUrl())) {
                continue;
            }

            // 根据 protocol 确定 transportMode
            String protocol =
                    StrUtil.blankToDefault(endpoint.getProtocol(), meta.getProtocolType());
            MCPTransportMode transportMode =
                    McpProtocolUtils.isStreamableHttp(protocol)
                            ? MCPTransportMode.STREAMABLE_HTTP
                            : MCPTransportMode.SSE;

            String url = McpProtocolUtils.normalizeEndpointUrl(endpoint.getEndpointUrl(), protocol);

            Product product = productMap.get(productId);
            configs.add(
                    MCPTransportConfig.builder()
                            .mcpServerName(endpoint.getMcpName())
                            .productId(productId)
                            .description(product != null ? product.getDescription() : null)
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
     * 沙箱来源：从 subscribeParams 中读取 API Key 鉴权信息。
     */
    private Map<String, String> resolveAuthHeaders(
            McpServerEndpoint endpoint, McpServerMeta meta, String userId) {
        McpHostingType hosting =
                McpHostingType.valueOf(
                        StrUtil.blankToDefault(
                                endpoint.getHostingType(), McpHostingType.DIRECT.name()));
        if (hosting == McpHostingType.GATEWAY || hosting == McpHostingType.NACOS) {
            try {
                CredentialContext credential = consumerService.getDefaultCredential(userId);
                Map<String, String> headers = credential.copyHeaders();
                return headers.isEmpty() ? null : headers;
            } catch (Exception e) {
                log.warn("[resolveAuthHeaders] 获取用户 credential 失败: {}", e.getMessage());
            }
        } else if (hosting == McpHostingType.SANDBOX) {
            // 从 subscribeParams 中读取 API Key 鉴权信息
            if (StrUtil.isNotBlank(endpoint.getSubscribeParams())) {
                try {
                    cn.hutool.json.JSONObject params =
                            cn.hutool.json.JSONUtil.parseObj(endpoint.getSubscribeParams());
                    String authType = params.getStr("authType");
                    if ("apikey".equalsIgnoreCase(authType)) {
                        String apiKey = params.getStr("apiKey");
                        if (StrUtil.isNotBlank(apiKey)) {
                            return Map.of("Authorization", apiKey);
                        }
                        log.error(
                                "[resolveAuthHeaders] 沙箱 endpoint authType=apikey 但 apiKey 为空:"
                                        + " endpointId={}",
                                endpoint.getEndpointId());
                        throw new BusinessException(
                                ErrorCode.INTERNAL_ERROR, "沙箱 API Key 鉴权配置异常：apiKey 为空");
                    }
                    // authType == "none" 或缺失：不需要额外 headers
                } catch (BusinessException e) {
                    throw e;
                } catch (Exception e) {
                    log.warn("[resolveAuthHeaders] 解析沙箱 subscribeParams 失败: {}", e.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * 将 MCP meta 数据同步到 ProductRef 表，使产品关联关系可见。
     * ProductRef.mcpConfig 不再存储 MCP 配置（McpServerMeta.connectionConfig 是唯一数据源）。
     * ProductRef 仅保留 sourceType、gatewayId/nacosId 等关联信息。
     * 与 saveMeta 在同一事务中，任一失败则全部回滚。
     */
    private void syncProductRef(McpServerMeta meta, SaveMcpMetaParam param) {
        String productId = meta.getProductId();
        SourceType refSourceType = determineSourceType(param);

        // 网关/Nacos 导入：拉取远端配置，回写到 meta.connectionConfig
        if (refSourceType == SourceType.GATEWAY || refSourceType == SourceType.NACOS) {
            fetchAndSyncRemoteConfig(meta, param, refSourceType);
        }

        // 创建或更新 ProductRef
        upsertProductRef(productId, param, refSourceType);

        // 更新产品状态为 READY（已发布的产品不变）
        markProductReady(productId);
    }

    /**
     * 根据 origin 和关联 ID 推断 SourceType。
     * GATEWAY + gatewayId → GATEWAY；NACOS + nacosId → NACOS；其余 → CUSTOM。
     */
    private SourceType determineSourceType(SaveMcpMetaParam param) {
        McpOrigin originEnum = McpOrigin.fromString(param.getOrigin());
        if (originEnum == McpOrigin.GATEWAY && StrUtil.isNotBlank(param.getGatewayId())) {
            return SourceType.GATEWAY;
        } else if (originEnum == McpOrigin.NACOS && StrUtil.isNotBlank(param.getNacosId())) {
            return SourceType.NACOS;
        }
        return SourceType.CUSTOM;
    }

    /**
     * 从网关/Nacos 拉取 MCP 配置，解析后回写到 meta.connectionConfig（单一数据源）。
     * 同时提取 protocol 和 tools 信息更新到 meta。
     */
    private void fetchAndSyncRemoteConfig(
            McpServerMeta meta, SaveMcpMetaParam param, SourceType sourceType) {
        String mcpConfigStr =
                sourceType == SourceType.GATEWAY
                        ? fetchGatewayConfig(param)
                        : fetchNacosConfig(param);

        if (StrUtil.isBlank(mcpConfigStr)) {
            return;
        }

        try {
            cn.hutool.json.JSONObject mcpJson = JSONUtil.parseObj(mcpConfigStr);
            String protocol = mcpJson.getByPath("meta.protocol", String.class);
            if (StrUtil.isNotBlank(protocol)) {
                meta.setProtocolType(McpProtocolUtils.normalize(protocol));
            }
            String tools = mcpJson.getStr("tools");
            if (StrUtil.isNotBlank(tools) && StrUtil.isBlank(meta.getToolsConfig())) {
                meta.setToolsConfig(McpToolsConfigParser.normalize(tools));
            }
            String standardConfig =
                    convertToStandardConnectionConfig(mcpJson, meta.getMcpName(), protocol);
            meta.setConnectionConfig(
                    StrUtil.isNotBlank(standardConfig) ? standardConfig : mcpConfigStr);
        } catch (Exception e) {
            log.warn("解析远端配置失败，保留原始格式: {}", e.getMessage());
            meta.setConnectionConfig(mcpConfigStr);
        }
        metaRepository.save(meta);
    }

    private String fetchGatewayConfig(SaveMcpMetaParam param) {
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
        return gatewayService.fetchMcpConfig(param.getGatewayId(), refConfigObj);
    }

    private String fetchNacosConfig(SaveMcpMetaParam param) {
        NacosRefConfig nacosRef =
                StrUtil.isNotBlank(param.getRefConfig())
                        ? JSONUtil.toBean(param.getRefConfig(), NacosRefConfig.class)
                        : null;
        return nacosService.fetchMcpConfig(param.getNacosId(), nacosRef);
    }

    /**
     * 创建或更新 ProductRef，设置网关/Nacos 关联信息。
     * MCP 配置不再写入 ProductRef（统一存储在 McpServerMeta.connectionConfig）。
     */
    private void upsertProductRef(
            String productId, SaveMcpMetaParam param, SourceType refSourceType) {
        ProductRef ref = productRefRepository.findByProductId(productId).orElse(null);

        if (ref == null) {
            ref =
                    ProductRef.builder()
                            .productId(productId)
                            .sourceType(refSourceType)
                            .enabled(true)
                            .build();
        } else {
            ref.setSourceType(refSourceType);
            ref.setEnabled(true);
        }

        // 设置网关/Nacos 关联信息
        if (refSourceType == SourceType.GATEWAY) {
            ref.setGatewayId(param.getGatewayId());
            applyGatewayRefConfig(ref, param.getRefConfig());
        } else if (refSourceType == SourceType.NACOS) {
            ref.setNacosId(param.getNacosId());
            if (StrUtil.isNotBlank(param.getRefConfig())) {
                ref.setNacosRefConfig(JSONUtil.toBean(param.getRefConfig(), NacosRefConfig.class));
            }
        }

        productRefRepository.save(ref);
    }

    /**
     * 根据 fromGatewayType 将 refConfig 设置到 ProductRef 对应的网关配置字段。
     */
    private void applyGatewayRefConfig(ProductRef ref, String refConfig) {
        if (StrUtil.isBlank(refConfig)) return;
        cn.hutool.json.JSONObject refJson = JSONUtil.parseObj(refConfig);
        String fromGatewayType = refJson.getStr("fromGatewayType");
        if ("HIGRESS".equals(fromGatewayType)) {
            ref.setHigressRefConfig(
                    JSONUtil.toBean(
                            refConfig,
                            com.alibaba.himarket.support.product.HigressRefConfig.class));
        } else if ("ADP_AI_GATEWAY".equals(fromGatewayType)) {
            ref.setAdpAIGatewayRefConfig(
                    JSONUtil.toBean(
                            refConfig, com.alibaba.himarket.support.product.APIGRefConfig.class));
        } else if ("APSARA_GATEWAY".equals(fromGatewayType)) {
            ref.setApsaraGatewayRefConfig(
                    JSONUtil.toBean(
                            refConfig, com.alibaba.himarket.support.product.APIGRefConfig.class));
        } else {
            ref.setApigRefConfig(
                    JSONUtil.toBean(
                            refConfig, com.alibaba.himarket.support.product.APIGRefConfig.class));
        }
    }

    /** 将产品状态更新为 READY（已发布的产品不变）。 */
    private void markProductReady(String productId) {
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
     *
     * <p>边界条件：
     * <ul>
     *   <li>connectionConfig 为空 → 直接返回，不创建 endpoint</li>
     *   <li>connectionConfig 无法解析出 URL → 仅记录 debug 日志，不抛异常</li>
     *   <li>非 StreamableHTTP 协议 → URL 自动拼接 /sse 后缀（去重）</li>
     *   <li>hostingType 根据 meta.origin 推断：GATEWAY→GATEWAY, NACOS→NACOS, 其余→DIRECT</li>
     * </ul>
     */
    private void syncPublicEndpoint(McpServerMeta meta) {
        String connectionConfig = meta.getConnectionConfig();
        if (StrUtil.isBlank(connectionConfig)) {
            return;
        }

        String endpointUrl;
        try {
            // 优先用 McpConnectionConfig 类型化解析
            endpointUrl = extractEndpointUrlTyped(connectionConfig, meta.getMcpName());
        } catch (Exception e1) {
            // fallback 到手动 JSON 解析（兼容 domains 等非标格式）
            try {
                cn.hutool.json.JSONObject connJson = JSONUtil.parseObj(connectionConfig);
                endpointUrl =
                        extractEndpointUrl(connJson, meta.getMcpName(), meta.getProtocolType());
            } catch (Exception e2) {
                log.debug(
                        "[syncPublicEndpoint] 无法从 connectionConfig 提取 URL，跳过:"
                                + " mcpServerId={}, error={}",
                        meta.getMcpServerId(),
                        e2.getMessage());
                return;
            }
        }

        if (StrUtil.isBlank(endpointUrl)) {
            return;
        }

        // 确定协议：明确指定 StreamableHTTP 的保持原样，其余统一走 SSE
        McpProtocolType protoType = McpProtocolType.fromString(meta.getProtocolType());
        boolean isStreamableHttp = protoType != null && protoType.isStreamableHttp();
        String protocol =
                isStreamableHttp
                        ? (protoType != null ? protoType.getValue() : meta.getProtocolType())
                        : McpProtocolType.SSE.getValue();

        // 标准化 URL：去掉尾部斜杠，SSE 协议追加 /sse 后缀
        endpointUrl = McpProtocolUtils.normalizeEndpointUrl(endpointUrl, meta.getProtocolType());

        McpOrigin metaOrigin = McpOrigin.fromString(meta.getOrigin());
        McpHostingType hostingType = McpHostingType.fromOrigin(metaOrigin);

        upsertEndpoint(
                meta.getMcpServerId(),
                meta.getMcpName(),
                endpointUrl,
                hostingType.name(),
                protocol,
                McpEndpointStatus.PUBLIC_USER_ID,
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
     * 管理员预部署沙箱：事务内只做 DB 操作（预创建 endpoint），
     * K8s CRD 部署通过 {@link McpSandboxDeployEvent} 在事务提交后异步执行。
     *
     * <p>这样避免了 K8s 资源泄漏：如果 DB 事务回滚，CRD 不会被部署。
     */
    private void doDeploySandbox(McpServerMeta meta, SaveMcpMetaParam param) {
        String sandboxId = param.getSandboxId();
        String transportType = StrUtil.blankToDefault(param.getTransportType(), "sse");
        String authType = StrUtil.blankToDefault(param.getAuthType(), "none");
        String paramValues = param.getParamValues();
        String adminUserId = getCreatedByOrDefault();

        // 当 authType 为 "apikey" 时生成随机 API Key
        String apiKey = "";
        if ("apikey".equalsIgnoreCase(authType)) {
            apiKey = generateApiKey();
        }

        // 先清理旧的公共沙箱 endpoint（DB 记录），包括 ACTIVE 和 INACTIVE 状态
        // 注意：旧 CRD 的清理也放到事务提交后，由 listener 处理
        // 先清理旧的公共沙箱 endpoint（DB 记录立即删除，CRD 清理延迟到事务提交后异步执行）
        List<McpServerEndpoint> existingPublic =
                endpointRepository.findByMcpServerId(meta.getMcpServerId()).stream()
                        .filter(ep -> McpEndpointStatus.PUBLIC_USER_ID.equals(ep.getUserId()))
                        .filter(
                                ep ->
                                        McpHostingType.SANDBOX
                                                .name()
                                                .equalsIgnoreCase(ep.getHostingType()))
                        .collect(Collectors.toList());
        for (McpServerEndpoint existing : existingPublic) {
            if (StrUtil.isNotBlank(existing.getHostingInstanceId())) {
                // 发布 undeploy 事件：事务提交后由 listener 异步清理旧 CRD
                eventPublisher.publishEvent(
                        McpSandboxUndeployEvent.builder()
                                .sandboxId(existing.getHostingInstanceId())
                                .mcpName(existing.getMcpName())
                                .userId(adminUserId)
                                .namespace(extractNamespace(existing))
                                .resourceName(extractResourceName(existing))
                                .secretName(extractSecretName(existing))
                                .build());
            }
            endpointRepository.delete(existing);
        }

        // 强制 flush 删除操作，避免后续 insert 时唯一约束冲突
        if (!existingPublic.isEmpty()) {
            endpointRepository.flush();
        }

        // 校验沙箱实例存在
        var sandbox = sandboxService.getSandbox(sandboxId);

        // 事务内预创建 endpoint（状态为 INACTIVE，等 CRD 部署成功后由 listener 更新为 ACTIVE）
        String resourceName =
                AgentRuntimeDeployStrategy.buildResourceNameStatic(meta.getMcpName(), adminUserId);
        cn.hutool.json.JSONObject subParams =
                JSONUtil.createObj()
                        .set("sandboxId", sandboxId)
                        .set("sandboxName", sandbox.getSandboxName())
                        .set("transportType", transportType)
                        .set("authType", authType)
                        .set("namespace", StrUtil.blankToDefault(param.getNamespace(), "default"))
                        .set("resourceName", resourceName);
        if ("apikey".equalsIgnoreCase(authType) && StrUtil.isNotBlank(apiKey)) {
            subParams.set("apiKey", apiKey);
        }
        if (StrUtil.isNotBlank(paramValues)) {
            subParams.set("extraParams", JSONUtil.parse(paramValues));
        }

        McpServerEndpoint pendingEndpoint =
                McpServerEndpoint.builder()
                        .endpointId(IdGenerator.genEndpointId())
                        .mcpServerId(meta.getMcpServerId())
                        .mcpName(meta.getMcpName())
                        .endpointUrl("") // 占位，CRD 部署成功后由 listener 填充
                        .hostingType(McpHostingType.SANDBOX.name())
                        .protocol(transportType)
                        .userId(McpEndpointStatus.PUBLIC_USER_ID)
                        .hostingInstanceId(sandboxId)
                        .hostingIdentifier(sandbox.getSandboxName())
                        .subscribeParams(subParams.toString())
                        .status(McpEndpointStatus.INACTIVE.name()) // 等待 CRD 部署成功
                        .build();
        endpointRepository.save(pendingEndpoint);

        // 发布事件：事务提交后由 McpSandboxDeployListener 执行 K8s CRD 部署
        eventPublisher.publishEvent(
                McpSandboxDeployEvent.builder()
                        .sandboxId(sandboxId)
                        .mcpServerId(meta.getMcpServerId())
                        .mcpName(meta.getMcpName())
                        .adminUserId(adminUserId)
                        .transportType(transportType)
                        .metaProtocolType(meta.getProtocolType())
                        .connectionConfig(meta.getConnectionConfig())
                        .authType(authType)
                        .paramValues(paramValues)
                        .extraParams(meta.getExtraParams())
                        .namespace(param.getNamespace())
                        .resourceSpec(param.getResourceSpec())
                        .endpointId(pendingEndpoint.getEndpointId())
                        .apiKey(apiKey)
                        .build());

        log.info("沙箱部署事件已发布（事务提交后执行）: mcpName={}, sandboxId={}", meta.getMcpName(), sandboxId);
    }

    /**
     * 使用密码学安全的随机数生成器生成 API Key。
     * 格式：sk_ + 32 位随机字母数字字符串（总长度 35）。
     */
    private String generateApiKey() {
        SecureRandom random = new SecureRandom();
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder("sk_");
        for (int i = 0; i < 32; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
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
        MCPTransportMode mode = McpProtocolType.resolveTransportMode(transportType);

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
                            .status(McpEndpointStatus.ACTIVE.name())
                            .build();
        } else {
            endpoint.setEndpointUrl(endpointUrl);
            endpoint.setProtocol(protocol);
            endpoint.setHostingIdentifier(hostingIdentifier);
            endpoint.setSubscribeParams(subscribeParams);
            endpoint.setStatus(McpEndpointStatus.ACTIVE.name());
        }
        return endpointRepository.save(endpoint);
    }

    /**
     * 将网关/Nacos 返回的原始配置转换为标准 mcpServers 格式。
     *
     * <p>支持两种输入格式：
     * <ul>
     *   <li>Nacos rawConfig：{ mcpServerConfig: { rawConfig: {...} } }
     *       → 如果 rawConfig 已含 mcpServers 则直接返回，否则包装为 mcpServers 格式</li>
     *   <li>网关 domains：{ mcpServerConfig: { domains: [...], path: "..." }, meta: { protocol: "sse" } }
     *       → 从 domains 拼接 URL，SSE 协议自动追加 /sse 后缀</li>
     * </ul>
     *
     * @param mcpJson  原始配置 JSON，不能为 null
     * @param mcpName  MCP 名称，用作 mcpServers 的 key（会标准化为小写+连字符）
     * @param protocol 协议类型，影响 SSE URL 后缀拼接
     * @return 标准格式 JSON 字符串，两种格式均无法转换时返回 null
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
            McpProtocolType proto = McpProtocolType.fromString(protocol);
            boolean isSse = proto == null || proto.isSse();
            if (isSse && !url.endsWith("/sse")) {
                url = url.endsWith("/") ? url + "sse" : url + "/sse";
            }

            cn.hutool.json.JSONObject serverEntry = JSONUtil.createObj().set("url", url);
            if (isSse) serverEntry.set("type", McpProtocolType.SSE.getValue());

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

    // ==================== Product 展示字段同步 ====================

    /**
     * 将 SaveMcpMetaParam 中的展示字段同步到 Product。
     * Product 是展示信息（name、description、icon、document）的唯一主人。
     */
    private void syncDisplayFieldsToProduct(String productId, SaveMcpMetaParam param) {
        productRepository
                .findByProductId(productId)
                .ifPresent(
                        product -> {
                            boolean changed = false;
                            if (StrUtil.isNotBlank(param.getDisplayName())
                                    && !param.getDisplayName().equals(product.getName())) {
                                product.setName(param.getDisplayName());
                                changed = true;
                            }
                            if (param.getDescription() != null
                                    && !param.getDescription().equals(product.getDescription())) {
                                product.setDescription(param.getDescription());
                                changed = true;
                            }
                            if (StrUtil.isNotBlank(param.getIcon())) {
                                try {
                                    product.setIcon(
                                            JSONUtil.toBean(
                                                    param.getIcon(),
                                                    com.alibaba.himarket.support.product.Icon
                                                            .class));
                                    changed = true;
                                } catch (Exception e) {
                                    log.warn("解析 icon JSON 失败: {}", e.getMessage());
                                }
                            }
                            if (StrUtil.isNotBlank(param.getServiceIntro())
                                    && !param.getServiceIntro().equals(product.getDocument())) {
                                product.setDocument(param.getServiceIntro());
                                changed = true;
                            }
                            if (changed) {
                                productRepository.save(product);
                            }
                        });
    }

    /**
     * 从 Product 填充 McpMetaResult 的展示字段。
     * 按 productId 查询 Product。
     */
    private void enrichFromProduct(McpMetaResult result, String productId) {
        if (productId == null) return;
        productRepository.findByProductId(productId).ifPresent(p -> enrichFromProduct(result, p));
    }

    /**
     * 从 Product 填充 McpMetaResult 的展示字段（已有 Product 对象时使用，避免重复查询）。
     */
    private void enrichFromProduct(McpMetaResult result, Product product) {
        if (product == null) return;
        result.setDisplayName(product.getName());
        result.setDescription(product.getDescription());
        result.setServiceIntro(product.getDocument());
        // createdBy 保留 meta 自身的值（记录实际上传者），不从 Product.adminId 覆盖
        // Product.status 映射到 publishStatus / visibility
        if (product.getStatus() == ProductStatus.PUBLISHED) {
            result.setPublishStatus("PUBLISHED");
            result.setVisibility("PUBLIC");
        } else {
            result.setPublishStatus(product.getStatus() == ProductStatus.READY ? "READY" : "DRAFT");
            result.setVisibility("PUBLIC");
        }
        // icon: Product 存储为 Icon 对象，DTO 需要 JSON 字符串
        if (product.getIcon() != null) {
            try {
                result.setIcon(JSONUtil.toJsonStr(product.getIcon()));
            } catch (Exception e) {
                // ignore
            }
        }
    }

    /** convertFrom + enrichFromProduct 的快捷方法 */
    private McpMetaResult enrichedResult(McpServerMeta meta) {
        McpMetaResult result = new McpMetaResult().convertFrom(meta);
        enrichFromProduct(result, meta.getProductId());
        return result;
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

    private String extractResourceName(McpServerEndpoint endpoint) {
        if (endpoint == null || StrUtil.isBlank(endpoint.getSubscribeParams())) {
            return null;
        }
        try {
            cn.hutool.json.JSONObject params = JSONUtil.parseObj(endpoint.getSubscribeParams());
            return params.getStr("resourceName");
        } catch (Exception e) {
            return null;
        }
    }

    private String extractSecretName(McpServerEndpoint endpoint) {
        if (endpoint == null || StrUtil.isBlank(endpoint.getSubscribeParams())) {
            return null;
        }
        try {
            cn.hutool.json.JSONObject params = JSONUtil.parseObj(endpoint.getSubscribeParams());
            return params.getStr("secretName");
        } catch (Exception e) {
            return null;
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
            if (!McpHostingType.SANDBOX.name().equalsIgnoreCase(ep.getHostingType())
                    || StrUtil.isBlank(ep.getHostingInstanceId())) {
                continue;
            }
            try {
                String namespace = extractNamespace(ep);
                String resourceName = extractResourceName(ep);
                String secretName = extractSecretName(ep);
                mcpSandboxDeployService.undeploy(
                        ep.getHostingInstanceId(),
                        meta.getMcpName(),
                        ep.getUserId(),
                        namespace,
                        resourceName,
                        secretName);
                log.info(
                        "沙箱 undeploy 成功: mcpName={}, sandboxId={}, namespace={}, resourceName={}",
                        meta.getMcpName(),
                        ep.getHostingInstanceId(),
                        namespace,
                        resourceName);
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
     * 使用 McpConnectionConfig 类型化解析提取 endpoint URL。
     * 支持 mcpServers 格式和单 server 格式。不支持 domains 格式（由 extractEndpointUrl fallback 处理）。
     *
     * @throws Exception 无法解析或提取 URL 时抛出
     */
    private String extractEndpointUrlTyped(String connectionConfigJson, String mcpName)
            throws Exception {
        McpConnectionConfig cfg = McpConnectionConfig.parse(connectionConfigJson);
        if (cfg.isMcpServersFormat()) {
            for (McpConnectionConfig.McpServerEntry entry : cfg.getMcpServers().values()) {
                Object url = entry.getExtra().get("url");
                if (url != null && StrUtil.isNotBlank(url.toString())) {
                    return url.toString();
                }
            }
        } else if (cfg.isSingleServerFormat()) {
            // 单 server 格式通常是 stdio，没有 URL
            Object url = cfg.getExtra().get("url");
            if (url != null && StrUtil.isNotBlank(url.toString())) {
                return url.toString();
            }
        } else if (cfg.isWrappedFormat()) {
            // 尝试从 rawConfig 中递归提取
            String rawJson = cfg.getRawConfigJson();
            if (rawJson != null) {
                return extractEndpointUrlTyped(rawJson, mcpName);
            }
        }
        throw new IllegalStateException("McpConnectionConfig 无法提取 URL");
    }

    /**
     * 从 connectionConfig JSON 中提取 endpoint URL。
     *
     * <p>支持三种格式（按优先级）：
     * <ol>
     *   <li>直接 url 字段：{ "url": "https://..." }</li>
     *   <li>mcpServers 格式：{ "mcpServers": { "name": { "url": "https://..." } } }</li>
     *   <li>domains 格式：{ "mcpServerConfig": { "domains": [...], "path": "..." } }</li>
     * </ol>
     *
     * @param connJson     connectionConfig 解析后的 JSON 对象，不能为 null
     * @param mcpName      MCP 名称，仅用于日志（可为空）
     * @param protocolType 协议类型，仅用于 domains 格式的 URL 拼接（可为空）
     * @return endpoint URL 字符串
     * @throws BusinessException 三种格式均无法提取到有效 URL 时抛出
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
                McpProtocolType proto =
                        McpProtocolType.fromString(
                                StrUtil.blankToDefault(endpoint.getProtocol(), "sse"));
                boolean isSse = proto == null || proto.isSse();

                cn.hutool.json.JSONObject serverEntry =
                        JSONUtil.createObj().set("url", endpoint.getEndpointUrl());
                serverEntry.set("type", isSse ? "sse" : "streamable-http");
                result.setResolvedConfig(
                        JSONUtil.createObj()
                                .set(
                                        "mcpServers",
                                        JSONUtil.createObj().set(serverName, serverEntry))
                                .toString());
                return;
            }

            // 冷数据 fallback：优先用 McpConnectionConfig 类型化解析
            if (StrUtil.isBlank(meta.getConnectionConfig())) return;

            try {
                McpConnectionConfig cfg = McpConnectionConfig.parse(meta.getConnectionConfig());
                if (cfg.isMcpServersFormat() || cfg.isSingleServerFormat()) {
                    result.setResolvedConfig(cfg.toMcpServersJsonWithoutEnv(serverName));
                    return;
                }
            } catch (Exception ignored) {
                // McpConnectionConfig 无法解析（可能是 domains 格式），fallback 到手动解析
            }

            cn.hutool.json.JSONObject connJson = JSONUtil.parseObj(meta.getConnectionConfig());
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
