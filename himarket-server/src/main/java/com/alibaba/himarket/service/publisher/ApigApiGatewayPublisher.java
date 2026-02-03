package com.alibaba.himarket.service.publisher;

import com.alibaba.himarket.entity.APIDefinition;
import com.alibaba.himarket.entity.Gateway;
import com.alibaba.himarket.service.api.GatewayPublisher;
import com.alibaba.himarket.service.gateway.AIGWOperator;
import com.alibaba.himarket.support.api.DeploymentConfig;
import com.alibaba.himarket.support.api.service.AiServiceConfig;
import com.alibaba.himarket.support.api.service.DnsServiceConfig;
import com.alibaba.himarket.support.api.service.FixedAddressServiceConfig;
import com.alibaba.himarket.support.api.service.GatewayServiceConfig;
import com.alibaba.himarket.support.api.service.ServiceConfig;
import com.alibaba.himarket.support.enums.APIType;
import com.alibaba.himarket.support.enums.GatewayType;
import com.alibaba.himarket.support.product.GatewayRefConfig;
import com.aliyun.sdk.service.apig20240327.models.CreateHttpApiRequest;
import com.aliyun.sdk.service.apig20240327.models.CreateServiceRequest;
import com.aliyun.sdk.service.apig20240327.models.HttpApiDeployConfig;
import com.aliyun.sdk.service.apig20240327.models.UpdateHttpApiRequest;
import com.aliyun.sdk.service.apig20240327.models.UpdateServiceRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Base publisher for APIG API Gateway. Provides common functionality for
 * creating and managing
 * services in the gateway. Subclasses can extend this to implement specific API
 * type publishing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApigApiGatewayPublisher implements GatewayPublisher {

    protected AIGWOperator operator;

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.APIG_API;
    }

    @Override
    public List<APIType> getSupportedAPITypes() {
        return List.of(APIType.REST_API);
    }

    @Override
    public GatewayRefConfig publish(
            Gateway gateway, APIDefinition apiDefinition, DeploymentConfig deploymentConfig) {
        return null;
    }

    @Override
    public String unpublish(
            Gateway gateway, APIDefinition apiDefinition, DeploymentConfig deploymentConfig) {
        return "Mock unpublish success";
    }

    @Override
    public boolean isPublished(Gateway gateway, APIDefinition apiDefinition) {
        return false;
    }

    @Override
    public void validateDeploymentConfig(
            APIDefinition apiDefinition, DeploymentConfig deploymentConfig) {
        // Mock validation
    }

    // ==================== Protected methods for subclasses ====================

    /**
     * Ensure service exists in the gateway (query/create/update)
     *
     * @param gateway       The gateway
     * @param apiName       The API name
     * @param serviceConfig The service configuration
     * @return The service ID
     */
    protected String ensureServiceExists(
            Gateway gateway, String apiName, ServiceConfig serviceConfig) {
        if (serviceConfig == null) {
            throw new IllegalArgumentException("ServiceConfig cannot be null");
        }

        // If it's GatewayServiceConfig type, use the serviceId directly without
        // creating/updating
        if (serviceConfig instanceof GatewayServiceConfig) {
            GatewayServiceConfig gatewayServiceConfig = (GatewayServiceConfig) serviceConfig;
            if (gatewayServiceConfig.getServiceId() != null
                    && !gatewayServiceConfig.getServiceId().isEmpty()) {
                log.info(
                        "Using GatewayServiceConfig with existing serviceId: {}",
                        gatewayServiceConfig.getServiceId());
                return gatewayServiceConfig.getServiceId();
            } else {
                throw new IllegalArgumentException(
                        "GatewayServiceConfig must have a serviceId. Please provide a valid"
                                + " serviceId.");
            }
        }

        // For FixedAddressServiceConfig and DnsServiceConfig, generate service name and
        // query/create/update
        // Generate service name
        String serviceName = generateServiceName(apiName, serviceConfig);

        // Check if service already exists
        Optional<String> existingServiceId = operator.findServiceIdByName(gateway, serviceName);

        if (existingServiceId.isPresent()) {
            // Service exists, update it
            log.info(
                    "Service with name '{}' already exists in gateway {}, updating existing"
                            + " service",
                    serviceName,
                    gateway.getGatewayId());

            String serviceId = existingServiceId.get();
            updateService(gateway, serviceId, serviceConfig, serviceName);
            return serviceId;
        } else {
            // Service doesn't exist, create new one
            log.info(
                    "Service with name '{}' does not exist in gateway {}, creating new service",
                    serviceName,
                    gateway.getGatewayId());
            return createServiceFromConfig(gateway, serviceConfig, serviceName);
        }
    }

    /**
     * Create Service from ServiceConfig in the specified gateway
     *
     * @param gateway       The gateway to create service in
     * @param serviceConfig The service configuration (FixedAddressServiceConfig or
     *                      DnsServiceConfig)
     * @param serviceName   The name of the service to create
     * @return The created service ID
     */
    protected String createServiceFromConfig(
            Gateway gateway, ServiceConfig serviceConfig, String serviceName) {
        if (serviceConfig == null) {
            throw new IllegalArgumentException("ServiceConfig cannot be null");
        }

        // Extract resource group ID
        String resourceGroupId = extractResourceGroupId(gateway);

        // Build service configs using extracted method
        CreateServiceRequest.ServiceConfigs sdkServiceConfig =
                buildSdkServiceConfig(serviceConfig, serviceName);
        String sourceType = getSourceType(serviceConfig);

        // Build CreateServiceRequest
        CreateServiceRequest.Builder requestBuilder =
                CreateServiceRequest.builder()
                        .gatewayId(gateway.getGatewayId())
                        .sourceType(sourceType)
                        .serviceConfigs(Collections.singletonList(sdkServiceConfig));

        // Add resource group ID if available
        if (resourceGroupId != null) {
            requestBuilder.resourceGroupId(resourceGroupId);
        }

        CreateServiceRequest request = requestBuilder.build();

        // Log the request for debugging
        log.info(
                "Creating Service: name={}, sourceType={}, gatewayId={}, resourceGroupId={}, "
                        + "addresses={}",
                serviceName,
                sourceType,
                gateway.getGatewayId(),
                resourceGroupId,
                sdkServiceConfig.getAddresses());

        // Call operator to create service
        String serviceId = operator.createService(gateway, request);

        log.info("Successfully created Service: name={}, serviceId={}", serviceName, serviceId);

        return serviceId;
    }

    /**
     * Update existing service
     *
     * @param gateway       The gateway
     * @param serviceId     The service ID
     * @param serviceConfig The service configuration
     * @param serviceName   The service name
     */
    protected void updateService(
            Gateway gateway, String serviceId, ServiceConfig serviceConfig, String serviceName) {
        // Extract resource group ID
        String resourceGroupId = extractResourceGroupId(gateway);

        // Build service configs using extracted method
        CreateServiceRequest.ServiceConfigs sdkServiceConfig =
                buildSdkServiceConfig(serviceConfig, serviceName);
        String sourceType = getSourceType(serviceConfig);

        // Build UpdateServiceRequest
        UpdateServiceRequest.Builder requestBuilder =
                UpdateServiceRequest.builder().serviceId(serviceId);

        // For DNS or Fixed Address services, set addresses directly
        if (serviceConfig instanceof DnsServiceConfig
                || serviceConfig instanceof FixedAddressServiceConfig) {
            List<String> addresses = sdkServiceConfig.getAddresses();
            if (addresses != null && !addresses.isEmpty()) {
                requestBuilder.addresses(addresses);
                log.info("Setting addresses for {} service: {}", sourceType, addresses);
            }
        }

        if (serviceConfig instanceof AiServiceConfig) {
            requestBuilder.aiServiceConfig(sdkServiceConfig.getAiServiceConfig());
        }

        UpdateServiceRequest request = requestBuilder.build();

        // Log the request for debugging
        log.info(
                "Updating Service: serviceId={}, name={}, sourceType={}, gatewayId={}, "
                        + "resourceGroupId={}, addresses={}",
                serviceId,
                serviceName,
                sourceType,
                gateway.getGatewayId(),
                resourceGroupId,
                sdkServiceConfig.getAddresses());

        // Call operator to update service
        operator.updateService(gateway, request);

        log.info("Successfully updated Service: serviceId={}, name={}", serviceId, serviceName);
    }

    /**
     * Build SDK ServiceConfigs from ServiceConfig
     *
     * @param serviceConfig The service configuration
     * @param serviceName   The service name
     * @return The SDK service configs
     */
    private CreateServiceRequest.ServiceConfigs buildSdkServiceConfig(
            ServiceConfig serviceConfig, String serviceName) {
        if (serviceConfig instanceof FixedAddressServiceConfig) {
            return buildFixedAddressServiceConfig(
                    (FixedAddressServiceConfig) serviceConfig, serviceName);
        } else if (serviceConfig instanceof DnsServiceConfig) {
            return buildDnsServiceConfig((DnsServiceConfig) serviceConfig, serviceName);
        } else if (serviceConfig instanceof AiServiceConfig) {
            return buildAiServiceConfig((AiServiceConfig) serviceConfig, serviceName);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported ServiceConfig type: "
                            + serviceConfig.getClass().getSimpleName()
                            + ". Only FixedAddressServiceConfig, DnsServiceConfig, and"
                            + " AiServiceConfig are supported.");
        }
    }

    /**
     * Get source type from ServiceConfig
     *
     * @param serviceConfig The service configuration
     * @return The source type
     */
    private String getSourceType(ServiceConfig serviceConfig) {
        if (serviceConfig instanceof FixedAddressServiceConfig) {
            return "VIP";
        } else if (serviceConfig instanceof DnsServiceConfig) {
            return "DNS";
        } else if (serviceConfig instanceof AiServiceConfig) {
            return "AI";
        } else {
            throw new IllegalArgumentException(
                    "Unsupported ServiceConfig type: " + serviceConfig.getClass().getSimpleName());
        }
    }

    /**
     * Build SDK ServiceConfigs for FixedAddressServiceConfig
     *
     * @param fixedConfig The fixed address service config
     * @param serviceName The service name
     * @return The SDK service configs
     */
    private CreateServiceRequest.ServiceConfigs buildFixedAddressServiceConfig(
            FixedAddressServiceConfig fixedConfig, String serviceName) {
        // Parse addresses from comma-separated string
        List<String> addresses = new ArrayList<>();
        if (fixedConfig.getAddress() != null && !fixedConfig.getAddress().isEmpty()) {
            String[] addressArray = fixedConfig.getAddress().split(",");
            for (String addr : addressArray) {
                addresses.add(addr.trim());
            }
        }

        if (addresses.isEmpty()) {
            throw new IllegalArgumentException(
                    "FixedAddressServiceConfig must have at least one address");
        }

        return CreateServiceRequest.ServiceConfigs.builder()
                .name(serviceName)
                .addresses(addresses)
                .build();
    }

    /**
     * Build SDK ServiceConfigs for DnsServiceConfig
     *
     * @param dnsConfig   The DNS service config
     * @param serviceName The service name
     * @return The SDK service configs
     */
    private CreateServiceRequest.ServiceConfigs buildDnsServiceConfig(
            DnsServiceConfig dnsConfig, String serviceName) {
        if (dnsConfig.getDomain() == null || dnsConfig.getDomain().isEmpty()) {
            throw new IllegalArgumentException("DnsServiceConfig must have a domain");
        }

        // For DNS, the address format is typically "domain:port"
        // If no port is specified, use default port 80
        String domain = dnsConfig.getDomain();
        String address = domain.contains(":") ? domain : domain + ":80";

        return CreateServiceRequest.ServiceConfigs.builder()
                .name(serviceName)
                .addresses(Collections.singletonList(address))
                .build();
    }

    /**
     * Build SDK ServiceConfigs for AiServiceConfig
     *
     * @param aiConfig    The AI service config
     * @param serviceName The service name
     * @return The SDK service configs
     */
    private CreateServiceRequest.ServiceConfigs buildAiServiceConfig(
            AiServiceConfig aiConfig, String serviceName) {
        if (aiConfig.getProvider() == null || aiConfig.getProvider().isEmpty()) {
            throw new IllegalArgumentException("AiServiceConfig must have a provider");
        }

        // Build protocols list - default to OpenAI/v1 if not specified
        List<String> protocols = new ArrayList<>();
        if (aiConfig.getProtocol() != null && !aiConfig.getProtocol().isEmpty()) {
            protocols.add(aiConfig.getProtocol());
        } else {
            protocols.add("OpenAI/v1");
        }

        // Determine address - use the address field directly
        String address = aiConfig.getAddress();

        // Build AI service config using builder pattern
        com.aliyun.sdk.service.apig20240327.models.AiServiceConfig.Builder
                sdkAiServiceConfigBuilder =
                        com.aliyun.sdk.service.apig20240327.models.AiServiceConfig.builder()
                                .provider(aiConfig.getProvider())
                                .protocols(protocols)
                                .apiKeyGenerateMode("Custom")
                                .enableHealthCheck(true);

        // Add address if available
        if (address != null) {
            sdkAiServiceConfigBuilder.address(address);
        }

        // Handle Bedrock-specific configuration
        if ("bedrock".equalsIgnoreCase(aiConfig.getProvider())) {
            buildBedrockServiceConfig(aiConfig, sdkAiServiceConfigBuilder);
        } else {
            // For non-Bedrock providers, add API keys if available
            if (aiConfig.getApiKey() != null && !aiConfig.getApiKey().isEmpty()) {
                sdkAiServiceConfigBuilder.apiKeys(Collections.singletonList(aiConfig.getApiKey()));
            }
        }

        // Build the service config with AI service config
        return CreateServiceRequest.ServiceConfigs.builder()
                .name(serviceName)
                .aiServiceConfig(sdkAiServiceConfigBuilder.build())
                .dnsServers(new ArrayList<>())
                .build();
    }

    /**
     * Build Bedrock-specific configuration
     *
     * @param aiConfig                 The AI service config
     * @param sdkAiServiceConfigBuilder The SDK AI service config builder
     */
    private void buildBedrockServiceConfig(
            AiServiceConfig aiConfig,
            com.aliyun.sdk.service.apig20240327.models.AiServiceConfig.Builder
                    sdkAiServiceConfigBuilder) {
        // Build bedrockServiceConfig using the SDK's typed class
        com.aliyun.sdk.service.apig20240327.models.AiServiceConfig.BedrockServiceConfig.Builder
                bedrockConfigBuilder =
                        com.aliyun.sdk.service.apig20240327.models.AiServiceConfig
                                .BedrockServiceConfig.builder();

        if (aiConfig.getAwsRegion() != null && !aiConfig.getAwsRegion().isEmpty()) {
            bedrockConfigBuilder.awsRegion(aiConfig.getAwsRegion());
        }

        // Handle authentication based on bedrockAuthType
        String authType = aiConfig.getBedrockAuthType();
        if ("AK_SK".equals(authType)) {
            // AK/SK authentication - apiKeys should be empty array
            sdkAiServiceConfigBuilder.apiKeys(new ArrayList<>());

            // Set credentials in bedrockServiceConfig
            if (aiConfig.getAwsAccessKey() != null && !aiConfig.getAwsAccessKey().isEmpty()) {
                bedrockConfigBuilder.awsAccessKey(aiConfig.getAwsAccessKey());
            }
            if (aiConfig.getAwsSecretKey() != null && !aiConfig.getAwsSecretKey().isEmpty()) {
                bedrockConfigBuilder.awsSecretKey(aiConfig.getAwsSecretKey());
            }
        } else {
            // API_KEY authentication - put apiKey in apiKeys array
            if (aiConfig.getApiKey() != null && !aiConfig.getApiKey().isEmpty()) {
                sdkAiServiceConfigBuilder.apiKeys(Collections.singletonList(aiConfig.getApiKey()));
            }
        }

        // Set bedrockServiceConfig using typed class
        sdkAiServiceConfigBuilder.bedrockServiceConfig(bedrockConfigBuilder.build());
        log.info("Set bedrockServiceConfig with region: {}", aiConfig.getAwsRegion());
    }

    /**
     * Generate service name based on API name and service type
     *
     * @param apiName       The API name
     * @param serviceConfig The service configuration
     * @return Generated service name
     */
    protected String generateServiceName(String apiName, ServiceConfig serviceConfig) {
        String serviceTypeSuffix = "";
        if (serviceConfig instanceof FixedAddressServiceConfig) {
            serviceTypeSuffix = "-vip";
        } else if (serviceConfig instanceof DnsServiceConfig) {
            serviceTypeSuffix = "-dns";
        } else if (serviceConfig instanceof AiServiceConfig) {
            AiServiceConfig aiConfig = (AiServiceConfig) serviceConfig;
            String provider = aiConfig.getProvider();
            if (provider != null && !provider.isEmpty()) {
                serviceTypeSuffix = "-ai-" + provider;
            } else {
                serviceTypeSuffix = "-ai";
            }
        }
        return apiName + serviceTypeSuffix;
    }

    /**
     * Extract resource group ID from gateway configuration
     *
     * @param gateway The gateway
     * @return Resource group ID, or null if not configured
     */
    protected String extractResourceGroupId(Gateway gateway) {
        // ResourceGroupId is not stored in APIGConfig
        // It should be configured separately or passed through publish config
        // For now, return null to use the default resource group
        // TODO: Add resourceGroupId field to Gateway or DeploymentConfig if needed
        return null;
    }

    /**
     * Ensure HTTP API exists in the gateway (query/create/update)
     *
     * @param gateway         The gateway
     * @param apiName         The API name
     * @param type            The API type ("LLM" or "Agent")
     * @param basePath        The base path
     * @param protocols       The list of protocols (AI or Agent protocols)
     * @param deployConfig    The deployment configuration
     * @param resourceGroupId The resource group ID (create only)
     * @param description     The description
     * @param modelCategory   The model category (for LLM type only, can be null)
     * @return The HTTP API ID
     */
    protected String createOrUpdateHttpApi(
            Gateway gateway,
            String apiName,
            String type,
            String basePath,
            List<String> protocols,
            HttpApiDeployConfig deployConfig,
            String resourceGroupId,
            String description,
            String modelCategory) {
        // Check if HTTP API already exists by name
        Optional<String> existingHttpApiId = operator.findHttpApiIdByName(gateway, apiName, type);

        String httpApiId;
        if (existingHttpApiId.isPresent()) {
            // HTTP API exists, use update interface
            httpApiId = existingHttpApiId.get();
            log.info(
                    "Found existing HTTP API: name={}, httpApiId={}, will update it",
                    apiName,
                    httpApiId);

            // Build UpdateHttpApi request
            UpdateHttpApiRequest.Builder updateRequestBuilder =
                    UpdateHttpApiRequest.builder()
                            .httpApiId(httpApiId)
                            .basePath(basePath)
                            .removeBasePathOnForward(true)
                            .firstByteTimeout(0)
                            .deployConfigs(Collections.singletonList(deployConfig));

            // Set protocols based on type
            if ("LLM".equalsIgnoreCase(type)) {
                updateRequestBuilder.aiProtocols(protocols);
            } else if ("Agent".equalsIgnoreCase(type)) {
                updateRequestBuilder.agentProtocols(protocols);
            }

            // Add description if available
            if (description != null && !description.isEmpty()) {
                updateRequestBuilder.description(description);
            }

            UpdateHttpApiRequest updateRequest = updateRequestBuilder.build();

            // Log the update request for debugging
            log.info(
                    "Updating {} API with request: httpApiId={}, basePath={}, protocols={}, "
                            + "gatewayId={}, domainIds={}, serviceConfigs={}",
                    type,
                    httpApiId,
                    basePath,
                    protocols,
                    gateway.getGatewayId(),
                    deployConfig.getCustomDomainIds(),
                    deployConfig.getServiceConfigs() != null
                            ? deployConfig.getServiceConfigs().size()
                            : 0);

            // Call operator to update HTTP API
            operator.updateHttpApi(gateway, updateRequest);

            log.info(
                    "Successfully updated {} API: name={}, httpApiId={}", type, apiName, httpApiId);
        } else {
            // HTTP API doesn't exist, create new one
            log.info("HTTP API does not exist: name={}, will create new one", apiName);

            // Build CreateHttpApi request
            CreateHttpApiRequest.Builder requestBuilder =
                    CreateHttpApiRequest.builder()
                            .name(apiName)
                            .type(type)
                            .removeBasePathOnForward(true)
                            .basePath(basePath)
                            .deployConfigs(Collections.singletonList(deployConfig))
                            .firstByteTimeout(0);

            // Set protocols based on type
            if ("LLM".equalsIgnoreCase(type)) {
                requestBuilder.aiProtocols(protocols);
                if (modelCategory != null) {
                    requestBuilder.modelCategory(modelCategory);
                }
            } else if ("Agent".equalsIgnoreCase(type)) {
                requestBuilder.agentProtocols(protocols);
            }

            // Add resource group ID if available
            if (resourceGroupId != null) {
                requestBuilder.resourceGroupId(resourceGroupId);
            }

            // Add description if available
            if (description != null && !description.isEmpty()) {
                requestBuilder.description(description);
            }

            CreateHttpApiRequest request = requestBuilder.build();

            // Log the request for debugging
            log.info(
                    "Creating {} API with request: name={}, type={}, basePath={}, protocols={}, "
                            + "gatewayId={}, domainIds={}, serviceConfigs={}, resourceGroupId={}",
                    type,
                    apiName,
                    type,
                    basePath,
                    protocols,
                    gateway.getGatewayId(),
                    deployConfig.getCustomDomainIds(),
                    deployConfig.getServiceConfigs() != null
                            ? deployConfig.getServiceConfigs().size()
                            : 0,
                    resourceGroupId);

            // Call operator to create HTTP API
            httpApiId = operator.createHttpApi(gateway, request);

            log.info(
                    "Successfully created {} API: name={}, httpApiId={}", type, apiName, httpApiId);
        }

        waitForHttpApiDeployStatus(gateway, httpApiId, apiName, type);

        return httpApiId;
    }

    /**
     * Wait until HTTP API is deployed, failed, or timeout reached.
     *
     * @param gateway   The gateway
     * @param httpApiId The HTTP API ID
     * @param apiName   The API name
     * @param type      The API type
     */
    protected void waitForHttpApiDeployStatus(
            Gateway gateway, String httpApiId, String apiName, String type) {
        final long timeoutMillis = TimeUnit.MINUTES.toMillis(3);
        final long pollIntervalMillis = TimeUnit.SECONDS.toMillis(3);
        final long startTime = System.currentTimeMillis();

        while (true) {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > timeoutMillis) {
                throw new IllegalStateException(
                        String.format(
                                "HTTP API deploy timeout after 3 minutes: name=%s, type=%s,"
                                        + " httpApiId=%s",
                                apiName, type, httpApiId));
            }

            String deployStatus = null;
            try {
                com.aliyun.sdk.service.apig20240327.models.HttpApiApiInfo apiInfo =
                        operator.fetchAPI(gateway, httpApiId);
                if (apiInfo != null
                        && apiInfo.getEnvironments() != null
                        && !apiInfo.getEnvironments().isEmpty()) {
                    deployStatus = apiInfo.getEnvironments().get(0).getDeployStatus();
                }
            } catch (Exception e) {
                log.warn(
                        "Failed to fetch HTTP API deploy status: name={}, type={}, httpApiId={},"
                                + " error={}",
                        apiName,
                        type,
                        httpApiId,
                        e.getMessage());
            }

            if ("Deployed".equalsIgnoreCase(deployStatus)) {
                log.info(
                        "HTTP API deployed successfully: name={}, type={}, httpApiId={}",
                        apiName,
                        type,
                        httpApiId);
                return;
            }

            if ("DeployFailed".equalsIgnoreCase(deployStatus)) {
                throw new IllegalStateException(
                        String.format(
                                "HTTP API deploy failed: name=%s, type=%s, httpApiId=%s",
                                apiName, type, httpApiId));
            }

            log.info(
                    "Waiting for HTTP API deploy: name={}, type={}, httpApiId={}, status={},"
                            + " elapsed={}ms",
                    apiName,
                    type,
                    httpApiId,
                    deployStatus,
                    elapsed);

            try {
                Thread.sleep(pollIntervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        String.format(
                                "HTTP API deploy wait interrupted: name=%s, type=%s, httpApiId=%s",
                                apiName, type, httpApiId));
            }
        }
    }
}
