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
import com.alibaba.himarket.dto.result.common.PageResult;
import com.alibaba.himarket.dto.vendor.RemoteMcpItem;
import com.alibaba.himarket.support.enums.McpVendorType;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
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
            JSONObject json = JSONUtil.parseObj(responseBody);

            JSONArray servers = json.getJSONArray("servers");
            if (servers == null || servers.isEmpty()) {
                return PageResult.empty(page, size);
            }

            // 解析分页元数据
            JSONObject metadata = json.getJSONObject("metadata");

            boolean hasNextPage = false;
            if (metadata != null) {
                String nextCursor = metadata.getStr("nextCursor");
                hasNextPage = nextCursor != null && !nextCursor.isBlank();
                if (hasNextPage) {
                    cursorCache.put(page + 1, nextCursor);
                }
            }
            List<RemoteMcpItem> items = new ArrayList<>();
            for (int i = 0; i < servers.size(); i++) {
                try {
                    JSONObject entry = servers.getJSONObject(i);
                    // version=latest 已过滤，只需检查 status=active
                    JSONObject meta = entry.getJSONObject("_meta");
                    if (meta != null) {
                        JSONObject official = meta.getJSONObject(META_KEY);
                        if (official != null && !"active".equals(official.getStr("status"))) {
                            continue;
                        }
                    }
                    JSONObject server = entry.getJSONObject("server");
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

    private RemoteMcpItem convertToRemoteMcpItem(JSONObject server) {
        String name = server.getStr("name");
        if (name == null || name.isBlank()) {
            return null;
        }

        String mcpName = toMcpName(name);
        String title = server.getStr("title");
        String displayName = (title != null && !title.isBlank()) ? title : name;
        String description = server.getStr("description");

        // Determine protocolType and connectionConfig from remotes or packages
        String protocolType = "stdio";
        String connectionConfig = "{}";

        JSONArray remotes = server.getJSONArray("remotes");
        JSONArray packages = server.getJSONArray("packages");

        if (remotes != null && !remotes.isEmpty()) {
            JSONObject remote = remotes.getJSONObject(0);
            protocolType = remote.getStr("type", "stdio");
            // Build connectionConfig JSON including url, type, and headers if present
            JSONObject connObj = JSONUtil.createObj();
            connObj.set("url", remote.getStr("url"));
            connObj.set("type", remote.getStr("type"));
            if (remote.containsKey("headers")) {
                connObj.set("headers", remote.get("headers"));
            }
            connectionConfig = connObj.toString();
        } else if (packages != null && !packages.isEmpty()) {
            JSONObject pkg = packages.getJSONObject(0);
            protocolType = "stdio";
            JSONObject connObj = JSONUtil.createObj();
            connObj.set("registryType", pkg.getStr("registryType"));
            connObj.set("identifier", pkg.getStr("identifier"));
            JSONObject transport = pkg.getJSONObject("transport");
            if (transport != null) {
                connObj.set("transport", transport);
            }
            connectionConfig = connObj.toString();
        }

        // Icon from icons[0].src
        String icon = null;
        JSONArray icons = server.getJSONArray("icons");
        if (icons != null && !icons.isEmpty()) {
            JSONObject iconObj = icons.getJSONObject(0);
            String iconSrc = iconObj.getStr("src");
            if (iconSrc != null && !iconSrc.isBlank()) {
                icon = JSONUtil.createObj().set("type", "URL").set("value", iconSrc).toString();
            }
        }

        // repoUrl: prefer repository.url, fallback to websiteUrl
        String repoUrl = null;
        JSONObject repository = server.getJSONObject("repository");
        if (repository != null) {
            repoUrl = repository.getStr("url");
        }
        if (repoUrl == null || repoUrl.isBlank()) {
            repoUrl = server.getStr("websiteUrl");
        }

        // extraParams: from remotes[].headers or packages[].environmentVariables
        String extraParams = null;
        JSONArray params = JSONUtil.createArray();
        if (remotes != null) {
            for (int i = 0; i < remotes.size(); i++) {
                JSONArray headers = remotes.getJSONObject(i).getJSONArray("headers");
                if (headers != null) {
                    for (int j = 0; j < headers.size(); j++) {
                        JSONObject h = headers.getJSONObject(j);
                        params.add(
                                JSONUtil.createObj()
                                        .set("name", h.getStr("name"))
                                        .set("description", h.getStr("description", ""))
                                        .set("required", h.getBool("isRequired", false))
                                        .set("secret", h.getBool("isSecret", false)));
                    }
                }
            }
        }
        if (packages != null) {
            for (int i = 0; i < packages.size(); i++) {
                JSONArray envVars = packages.getJSONObject(i).getJSONArray("environmentVariables");
                if (envVars != null) {
                    for (int j = 0; j < envVars.size(); j++) {
                        JSONObject ev = envVars.getJSONObject(j);
                        params.add(
                                JSONUtil.createObj()
                                        .set("name", ev.getStr("name"))
                                        .set("description", ev.getStr("description", ""))
                                        .set("required", ev.getBool("isRequired", false))
                                        .set("secret", ev.getBool("isSecret", false)));
                    }
                }
            }
        }
        if (!params.isEmpty()) {
            extraParams = params.toString();
        }

        return RemoteMcpItem.builder()
                .remoteId(name)
                .mcpName(mcpName)
                .displayName(displayName)
                .description(description)
                .protocolType(protocolType)
                .connectionConfig(connectionConfig)
                .tags(null)
                .icon(icon)
                .repoUrl(repoUrl)
                .extraParams(extraParams)
                .build();
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
