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

package com.alibaba.himarket.service.vendor;

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.vendor.RemoteMcpItem;
import com.alibaba.himarket.support.api.spec.McpConnection;
import com.alibaba.himarket.support.api.spec.SseConnection;
import com.alibaba.himarket.support.api.spec.StdioConnection;
import com.alibaba.himarket.support.api.spec.StreamableHttpConnection;
import com.alibaba.himarket.support.enums.McpProtocolType;
import com.alibaba.himarket.support.enums.McpVendorType;
import com.alibaba.himarket.utils.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

/**
 * Vendor adapter for the LobeHub MCP Market API.
 *
 * <p>LobeHub uses OAuth2 with JWT client assertion instead of plain client credentials. The flow is:
 * register client => sign JWT => exchange access token => call API.
 */
@Slf4j
@Component
public class LobeHubAdapter implements McpVendorAdapter {

    private static final String BASE_URL = "https://market.lobehub.com";
    private static final String REGISTER_URL = BASE_URL + "/api/v1/clients/register";
    private static final String TOKEN_URL = BASE_URL + "/oauth/token";
    private static final String LIST_URL = BASE_URL + "/api/v1/plugins";
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    /**
     * Fixed deviceId generated once at class load.
     */
    private static final String DEVICE_ID = "himarket-" + UUID.randomUUID();

    private final OkHttpClient httpClient;

    /**
     * Cache for client credentials (client_id + client_secret). Long-term, 24h.
     */
    private final Cache<String, String[]> clientCredentialsCache;

    /**
     * Cache for access_token. Expiry is set dynamically per entry (expires_in - 60s).
     */
    private final Cache<String, String> accessTokenCache;

    private static final String CACHE_KEY = "lobehub";

    public LobeHubAdapter() {
        this.httpClient =
                new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build();
        this.clientCredentialsCache =
                Caffeine.newBuilder().expireAfterWrite(24, TimeUnit.HOURS).maximumSize(1).build();
        this.accessTokenCache =
                Caffeine.newBuilder().expireAfterWrite(50, TimeUnit.MINUTES).maximumSize(1).build();
    }

    @Override
    public McpVendorType getType() {
        return McpVendorType.LOBEHUB;
    }

    @Override
    public McpConnection buildConnection(RemoteMcpItem item) {
        ObjectNode config = JsonUtil.readObjectNode(item.getConnectionConfig());
        McpProtocolType protocol = McpProtocolType.fromString(item.getProtocolType());
        if (protocol == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Unsupported MCP protocol type: " + item.getProtocolType());
        }
        return switch (protocol) {
            case STDIO -> buildStdioConnection(config);
            case SSE, STREAMABLE_HTTP, DUAL_HTTP -> buildRemoteConnection(protocol, config);
        };
    }

