package com.alibaba.himarket.service.hichat.service;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONUtil;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
        String completionPath = "/chat/completions";
        URI uri =
                buildUri(
                        product.getModelConfig(),
                        request.getGatewayUris(),
                        completionPath,
                        path ->
                                path.endsWith(completionPath)
                                        ? path.substring(0, path.length() - completionPath.length())
                                        : path);
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

    @Override
    public List<AIProtocol> getProtocols() {
        return Collections.singletonList(AIProtocol.OPENAI);
    }
}
