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
package com.alibaba.himarket.service.hichat.service;

import com.alibaba.himarket.dto.result.model.ModelConfigResult;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.service.GatewayService;
import com.alibaba.himarket.service.hichat.manager.ChatBotManager;
import com.alibaba.himarket.service.hichat.support.InvokeModelParam;
import com.alibaba.himarket.service.hichat.support.LlmChatRequest;
import com.alibaba.himarket.support.enums.AIProtocol;
import com.alibaba.himarket.support.product.ModelFeature;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import java.net.URI;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DashScopeLlmService extends AbstractLlmService {

    public DashScopeLlmService(GatewayService gatewayService, ChatBotManager chatBotManager) {
        super(gatewayService, chatBotManager);
    }

    @Override
    protected LlmChatRequest composeRequest(InvokeModelParam param) {
        LlmChatRequest request = super.composeRequest(param);
        ProductResult product = param.getProduct();

        if (product.getModelConfig() != null) {
            URI uri = getUri(product.getModelConfig(), request.getGatewayUris());
            request.setUri(uri);
        }

        return request;
    }

    @Override
    public Model newChatModel(LlmChatRequest request) {
        // Build GenerateOptions with additional parameters
        GenerateOptions.Builder optionsBuilder =
                GenerateOptions.builder()
                        .additionalHeaders(request.getHeaders())
                        .additionalQueryParams(request.getQueryParams())
                        .additionalBodyParams(request.getBodyParams());

        GenerateOptions options = optionsBuilder.build();

        ModelFeature modelFeature = getOrDefaultModelFeature(request.getProduct());

        // TODO set dashscope request uri

        // Build DashScopeChatModel using Builder pattern
        return DashScopeChatModel.builder()
                .apiKey(request.getApiKey())
                .modelName(modelFeature.getModel())
                .enableSearch(modelFeature.getWebSearch())
                .stream(true)
                .defaultOptions(options)
                .build();
    }

    private URI getUri(ModelConfigResult modelConfig, List<URI> gatewayUris) {
        return null;
    }

    @Override
    public List<AIProtocol> getProtocols() {
        return List.of(AIProtocol.DASHSCOPE, AIProtocol.DASHSCOPE_IMAGE);
    }
}
