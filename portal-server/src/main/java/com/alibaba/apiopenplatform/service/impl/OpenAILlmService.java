package com.alibaba.apiopenplatform.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.apiopenplatform.dto.params.chat.ChatContent;
import com.alibaba.apiopenplatform.dto.result.httpapi.DomainResult;
import com.alibaba.apiopenplatform.dto.result.httpapi.HttpRouteResult;
import com.alibaba.apiopenplatform.dto.result.model.ModelConfigResult;
import com.alibaba.apiopenplatform.dto.params.chat.InvokeModelParam;
import com.alibaba.apiopenplatform.dto.result.chat.LlmChatRequest;
import com.alibaba.apiopenplatform.dto.params.chat.ChatRequestBody;
import com.alibaba.apiopenplatform.dto.result.chat.OpenAIChatStreamResponse;
import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.support.chat.ChatUsage;
import com.alibaba.apiopenplatform.support.enums.AIProtocol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class OpenAILlmService extends AbstractLlmService {

    @Override
    protected LlmChatRequest composeRequest(InvokeModelParam param) {
        // 1. Get request URL (with query params)
        URL url = getUrl(param.getModelConfig(), param.getQueryParams());

        // 2. Build headers
        Map<String, String> headers = param.getRequestHeaders() == null ? new HashMap<>() : param.getRequestHeaders();

        // 3. Build request body
        ChatRequestBody requestBody = ChatRequestBody.builder()
                .model(extractModelName())
                .messages(param.getChatMessages())
                .stream(true)
                .maxTokens(extractMaxTokens())
                .temperature(extractTemperature())
                .build();

        return LlmChatRequest.builder()
                .url(url)
                .method(HttpMethod.POST)
                .headers(headers)
                .body(requestBody)
                .gatewayIps(param.getGatewayIps())
                .build();
    }

    @Override
    public void processStreamChunk(String chunk, ChatContent chatContent) {
        try {
            if ("[DONE]".equals(chunk)) {
                return;
            }

            // OpenAI common response format
            OpenAIChatStreamResponse response = JSONUtil.toBean(chunk, OpenAIChatStreamResponse.class);

            // Answer from LLM
            String content = extractContentFromResponse(response);

            if (content != null) {
                // Append to answer content and reset current content
                chatContent.getAnswerContent().append(content);
                chatContent.setCurrentContent(content);
            }

            if (response.getUsage() != null) {
                ChatUsage usage = response.toStandardUsage();
                chatContent.setUsage(usage);
            }

        } catch (Exception e) {
            log.warn("Failed to process chunk: {}", chunk, e);
            // Caused by invalid JSON or other errors, append to unexpected content
            chatContent.getUnexpectedContent().append(chunk);
            chatContent.getAnswerContent().append(chunk);
        }
    }

    private String extractContentFromResponse(OpenAIChatStreamResponse response) {
        return Optional.ofNullable(response.getChoices())
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(OpenAIChatStreamResponse.Choice::getDelta)
                .map(OpenAIChatStreamResponse.Delta::getContent)
                .orElse(null);
    }

    private String extractModelName() {
        return "qwen-max";
    }

    private Integer extractMaxTokens() {
        return 5000;
    }

    private Double extractTemperature() {
        return 0.9;
    }

    private URL getUrl(ModelConfigResult modelConfig, Map<String, String> queryParams) {
        ModelConfigResult.ModelAPIConfig modelAPIConfig = modelConfig.getModelAPIConfig();
        if (modelAPIConfig == null) {
            return null;
        }

        List<HttpRouteResult> routes = modelAPIConfig.getRoutes();
        if (CollUtil.isEmpty(routes)) {
            return null;
        }

        // Find route ending with /chat/completions
        for (HttpRouteResult route : routes) {
            String pathValue = Optional.ofNullable(route.getMatch())
                    .map(HttpRouteResult.RouteMatchResult::getPath)
                    .map(HttpRouteResult.RouteMatchPath::getValue)
                    .orElse("");

            if (!pathValue.endsWith("/chat/completions")) {
                continue;
            }

            // Find first external domain
            Optional<DomainResult> externalDomain = route.getDomains().stream()
                    // TODO 调试场景专用，防止域名被ICP拦截，可恶啊
                    .filter(domain -> StrUtil.endWith(domain.getDomain(), ".alicloudapi.com"))
                    .filter(domain -> !StrUtil.equalsIgnoreCase(domain.getNetworkType(), "intranet"))
                    .findFirst();

            if (externalDomain.isPresent()) {
                DomainResult domain = externalDomain.get();
                String protocol = StrUtil.isNotBlank(domain.getProtocol()) ?
                        domain.getProtocol().toLowerCase() : "http";

                try {
                    // Build URL with query params
                    UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
                            .scheme(protocol)
                            .host(domain.getDomain())
                            .path(pathValue);
                    
                    if (CollUtil.isNotEmpty(queryParams)) {
                        queryParams.forEach(builder::queryParam);
                    }
                    
                    return new URL(builder.build().toUriString());
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        // No suitable route found
        return null;
    }

    private URL getUrl(ModelConfigResult modelConfig) {
        return getUrl(modelConfig, null);
    }

    @Override
    public AIProtocol getProtocol() {
        return AIProtocol.OPENAI;
    }
}
