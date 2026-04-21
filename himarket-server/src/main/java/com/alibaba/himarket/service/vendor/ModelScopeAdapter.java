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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

/** ModelScope（魔搭社区）供应商适配器，调用 ModelScope REST API 查询 MCP Server 列表。 */
@Slf4j
@Component
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
                            "ModelScope detail API failed for {}: {}",
                            item.getRemoteId(),
                            response.code());
                    return item;
                }

                String responseBody = response.body().string();
                JSONObject json = JSONUtil.parseObj(responseBody);
                if (!json.getBool("success", false)) {
                    return item;
                }

                JSONObject data = json.getJSONObject("data");
                if (data == null) {
                    return item;
                }

                // connectionConfig from server_config[0]
                JSONArray serverConfig = data.getJSONArray("server_config");
                if (serverConfig != null && !serverConfig.isEmpty()) {
                    item.setConnectionConfig(serverConfig.getJSONObject(0).toString());
                }

                // protocolType: infer from server_config command
                if (serverConfig != null && !serverConfig.isEmpty()) {
                    JSONObject cfg = serverConfig.getJSONObject(0);
                    JSONObject mcpServers = cfg.getJSONObject("mcpServers");
                    if (mcpServers != null && !mcpServers.isEmpty()) {
                        String firstKey = mcpServers.keySet().iterator().next();
                        JSONObject serverEntry = mcpServers.getJSONObject(firstKey);
                        String command = serverEntry != null ? serverEntry.getStr("command") : null;
                        if ("npx".equals(command)
                                || "uvx".equals(command)
                                || "node".equals(command)
                                || "python".equals(command)) {
                            item.setProtocolType("stdio");
                        }
                    }
                }

                // icon from logo_url (detail may have better quality)
                String logoUrl = data.getStr("logo_url");
                if (logoUrl != null && !logoUrl.isBlank()) {
                    item.setIcon(
                            JSONUtil.createObj()
                                    .set("type", "URL")
                                    .set("value", logoUrl)
                                    .toString());
                }

                // repoUrl from source_url
                String sourceUrl = data.getStr("source_url");
                if (sourceUrl != null && !sourceUrl.isBlank()) {
                    item.setRepoUrl(sourceUrl);
                }

                // extraParams from env_schema
                JSONObject envSchema = data.getJSONObject("env_schema");
                if (envSchema != null) {
                    JSONObject properties = envSchema.getJSONObject("properties");
                    JSONArray required = envSchema.getJSONArray("required");
                    if (properties != null && !properties.isEmpty()) {
                        JSONArray params = JSONUtil.createArray();
                        for (String key : properties.keySet()) {
                            JSONObject prop = properties.getJSONObject(key);
                            JSONObject paramDef = JSONUtil.createObj();
                            paramDef.set("name", key);
                            paramDef.set(
                                    "description",
                                    prop != null ? prop.getStr("description", "") : "");
                            paramDef.set("required", required != null && required.contains(key));
                            paramDef.set(
                                    "type",
                                    prop != null ? prop.getStr("type", "string") : "string");
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
                JSONObject locales = data.getJSONObject("locales");
                if (locales != null) {
                    JSONObject zh = locales.getJSONObject("zh");
                    if (zh != null) {
                        readme = zh.getStr("readme");
                    }
                }
                if (readme == null || readme.isBlank()) {
                    readme = data.getStr("readme");
                }
                if (readme == null || readme.isBlank()) {
                    if (locales != null) {
                        JSONObject en = locales.getJSONObject("en");
                        if (en != null) {
                            readme = en.getStr("readme");
                        }
                    }
                }
                if (readme != null && !readme.isBlank()) {
                    item.setServiceIntro(readme);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to enrich ModelScope MCP detail for {}", item.getRemoteId(), e);
        }
        return item;
    }

    @Override
    public PageResult<RemoteMcpItem> listMcpServers(String keyword, int page, int size) {
        try {
            JSONObject body =
                    JSONUtil.createObj()
                            .set("filter", JSONUtil.createObj())
                            .set("page_number", page)
                            .set("page_size", size)
                            .set("search", keyword != null ? keyword : "");

            Request request =
                    new Request.Builder()
                            .url(LIST_URL)
                            .put(RequestBody.create(body.toString(), JSON))
                            .header("Content-Type", "application/json")
                            .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("ModelScope API returned non-success status: {}", response.code());
                    return PageResult.empty(page, size);
                }

                String responseBody = response.body().string();
                JSONObject json = JSONUtil.parseObj(responseBody);

                if (!json.getBool("success", false)) {
                    log.warn("ModelScope API returned success=false");
                    return PageResult.empty(page, size);
                }

                JSONObject data = json.getJSONObject("data");
                if (data == null) {
                    return PageResult.empty(page, size);
                }

                long totalCount = data.getLong("total_count", 0L);
                JSONArray serverList = data.getJSONArray("mcp_server_list");
                if (serverList == null || serverList.isEmpty()) {
                    return PageResult.empty(page, size);
                }

                List<RemoteMcpItem> items = new ArrayList<>();
                for (int i = 0; i < serverList.size(); i++) {
                    try {
                        JSONObject server = serverList.getJSONObject(i);
                        RemoteMcpItem item = convertToRemoteMcpItem(server);
                        if (item != null) {
                            items.add(item);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse ModelScope MCP item at index {}", i, e);
                    }
                }

                return PageResult.of(items, page, size, totalCount);
            }
        } catch (IOException e) {
            log.warn("ModelScope API call failed", e);
            return PageResult.empty(page, size);
        }
    }

    private RemoteMcpItem convertToRemoteMcpItem(JSONObject server) {
        String id = server.getStr("id");
        if (id == null || id.isBlank()) {
            return null;
        }

        String mcpName = toMcpName(id);
        String displayName = resolveLocaleName(server);
        String description = resolveLocaleDescription(server);

        // icon
        String icon = null;
        String logoUrl = server.getStr("logo_url");
        if (logoUrl != null && !logoUrl.isBlank()) {
            icon = JSONUtil.createObj().set("type", "URL").set("value", logoUrl).toString();
        }

        // tags from categories
        String tags = null;
        JSONArray categories = server.getJSONArray("categories");
        if (categories != null && !categories.isEmpty()) {
            tags = categories.toString();
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
     * 将 ModelScope id 转换为 mcpName。
     *
     * <p>规则：去掉 {@code @} 前缀（如有），{@code /} 替换为 {@code -}，转小写，截断 63 字符。
     *
     * <p>示例：{@code @amap/amap-maps} → {@code amap-amap-maps}，{@code Alipay/alipay-subscription}
     * → {@code alipay-alipay-subscription}
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

    /** 优先取 locales.zh.name，fallback 到顶层 name，再 fallback 到 locales.en.name。 */
    private String resolveLocaleName(JSONObject server) {
        JSONObject locales = server.getJSONObject("locales");
        if (locales != null) {
            JSONObject zh = locales.getJSONObject("zh");
            if (zh != null) {
                String zhName = zh.getStr("name");
                if (zhName != null && !zhName.isBlank()) {
                    return zhName;
                }
            }
        }
        String topName = server.getStr("name");
        if (topName != null && !topName.isBlank()) {
            return topName;
        }
        if (locales != null) {
            JSONObject en = locales.getJSONObject("en");
            if (en != null) {
                return en.getStr("name");
            }
        }
        return null;
    }

    /** 优先取 locales.zh.description，fallback 到顶层 description，再 fallback 到 locales.en.description。 */
    private String resolveLocaleDescription(JSONObject server) {
        JSONObject locales = server.getJSONObject("locales");
        if (locales != null) {
            JSONObject zh = locales.getJSONObject("zh");
            if (zh != null) {
                String zhDesc = zh.getStr("description");
                if (zhDesc != null && !zhDesc.isBlank()) {
                    return zhDesc;
                }
            }
        }
        String topDesc = server.getStr("description");
        if (topDesc != null && !topDesc.isBlank()) {
            return topDesc;
        }
        if (locales != null) {
            JSONObject en = locales.getJSONObject("en");
            if (en != null) {
                return en.getStr("description");
            }
        }
        return null;
    }
}