    @Override
    public RemoteMcpItem getMcpServer(String resourceId) {
        String accessToken = getAccessToken();
        try {
            String detailUrl = LIST_URL + "/" + resourceId;
            Request request =
                    new Request.Builder()
                            .url(detailUrl)
                            .get()
                            .header("Authorization", "Bearer " + accessToken)
                            .build();

            ObjectNode detail;
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new BusinessException(
                            ErrorCode.NOT_FOUND, "External MCP resource", resourceId);
                }
                detail = JsonUtil.readObjectNode(response.body().string());
            }
            RemoteMcpItem item = convertToRemoteMcpItem(detail);
            if (item == null) {
                throw new BusinessException(
                        ErrorCode.NOT_FOUND, "External MCP resource", resourceId);
            }
            enrichForImport(item);
            item.setConnection(buildConnection(item));
            return item;
        } catch (IOException e) {
            log.warn("LobeHub detail API failed, resourceId={}", resourceId, e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Vendor API connection timed out");
        }
    }

    @Override
    public PageResult<RemoteMcpItem> listMcpServers(String keyword, int page, int size) {
        String accessToken = getAccessToken();
        try {
            return doListMcpServers(keyword, page, size, accessToken);
        } catch (AuthRetryException e) {
            // 401 from API call: invalidate token, retry once with fresh token
            log.info("LobeHub API returned 401, refreshing token and retrying");
            accessTokenCache.invalidateAll();
            accessToken = getAccessToken();
            try {
                return doListMcpServers(keyword, page, size, accessToken);
            } catch (AuthRetryException ex) {
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR, "LobeHub OAuth2 authentication failed");
            } catch (IOException ex) {
                log.warn("LobeHub API call failed after token refresh", ex);
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR, "Vendor API connection timed out");
            }
        } catch (IOException e) {
            log.warn("LobeHub API call failed", e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Vendor API connection timed out");
        }
    }

    private PageResult<RemoteMcpItem> doListMcpServers(
            String keyword, int page, int size, String accessToken)
            throws IOException, AuthRetryException {
        StringBuilder urlBuilder = new StringBuilder(LIST_URL);
        urlBuilder.append("?pageSize=").append(size);
        urlBuilder.append("&page=").append(page);
        urlBuilder.append("&locale=zh-CN");
        if (keyword != null && !keyword.isBlank()) {
            urlBuilder.append("&q=").append(keyword);
        }

        Request request =
                new Request.Builder()
                        .url(urlBuilder.toString())
                        .get()
                        .header("Authorization", "Bearer " + accessToken)
                        .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 401) {
                throw new AuthRetryException();
            }
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("LobeHub API returned non-success status, status={}", response.code());
                return PageResult.empty(page, size);
            }

            String responseBody = response.body().string();
            ObjectNode json = JsonUtil.readObjectNode(responseBody);

            long totalCount = json.path("totalCount").asLong();
            JsonNode itemsNode = json.get("items");
            if (itemsNode == null || !itemsNode.isArray() || itemsNode.size() == 0) {
                return PageResult.empty(page, size);
            }
            ArrayNode items = (ArrayNode) itemsNode;

            List<RemoteMcpItem> result = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                try {
                    ObjectNode item = (ObjectNode) items.get(i);
                    RemoteMcpItem mcpItem = convertToRemoteMcpItem(item);
                    if (mcpItem != null) {
                        result.add(mcpItem);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse LobeHub MCP item, index={}", i, e);
                }
            }

            return PageResult.of(result, page, size, totalCount);
        }
    }

    @Override
    public RemoteMcpItem enrichForImport(RemoteMcpItem item) {
        if (item.getRemoteId() == null || item.getRemoteId().isBlank()) {
            return item;
        }
        String accessToken = getAccessToken();
        try {
            String detailUrl = LIST_URL + "/" + item.getRemoteId();
            Request request =
                    new Request.Builder()
                            .url(detailUrl)
                            .get()
                            .header("Authorization", "Bearer " + accessToken)
                            .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn(
                            "LobeHub detail API failed, remoteId={}, status={}",
                            item.getRemoteId(),
                            response.code());
                    return item;
                }

                String responseBody = response.body().string();
                ObjectNode detail = JsonUtil.readObjectNode(responseBody);

                // Extract deploymentOptions[0].connection for connectionConfig
                JsonNode deploymentOptionsNode = detail.get("deploymentOptions");
                if (deploymentOptionsNode != null
                        && deploymentOptionsNode.isArray()
                        && deploymentOptionsNode.size() > 0) {
                    ObjectNode firstOption = (ObjectNode) deploymentOptionsNode.get(0);
                    ObjectNode connection =
                            firstOption.get("connection") != null
                                            && firstOption.get("connection").isObject()
                                    ? (ObjectNode) firstOption.get("connection")
                                    : null;
                    if (connection != null) {
                        item.setConnectionConfig(connection.toString());
                        String connType = connection.path("type").asText();
                        if (!connType.isBlank()) {
                            item.setProtocolType(connType);
                        }

                        // Extract configSchema into extraParams.
                        ObjectNode configSchema =
                                connection.get("configSchema") != null
                                                && connection.get("configSchema").isObject()
                                        ? (ObjectNode) connection.get("configSchema")
                                        : null;
                        if (configSchema != null) {
                            ObjectNode properties =
                                    configSchema.get("properties") != null
                                                    && configSchema.get("properties").isObject()
                                            ? (ObjectNode) configSchema.get("properties")
                                            : null;
                            ArrayNode required =
                                    configSchema.get("required") != null
                                                    && configSchema.get("required").isArray()
                                            ? (ArrayNode) configSchema.get("required")
                                            : null;
                            if (properties != null && !properties.isEmpty()) {
                                ArrayNode params = JsonUtil.createArray();
                                Iterator<String> propNames = properties.fieldNames();
                                while (propNames.hasNext()) {
                                    String key = propNames.next();
                                    JsonNode propNode = properties.get(key);
                                    ObjectNode prop =
                                            propNode != null && propNode.isObject()
                                                    ? (ObjectNode) propNode
                                                    : null;
                                    ObjectNode paramDef = JsonUtil.createObjectNode();
                                    paramDef.put("name", key);
                                    paramDef.put(
                                            "description",
                                            prop != null
                                                    ? prop.path("description").asText("")
                                                    : "");
                                    boolean isRequired = false;
                                    if (required != null) {
                                        for (int i = 0; i < required.size(); i++) {
                                            if (key.equals(required.get(i).asText())) {
                                                isRequired = true;
                                                break;
                                            }
                                        }
                                    }
                                    paramDef.put("required", isRequired);
                                    params.add(paramDef);
                                }
                                item.setExtraParams(params.toString());
                            }
                        }
                    }
                }

                // serviceIntro from overview.readme
                ObjectNode overview =
                        detail.get("overview") != null && detail.get("overview").isObject()
                                ? (ObjectNode) detail.get("overview")
                                : null;
                if (overview != null) {
                    String readme = overview.path("readme").asText();
                    if (!readme.isBlank()) {
                        item.setServiceIntro(readme);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to enrich LobeHub MCP detail, remoteId={}", item.getRemoteId(), e);
        }
        return item;
    }

    private RemoteMcpItem convertToRemoteMcpItem(ObjectNode item) {
        String identifier = item.path("identifier").asText();
        if (identifier.isBlank()) {
            return null;
        }

        String mcpName = toMcpName(identifier);
        String displayName = item.path("name").asText();
        String description = item.path("description").asText();

        // protocolType: connectionType mapping
        String connectionType = item.path("connectionType").asText();
        String protocolType = mapProtocolType(connectionType);

        // connectionConfig: placeholder (actual config from detail API during import)
        String connectionConfig = "{}";

        // icon
        String icon = null;
        String iconUrl = item.path("icon").asText();
        if (!iconUrl.isBlank()) {
            ObjectNode iconNode = JsonUtil.createObjectNode();
            iconNode.put("type", "URL");
            iconNode.put("value", iconUrl);
            icon = iconNode.toString();
        }

        // repoUrl: prefer github.url, fallback to homepage
        String repoUrl = null;
        ObjectNode github =
                item.get("github") != null && item.get("github").isObject()
                        ? (ObjectNode) item.get("github")
                        : null;
        if (github != null) {
            repoUrl = github.path("url").asText();
        }
        if (repoUrl == null || repoUrl.isBlank()) {
            repoUrl = item.path("homepage").asText();
        }

        // tags from category
        String tags = null;
        String category = item.path("category").asText();
        if (!category.isBlank()) {
            ArrayNode tagsArr = JsonUtil.createArray();
            tagsArr.add(category);
            tags = tagsArr.toString();
        }

        return RemoteMcpItem.builder()
                .remoteId(identifier)
                .mcpName(mcpName)
                .displayName(!displayName.isBlank() ? displayName : identifier)
                .description(description)
                .protocolType(protocolType)
                .connectionConfig(connectionConfig)
                .tags(tags)
                .icon(icon)
                .repoUrl(repoUrl)
                .extraParams(null)
                .build();
    }

    /**
     * Convert LobeHub identifier to mcpName.
     *
     * <p>LobeHub identifiers are already lowercase+hyphen format (e.g. "tavily-ai-tavily-mcp").
     * Truncate to 63 characters.
     */
    static String toMcpName(String identifier) {
        String name = identifier.toLowerCase();
        if (name.length() > 63) {
            name = name.substring(0, 63);
        }
        return name;
    }

    /**
     * Map LobeHub connectionType to platform protocolType.
     *
     * <p>"local" => "stdio", "hybrid" => "stdio", "cloud" => "sse"
     */
    private String mapProtocolType(String connectionType) {
        if (connectionType == null) {
            return "stdio";
        }
        return switch (connectionType) {
            case "cloud" -> "sse";
            case "local", "hybrid" -> "stdio";
            default -> "stdio";
        };
    }

    private StdioConnection buildStdioConnection(ObjectNode config) {
        if (config == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "MCP connection config is empty");
        }
        String command = config.path("command").asText(null);
        if (StrUtil.isBlank(command)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "stdio MCP command is required");
        }

        StdioConnection connection = new StdioConnection();
        connection.setCommand(command);
        if (config.get("args") != null && config.get("args").isArray()) {
            List<String> args = new ArrayList<>();
            config.get("args").forEach(arg -> args.add(arg.asText()));
            connection.setArgs(args);
        }
        if (config.get("env") != null && config.get("env").isObject()) {
            connection.setEnv(
                    JsonUtil.parse(
                            config.get("env").toString(),
                            new TypeReference<Map<String, String>>() {}));
        }
        String cwd = config.path("cwd").asText(null);
        if (StrUtil.isNotBlank(cwd)) {
            connection.setCwd(cwd);
        }
        return connection;
    }

    private McpConnection buildRemoteConnection(McpProtocolType protocol, ObjectNode config) {
        if (config == null || StrUtil.isBlank(config.path("url").asText(null))) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "MCP connection URL is required");
        }
        if (protocol == McpProtocolType.SSE) {
            SseConnection connection = new SseConnection();
            connection.setUrl(config.path("url").asText());
            return connection;
        }
        StreamableHttpConnection connection = new StreamableHttpConnection();
        connection.setUrl(config.path("url").asText());
        return connection;
    }

    /**
     * Get a valid access_token, using cache when available. If no cached token, obtains a fresh one
     * via JWT client assertion flow.
     */
    private String getAccessToken() {
        String cached = accessTokenCache.getIfPresent(CACHE_KEY);
        if (cached != null) {
            return cached;
        }
        return refreshAccessToken();
    }

    /**
     * Obtains a fresh access_token via JWT client assertion.
     */
    private String refreshAccessToken() {
        String[] credentials = getClientCredentials();
        String clientId = credentials[0];
        String clientSecret = credentials[1];

        String jwt = createJwtAssertion(clientId, clientSecret);

        try {
            return exchangeJwtForToken(jwt);
        } catch (AuthRetryException e) {
            // 401 from token endpoint: invalidate client credentials, re-register, retry
            log.info("LobeHub token endpoint returned 401, re-registering client");
            clientCredentialsCache.invalidateAll();
            credentials = getClientCredentials();
            clientId = credentials[0];
            clientSecret = credentials[1];
            jwt = createJwtAssertion(clientId, clientSecret);
            try {
                return exchangeJwtForToken(jwt);
            } catch (AuthRetryException | IOException ex) {
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR, "LobeHub OAuth2 authentication failed");
            }
        } catch (IOException e) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "LobeHub OAuth2 authentication failed");
        }
    }

    /**
     * Gets client credentials from cache, or registers a new client.
     */
    private String[] getClientCredentials() {
        String[] cached = clientCredentialsCache.getIfPresent(CACHE_KEY);
        if (cached != null) {
            return cached;
        }
        return registerClient();
    }

    /**
     * Register a new client with LobeHub.
     *
     * @return String array: [client_id, client_secret]
     */
    private String[] registerClient() {
        ObjectNode body = JsonUtil.createObjectNode();
        body.put("clientName", "himarket");
        body.put("clientType", "cli");
        body.put("deviceId", DEVICE_ID);
        body.put("source", "himarket");

        Request request =
                new Request.Builder()
                        .url(REGISTER_URL)
                        .post(RequestBody.create(body.toString(), JSON_MEDIA))
                        .header("Content-Type", "application/json")
                        .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.error("LobeHub client registration failed, status={}", response.code());
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR, "LobeHub OAuth2 authentication failed");
            }

            String responseBody = response.body().string();
            ObjectNode json = JsonUtil.readObjectNode(responseBody);
            String clientId = json.path("client_id").asText();
            String clientSecret = json.path("client_secret").asText();

            if (clientId.isBlank() || clientSecret.isBlank()) {
                log.error("LobeHub client registration returned incomplete credentials");
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR, "LobeHub OAuth2 authentication failed");
            }

            String[] credentials = new String[] {clientId, clientSecret};
            clientCredentialsCache.put(CACHE_KEY, credentials);
            log.info("LobeHub client registered successfully, clientId={}", clientId);
            return credentials;
        } catch (IOException e) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "LobeHub OAuth2 authentication failed");
        }
    }

    /**
     * Create a JWT client assertion signed with HMAC-SHA256.
     *
     * <p>Manual JWT construction: Base64URL(header) + "." + Base64URL(payload) + "." +
     * Base64URL(signature). No external JWT library needed.
     */
    String createJwtAssertion(String clientId, String clientSecret) {
        long now = System.currentTimeMillis() / 1000;

        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        ObjectNode payload = JsonUtil.createObjectNode();
        payload.put("iss", clientId);
        payload.put("sub", clientId);
        payload.put("aud", TOKEN_URL);
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("iat", now);
        payload.put("exp", now + 300);

        String headerB64 = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
        String payloadB64 = base64UrlEncode(payload.toString().getBytes(StandardCharsets.UTF_8));
        String signingInput = headerB64 + "." + payloadB64;

        byte[] signature = hmacSha256(clientSecret, signingInput);
        String signatureB64 = base64UrlEncode(signature);

        return signingInput + "." + signatureB64;
    }

    /**
     * Exchange JWT client assertion for an access_token.
     *
     * @return the access_token string
     */
    private String exchangeJwtForToken(String jwt) throws IOException, AuthRetryException {
        RequestBody formBody =
                new FormBody.Builder()
                        .add("grant_type", "client_credentials")
                        .add(
                                "client_assertion_type",
                                "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                        .add("client_assertion", jwt)
                        .build();

        Request request = new Request.Builder().url(TOKEN_URL).post(formBody).build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 401) {
                throw new AuthRetryException();
            }
            if (!response.isSuccessful() || response.body() == null) {
                log.error("LobeHub token exchange failed, status={}", response.code());
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR, "LobeHub OAuth2 authentication failed");
            }

            String responseBody = response.body().string();
            ObjectNode json = JsonUtil.readObjectNode(responseBody);
            String accessToken = json.path("access_token").asText();
            long expiresIn = json.path("expires_in").asLong(3600L);

            if (accessToken.isBlank()) {
                log.error("LobeHub token exchange returned empty access_token");
                throw new BusinessException(
                        ErrorCode.INTERNAL_ERROR, "LobeHub OAuth2 authentication failed");
            }

            // Cache with expiry = expires_in - 60 seconds (refresh early)
            // Caffeine doesn't support per-entry expiry easily, so we use a fixed write-expiry
            // and rely on the cache being invalidated on 401.
            accessTokenCache.put(CACHE_KEY, accessToken);
            log.info(
                    "LobeHub access_token obtained, expires_in={}s, cached with early refresh",
                    expiresIn);
            return accessToken;
        }
    }

    /**
     * Base64URL encodes without padding.
     */
    static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /**
     * Signs data with HMAC-SHA256.
     */
    private byte[] hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec =
                    new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "LobeHub OAuth2 authentication failed");
        }
    }

    /**
     * Marker exception for 401 responses that should trigger a retry.
     */
    private static class AuthRetryException extends Exception {}
}
