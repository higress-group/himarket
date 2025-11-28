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
import com.alibaba.apiopenplatform.dto.result.product.ProductResult;
import com.alibaba.apiopenplatform.support.chat.ChatUsage;
import com.alibaba.apiopenplatform.support.enums.AIProtocol;
import com.alibaba.apiopenplatform.support.product.ModelFeature;
import com.alibaba.apiopenplatform.support.product.ProductFeature;
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
        // Not be null
        ProductResult product = param.getProduct();
        ModelConfigResult modelConfig = product.getModelConfig();
        // 1. Get request URL (with query params)
        URL url = getUrl(modelConfig, param.getQueryParams());

        // 2. Build headers
        Map<String, String> headers = param.getRequestHeaders() == null ? new HashMap<>() : param.getRequestHeaders();

        // 3. Build request body
        ModelFeature modelFeature = getOrDefaultModelFeature(product);
        ChatRequestBody requestBody = ChatRequestBody.builder()
                .model(modelFeature.getModel())
                .messages(param.getChatMessages())
                .stream(modelFeature.getStreaming())
                .maxTokens(modelFeature.getMaxTokens())
                .temperature(modelFeature.getTemperature())
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
    public String processStreamChunk(String chunk, ChatContent chatContent) {
        try {
            if ("[DONE]".equals(chunk)) {
                return chunk;
            }

            // OpenAI common response format
            OpenAIChatStreamResponse response = JSONUtil.toBean(chunk, OpenAIChatStreamResponse.class);

            // Answer from LLM
            String content = extractContentFromResponse(response);

            if (content != null) {
                // Append to answer content
                chatContent.getAnswerContent().append(content);
            }

            if (response.getUsage() != null) {
                ChatUsage usage = response.toStandardUsage();
                chatContent.setUsage(usage);
                chatContent.stop();
                response.getUsage().setFirstByteTimeout(usage.getFirstByteTimeout());
                response.getUsage().setElapsedTime(usage.getElapsedTime());
            }

            return JSONUtil.toJsonStr(response);
        } catch (Exception e) {
            log.warn("Failed to process chunk: {}", chunk, e);
            // Caused by invalid JSON or other errors, append to unexpected content
            chatContent.getUnexpectedContent().append(chunk);
            chatContent.getAnswerContent().append(chunk);
        }
        return chunk;
    }

    private String extractContentFromResponse(OpenAIChatStreamResponse response) {
        return Optional.ofNullable(response.getChoices())
                .filter(choices -> !choices.isEmpty())
                .map(choices -> choices.get(0))
                .map(OpenAIChatStreamResponse.Choice::getDelta)
                .map(OpenAIChatStreamResponse.Delta::getContent)
                .orElse(null);
    }

    private ModelFeature getOrDefaultModelFeature(ProductResult product) {
        ModelFeature modelFeature = Optional.ofNullable(product)
                .map(ProductResult::getFeature)
                .map(ProductFeature::getModelFeature)
                .orElse(new ModelFeature());

        // Default values
        if (modelFeature.getModel() == null) {
            modelFeature.setModel("qwen-max");
        }
        if (modelFeature.getMaxTokens() == null) {
            modelFeature.setMaxTokens(5000);
        }
        if (modelFeature.getTemperature() == null) {
            modelFeature.setTemperature(0.9);
        }
        if (modelFeature.getStreaming() == null) {
            modelFeature.setStreaming(true);
        }

        return modelFeature;
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
//                    .filter(domain -> StrUtil.endWith(domain.getDomain(), ".alicloudapi.com"))
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

    @Override
    public AIProtocol getProtocol() {
        return AIProtocol.OPENAI;
    }
}
