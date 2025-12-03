package com.alibaba.apiopenplatform.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.dto.params.chat.ChatRequestBody;
import com.alibaba.apiopenplatform.dto.result.chat.ChatAnswerMessage;
import com.alibaba.apiopenplatform.dto.result.common.DomainResult;
import com.alibaba.apiopenplatform.dto.result.httpapi.HttpRouteResult;
import com.alibaba.apiopenplatform.dto.result.model.ModelConfigResult;
import com.alibaba.apiopenplatform.dto.result.product.ProductResult;
import com.alibaba.apiopenplatform.service.LlmService;
import com.alibaba.apiopenplatform.dto.params.chat.InvokeModelParam;
import com.alibaba.apiopenplatform.dto.result.chat.LlmChatRequest;
import com.alibaba.apiopenplatform.dto.result.chat.LlmInvokeResult;
import com.alibaba.apiopenplatform.support.product.ModelFeature;
import com.alibaba.apiopenplatform.support.product.ProductFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractLlmService implements LlmService {

    // 一次chat中模型调用的最大次数
    protected static final int MAX_MODEL_REQUEST_PER_CHAT = 20;

    @Override
    public Flux<ChatAnswerMessage> invokeLLM(InvokeModelParam param, HttpServletResponse response, Consumer<LlmInvokeResult> resultHandler) {
        // ResultHandler is mainly used to record answer and usage
        try {
            LlmChatRequest request = composeRequest(param);

            return call(request, response, resultHandler);
        } catch (Exception e) {
            log.error("Failed to invoke LLM, chatId={}", param.getChatId(), e);
            response.setStatus(ErrorCode.INTERNAL_ERROR.getStatus().value());
            return Flux.just(ChatAnswerMessage.ofError(ErrorCode.INTERNAL_ERROR.getMessage(e.getMessage()), "Failed to invoke LLM"));
        }
    }

    protected abstract Flux<ChatAnswerMessage> call(LlmChatRequest request, HttpServletResponse response, Consumer<LlmInvokeResult> resultHandler);

    private LlmChatRequest composeRequest(InvokeModelParam param) {
        // Not be null
        ProductResult product = param.getProduct();
        ModelConfigResult modelConfig = product.getModelConfig();
        // 1. Get request URL (with query params)
        URL url = getUrl(modelConfig, param.getQueryParams());

        // 2. Build headers
        Map<String, String> headers = param.getRequestHeaders() == null ? new HashMap<>() : param.getRequestHeaders();

        // 3. Build request body
        ModelFeature modelFeature = getOrDefaultModelFeature(product);
        ChatRequestBody chatRequest = ChatRequestBody.builder()
                .model(modelFeature.getModel())
                .userQuestion(param.getUserQuestion())
                .messages(param.getChatMessages())
                // TODO: 这个参数应该用不到了
                .stream(modelFeature.getStreaming())
                .maxTokens(modelFeature.getMaxTokens())
                .temperature(modelFeature.getTemperature())
                .mcpServerConfigs(param.getMcpServerConfigs())
                .build();

        if (BooleanUtil.isTrue(modelFeature.getWebSearch()) && BooleanUtil.isTrue(param.getEnableWebSearch())) {
            chatRequest.setWebSearchOptions(new OpenAiApi.ChatCompletionRequest.WebSearchOptions(
                    OpenAiApi.ChatCompletionRequest.WebSearchOptions.SearchContextSize.MEDIUM, null));
        }

        return LlmChatRequest.builder()
                .chatId(param.getChatId())
                .url(url)
                .headers(headers)
                .chatRequest(chatRequest)
                .gatewayIps(param.getGatewayIps())
                .credentialContext(param.getCredentialContext())
                .build();
    }

    private ModelFeature getOrDefaultModelFeature(ProductResult product) {
        ModelFeature modelFeature = Optional.ofNullable(product)
                .map(ProductResult::getFeature)
                .map(ProductFeature::getModelFeature)
                .orElseGet(() -> ModelFeature.builder().build());

        return ModelFeature.builder()
                .model(Optional.ofNullable(modelFeature)
                        .map(ModelFeature::getModel)
                        .orElse("qwen-max"))
                .maxTokens(Optional.ofNullable(modelFeature)
                        .map(ModelFeature::getMaxTokens)
                        .orElse(5000))
                .temperature(Optional.ofNullable(modelFeature)
                        .map(ModelFeature::getTemperature)
                        .orElse(0.9))
                .streaming(Optional.ofNullable(modelFeature)
                        .map(ModelFeature::getStreaming)
                        .orElse(true))
                .webSearch(Optional.ofNullable(modelFeature)
                        .map(ModelFeature::getWebSearch)
                        .orElse(false))
                .build();
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
}