package com.alibaba.apiopenplatform.service.chat.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.apiopenplatform.dto.result.httpapi.DomainResult;
import com.alibaba.apiopenplatform.dto.result.httpapi.HttpRouteResult;
import com.alibaba.apiopenplatform.dto.result.model.ModelConfigResult;
import com.alibaba.apiopenplatform.service.chat.dto.InvokeModelParam;
import com.alibaba.apiopenplatform.service.chat.dto.LlmChatRequest;
import com.alibaba.apiopenplatform.service.chat.dto.ChatRequestBody;
import com.alibaba.apiopenplatform.service.chat.dto.OpenAIChatStreamResponse;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class OpenAILlmService extends LlmService {

    public OpenAILlmService(WebClient webClient) {
        super(webClient);
    }

    @Override
    protected LlmChatRequest composeRequest(InvokeModelParam param) {
        // 1. Get request URL
        String url = getApiUrl(param.getModelConfig());

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
                .build();
    }

    @Override
    public String processStreamChunk(String chunk, StringBuilder answerBuilder) {
        try {
            if ("[DONE]".equals(chunk)) {
                return chunk;
            }

            log.info("zhaoh-test-jsonData: {}", chunk);
            // OpenAI common response format
            OpenAIChatStreamResponse response = JSONUtil.toBean(chunk, OpenAIChatStreamResponse.class);
            log.info("zhaoh-test-response: {}", JSONUtil.toJsonStr(response));

            // Answer from LLM
            String content = extractContentFromResponse(response);
            log.info("zhaoh-test-content: {}", content);

            if (content != null) {
                answerBuilder.append(content);
            }

            // TODO
//            response.setAnswerId(answerId);
//            return "data: " + JSONUtil.toJsonStr(response) + "\n\n";
            return JSONUtil.toJsonStr(response);

        } catch (Exception e) {
            log.warn("Failed to process chunk: {}", chunk, e);
            return chunk;
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

    private String getApiUrl(ModelConfigResult modelConfig) {
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
                    .filter(domain -> !StrUtil.equalsIgnoreCase(domain.getNetworkType(), "intranet"))
                    .filter(domain -> domain.getDomain().endsWith("alicloudapi.com"))
                    .findFirst();

            if (externalDomain.isPresent()) {
                DomainResult domain = externalDomain.get();
                String protocol = StrUtil.isNotBlank(domain.getProtocol()) ?
                        domain.getProtocol() + "://" :
                        "https://";
                return protocol + domain.getDomain() + pathValue;
            }
        }

        // No suitable route found
        return null;
    }
}
