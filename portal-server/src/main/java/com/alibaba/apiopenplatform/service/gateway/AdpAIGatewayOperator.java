package com.alibaba.apiopenplatform.service.gateway;

import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.dto.params.gateway.QueryAPIGParam;
import com.alibaba.apiopenplatform.dto.params.gateway.QueryAdpAIGatewayParam;
import com.alibaba.apiopenplatform.dto.result.*;
import com.alibaba.apiopenplatform.dto.result.AdpGatewayInstanceResult;
import com.alibaba.apiopenplatform.entity.Consumer;
import com.alibaba.apiopenplatform.entity.ConsumerCredential;
import com.alibaba.apiopenplatform.entity.Gateway;
import com.alibaba.apiopenplatform.support.consumer.ConsumerAuthConfig;
import com.alibaba.apiopenplatform.support.enums.GatewayType;
import com.alibaba.apiopenplatform.support.gateway.AdpAIGatewayConfig;
import com.alibaba.apiopenplatform.service.gateway.client.AdpAIGatewayClient;
import com.alibaba.apiopenplatform.support.gateway.GatewayConfig;
import com.alibaba.apiopenplatform.support.product.APIGRefConfig;
import com.alibaba.apiopenplatform.dto.result.MCPConfigResult;
import cn.hutool.json.JSONUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ADP AI网关操作器
 */
@Service
@Slf4j
public class AdpAIGatewayOperator extends GatewayOperator {

    @Override
    public PageResult<APIResult> fetchHTTPAPIs(Gateway gateway, int page, int size) {
        return null;
    }

    @Override
    public PageResult<APIResult> fetchRESTAPIs(Gateway gateway, int page, int size) {
        return null;
    }

