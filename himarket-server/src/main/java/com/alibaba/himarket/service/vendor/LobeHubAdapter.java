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

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.vendor.RemoteMcpItem;
import com.alibaba.himarket.support.enums.McpVendorType;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
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
 * LobeHub MCP Market 供应商适配器。
 *
 * <p>使用 JWT client assertion 方式的 OAuth2 认证（非普通 client_credentials）。 认证流程：注册客户端 →
 * 签发 JWT → 换取 access_token → 调用 API。
 */
@Slf4j
@Component
public class LobeHubAdapter implements McpVendorAdapter {

    private static final String BASE_URL = "https://market.lobehub.com";
    private static final String REGISTER_URL = BASE_URL + "/api/v1/clients/register";
    private static final String TOKEN_URL = BASE_URL + "/oauth/token";
    private static final String LIST_URL = BASE_URL + "/api/v1/plugins";
    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

    /** Fixed deviceId generated once at class load. */
    private static final String DEVICE_ID = "himarket-" + UUID.randomUUID();

    private final OkHttpClient httpClient;

    /** Cache for client credentials (client_id + client_secret). Long-term, 24h. */
    private final Cache<String, String[]> clientCredentialsCache;

    /** Cache for access_token. Expiry is set dynamically per entry (expires_in - 60s). */
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
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "LobeHub OAuth2 认证失败");
            } catch (IOException ex) {
                log.warn("LobeHub API call failed after token refresh", ex);
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "供应商 API 连接超时");
            }
        } catch (IOException e) {
            log.warn("LobeHub API call failed", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "供应商 API 连接超时");
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
                log.warn("LobeHub API returned non-success status: {}", response.code());
                return PageResult.empty(page, size);
            }

            String responseBody = response.body().string();
            JSONObject json = JSONUtil.parseObj(responseBody);

            long totalCount = json.getLong("totalCount", 0L);
            JSONArray items = json.getJSONArray("items");
            if (items == null || items.isEmpty()) {
                return PageResult.empty(page, size);
            }

            List<RemoteMcpItem> result = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                try {
                    JSONObject item = items.getJSONObject(i);
                    RemoteMcpItem mcpItem = convertToRemoteMcpItem(item);
                    if (mcpItem != null) {
                        result.add(mcpItem);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse LobeHub MCP item at index {}", i, e);
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
                            "LobeHub detail API failed for {}: {}",
                            item.getRemoteId(),
                            response.code());
                    return item;
                }

                String responseBody = response.body().string();
                JSONObject detail = JSONUtil.parseObj(responseBody);

                // Extract deploymentOptions[0].connection for connectionConfig
                JSONArray deploymentOptions = detail.getJSONArray("deploymentOptions");
                if (deploymentOptions != null && !deploymentOptions.isEmpty()) {
                    JSONObject firstOption = deploymentOptions.getJSONObject(0);
                    JSONObject connection = firstOption.getJSONObject("connection");
                    if (connection != null) {
                        item.setConnectionConfig(connection.toString());
                        String connType = connection.getStr("type");
                        if (connType != null && !connType.isBlank()) {
                            item.setProtocolType(connType);
                        }

                        // Extract configSchema → extraParams
                        JSONObject configSchema = connection.getJSONObject("configSchema");
                        if (configSchema != null) {
                            JSONObject properties = configSchema.getJSONObject("properties");
                            JSONArray required = configSchema.getJSONArray("required");
                            if (properties != null && !properties.isEmpty()) {
                                JSONArray params = JSONUtil.createArray();
                                for (String key : properties.keySet()) {
                                    JSONObject prop = properties.getJSONObject(key);
                                    JSONObject paramDef = JSONUtil.createObj();
                                    paramDef.set("name", key);
                                    paramDef.set(
                                            "description",
                                            prop != null ? prop.getStr("description", "") : "");
                                    paramDef.set(
                                            "required", required != null && required.contains(key));
                                    params.add(paramDef);
                                }
                                item.setExtraParams(params.toString());
                            }
                        }
                    }
                }

                // serviceIntro from overview.readme
                JSONObject overview = detail.getJSONObject("overview");
                if (overview != null) {
                    String readme = overview.getStr("readme");
                    if (readme != null && !readme.isBlank()) {
                        item.setServiceIntro(readme);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to enrich LobeHub MCP detail for {}", item.getRemoteId(), e);
        }
        return item;
    }

    // ==================== Data Conversion ====================

    private RemoteMcpItem convertToRemoteMcpItem(JSONObject item) {
        String identifier = item.getStr("identifier");
        if (identifier == null || identifier.isBlank()) {
            return null;
        }

        String mcpName = toMcpName(identifier);
        String displayName = item.getStr("name");
        String description = item.getStr("description");

        // protocolType: connectionType mapping
        String connectionType = item.getStr("connectionType");
        String protocolType = mapProtocolType(connectionType);

        // connectionConfig: placeholder (actual config from detail API during import)
        String connectionConfig = "{}";

        // icon
        String icon = null;
        String iconUrl = item.getStr("icon");
        if (iconUrl != null && !iconUrl.isBlank()) {
            icon = JSONUtil.createObj().set("type", "URL").set("value", iconUrl).toString();
        }

        // repoUrl: prefer github.url, fallback to homepage
        String repoUrl = null;
        JSONObject github = item.getJSONObject("github");
        if (github != null) {
            repoUrl = github.getStr("url");
        }
        if (repoUrl == null || repoUrl.isBlank()) {
            repoUrl = item.getStr("homepage");
        }

        // tags from category
        String tags = null;
        String category = item.getStr("category");
        if (category != null && !category.isBlank()) {
            tags = JSONUtil.createArray().set(category).toString();
        }

        return RemoteMcpItem.builder()
                .remoteId(identifier)
                .mcpName(mcpName)
                .displayName(displayName != null ? displayName : identifier)
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
     * <p>"local" → "stdio", "hybrid" → "stdio", "cloud" → "sse"
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

    // ==================== Authentication ====================

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

    /** Obtain a fresh access_token via JWT client assertion. */
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
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "LobeHub OAuth2 认证失败");
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "LobeHub OAuth2 认证失败");
        }
    }

    /** Get client credentials from cache, or register a new client. */
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
        JSONObject body =
                JSONUtil.createObj()
                        .set("clientName", "himarket")
                        .set("clientType", "cli")
                        .set("deviceId", DEVICE_ID)
                        .set("source", "himarket");

        Request request =
                new Request.Builder()
                        .url(REGISTER_URL)
                        .post(RequestBody.create(body.toString(), JSON_MEDIA))
                        .header("Content-Type", "application/json")
                        .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.error("LobeHub client registration failed with status: {}", response.code());
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "LobeHub OAuth2 认证失败");
            }

            String responseBody = response.body().string();
            JSONObject json = JSONUtil.parseObj(responseBody);
            String clientId = json.getStr("client_id");
            String clientSecret = json.getStr("client_secret");

            if (clientId == null || clientSecret == null) {
                log.error("LobeHub client registration returned incomplete credentials");
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "LobeHub OAuth2 认证失败");
            }

            String[] credentials = new String[] {clientId, clientSecret};
            clientCredentialsCache.put(CACHE_KEY, credentials);
            log.info("LobeHub client registered successfully, clientId={}", clientId);
            return credentials;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "LobeHub OAuth2 认证失败");
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
        JSONObject payload =
                JSONUtil.createObj()
                        .set("iss", clientId)
                        .set("sub", clientId)
                        .set("aud", TOKEN_URL)
                        .set("jti", UUID.randomUUID().toString())
                        .set("iat", now)
                        .set("exp", now + 300);

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
                log.error("LobeHub token exchange failed with status: {}", response.code());
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "LobeHub OAuth2 认证失败");
            }

            String responseBody = response.body().string();
            JSONObject json = JSONUtil.parseObj(responseBody);
            String accessToken = json.getStr("access_token");
            long expiresIn = json.getLong("expires_in", 3600L);

            if (accessToken == null || accessToken.isBlank()) {
                log.error("LobeHub token exchange returned empty access_token");
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "LobeHub OAuth2 认证失败");
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

    // ==================== Crypto Utilities ====================

    /** Base64URL encode (no padding). */
    static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /** HMAC-SHA256 sign. */
    private byte[] hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec =
                    new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "LobeHub OAuth2 认证失败");
        }
    }

    // ==================== Internal Exception ====================

    /** Marker exception for 401 responses that should trigger a retry. */
    private static class AuthRetryException extends Exception {}
}
