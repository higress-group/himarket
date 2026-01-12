package com.alibaba.himarket.service.hichat.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.dto.result.httpapi.HttpRouteResult;
import com.alibaba.himarket.dto.result.model.ModelConfigResult;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.service.GatewayService;
import com.alibaba.himarket.service.hichat.manager.ChatBotManager;
import com.alibaba.himarket.service.hichat.support.InvokeModelParam;
import com.alibaba.himarket.service.hichat.support.LlmChatRequest;
import com.alibaba.himarket.support.enums.AIProtocol;
import com.alibaba.himarket.support.product.ModelFeature;
import io.agentscope.core.formatter.openai.OpenAIChatFormatter;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.OpenAIChatModel;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
public class OpenAILlmService extends AbstractLlmService {

    public OpenAILlmService(GatewayService gatewayService, ChatBotManager chatBotManager) {
        super(gatewayService, chatBotManager);
    }

    @Override
    protected LlmChatRequest composeRequest(InvokeModelParam param) {
        LlmChatRequest request = super.composeRequest(param);
        ProductResult product = param.getProduct();

        // Request URI (without query params)
        URI uri = getUri(product.getModelConfig(), request.getGatewayUris());
        request.setUri(uri);

        if (BooleanUtil.isTrue(param.getEnableWebSearch())) {
            Map<String, Object> webSearchOptions =
                    JSONUtil.parseObj(
                            """
                                {
                                    "web_search_options": {
                                        "search_context_size": "medium"
                                    }
                                }
                            """);
            request.setBodyParams(webSearchOptions);
        }

        return request;
    }

    @Override
    public Model newChatModel(LlmChatRequest request) {
        URI uri = request.getUri();

        String baseUrl =
                uri.getScheme()
                        + "://"
                        + uri.getHost()
                        + (uri.getPort() == -1 ? "" : ":" + uri.getPort())
                        + uri.getPath();

        ModelFeature modelFeature = getOrDefaultModelFeature(request.getProduct());
        GenerateOptions options =
                GenerateOptions.builder().stream(true)
                        .temperature(modelFeature.getTemperature())
                        .maxTokens(modelFeature.getMaxTokens())
                        .additionalHeaders(request.getHeaders())
                        .additionalQueryParams(request.getQueryParams())
                        .additionalBodyParams(request.getBodyParams())
                        .build();

        return OpenAIChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(request.getApiKey())
                .modelName(modelFeature.getModel())
                .stream(true)
                .formatter(new OpenAIChatFormatter())
                .generateOptions(options)
                .build();
    }

    private URI getUri(ModelConfigResult modelConfig, List<URI> gatewayUris) {
        ModelConfigResult.ModelAPIConfig modelAPIConfig = modelConfig.getModelAPIConfig();
        if (modelAPIConfig == null || CollUtil.isEmpty(modelAPIConfig.getRoutes())) {
            log.error("Failed to build URI: model API config is null or contains no routes");
            return null;
        }

        String completionPath = "/chat/completions";
        // Find matching route or use first route as fallback
        HttpRouteResult route =
                modelAPIConfig.getRoutes().stream()
                        .filter(
                                r ->
                                        Optional.ofNullable(r.getMatch())
                                                .map(HttpRouteResult.RouteMatchResult::getPath)
                                                .map(HttpRouteResult.RouteMatchPath::getValue)
                                                .filter(path -> path.endsWith(completionPath))
                                                .isPresent())
                        .findFirst()
                        .orElseGet(() -> modelAPIConfig.getRoutes().get(0));

        // Get and process path
        String path =
                Optional.ofNullable(route.getMatch())
                        .map(HttpRouteResult.RouteMatchResult::getPath)
                        .map(HttpRouteResult.RouteMatchPath::getValue)
                        .map(
                                p ->
                                        p.endsWith(completionPath)
                                                ? p.substring(
                                                        0, p.length() - completionPath.length())
                                                : p)
                        .orElse("");

        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();

        // Try to get public domain first, fallback to first domain
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
            URI uri = gatewayUris.get(0);
            builder.scheme(uri.getScheme() != null ? uri.getScheme() : "http").host(uri.getHost());
            if (uri.getPort() != -1) {
                builder.port(uri.getPort());
            }
        } else {
            log.error("Failed to build URI: no valid domain found and no gateway URIs provided");
            return null;
        }

        builder.path(path);
        URI uri = builder.build().toUri();
        log.debug("Successfully built URI: {}", uri);
        return uri;
    }

    @Override
    public List<AIProtocol> getProtocols() {
        return List.of(AIProtocol.OPENAI, AIProtocol.OPENAI_COMPATIBLE);
    }
}