    @Override
    public PageResult<? extends GatewayMCPServerResult> fetchMcpServers(Gateway gateway, int page, int size) {
        AdpAIGatewayConfig config = gateway.getAdpAIGatewayConfig();
        if (config == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ADP AI Gateway 配置缺失");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/mcpServer/listMcpServers");
            // 修复：添加必需的 gwInstanceId 参数
            String requestBody = String.format(
                "{\"current\": %d, \"size\": %d, \"gwInstanceId\": \"%s\"}", 
                page, 
                size, 
                gateway.getGatewayId()
            );
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            ResponseEntity<AdpMcpServerListResult> response = client.getRestTemplate().exchange(
                    url, HttpMethod.POST, requestEntity, AdpMcpServerListResult.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                AdpMcpServerListResult result = response.getBody();
                if (result.getCode() != null && result.getCode() == 200 && result.getData() != null) {
                    List<GatewayMCPServerResult> items = new ArrayList<>();
                    if (result.getData().getRecords() != null) {
                        items.addAll(result.getData().getRecords());
                    }
                    int total = result.getData().getTotal() != null ? result.getData().getTotal() : 0;
                    return PageResult.of(items, page, size, total);
                }
                String msg = result.getMessage() != null ? result.getMessage() : result.getMsg();
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, msg);
            }
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "调用 ADP /mcpServer/listMcpServers 失败");
        } catch (Exception e) {
            log.error("Error fetching ADP MCP servers", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        } finally {
            client.close();
        }
    }

    @Override
    public String fetchAPIConfig(Gateway gateway, Object config) {
        return "";
    }

    @Override
    public String fetchMcpConfig(Gateway gateway, Object conf) {
        AdpAIGatewayConfig config = gateway.getAdpAIGatewayConfig();
        if (config == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "ADP AI Gateway 配置缺失");
        }

        // 从 conf 参数中获取 APIGRefConfig
        APIGRefConfig apigRefConfig = (APIGRefConfig) conf;
        if (apigRefConfig == null || apigRefConfig.getMcpServerName() == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "MCP Server 名称缺失");
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/mcpServer/getMcpServer");
            
            // 构建请求体，包含 gwInstanceId 和 mcpServerName
            String requestBody = String.format(
                "{\"gwInstanceId\": \"%s\", \"mcpServerName\": \"%s\"}", 
                gateway.getGatewayId(), 
                apigRefConfig.getMcpServerName()
            );
            
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            ResponseEntity<AdpMcpServerDetailResult> response = client.getRestTemplate().exchange(
                    url, HttpMethod.POST, requestEntity, AdpMcpServerDetailResult.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                AdpMcpServerDetailResult result = response.getBody();
                if (result.getCode() != null && result.getCode() == 200 && result.getData() != null) {
                    return convertToMCPConfig(result.getData(), config);
                }
                String msg = result.getMessage() != null ? result.getMessage() : result.getMsg();
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, msg);
            }
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "调用 ADP /mcpServer/getMcpServer 失败");
        } catch (Exception e) {
            log.error("Error fetching ADP MCP config for server: {}", apigRefConfig.getMcpServerName(), e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        } finally {
            client.close();
        }
    }

    /**
     * 将 ADP MCP Server 详情转换为 MCPConfigResult 格式
     */
    private String convertToMCPConfig(AdpMcpServerDetailResult.AdpMcpServerDetail data, AdpAIGatewayConfig config) {
        MCPConfigResult mcpConfig = new MCPConfigResult();
        mcpConfig.setMcpServerName(data.getName());

        // 设置 MCP Server 配置
        MCPConfigResult.MCPServerConfig serverConfig = new MCPConfigResult.MCPServerConfig();
        serverConfig.setPath("/" + data.getName());
        
        // 获取网关实例访问信息并设置域名信息
        List<MCPConfigResult.Domain> domains = getGatewayAccessDomains(data.getGwInstanceId(), config);
        if (domains != null && !domains.isEmpty()) {
            serverConfig.setDomains(domains);
        } else {
            // 如果无法获取网关访问信息，则使用原有的services信息作为备选
            if (data.getServices() != null && !data.getServices().isEmpty()) {
                List<MCPConfigResult.Domain> fallbackDomains = data.getServices().stream()
                        .map(domain -> MCPConfigResult.Domain.builder()
                                .domain(domain.getName() + ":" + domain.getPort())
                                .protocol("http")
                                .build())
                        .collect(Collectors.toList());
                serverConfig.setDomains(fallbackDomains);
            }
        }
        
        mcpConfig.setMcpServerConfig(serverConfig);

        // 设置工具配置
        mcpConfig.setTools(data.getRawConfigurations());

        // 设置元数据
        MCPConfigResult.McpMetadata meta = new MCPConfigResult.McpMetadata();
        meta.setSource(GatewayType.ADP_AI_GATEWAY.name());
        meta.setFromType(data.getType());
        mcpConfig.setMeta(meta);

        return JSONUtil.toJsonStr(mcpConfig);
    }

    /**
     * 获取网关实例的访问信息并构建域名列表
     */
    private List<MCPConfigResult.Domain> getGatewayAccessDomains(String gwInstanceId, AdpAIGatewayConfig config) {
        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/gatewayInstance/getInstanceInfo");
            String requestBody = String.format("{\"gwInstanceId\": \"%s\"}", gwInstanceId);
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            // 注意：getInstanceInfo 返回的 data 是单个实例对象（无 records 字段），直接从 data.accessMode 读取
            ResponseEntity<String> response = client.getRestTemplate().exchange(
                    url, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                cn.hutool.json.JSONObject root = JSONUtil.parseObj(response.getBody());
                Integer code = root.getInt("code");
                if (code != null && code == 200 && root.containsKey("data")) {
                    cn.hutool.json.JSONObject dataObj = root.getJSONObject("data");
                    if (dataObj != null && dataObj.containsKey("accessMode")) {
                        cn.hutool.json.JSONArray arr = dataObj.getJSONArray("accessMode");
                        List<AdpGatewayInstanceResult.AccessMode> accessModes = JSONUtil.toList(arr, AdpGatewayInstanceResult.AccessMode.class);
                        return buildDomainsFromAccessModes(accessModes);
                    }
                    log.warn("Gateway instance has no accessMode, instanceId={}", gwInstanceId);
                    return null;
                }
                String message = root.getStr("message", root.getStr("msg"));
                log.warn("Failed to get gateway instance access info: {}", message);
                return null;
            }
            log.warn("Failed to call gateway instance access API");
            return null;
        } catch (Exception e) {
            log.error("Error fetching gateway access info for instance: {}", gwInstanceId, e);
            return null;
        } finally {
            client.close();
        }
    }

    /**
     * 根据网关实例访问信息构建域名列表
     */
    private List<MCPConfigResult.Domain> buildDomainsFromAccessInfo(AdpGatewayInstanceResult.AdpGatewayInstanceData data) {
        // 兼容 listInstances 调用：取第一条记录的 accessMode
        if (data != null && data.getRecords() != null && !data.getRecords().isEmpty()) {
            AdpGatewayInstanceResult.AdpGatewayInstance instance = data.getRecords().get(0);
            if (instance.getAccessMode() != null) {
                return buildDomainsFromAccessModes(instance.getAccessMode());
            }
        }
        return new ArrayList<>();
    }

    private List<MCPConfigResult.Domain> buildDomainsFromAccessModes(List<AdpGatewayInstanceResult.AccessMode> accessModes) {
        List<MCPConfigResult.Domain> domains = new ArrayList<>();
        if (accessModes == null || accessModes.isEmpty()) { return domains; }
        AdpGatewayInstanceResult.AccessMode accessMode = accessModes.get(0);

        // 1) LoadBalancer: externalIps:80
        if ("LoadBalancer".equalsIgnoreCase(accessMode.getAccessModeType())) {
            if (accessMode.getExternalIps() != null && !accessMode.getExternalIps().isEmpty()) {
                for (String externalIp : accessMode.getExternalIps()) {
                    if (externalIp == null || externalIp.isEmpty()) { continue; }
                    MCPConfigResult.Domain domain = MCPConfigResult.Domain.builder()
                            .domain(externalIp + ":80")
                            .protocol("http")
                            .build();
                    domains.add(domain);
                }
            }
        }

        // 2) NodePort: ips + ports → ip:nodePort
        if (domains.isEmpty() && "NodePort".equalsIgnoreCase(accessMode.getAccessModeType())) {
            List<String> ips = accessMode.getIps();
            List<String> ports = accessMode.getPorts();
            if (ips != null && !ips.isEmpty() && ports != null && !ports.isEmpty()) {
                for (String ip : ips) {
                    if (ip == null || ip.isEmpty()) { continue; }
                    for (String portMapping : ports) {
                        if (portMapping == null || portMapping.isEmpty()) { continue; }
                        String[] parts = portMapping.split(":");
                        if (parts.length >= 2) {
                            String nodePort = parts[1].split("/")[0];
                            MCPConfigResult.Domain domain = MCPConfigResult.Domain.builder()
                                    .domain(ip + ":" + nodePort)
                                    .protocol("http")
                                    .build();
                            domains.add(domain);
                        }
                    }
                }
            }
        }

        // 3) fallback: only externalIps → :80
        if (domains.isEmpty() && accessMode.getExternalIps() != null && !accessMode.getExternalIps().isEmpty()) {
            for (String externalIp : accessMode.getExternalIps()) {
                if (externalIp == null || externalIp.isEmpty()) { continue; }
                MCPConfigResult.Domain domain = MCPConfigResult.Domain.builder()
                        .domain(externalIp + ":80")
                        .protocol("http")
                        .build();
                domains.add(domain);
            }
        }

        return domains;
    }

    @Override
    public String createConsumer(Consumer consumer, ConsumerCredential credential, GatewayConfig config) {
        return "";
    }

    @Override
    public void updateConsumer(String consumerId, ConsumerCredential credential, GatewayConfig config) {

    }

    @Override
    public void deleteConsumer(String consumerId, GatewayConfig config) {

    }

    @Override
    public ConsumerAuthConfig authorizeConsumer(Gateway gateway, String consumerId, Object refConfig) {
        return null;
    }

    @Override
    public void revokeConsumerAuthorization(Gateway gateway, String consumerId, ConsumerAuthConfig authConfig) {

    }

    @Override
    public APIResult fetchAPI(Gateway gateway, String apiId) {
        return null;
    }

    @Override
    public GatewayType getGatewayType() {
        return GatewayType.ADP_AI_GATEWAY;
    }

    @Override
    public String getDashboard(Gateway gateway) {
        return null;
    }

    @Override
    public PageResult<GatewayResult> fetchGateways(Object param, int page, int size) {
        if (!(param instanceof QueryAdpAIGatewayParam)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "param");
        }
        return fetchGateways((QueryAdpAIGatewayParam) param, page, size);
    }

    public PageResult<GatewayResult> fetchGateways(QueryAdpAIGatewayParam param, int page, int size) {
        AdpAIGatewayConfig config = new AdpAIGatewayConfig();
        config.setBaseUrl(param.getBaseUrl());
        config.setPort(param.getPort());
        
        // 根据认证类型设置不同的认证信息
        if ("Seed".equals(param.getAuthType())) {
            if (param.getAuthSeed() == null || param.getAuthSeed().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Seed认证方式下authSeed不能为空");
            }
            config.setAuthSeed(param.getAuthSeed());
        } else if ("Header".equals(param.getAuthType())) {
            if (param.getAuthHeaders() == null || param.getAuthHeaders().isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER, "Header认证方式下authHeaders不能为空");
            }
            // 将authHeaders转换为配置
            List<AdpAIGatewayConfig.AuthHeader> configHeaders = new ArrayList<>();
            for (QueryAdpAIGatewayParam.AuthHeader paramHeader : param.getAuthHeaders()) {
                AdpAIGatewayConfig.AuthHeader configHeader = new AdpAIGatewayConfig.AuthHeader();
                configHeader.setKey(paramHeader.getKey());
                configHeader.setValue(paramHeader.getValue());
                configHeaders.add(configHeader);
            }
            config.setAuthHeaders(configHeaders);
        } else {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "不支持的认证类型: " + param.getAuthType());
        }

        AdpAIGatewayClient client = new AdpAIGatewayClient(config);
        try {
            String url = client.getFullUrl("/gatewayInstance/listInstances");
            String requestBody = String.format("{\"current\": %d, \"size\": %d}", page, size);
            HttpEntity<String> requestEntity = client.createRequestEntity(requestBody);

            ResponseEntity<AdpGatewayInstanceResult> response = client.getRestTemplate().exchange(
                    url, HttpMethod.POST, requestEntity, AdpGatewayInstanceResult.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                AdpGatewayInstanceResult result = response.getBody();
                if (result.getCode() == 200 && result.getData() != null) {
                    return convertToGatewayResult(result.getData(), page, size);
                }
                String msg = result.getMessage() != null ? result.getMessage() : result.getMsg();
                throw new BusinessException(ErrorCode.GATEWAY_ERROR, msg);
            }
            throw new BusinessException(ErrorCode.GATEWAY_ERROR, "Failed to call ADP gateway API");
        } catch (Exception e) {
            log.error("Error fetching ADP gateways", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e.getMessage());
        } finally {
            client.close();
        }
    }

    private PageResult<GatewayResult> convertToGatewayResult(AdpGatewayInstanceResult.AdpGatewayInstanceData data, int page, int size) {
        List<GatewayResult> gateways = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if (data.getRecords() != null) {
            for (AdpGatewayInstanceResult.AdpGatewayInstance instance : data.getRecords()) {
                LocalDateTime createTime = null;
                try {
                    if (instance.getCreateTime() != null) {
                        createTime = LocalDateTime.parse(instance.getCreateTime(), formatter);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse create time: {}", instance.getCreateTime(), e);
                }
                GatewayResult gateway = GatewayResult.builder()
                        .gatewayId(instance.getGwInstanceId())
                        .gatewayName(instance.getName())
                        .gatewayType(GatewayType.ADP_AI_GATEWAY)
                        .createAt(createTime)
                        .build();
                gateways.add(gateway);
            }
        }
        return PageResult.of(gateways, page, size, data.getTotal() != null ? data.getTotal() : 0);
    }

    @Data
    public static class AdpMcpServerDetailResult {
        private Integer code;
        private String msg;
        private String message;
        private AdpMcpServerDetail data;

        @Data
        public static class AdpMcpServerDetail {
            private String gwInstanceId;
            private String name;
            private String description;
            private List<String> domains;
            private List<Service> services;
            private ConsumerAuthInfo consumerAuthInfo;
            private String rawConfigurations;
            private String type;
            private String dsn;
            private String dbType;
            private String upstreamPathPrefix;

            @Data
            public static class Service {
                private String name;
                private Integer port;
                private String version;
                private Integer weight;
            }

            @Data
            public static class ConsumerAuthInfo {
                private String type;
                private Boolean enable;
                private List<String> allowedConsumers;
            }
        }
    }
}
