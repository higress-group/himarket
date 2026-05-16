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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

/** 官方 MCP Registry 供应商适配器，调用 MCP Registry 公开 API 查询 MCP Server 列表。 */
@Slf4j
@Component
public class McpRegistryAdapter implements McpVendorAdapter {

    private static final String BASE_URL = "https://registry.modelcontextprotocol.io/v0.1/servers";
    private static final String META_KEY = "io.modelcontextprotocol.registry/official";
    private static final String LATEST_VERSION = "latest";

    private final OkHttpClient httpClient;

    /**
     * Caffeine cache: maps page number (1-based) to the nextCursor returned by the previous page.
     * Page 1 has no cursor. For page N>1, we look up the cached nextCursor from page N-1.
     * Entries expire after 5 minutes to avoid stale cursors.
     */
    private final Cache<Integer, String> cursorCache;

    public McpRegistryAdapter() {
        this.httpClient =
                new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build();
        this.cursorCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(200)
                        .build();
    }

    @Override
    public McpVendorType getType() {
        return McpVendorType.MCP_REGISTRY;
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
            case STDIO -> buildPackageConnection(config);
            case SSE, STREAMABLE_HTTP, DUAL_HTTP -> buildRemoteConnection(protocol, config);
        };
    }

    @Override
    public RemoteMcpItem getMcpServer(String resourceId) {
        try {
            ObjectNode server = fetchServer(resourceId);
            if (server == null) {
                throw new BusinessException(
                        ErrorCode.NOT_FOUND, "External MCP resource", resourceId);
            }
            RemoteMcpItem item = convertToRemoteMcpItem(server);
            if (item == null) {
                throw new BusinessException(
                        ErrorCode.NOT_FOUND, "External MCP resource", resourceId);
            }
            return item;
        } catch (IOException e) {
            log.warn("MCP Registry detail API failed for {}", resourceId, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "供应商 API 连接超时");
        }
    }

    @Override
    public PageResult<RemoteMcpItem> listMcpServers(String keyword, int page, int size) {
        try {
            // For page > 1, we need the cursor from the previous page
            if (page > 1) {
                String cursor = cursorCache.getIfPresent(page);
                if (cursor == null) {
                    log.warn("MCP Registry: no cached cursor for page {}, returning empty", page);
                    return PageResult.empty(page, size);
                }
                return fetchPage(keyword, page, size, cursor);
            }
            return fetchPage(keyword, page, size, null);
        } catch (IOException e) {
            log.warn("MCP Registry API call failed", e);
            return PageResult.empty(page, size);
        }
    }

    private PageResult<RemoteMcpItem> fetchPage(String keyword, int page, int size, String cursor)
            throws IOException {
        StringBuilder urlBuilder = new StringBuilder(BASE_URL);
        urlBuilder.append("?limit=").append(size);
        // 只获取最新版本，避免重复
        urlBuilder.append("&version=latest");
        if (cursor != null && !cursor.isBlank()) {
            urlBuilder.append("&cursor=").append(cursor);
        }
        // MCP Registry 支持按 name 子串搜索
        if (keyword != null && !keyword.isBlank()) {
            urlBuilder.append("&search=").append(keyword);
        }

        Request request = new Request.Builder().url(urlBuilder.toString()).get().build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("MCP Registry API returned non-success status: {}", response.code());
                return PageResult.empty(page, size);
            }

            String responseBody = response.body().string();
            ObjectNode json = JsonUtil.readObjectNode(responseBody);

            ArrayNode servers = (ArrayNode) json.get("servers");
            if (servers == null || servers.size() == 0) {
                return PageResult.empty(page, size);
            }

            // 解析分页元数据
            ObjectNode metadata = (ObjectNode) json.get("metadata");

            boolean hasNextPage = false;
            if (metadata != null) {
                String nextCursor = metadata.path("nextCursor").asText(null);
                hasNextPage = nextCursor != null && !nextCursor.isBlank();
                if (hasNextPage) {
                    cursorCache.put(page + 1, nextCursor);
                }
            }
            List<RemoteMcpItem> items = new ArrayList<>();
            for (int i = 0; i < servers.size(); i++) {
                try {
                    ObjectNode entry = (ObjectNode) servers.get(i);
                    // version=latest 已过滤，只需检查 status=active
                    ObjectNode meta = (ObjectNode) entry.get("_meta");
                    if (meta != null) {
                        ObjectNode official = (ObjectNode) meta.get(META_KEY);
                        if (official != null
                                && !"active".equals(official.path("status").asText(null))) {
                            continue;
                        }
                    }
                    ObjectNode server = (ObjectNode) entry.get("server");
                    if (server == null) {
                        continue;
                    }
                    RemoteMcpItem item = convertToRemoteMcpItem(server);
                    if (item != null) {
                        items.add(item);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse MCP Registry item at index {}", i, e);
                }
            }

            // 如果有下一页，totalCount 设为 (当前页号 * 每页大小) + 1，确保前端显示下一页按钮
            // 如果没有下一页，totalCount 就是已加载的总数
            long totalCount;
            if (hasNextPage) {
                totalCount = (long) page * size + size + 1;
            } else {
                totalCount = (long) (page - 1) * size + items.size();
            }

            return PageResult.of(items, page, size, totalCount);
        }
    }

    private ObjectNode fetchServer(String resourceId) throws IOException {
        HttpUrl detailUrl = buildDetailUrl(resourceId);
        Request request = new Request.Builder().url(detailUrl).get().build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("MCP Registry detail API failed for {}: {}", resourceId, response.code());
                return null;
            }

            ObjectNode json = JsonUtil.readObjectNode(response.body().string());
            ObjectNode meta =
                    json.get("_meta") != null && json.get("_meta").isObject()
                            ? (ObjectNode) json.get("_meta")
                            : null;
            if (meta != null) {
                ObjectNode official =
                        meta.get(META_KEY) != null && meta.get(META_KEY).isObject()
                                ? (ObjectNode) meta.get(META_KEY)
                                : null;
                if (official != null && !"active".equals(official.path("status").asText(null))) {
                    return null;
                }
            }
            return json.get("server") != null && json.get("server").isObject()
                    ? (ObjectNode) json.get("server")
                    : json;
        }
    }

    static HttpUrl buildDetailUrl(String resourceId) {
        return Objects.requireNonNull(HttpUrl.parse(BASE_URL))
                .newBuilder()
                .addPathSegment(resourceId)
                .addPathSegment("versions")
                .addPathSegment(LATEST_VERSION)
                .build();
    }

    private RemoteMcpItem convertToRemoteMcpItem(ObjectNode server) {
        String name = server.path("name").asText(null);
        if (name == null || name.isBlank()) {
            return null;
        }

        String mcpName = toMcpName(name);
        String title = server.path("title").asText(null);
        String displayName = (title != null && !title.isBlank()) ? title : name;
        String description = server.path("description").asText(null);

        // Determine protocolType and connectionConfig from remotes or packages
        String protocolType = "stdio";
        String connectionConfig = "{}";

        ArrayNode remotes = (ArrayNode) server.get("remotes");
        ArrayNode packages = (ArrayNode) server.get("packages");

        if (remotes != null && remotes.size() > 0) {
            ObjectNode remote = (ObjectNode) remotes.get(0);
            protocolType = remote.path("type").asText("stdio");
            // Build connectionConfig JSON including url, type, and headers if present
            ObjectNode connObj = JsonUtil.createObjectNode();
            connObj.put("url", remote.path("url").asText(null));
            connObj.put("type", remote.path("type").asText(null));
            if (remote.has("headers")) {
                connObj.set("headers", remote.get("headers"));
            }
            connectionConfig = connObj.toString();
        } else if (packages != null && packages.size() > 0) {
            ObjectNode pkg = (ObjectNode) packages.get(0);
            protocolType = "stdio";
            ObjectNode connObj = JsonUtil.createObjectNode();
            connObj.put("registryType", pkg.path("registryType").asText(null));
            connObj.put("identifier", pkg.path("identifier").asText(null));
            ObjectNode transport = (ObjectNode) pkg.get("transport");
            if (transport != null) {
                connObj.set("transport", transport);
            }
            connectionConfig = connObj.toString();
        }

        // Icon from icons[0].src
        String icon = null;
        ArrayNode icons = (ArrayNode) server.get("icons");
        if (icons != null && icons.size() > 0) {
            ObjectNode iconObj = (ObjectNode) icons.get(0);
            String iconSrc = iconObj.path("src").asText(null);
            if (iconSrc != null && !iconSrc.isBlank()) {
                ObjectNode iconNode = JsonUtil.createObjectNode();
                iconNode.put("type", "URL");
                iconNode.put("value", iconSrc);
                icon = iconNode.toString();
            }
        }

        // repoUrl: prefer repository.url, fallback to websiteUrl
        String repoUrl = null;
        ObjectNode repository = (ObjectNode) server.get("repository");
        if (repository != null) {
            repoUrl = repository.path("url").asText(null);
        }
        if (repoUrl == null || repoUrl.isBlank()) {
            repoUrl = server.path("websiteUrl").asText(null);
        }

        // extraParams: from remotes[].headers or packages[].environmentVariables
        String extraParams = null;
        ArrayNode params = JsonUtil.createArray();
        if (remotes != null) {
            for (int i = 0; i < remotes.size(); i++) {
                ArrayNode headers = (ArrayNode) remotes.get(i).get("headers");
                if (headers != null) {
                    for (int j = 0; j < headers.size(); j++) {
                        ObjectNode h = (ObjectNode) headers.get(j);
                        ObjectNode paramObj = JsonUtil.createObjectNode();
                        paramObj.put("name", h.path("name").asText(null));
                        paramObj.put("description", h.path("description").asText(""));
                        paramObj.put("required", h.path("isRequired").asBoolean(false));
                        paramObj.put("secret", h.path("isSecret").asBoolean(false));
                        params.add(paramObj);
                    }
                }
            }
        }
        if (packages != null) {
            for (int i = 0; i < packages.size(); i++) {
                ArrayNode envVars = (ArrayNode) packages.get(i).get("environmentVariables");
                if (envVars != null) {
                    for (int j = 0; j < envVars.size(); j++) {
                        ObjectNode ev = (ObjectNode) envVars.get(j);
                        ObjectNode paramObj = JsonUtil.createObjectNode();
                        paramObj.put("name", ev.path("name").asText(null));
                        paramObj.put("description", ev.path("description").asText(""));
                        paramObj.put("required", ev.path("isRequired").asBoolean(false));
                        paramObj.put("secret", ev.path("isSecret").asBoolean(false));
                        params.add(paramObj);
                    }
                }
            }
        }
        if (params.size() > 0) {
            extraParams = params.toString();
        }

        return RemoteMcpItem.builder()
                .remoteId(name)
                .mcpName(mcpName)
                .displayName(displayName)
                .description(description)
                .protocolType(protocolType)
                .connectionConfig(connectionConfig)
                .connection(buildConnection(protocolType, connectionConfig))
                .tags(null)
                .icon(icon)
                .repoUrl(repoUrl)
                .extraParams(extraParams)
                .build();
    }

    private McpConnection buildConnection(String protocolType, String connectionConfig) {
        RemoteMcpItem item = new RemoteMcpItem();
        item.setProtocolType(protocolType);
        item.setConnectionConfig(connectionConfig);
        return buildConnection(item);
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

    private StdioConnection buildPackageConnection(ObjectNode config) {
        if (config == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "MCP connection config is empty");
        }
        String identifier = config.path("identifier").asText(null);
        String registryType = config.path("registryType").asText(null);
        if (StrUtil.isBlank(identifier)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "MCP package identifier is required");
        }

        StdioConnection connection = new StdioConnection();
        String normalizedRegistryType = StrUtil.blankToDefault(registryType, "").toLowerCase();
        if (normalizedRegistryType.contains("npm")) {
            connection.setCommand("npx");
            connection.setArgs(List.of("-y", identifier));
            return connection;
        }
        if (normalizedRegistryType.contains("pypi") || normalizedRegistryType.contains("python")) {
            connection.setCommand("uvx");
            connection.setArgs(List.of(identifier));
            return connection;
        }
        throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "Unsupported MCP package registry type: " + registryType);
    }

    /**
     * Convert MCP Registry server name to mcpName.
     *
     * <p>Rules: replace {@code /} with {@code -}, replace {@code .} with {@code -}, lowercase,
     * truncate to 63 characters.
     *
     * <p>Example: {@code agency.lona/trading} → {@code agency-lona-trading}
     */
    static String toMcpName(String name) {
        String result = name.replace("/", "-").replace(".", "-").toLowerCase();
        if (result.length() > 63) {
            result = result.substring(0, 63);
        }
        return result;
    }
}
