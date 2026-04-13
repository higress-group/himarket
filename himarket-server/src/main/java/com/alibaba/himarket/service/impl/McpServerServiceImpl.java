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
import com.alibaba.himarket.dto.result.mcp.McpEndpointResult;
import com.alibaba.himarket.dto.result.mcp.McpMetaResult;
import com.alibaba.himarket.dto.result.mcp.MyEndpointResult;
import com.alibaba.himarket.entity.McpServerEndpoint;
import com.alibaba.himarket.entity.McpServerMeta;
import com.alibaba.himarket.entity.Product;
import com.alibaba.himarket.repository.McpServerEndpointRepository;
import com.alibaba.himarket.repository.McpServerMetaRepository;
import com.alibaba.himarket.repository.ProductPublicationRepository;
import com.alibaba.himarket.repository.ProductRepository;
import com.alibaba.himarket.service.ConsumerService;
import com.alibaba.himarket.service.McpServerService;
import com.alibaba.himarket.service.hichat.manager.ToolManager;
import com.alibaba.himarket.service.mcp.McpConfigSyncHelper;
import com.alibaba.himarket.service.mcp.McpConnectionConfig;
import com.alibaba.himarket.service.mcp.McpProtocolUtils;
import com.alibaba.himarket.service.mcp.McpSandboxOrchestrator;
import com.alibaba.himarket.service.mcp.McpToolsConfigParser;
import com.alibaba.himarket.service.mcp.McpTransportResolver;
import com.alibaba.himarket.support.chat.mcp.MCPTransportConfig;
import com.alibaba.himarket.support.enums.MCPTransportMode;
import com.alibaba.himarket.support.enums.McpEndpointStatus;
import com.alibaba.himarket.support.enums.McpHostingType;
import com.alibaba.himarket.support.enums.McpOrigin;
import com.alibaba.himarket.support.enums.McpProtocolType;
import com.alibaba.himarket.support.enums.ProductStatus;
import com.alibaba.himarket.support.enums.SourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class McpServerServiceImpl implements McpServerService {

    private final McpServerMetaRepository metaRepository;
    private final McpServerEndpointRepository endpointRepository;
    private final ProductRepository productRepository;
    private final ProductPublicationRepository publicationRepository;
    private final ContextHolder contextHolder;
    private final ToolManager toolManager;
    private final ObjectMapper objectMapper;
    private final McpConfigSyncHelper configSyncHelper;
    private final McpSandboxOrchestrator sandboxOrchestrator;
    private final McpTransportResolver transportResolver;

    @Lazy @Resource private ConsumerService consumerService;

    public McpServerServiceImpl(
            McpServerMetaRepository metaRepository,
            McpServerEndpointRepository endpointRepository,
            ProductRepository productRepository,
            ProductPublicationRepository publicationRepository,
            ContextHolder contextHolder,
            ToolManager toolManager,
            ObjectMapper objectMapper,
            McpConfigSyncHelper configSyncHelper,
            McpSandboxOrchestrator sandboxOrchestrator,
            McpTransportResolver transportResolver) {
        this.metaRepository = metaRepository;
        this.endpointRepository = endpointRepository;
        this.productRepository = productRepository;
        this.publicationRepository = publicationRepository;
        this.contextHolder = contextHolder;
        this.toolManager = toolManager;
        this.objectMapper = objectMapper;
        this.configSyncHelper = configSyncHelper;
        this.sandboxOrchestrator = sandboxOrchestrator;
        this.transportResolver = transportResolver;
    }

    @Override
    @Transactional
    public McpMetaResult saveMeta(SaveMcpMetaParam param) {
        param.setProtocolType(McpProtocolUtils.normalize(param.getProtocolType()));

        McpProtocolType protocolEnum = McpProtocolType.fromString(param.getProtocolType());
        if (protocolEnum != null && protocolEnum.isStdio()) {
            param.setSandboxRequired(true);
        } else if (param.getSandboxRequired() == null) {
            McpOrigin paramOrigin = McpOrigin.fromString(param.getOrigin());
            param.setSandboxRequired(
                    paramOrigin != McpOrigin.GATEWAY && paramOrigin != McpOrigin.NACOS);
        }

        McpServerMeta meta =
                metaRepository
                        .findByProductIdAndMcpName(param.getProductId(), param.getMcpName())
                        .orElse(null);

        if (meta == null) {
            List<McpServerMeta> existing = metaRepository.findByProductId(param.getProductId());
            if (existing.size() == 1) {
                meta = existing.get(0);
            }
        }

        if (meta == null) {
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
                                            param.getCreatedBy(),
                                            sandboxOrchestrator.getCreatedByOrDefault()))
                            .build();
        } else {
            param.setToolsConfig(McpToolsConfigParser.normalize(param.getToolsConfig()));
            BeanUtil.copyProperties(
                    param,
                    meta,
                    CopyOptions.create().ignoreNullValue().setIgnoreProperties("productId"));
        }

        metaRepository.save(meta);

        configSyncHelper.syncDisplayFieldsToProduct(meta.getProductId(), param);
        configSyncHelper.syncProductRef(meta, param);

        if (!Boolean.TRUE.equals(meta.getSandboxRequired())) {
            configSyncHelper.syncPublicEndpoint(meta);
        }

        return configSyncHelper.enrichedResult(meta);
    }

    @Override
    @Transactional
    public McpMetaResult registerMcp(RegisterMcpParam param) {
        metaRepository
                .findByMcpName(param.getMcpName())
                .ifPresent(
                        existing -> {
                            throw new BusinessException(
                                    ErrorCode.INVALID_REQUEST,
                                    "MCP 名称「" + param.getMcpName() + "」已被注册，请更换名称");
                        });

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
                        configSyncHelper.extractEndpointUrl(
                                connJson, param.getMcpName(), param.getProtocolType());
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

        String productId = IdGenerator.genApiProductId();
        String productName = StrUtil.blankToDefault(param.getDisplayName(), param.getMcpName());
        Product product =
                Product.builder()
                        .productId(productId)
                        .name(productName)
                        .type(com.alibaba.himarket.support.enums.ProductType.MCP_SERVER)
                        .description(param.getDescription())
                        .status(ProductStatus.PENDING)
                        .adminId(
                                StrUtil.blankToDefault(
                                        param.getCreatedBy(),
                                        sandboxOrchestrator.getCreatedByOrDefault()))
                        .enableConsumerAuth(false)
                        .autoApprove(true)
                        .build();

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

        SaveMcpMetaParam metaParam = new SaveMcpMetaParam();
        metaParam.setProductId(productId);
        metaParam.setMcpName(param.getMcpName());
        metaParam.setDisplayName(param.getDisplayName());
        metaParam.setDescription(param.getDescription());
        metaParam.setRepoUrl(param.getRepoUrl());
        metaParam.setSourceType(SourceType.CUSTOM.name());
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
        configSyncHelper.enrichFromProduct(result, meta.getProductId());
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
        configSyncHelper.fillResolvedConfig(result, meta, ep);
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
        configSyncHelper.enrichFromProduct(result, meta.getProductId());
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
                            configSyncHelper.enrichFromProduct(r, m.getProductId());
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
                            configSyncHelper.enrichFromProduct(r, m.getProductId());
                            return r;
                        });
    }

    @Override
    public PageResult<McpMetaResult> listPublishedMetaByOrigin(String origin, Pageable pageable) {
        return listPublishedMetaInternal(
                pageable, ids -> metaRepository.findByProductIdInAndOrigin(ids, origin, pageable));
    }

    @Override
    public PageResult<McpMetaResult> listAllPublishedMeta(Pageable pageable) {
        return listPublishedMetaInternal(
                pageable, ids -> metaRepository.findByProductIdIn(ids, pageable));
    }

    private PageResult<McpMetaResult> listPublishedMetaInternal(
            Pageable pageable,
            java.util.function.Function<List<String>, Page<McpServerMeta>> queryFn) {
        List<String> publishedProductIds =
                productRepository.findProductIdsByTypeAndStatus(
                        com.alibaba.himarket.support.enums.ProductType.MCP_SERVER,
                        ProductStatus.PUBLISHED);
        if (publishedProductIds.isEmpty()) {
            return PageResult.of(
                    List.of(), pageable.getPageNumber() + 1, pageable.getPageSize(), 0);
        }

        Page<McpServerMeta> metaPage = queryFn.apply(publishedProductIds);
        if (metaPage.isEmpty()) {
            return PageResult.of(List.of(), metaPage.getNumber() + 1, metaPage.getSize(), 0);
        }

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
                                    configSyncHelper.enrichFromProduct(
                                            r, productMap.get(m.getProductId()));
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
        return getMeta(meta.getMcpServerId());
    }

    @Override
    public List<McpMetaResult> listMetaByProduct(String productId) {
        Product product = productRepository.findByProductId(productId).orElse(null);
        return metaRepository.findByProductId(productId).stream()
                .map(
                        m -> {
                            McpMetaResult result = new McpMetaResult().convertFrom(m);
                            configSyncHelper.enrichFromProduct(result, product);
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
                            configSyncHelper.fillResolvedConfig(result, m, ep);
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

        Map<String, Product> productMap =
                productRepository.findByProductIdIn(productIds).stream()
                        .collect(Collectors.toMap(Product::getProductId, p -> p, (a, b) -> a));

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
                            configSyncHelper.enrichFromProduct(
                                    result, productMap.get(m.getProductId()));
                            McpServerEndpoint ep = endpointMap.get(m.getMcpServerId());
                            if (ep != null) {
                                result.setEndpointUrl(ep.getEndpointUrl());
                                result.setEndpointProtocol(ep.getProtocol());
                                result.setEndpointStatus(ep.getStatus());
                                result.setSubscribeParams(ep.getSubscribeParams());
                                result.setEndpointHostingType(ep.getHostingType());
                            }
                            configSyncHelper.fillResolvedConfig(result, m, ep);
                            return result;
                        })
                .collect(Collectors.toList());
    }

    @Override
    public McpMetaResult refreshTools(String mcpServerId) {
        McpServerMeta meta = findMeta(mcpServerId);

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
        configSyncHelper.enrichFromProduct(result, meta.getProductId());
        return result;
    }

    @Override
    @Transactional
    public McpMetaResult updateServiceIntro(String mcpServerId, String serviceIntro) {
        McpServerMeta meta = findMeta(mcpServerId);
        productRepository
                .findByProductId(meta.getProductId())
                .ifPresent(
                        product -> {
                            product.setDocument(serviceIntro);
                            productRepository.save(product);
                        });
        McpMetaResult result = new McpMetaResult().convertFrom(meta);
        configSyncHelper.enrichFromProduct(result, meta.getProductId());
        return result;
    }

    @Override
    @Transactional
    public McpMetaResult updateToolsConfig(String mcpServerId, String toolsConfig) {
        McpServerMeta meta = findMeta(mcpServerId);
        String normalized = McpToolsConfigParser.normalize(toolsConfig);
        meta.setToolsConfig(normalized);
        metaRepository.save(meta);
        McpMetaResult result = new McpMetaResult().convertFrom(meta);
        configSyncHelper.enrichFromProduct(result, meta.getProductId());
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
        if (meta.getMcpName() != null && meta.getMcpName().length() > 32) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "MCP 英文名称不能超过 32 个字符（当前 " + meta.getMcpName().length() + " 个），请先修改名称后再部署沙箱");
        }
        param.setProductId(meta.getProductId());
        param.setMcpName(meta.getMcpName());
        sandboxOrchestrator.doDeploySandbox(meta, param);
        McpMetaResult result = new McpMetaResult().convertFrom(meta);
        configSyncHelper.enrichFromProduct(result, meta.getProductId());
        return result;
    }

    @Override
    @Transactional
    public McpMetaResult undeploySandbox(String mcpServerId) {
        McpServerMeta meta = findMeta(mcpServerId);
        if (!Boolean.TRUE.equals(meta.getSandboxRequired())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "该 MCP 配置未启用沙箱托管");
        }
        sandboxOrchestrator.undeploySandboxEndpoints(meta);
        List<McpServerEndpoint> endpoints =
                endpointRepository.findByMcpServerId(meta.getMcpServerId());
        for (McpServerEndpoint ep : endpoints) {
            if (McpHostingType.SANDBOX.name().equalsIgnoreCase(ep.getHostingType())) {
                endpointRepository.delete(ep);
            }
        }
        log.info("管理员取消沙箱托管: mcpName={}, mcpServerId={}", meta.getMcpName(), mcpServerId);
        McpMetaResult result = new McpMetaResult().convertFrom(meta);
        configSyncHelper.enrichFromProduct(result, meta.getProductId());
        return result;
    }

    @Override
    @Transactional
    public void deleteMeta(String mcpServerId) {
        McpServerMeta meta = findMeta(mcpServerId);
        String productId = meta.getProductId();

        if (publicationRepository.existsByProductId(productId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "产品已发布，请先下架后再删除 MCP 配置");
        }

        sandboxOrchestrator.undeploySandboxEndpoints(meta);

        endpointRepository.deleteByMcpServerId(mcpServerId);
        metaRepository.delete(meta);

        List<McpServerMeta> remaining = metaRepository.findByProductId(productId);
        if (remaining.isEmpty()) {
            configSyncHelper.deleteProductRef(productId);
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

        if (publicationRepository.existsByProductId(productId)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "产品已发布，请先下架后再删除 MCP 配置");
        }

        for (McpServerMeta meta : metas) {
            sandboxOrchestrator.undeploySandboxEndpoints(meta);
            endpointRepository.deleteByMcpServerId(meta.getMcpServerId());
            metaRepository.delete(meta);
        }

        configSyncHelper.deleteProductRef(productId);

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
            sandboxOrchestrator.undeploySandboxEndpoints(meta);
            endpointRepository.deleteByMcpServerId(meta.getMcpServerId());
            metaRepository.delete(meta);
        }

        configSyncHelper.deleteProductRef(productId);
    }

    @Override
    @Transactional
    public McpEndpointResult saveEndpoint(SaveMcpEndpointParam param) {
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
        List<McpServerMeta> metas = metaRepository.findByProductIdIn(productIds);
        List<McpMetaResult> results =
                metas.stream()
                        .map(
                                m -> {
                                    McpMetaResult r = new McpMetaResult().convertFrom(m);
                                    configSyncHelper.enrichFromProduct(
                                            r, productMap.get(m.getProductId()));
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

        Map<String, McpServerMeta> metaMap =
                metaRepository.findByMcpServerIdIn(mcpServerIds).stream()
                        .collect(
                                Collectors.toMap(
                                        McpServerMeta::getMcpServerId, m -> m, (a, b) -> a));

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

    // ==================== Transport Config Resolution ====================

    @Override
    public List<MCPTransportConfig> resolveTransportConfigs(
            List<String> productIds, String userId) {
        return transportResolver.resolveTransportConfigs(productIds, userId);
    }

    // ==================== Private Methods ====================

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

    private void fetchAndSaveToolsListOrThrow(
            String mcpServerId, String endpointUrl, String transportType) {
        McpServerMeta meta = metaRepository.findByMcpServerId(mcpServerId).orElse(null);
        if (meta == null) {
            log.warn("异步获取工具列表时 meta 已不存在: mcpServerId={}", mcpServerId);
            return;
        }
        fetchAndSaveToolsListOrThrow(meta, endpointUrl, transportType);
    }

    private void fetchAndSaveToolsListOrThrow(
            McpServerMeta meta, String endpointUrl, String transportType) {
        MCPTransportMode mode = McpProtocolType.resolveTransportMode(transportType);

        MCPTransportConfig config =
                MCPTransportConfig.builder()
                        .mcpServerName(meta.getMcpName())
                        .transportMode(mode)
                        .url(endpointUrl)
                        .build();

        McpClientWrapper client = toolManager.createClient(config);
        if (client == null) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "创建 MCP 客户端失败，请检查连接地址是否可达: mcpName=" + meta.getMcpName());
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
}
