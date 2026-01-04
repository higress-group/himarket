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

package com.alibaba.himarket.service.legacy;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.dto.result.chat.LlmChatRequestLegacy;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.httpapi.HttpRouteResult;
import com.alibaba.himarket.dto.result.model.ModelConfigResult;
import com.alibaba.himarket.support.enums.AIProtocol;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
@Deprecated
public class OpenAILlmServiceLegacy extends AbstractLlmServiceLegacy {

    public OpenAILlmServiceLegacy(ToolCallingManager toolCallingManager) {
        super(toolCallingManager);
    }

    @Override
    public ChatClient newChatClient(LlmChatRequestLegacy request) {
        MultiValueMap<String, String> headers = new HttpHeaders();
        Optional.ofNullable(request.getHeaders())
                .ifPresent(headerMap -> headerMap.forEach(headers::add));

        // Build base URL
        URI uri = request.getUri();
        String baseUrl =
                uri.getScheme()
                        + "://"
                        + uri.getHost()
                        + (uri.getPort() == -1 ? "" : ":" + uri.getPort());

        String pathWithQuery = uri.getPath();
        if (StrUtil.isNotBlank(uri.getRawQuery())) {
            pathWithQuery += "?" + uri.getRawQuery();
        }

        WebClient.Builder webClientBuilder =
                WebClient.builder()
                        .codecs(
                                configurer ->
                                        configurer
                                                .defaultCodecs()
                                                .maxInMemorySize(16 * 1024 * 1024));

        // Configure OpenAI API client
        OpenAiApi openAiApi =
                OpenAiApi.builder()
                        .baseUrl(baseUrl)
                        .completionsPath(pathWithQuery)
                        .headers(headers)
                        .apiKey(
                                Optional.ofNullable(request.getCredentialContext())
                                        .map(CredentialContext::getApiKey)
                                        .orElse(""))
                        .webClientBuilder(webClientBuilder)
                        .build();

        OpenAiChatModel chatModel =
                OpenAiChatModel.builder()
                        .openAiApi(openAiApi)
                        .toolCallingManager(toolCallingManager)
                        .defaultOptions(OpenAiChatOptions.builder().streamUsage(true).build())
                        .build();

        return ChatClient.builder(chatModel).build();
    }

    @Override
    public URI getUri(
            ModelConfigResult modelConfig, Map<String, String> queryParams, List<URI> gatewayUris) {
        ModelConfigResult.ModelAPIConfig modelAPIConfig = modelConfig.getModelAPIConfig();
        if (modelAPIConfig == null || CollUtil.isEmpty(modelAPIConfig.getRoutes())) {
            log.error("Invalid model config - modelAPIConfig is null or routes is empty");
            return null;
        }

        // Find preferred route
        HttpRouteResult route =
                modelAPIConfig.getRoutes().stream()
                        .filter(
                                r ->
                                        Optional.ofNullable(r.getMatch())
                                                .map(HttpRouteResult.RouteMatchResult::getPath)
                                                .map(HttpRouteResult.RouteMatchPath::getValue)
                                                // Find path that ends with /chat/completions
                                                .filter(path -> path.endsWith("/chat/completions"))
                                                .isPresent())
                        .findFirst()
                        .orElseGet(() -> modelAPIConfig.getRoutes().get(0));

        String path =
                Optional.ofNullable(route.getMatch())
                        .map(HttpRouteResult.RouteMatchResult::getPath)
                        .map(HttpRouteResult.RouteMatchPath::getValue)
                        .orElse("");

        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();

        // Find domain or use gateway IP
        DomainResult domain =
                route.getDomains().stream()
                        .filter(d -> !StrUtil.equalsIgnoreCase(d.getNetworkType(), "intranet"))
                        .findFirst()
                        .orElseGet(
                                () ->
                                        CollUtil.isNotEmpty(route.getDomains())
                                                ? route.getDomains().get(0)
                                                : null);

        if (domain != null) {
            String protocol =
                    StrUtil.isNotBlank(domain.getProtocol())
                            ? domain.getProtocol().toLowerCase()
                            : "http";

            builder.scheme(protocol).host(domain.getDomain());
            if (domain.getPort() != null && domain.getPort() > 0) {
                builder.port(domain.getPort());
            }
        } else if (CollUtil.isNotEmpty(gatewayUris)) {
            URI uri = gatewayUris.get(RandomUtil.randomInt(gatewayUris.size()));
            builder.scheme(uri.getScheme() != null ? uri.getScheme() : "http").host(uri.getHost());

            if (uri.getPort() != -1) {
                builder.port(uri.getPort());
            }
        } else {
            log.error("Failed to build URI - no valid domain found and no gateway URIs available");
            return null;
        }

        // Add path and query params
        builder.path(path);
        Optional.ofNullable(queryParams).ifPresent(params -> params.forEach(builder::queryParam));

        return builder.build().toUri();
    }

    @Override
    public AIProtocol getProtocol() {
        return AIProtocol.OPENAI;
    }
}
