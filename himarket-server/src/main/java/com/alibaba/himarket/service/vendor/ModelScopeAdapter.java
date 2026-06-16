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
import com.alibaba.himarket.support.api.spec.StdioConnection;
import com.alibaba.himarket.support.enums.McpVendorType;
import com.alibaba.himarket.utils.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

/**
 * Vendor adapter for querying MCP servers through the ModelScope REST API.
 */
@Component
@Slf4j
public class ModelScopeAdapter implements McpVendorAdapter {

    private static final String LIST_URL = "https://www.modelscope.cn/openapi/v1/mcp/servers";
    private static final String DETAIL_URL_PREFIX =
            "https://www.modelscope.cn/openapi/v1/mcp/servers/";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;

    public ModelScopeAdapter() {
        this.httpClient =
                new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build();
    }

    @Override
    public McpVendorType getType() {
        return McpVendorType.MODELSCOPE;
    }

    @Override
    public McpConnection buildConnection(RemoteMcpItem item) {
        ObjectNode config = JsonUtil.readObjectNode(item.getConnectionConfig());
        ObjectNode serverConfig = firstMcpServer(config);
        String command = serverConfig.path("command").asText(null);
        if (StrUtil.isBlank(command)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "stdio MCP command is required");
        }

        StdioConnection connection = new StdioConnection();
        connection.setCommand(command);
        if (serverConfig.get("args") != null && serverConfig.get("args").isArray()) {
            List<String> args = new ArrayList<>();
            serverConfig.get("args").forEach(arg -> args.add(arg.asText()));
            connection.setArgs(args);
        }
        if (serverConfig.get("env") != null && serverConfig.get("env").isObject()) {
            Map<String, String> env = new HashMap<>();
            serverConfig
                    .get("env")
                    .fields()
                    .forEachRemaining(entry -> env.put(entry.getKey(), entry.getValue().asText()));
            connection.setEnv(env);
        }
        String cwd = serverConfig.path("cwd").asText(null);
        if (StrUtil.isNotBlank(cwd)) {
            connection.setCwd(cwd);
        }
        return connection;
    }

    @Override
    public RemoteMcpItem getMcpServer(String resourceId) {
        RemoteMcpItem item =
                RemoteMcpItem.builder()
                        .remoteId(resourceId)
                        .mcpName(toMcpName(resourceId))
                        .displayName(resourceId)
                        .protocolType("stdio")
                        .connectionConfig("{}")
                        .build();
        RemoteMcpItem enriched = enrichForImport(item);
        enriched.setConnection(buildConnection(enriched));
        return enriched;
    }

    @Override
    public RemoteMcpItem enrichForImport(RemoteMcpItem item) {
        if (item.getRemoteId() == null || item.getRemoteId().isBlank()) {
            return item;
        }
        try {
            String detailUrl =
                    DETAIL_URL_PREFIX + item.getRemoteId() + "?get_operational_url=False";
            Request request = new Request.Builder().url(detailUrl).get().build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn(
                            "ModelScope detail API failed, remoteId={}, status={}",
                            item.getRemoteId(),
                            response.code());
                    return item;
                }

                String responseBody = response.body().string();
                ObjectNode json = JsonUtil.readObjectNode(responseBody);
                if (!json.path("success").asBoolean()) {
                    return item;
                }

                ObjectNode data =
                        json.get("data") != null && json.get("data").isObject()
                                ? (ObjectNode) json.get("data")
                                : null;
                if (data == null) {
                    return item;
                }

                // connectionConfig from server_config[0]
                JsonNode serverConfigNode = data.get("server_config");
                if (serverConfigNode != null
                        && serverConfigNode.isArray()
                        && serverConfigNode.size() > 0) {
                    item.setConnectionConfig(serverConfigNode.get(0).toString());
                }

                // protocolType: infer from server_config command
                if (serverConfigNode != null
                        && serverConfigNode.isArray()
                        && serverConfigNode.size() > 0) {
                    ObjectNode cfg = (ObjectNode) serverConfigNode.get(0);
                    ObjectNode mcpServers =
                            cfg.get("mcpServers") != null && cfg.get("mcpServers").isObject()
                                    ? (ObjectNode) cfg.get("mcpServers")
                                    : null;
                    if (mcpServers != null && mcpServers.size() > 0) {
                        Iterator<String> keys = mcpServers.fieldNames();
                        String firstKey = keys.hasNext() ? keys.next() : null;
                        ObjectNode serverEntry =
                                firstKey != null
                                                && mcpServers.get(firstKey) != null
                                                && mcpServers.get(firstKey).isObject()
                                        ? (ObjectNode) mcpServers.get(firstKey)
                                        : null;
                        String command =
                                serverEntry != null ? serverEntry.path("command").asText() : null;
                        if ("npx".equals(command)
                                || "uvx".equals(command)
                                || "node".equals(command)
                                || "python".equals(command)) {
                            item.setProtocolType("stdio");
                        }
                    }
                }

                // icon from logo_url (detail may have better quality)
                String logoUrl = data.path("logo_url").asText();
                if (!logoUrl.isBlank()) {
                    ObjectNode iconNode = JsonUtil.createObjectNode();
                    iconNode.put("type", "URL");
                    iconNode.put("value", logoUrl);
                    item.setIcon(iconNode.toString());
                }

                // repoUrl from source_url
                String sourceUrl = data.path("source_url").asText();
                if (!sourceUrl.isBlank()) {
                    item.setRepoUrl(sourceUrl);
                }

                // extraParams from env_schema
                ObjectNode envSchema =
                        data.get("env_schema") != null && data.get("env_schema").isObject()
                                ? (ObjectNode) data.get("env_schema")
                                : null;
                if (envSchema != null) {
                    ObjectNode properties =
                            envSchema.get("properties") != null
                                            && envSchema.get("properties").isObject()
                                    ? (ObjectNode) envSchema.get("properties")
                                    : null;
                    ArrayNode required =
                            envSchema.get("required") != null && envSchema.get("required").isArray()
                                    ? (ArrayNode) envSchema.get("required")
                                    : null;
                    if (properties != null && properties.size() > 0) {
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
                                    prop != null ? prop.path("description").asText("") : "");
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
                            paramDef.put(
                                    "type",
                                    prop != null ? prop.path("type").asText("string") : "string");
                            params.add(paramDef);
                        }
                        item.setExtraParams(params.toString());
                    }
                }

                // Enrich displayName/description from detail locales if better
                String enName = resolveLocaleName(data);
                if (enName != null && !enName.isBlank()) {
                    item.setDisplayName(enName);
                }
                String enDesc = resolveLocaleDescription(data);
                if (enDesc != null && !enDesc.isBlank()) {
                    item.setDescription(enDesc);
                }

                // serviceIntro from readme (prefer Chinese locale)
                String readme = null;
                ObjectNode locales =
                        data.get("locales") != null && data.get("locales").isObject()
                                ? (ObjectNode) data.get("locales")
                                : null;
                if (locales != null) {
                    ObjectNode zh =
                            locales.get("zh") != null && locales.get("zh").isObject()
                                    ? (ObjectNode) locales.get("zh")
                                    : null;
                    if (zh != null) {
                        readme = zh.path("readme").asText();
                    }
                }
                if (readme == null || readme.isBlank()) {
                    readme = data.path("readme").asText();
                }
                if (readme == null || readme.isBlank()) {
                    if (locales != null) {
                        ObjectNode en =
                                locales.get("en") != null && locales.get("en").isObject()
                                        ? (ObjectNode) locales.get("en")
                                        : null;
                        if (en != null) {
                            readme = en.path("readme").asText();
                        }
                    }
                }
                if (readme != null && !readme.isBlank()) {
                    item.setServiceIntro(readme);
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Failed to enrich ModelScope MCP detail, remoteId={}, errorMessage={}",
                    item.getRemoteId(),
                    e.getMessage(),
                    e);
        }
        return item;
    }

    private ObjectNode firstMcpServer(ObjectNode config) {
        if (config == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "MCP connection config is empty");
        }
        JsonNode mcpServers = config.get("mcpServers");
        if (mcpServers != null && mcpServers.isObject()) {
            Iterator<String> keys = mcpServers.fieldNames();
            if (keys.hasNext()) {
                JsonNode server = mcpServers.get(keys.next());
                if (server != null && server.isObject()) {
                    return (ObjectNode) server;
                }
            }
        }
        return config;
    }

    @Override
    public PageResult<RemoteMcpItem> listMcpServers(String keyword, int page, int size) {
        try {
            ObjectNode body = JsonUtil.createObjectNode();
            body.set("filter", JsonUtil.createObjectNode());
            body.put("page_number", page);
            body.put("page_size", size);
            body.put("search", keyword != null ? keyword : "");

            Request request =
                    new Request.Builder()
                            .url(LIST_URL)
                            .put(RequestBody.create(body.toString(), JSON))
                            .header("Content-Type", "application/json")
                            .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn(
                            "ModelScope API returned non-success status, status={}",
                            response.code());
                    return PageResult.empty(page, size);
                }

                String responseBody = response.body().string();
                ObjectNode json = JsonUtil.readObjectNode(responseBody);

                if (!json.path("success").asBoolean()) {
                    log.warn("ModelScope API returned success=false");
                    return PageResult.empty(page, size);
                }

                ObjectNode data =
                        json.get("data") != null && json.get("data").isObject()
                                ? (ObjectNode) json.get("data")
                                : null;
                if (data == null) {
                    return PageResult.empty(page, size);
                }

                long totalCount = data.path("total_count").asLong();
                JsonNode serverListNode = data.get("mcp_server_list");
                if (serverListNode == null
                        || !serverListNode.isArray()
                        || serverListNode.size() == 0) {
                    return PageResult.empty(page, size);
                }
                ArrayNode serverList = (ArrayNode) serverListNode;

                List<RemoteMcpItem> items = new ArrayList<>();
                for (int i = 0; i < serverList.size(); i++) {
                    try {
                        ObjectNode server = (ObjectNode) serverList.get(i);
                        RemoteMcpItem item = convertToRemoteMcpItem(server);
                        if (item != null) {
                            items.add(item);
                        }
                    } catch (Exception e) {
                        log.warn(
                                "Failed to parse ModelScope MCP item, index={}, errorMessage={}",
                                i,
                                e.getMessage(),
                                e);
                    }
                }

                return PageResult.of(items, page, size, totalCount);
            }
        } catch (IOException e) {
            log.warn(
                    "ModelScope API call failed, keyword={}, page={}, size={}",
                    keyword,
                    page,
                    size,
                    e);
            return PageResult.empty(page, size);
        }
    }

    private RemoteMcpItem convertToRemoteMcpItem(ObjectNode server) {
        String id = server.path("id").asText();
        if (id.isBlank()) {
            return null;
        }

        String mcpName = toMcpName(id);
        String displayName = resolveLocaleName(server);
        String description = resolveLocaleDescription(server);

        // icon
        String icon = null;
        String logoUrl = server.path("logo_url").asText();
        if (!logoUrl.isBlank()) {
            ObjectNode iconNode = JsonUtil.createObjectNode();
            iconNode.put("type", "URL");
            iconNode.put("value", logoUrl);
            icon = iconNode.toString();
        }

        // tags from categories
        String tags = null;
        JsonNode categoriesNode = server.get("categories");
        if (categoriesNode != null && categoriesNode.isArray() && categoriesNode.size() > 0) {
            tags = categoriesNode.toString();
        }

        return RemoteMcpItem.builder()
                .remoteId(id)
                .mcpName(mcpName)
                .displayName(displayName != null ? displayName : id)
                .description(description)
                .protocolType("stdio")
                .connectionConfig("{}")
                .tags(tags)
                .icon(icon)
                .repoUrl(null)
                .extraParams(null)
                .build();
    }

    /**
     * Converts a ModelScope id to an mcpName.
     *
     * <p>Rules: remove the leading {@code @} when present, replace {@code /} with {@code -},
     * lowercase the result, and truncate to 63 characters.
     *
     * <p>Examples: {@code @amap/amap-maps} => {@code amap-amap-maps}, {@code
     * Alipay/alipay-subscription} => {@code alipay-alipay-subscription}
     */
    static String toMcpName(String id) {
        String name = id;
        if (name.startsWith("@")) {
            name = name.substring(1);
        }
        name = name.replace("/", "-").toLowerCase();
        if (name.length() > 63) {
            name = name.substring(0, 63);
        }
        return name;
    }

    /**
     * Resolves the best display name from localized fields and the top-level fallback.
     */
    private String resolveLocaleName(ObjectNode server) {
        ObjectNode locales =
                server.get("locales") != null && server.get("locales").isObject()
                        ? (ObjectNode) server.get("locales")
                        : null;
        if (locales != null) {
            ObjectNode zh =
                    locales.get("zh") != null && locales.get("zh").isObject()
                            ? (ObjectNode) locales.get("zh")
                            : null;
            if (zh != null) {
                String zhName = zh.path("name").asText();
                if (!zhName.isBlank()) {
                    return zhName;
                }
            }
        }
        String topName = server.path("name").asText();
        if (!topName.isBlank()) {
            return topName;
        }
        if (locales != null) {
            ObjectNode en =
                    locales.get("en") != null && locales.get("en").isObject()
                            ? (ObjectNode) locales.get("en")
                            : null;
            if (en != null) {
                return en.path("name").asText();
            }
        }
        return null;
    }

    /**
     * Resolves the best description from localized fields and the top-level fallback.
     */
    private String resolveLocaleDescription(ObjectNode server) {
        ObjectNode locales =
                server.get("locales") != null && server.get("locales").isObject()
                        ? (ObjectNode) server.get("locales")
                        : null;
        if (locales != null) {
            ObjectNode zh =
                    locales.get("zh") != null && locales.get("zh").isObject()
                            ? (ObjectNode) locales.get("zh")
                            : null;
            if (zh != null) {
                String zhDesc = zh.path("description").asText();
                if (!zhDesc.isBlank()) {
                    return zhDesc;
                }
            }
        }
        String topDesc = server.path("description").asText();
        if (!topDesc.isBlank()) {
            return topDesc;
        }
        if (locales != null) {
            ObjectNode en =
                    locales.get("en") != null && locales.get("en").isObject()
                            ? (ObjectNode) locales.get("en")
                            : null;
            if (en != null) {
                return en.path("description").asText();
            }
        }
        return null;
    }
}
