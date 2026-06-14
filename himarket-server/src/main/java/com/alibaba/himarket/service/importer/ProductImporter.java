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

package com.alibaba.himarket.service.importer;

import cn.hutool.core.util.EnumUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.params.apidefinition.CreateApiDefinitionParam;
import com.alibaba.himarket.dto.params.product.*;
import com.alibaba.himarket.dto.result.gateway.GatewayResult;
import com.alibaba.himarket.dto.result.mcp.APIGMcpServerResult;
import com.alibaba.himarket.dto.result.mcp.GatewayMcpServerResult;
import com.alibaba.himarket.dto.result.product.ImportProductsResult;
import com.alibaba.himarket.dto.result.product.ProductImportResult;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.dto.vendor.RemoteMcpItem;
import com.alibaba.himarket.service.AiRegistryService;
import com.alibaba.himarket.service.ApiDefinitionService;
import com.alibaba.himarket.service.GatewayService;
import com.alibaba.himarket.service.ProductService;
import com.alibaba.himarket.service.vendor.McpVendorAdapter;
import com.alibaba.himarket.service.vendor.VendorAdapterRegistry;
import com.alibaba.himarket.support.api.meta.ApiDefinitionMeta;
import com.alibaba.himarket.support.api.meta.ApiDefinitionSource;
import com.alibaba.himarket.support.api.meta.ApiDefinitionSourceType;
import com.alibaba.himarket.support.api.spec.McpServerSpec;
import com.alibaba.himarket.support.enums.ApiType;
import com.alibaba.himarket.support.enums.GatewayType;
import com.alibaba.himarket.support.enums.McpFromType;
import com.alibaba.himarket.support.enums.McpProtocolType;
import com.alibaba.himarket.support.enums.McpVendorType;
import com.alibaba.himarket.support.enums.ProductType;
import com.alibaba.himarket.support.enums.SkillRegistryType;
import com.alibaba.himarket.support.enums.SourceType;
import com.alibaba.himarket.support.product.*;
import com.alibaba.himarket.utils.JsonUtil;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductImporter {

    private final ProductService productService;

    private final GatewayService gatewayService;

    private final ApiDefinitionService apiDefinitionService;

    private final AiRegistryService aiRegistryService;

    private final VendorAdapterRegistry vendorAdapterRegistry;

    /**
     * Import product resources from the selected source and record item-level failures.
     *
     * @param param the import parameters
     * @return the import result with success count and failures
     */
    public ImportProductsResult importProducts(ImportProductsParam param) {
        ImportProductsResult result = new ImportProductsResult();
        List<ProductImportResult> failures = new ArrayList<>();

        ProductType productType = param.getProductType();
        ProductImportSourceConfigParam sourceConfig = param.getSourceConfig();
        log.info(
                "Importing products, productType={}, sourceConfig={}",
                productType,
                JsonUtil.toJson(sourceConfig));
        for (ProductImportItemParam item : param.getItems()) {
            try {
                switch (param.getSource()) {
                    case GATEWAY -> importFromGateway(productType, sourceConfig, item);
                    case NACOS -> importFromNacos(productType, sourceConfig, item);
                    case AIREGISTRY -> importFromAiRegistry(productType, sourceConfig, item);
                    case EXTERNAL -> importFromExternal(productType, sourceConfig, item);
                }
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception e) {
                log.warn(
                        "Failed to import product item, resourceName={}",
                        item.getResourceName(),
                        e);
                ProductImportResult failure = new ProductImportResult();
                failure.setResourceName(item.getResourceName());
                failure.setErrorMessage(e.getMessage());
                failures.add(failure);
            }
        }

        result.setFailures(failures);
        return result;
    }

    /**
     * Import a gateway-backed product and link it to the selected gateway resource.
     *
     * @param productType the product type to import
     * @param sourceConfig the gateway import source configuration
     * @param item the resource item selected for import
     */
    private void importFromGateway(
            ProductType productType,
            ProductImportSourceConfigParam sourceConfig,
            ProductImportItemParam item) {
        CreateProductParam createProductParam = buildCreateProductParam(productType, item);
        ProductResult product = productService.createProduct(createProductParam);
        try {
            AddProductRefParam addProductRefParam =
                    buildGatewayRefParam(
                            productType, (GatewayImportConfigParam) sourceConfig, item);
            productService.addProductRef(product.getProductId(), addProductRefParam);
        } catch (Exception e) {
            log.warn(
                    "Failed to add gateway reference for product, productId={}",
                    product.getProductId(),
                    e);
            productService.deleteProduct(product.getProductId());
            throw e;
        }
    }

    /**
     * Build the product reference payload for gateway imports.
     *
     * @param productType the product type to import
     * @param importConfigParam the gateway import source configuration
     * @param item the resource item selected for import
     * @return the gateway product reference payload
     */
    private AddProductRefParam buildGatewayRefParam(
            ProductType productType,
            GatewayImportConfigParam importConfigParam,
            ProductImportItemParam item) {
        GatewayResult gateway = gatewayService.getGateway(importConfigParam.getInstanceId());

        // TODO: Support other gateway types.
        GatewayType gatewayType = gateway.getGatewayType();
        HigressRefConfig higressRefConfig =
                gatewayType.isHigress() ? buildHigressRefConfig(productType, item) : null;
        APIGRefConfig apigConfig =
                gatewayType.isAPIG() ? buildApigRefConfig(productType, item, gateway) : null;

        return AddProductRefParam.builder()
                .sourceType(SourceType.GATEWAY)
                .gatewayId(gateway.getGatewayId())
                .apigRefConfig(apigConfig)
                .higressRefConfig(higressRefConfig)
                .build();
    }

    /**
     * Build APIG-specific resource binding information for the imported product.
     *
     * @param productType the product type to import
     * @param item the resource item selected for import
     * @param gateway the gateway result
     * @return the APIG reference configuration
     */
    private APIGRefConfig buildApigRefConfig(
            ProductType productType, ProductImportItemParam item, GatewayResult gateway) {
        APIGRefConfig config = new APIGRefConfig();
        switch (productType) {
            case REST_API -> {
                config.setApiId(item.getResourceId());
                config.setApiName(item.getResourceName());
            }
            case MCP_SERVER -> {
                config.setMcpServerId(item.getResourceId());
                config.setMcpServerName(item.getResourceName());
                completeMcpRouteId(gateway, config);
            }
            case AGENT_API -> {
                config.setAgentApiId(item.getResourceId());
                config.setAgentApiName(item.getResourceName());
            }
            case MODEL_API -> {
                config.setModelApiId(item.getResourceId());
                config.setModelApiName(item.getResourceName());
            }
            default ->
                    throw new BusinessException(
                            ErrorCode.INVALID_REQUEST,
                            "Unsupported product type for gateway import");
        }
        return config;
    }

    /**
     * Complete APIG AI gateway MCP route ID for imported MCP products.
     *
     * <p>Only APIG AI gateway MCP resources require both MCP server ID and route ID. Other gateways
     * either do not use APIG reference config or do not need this compatibility field.
     */
    private void completeMcpRouteId(GatewayResult gateway, APIGRefConfig config) {
        String mcpServerId = config.getMcpServerId();
        if (!gateway.getGatewayType().isAIGateway() || StrUtil.isBlank(mcpServerId)) {
            return;
        }

        GatewayMcpServerResult mcpServer =
                gatewayService.fetchMcpServer(gateway.getGatewayId(), mcpServerId);
        if (mcpServer instanceof APIGMcpServerResult m && StrUtil.isNotBlank(m.getMcpRouteId())) {
            config.setMcpServerName(m.getMcpServerName());
            config.setMcpRouteId(m.getMcpRouteId());
            return;
        }

        throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "Cannot resolve MCP route id for resource: " + mcpServerId);
    }

    /**
     * Build Higress-specific resource binding information for the imported product.
     *
     * @param productType the product type to import
     * @param item the resource item selected for import
     * @return the Higress reference configuration
     */
    private HigressRefConfig buildHigressRefConfig(
            ProductType productType, ProductImportItemParam item) {
        HigressRefConfig higressRefConfig = new HigressRefConfig();
        switch (productType) {
            case MCP_SERVER -> higressRefConfig.setMcpServerName(item.getResourceName());
            case MODEL_API -> higressRefConfig.setModelRouteName(item.getResourceName());
            case AGENT_API -> higressRefConfig.setRouteName(item.getResourceName());
            default ->
                    throw new BusinessException(
                            ErrorCode.INVALID_REQUEST,
                            "Unsupported product type for gateway import");
        }
        return higressRefConfig;
    }

    /**
     * Import a Nacos-backed product and link it to the selected Nacos resource.
     *
     * @param productType the product type to import
     * @param sourceConfig the Nacos import source configuration
     * @param item the resource item selected for import
     */
    private void importFromNacos(
            ProductType productType,
            ProductImportSourceConfigParam sourceConfig,
            ProductImportItemParam item) {
        if (productType == ProductType.AGENT_SKILL) {
            NacosImportConfigParam nacosSourceConfig = (NacosImportConfigParam) sourceConfig;
            productService.createProduct(
                    buildSkillProductParam(
                            productType,
                            item,
                            SkillConfig.builder()
                                    .registryType(SkillRegistryType.NACOS)
                                    .nacosId(nacosSourceConfig.getInstanceId())
                                    .namespace(nacosSourceConfig.getNamespace())
                                    .skillName(item.getResourceName())
                                    .build()));
            return;
        }

        CreateProductParam createProductParam = buildCreateProductParam(productType, item);
        ProductResult product = productService.createProduct(createProductParam);
        try {
            NacosImportConfigParam nacosSourceConfig = (NacosImportConfigParam) sourceConfig;
            NacosRefConfig nacosRefConfig =
                    buildNacosRefConfig(productType, item, nacosSourceConfig);

            AddProductRefParam addProductRefParam =
                    AddProductRefParam.builder()
                            .sourceType(SourceType.NACOS)
                            .nacosId(nacosSourceConfig.getInstanceId())
                            .nacosRefConfig(nacosRefConfig)
                            .build();

            productService.addProductRef(product.getProductId(), addProductRefParam);
        } catch (Exception e) {
            log.warn(
                    "Failed to add Nacos reference for product, productId={}",
                    product.getProductId(),
                    e);
            productService.deleteProduct(product.getProductId());
            throw e;
        }
    }

    /**
     * Build Nacos-specific resource binding information for the imported product.
     *
     * @param productType the product type to import
     * @param item the resource item selected for import
     * @param nacosSourceConfig the Nacos import source configuration
     * @return the Nacos reference configuration
     */
    private NacosRefConfig buildNacosRefConfig(
            ProductType productType,
            ProductImportItemParam item,
            NacosImportConfigParam nacosSourceConfig) {
        NacosRefConfig nacosRefConfig = new NacosRefConfig();
        nacosRefConfig.setNamespaceId(nacosSourceConfig.getNamespace());

        // Only MCP server and agent API are supported for Nacos
        switch (productType) {
            case MCP_SERVER -> nacosRefConfig.setMcpServerName(item.getResourceName());
            case AGENT_API -> nacosRefConfig.setAgentName(item.getResourceName());
            default ->
                    throw new BusinessException(
                            ErrorCode.INVALID_REQUEST, "Unsupported product type for Nacos import");
        }
        return nacosRefConfig;
    }

    private void importFromAiRegistry(
            ProductType productType,
            ProductImportSourceConfigParam sourceConfig,
            ProductImportItemParam item) {
        if (productType != ProductType.AGENT_SKILL) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "AIRegistry import only supports Agent Skill products");
        }

        AiRegistryImportConfigParam aiRegistryConfig = (AiRegistryImportConfigParam) sourceConfig;
        aiRegistryService.getAiRegistryInstance(aiRegistryConfig.getInstanceId());
        productService.createProduct(
                buildSkillProductParam(
                        productType,
                        item,
                        SkillConfig.builder()
                                .registryType(SkillRegistryType.AIREGISTRY)
                                .aiRegistryId(aiRegistryConfig.getInstanceId())
                                .namespace(aiRegistryConfig.getNamespace())
                                .skillName(item.getResourceName())
                                .build()));
    }

    /**
     * Import an external MCP server and create the matching API definition.
     *
     * @param productType the product type to import
     * @param sourceConfig the external import source configuration
     * @param item the resource item selected for import
     */
    private void importFromExternal(
            ProductType productType,
            ProductImportSourceConfigParam sourceConfig,
            ProductImportItemParam item) {

        if (productType != ProductType.MCP_SERVER) {
            throw new IllegalArgumentException(
                    "External import is only supported for MCP server products");
        }

        ExternalImportConfigParam externalSourceConfig = (ExternalImportConfigParam) sourceConfig;

        McpVendorType mcpVendorType =
                EnumUtil.fromString(McpVendorType.class, externalSourceConfig.getProvider());

        McpVendorAdapter adapter = vendorAdapterRegistry.getAdapter(mcpVendorType);
        RemoteMcpItem remoteItem = adapter.getMcpServer(item.getResourceId());

        ProductResult product = productService.createProduct(buildMcpProductParam(remoteItem));
        try {
            apiDefinitionService.createApiDefinition(
                    buildMcpApiDefinitionParam(remoteItem, mcpVendorType, product.getProductId()));
        } catch (Exception e) {
            productService.deleteProduct(product.getProductId());
            throw e;
        }
    }

    /**
     * Build the product creation payload for an externally imported MCP server.
     *
     * @param item the external MCP item returned by the vendor adapter
     * @return the product creation payload
     */
    private CreateProductParam buildMcpProductParam(RemoteMcpItem item) {
        return CreateProductParam.builder()
                .name(StrUtil.blankToDefault(item.getDisplayName(), item.getMcpName()))
                .description(item.getDescription())
                .type(ProductType.MCP_SERVER)
                .autoApprove(true)
                .document(item.getServiceIntro())
                .icon(JsonUtil.parse(item.getIcon(), Icon.class))
                .build();
    }

    /**
     * Build the API definition creation payload for an externally imported MCP server.
     *
     * @param item the external MCP item returned by the vendor adapter
     * @param provider the MCP vendor type
     * @param productId the product ID to bind to the API definition
     * @return the API definition creation payload
     */
    private CreateApiDefinitionParam buildMcpApiDefinitionParam(
            RemoteMcpItem item, McpVendorType provider, String productId) {
        CreateApiDefinitionParam param = new CreateApiDefinitionParam();
        param.setName(item.getMcpName());
        param.setDescription(item.getDescription());
        param.setType(ApiType.MCP_SERVER);
        param.setRelatedProductId(productId);
        param.setSpec(buildMcpServerSpec(item));
        param.setMeta(buildMcpApiDefinitionMeta(item, provider));
        return param;
    }

    /**
     * Build the MCP server spec from the standard connection produced by a vendor adapter.
     *
     * @param item the external MCP item with standard connection data
     * @return the MCP server spec
     */
    private McpServerSpec buildMcpServerSpec(RemoteMcpItem item) {
        McpProtocolType protocol = McpProtocolType.fromString(item.getProtocolType());
        if (protocol == null || item.getConnection() == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Imported MCP connection is incomplete");
        }
        McpServerSpec spec = new McpServerSpec();
        spec.setType(ApiType.MCP_SERVER.name());
        spec.setDescription(item.getDescription());
        spec.setFromType(McpFromType.NATIVE_MCP);
        spec.setProtocol(protocol);
        spec.setConnection(item.getConnection());
        return spec;
    }

    /**
     * Build source metadata for an externally imported MCP API definition.
     *
     * @param item the external MCP item returned by the vendor adapter
     * @param provider the MCP vendor type
     * @return the API definition metadata
     */
    private ApiDefinitionMeta buildMcpApiDefinitionMeta(
            RemoteMcpItem item, McpVendorType provider) {
        return ApiDefinitionMeta.builder()
                .source(
                        ApiDefinitionSource.builder()
                                .type(ApiDefinitionSourceType.EXTERNAL)
                                .provider(provider.name())
                                .repo(item.getRepoUrl())
                                .build())
                .build();
    }

    /**
     * Build the common product creation payload used by gateway and Nacos imports.
     *
     * @param productType the product type to import
     * @param item the resource item selected for import
     * @return the product creation payload
     */
    private CreateProductParam buildCreateProductParam(
            ProductType productType, ProductImportItemParam item) {
        return CreateProductParam.builder()
                .name(item.getResourceName())
                .description(item.getDescription())
                .type(productType)
                .autoApprove(true)
                .build();
    }

    private CreateProductParam buildSkillProductParam(
            ProductType productType, ProductImportItemParam item, SkillConfig skillConfig) {
        return CreateProductParam.builder()
                .name(item.getResourceName())
                .description(item.getDescription())
                .type(productType)
                .autoApprove(true)
                .feature(ProductFeature.builder().skillConfig(skillConfig).build())
                .build();
    }
}
