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

package com.alibaba.apiopenplatform.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.core.exception.BusinessException;
import com.alibaba.apiopenplatform.core.exception.ErrorCode;
import com.alibaba.apiopenplatform.core.security.ContextHolder;
import com.alibaba.apiopenplatform.core.utils.CacheUtil;
import com.alibaba.apiopenplatform.dto.params.chat.CreateChatParam;
import com.alibaba.apiopenplatform.dto.params.chat.InvokeModelParam;
import com.alibaba.apiopenplatform.dto.result.chat.LlmInvokeResult;
import com.alibaba.apiopenplatform.dto.result.consumer.CredentialContext;
import com.alibaba.apiopenplatform.dto.result.product.ProductRefResult;
import com.alibaba.apiopenplatform.dto.result.product.ProductResult;
import com.alibaba.apiopenplatform.service.ConsumerService;
import com.alibaba.apiopenplatform.service.GatewayService;
import com.alibaba.apiopenplatform.service.LlmService;
import com.alibaba.apiopenplatform.service.ProductService;
import com.alibaba.apiopenplatform.service.SearchRewriteService;
import com.alibaba.apiopenplatform.support.chat.ChatMessage;
import com.alibaba.apiopenplatform.support.chat.search.SearchInput;
import com.alibaba.apiopenplatform.support.enums.ChatRole;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.rholder.retry.BlockStrategies;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.google.common.base.Throwables;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SearchRewirteServiceImpl implements SearchRewriteService {
    
    public static String queryRewritePrompt = "### 任务:\n" +
            "分析聊天记录，以确定是否需要生成搜索查询，使用给定的语言。**生成1-3个宽泛且相关的搜索关键词**，除非能够完全确定不需要额外信息。\n" +
            "同时，根据聊天记录生成需要搜索的时间范围\n" +
            "目标是在存在最小不确定性的情况下，依然能够获取全面、最新且有价值的信息。如果完全确定不需要搜索，则返回一个空列表。\n" +
            "\n" +
            "### 指南:\n" +
            "- 必须**仅以 JSON 对象回答**。严禁任何形式的额外评论、解释或其他文本。\n" +
            "- 生成搜索查询时，请使用如下格式回答：{ \"query\": \"query_key_word1,query_key_word2\", \"time\": [\"2025-01-01\", \"2025-03-06\"] }，确保每个查询都是独特的、简洁的，并与主题相关。\n" +
            "- 可以确定搜索的时间范围时，在JSON对象中加入\"time\"字段，指定搜索范围的开始和结束时间，不要在搜索关键词中加入时间范围。\n" +
            "- 仅当完全确定通过搜索无法获得任何有用信息时，返回：{ \"query\": \"\" }。\n" +
            "- 如果存在**任何可能**提供有用或更新信息的机会，应倾向于建议生成搜索查询。\n" +
            "- 请保持简洁，专注于构造高质量的搜索查询，避免不必要的展开、评论或假设。\n" +
            "- 今天的日期为：%s。\n" +
            "- 始终优先提供既可操作又覆盖面广的查询，以最大化信息覆盖。\n" +
            "\n" +
            "### 输出:\n" +
            "严格以 JSON 格式返回，不要包含任何其他内容：\n" +
            "{\n" +
            "    \"query\":\"query_key_word1,query_key_word2\",\n" +
            "    \"time\": [\"2025-01-01\", \"2025-03-06\"]\n" +
            "}\n" +
            "\n" +
            "### 聊天记录:\n" +
            "<chat_history>\n" +
            "%s\n" +
            "</chat_history>";
    
    private final LlmService llmService;
    
    private final ProductService productService;
    
    private final GatewayService gatewayService;
    
    private final ContextHolder contextHolder;
    
    private final ConsumerService consumerService;
    
    private final Cache<String, List<String>> cache = CacheUtil.newCache(5);
    
    public SearchRewirteServiceImpl(LlmService llmService,
            ProductService productService,
            GatewayService gatewayService,
            ContextHolder contextHolder,
            ConsumerService consumerService) {
        this.llmService = llmService;
        this.productService = productService;
        this.gatewayService = gatewayService;
        this.contextHolder = contextHolder;
        this.consumerService = consumerService;
    }
    
    @Override
    public SearchInput rewriteWithRetry(List<ChatMessage> chatMessages, CreateChatParam chat) {
        Retryer<SearchInput> retry = RetryerBuilder.<SearchInput>newBuilder()
                .retryIfException()
                .withStopStrategy(StopStrategies.stopAfterDelay(3000L, TimeUnit.MILLISECONDS))
                .withBlockStrategy(BlockStrategies.threadSleepStrategy())
                .build();
        try {
            return retry.call(() -> rewrite(chatMessages, chat));
        } catch (ExecutionException e) {
            log.error("rewrite execution error, error message: {}", Throwables.getStackTraceAsString(e));
        } catch (RetryException e) {
            log.error("rewrite final fail, error message: {}", Throwables.getStackTraceAsString(e));
            return new SearchInput();
        }
        return new SearchInput();
    }
    
    
    private SearchInput rewrite(List<ChatMessage> chatMessages, CreateChatParam param) {
        List<ChatMessage> messages = buildRewriteMessagesFromHistory(chatMessages);
        
        InvokeModelParam invokeModelParam = buildInvokeModelParam(messages, param);
        
        // 使用 CountDownLatch 等待异步调用完成
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<LlmInvokeResult> resultRef = new AtomicReference<>();
        
        try {
            // 创建一个 Mock 的 HttpServletResponse，避免传 null 导致 NPE
            MockHttpServletResponse mockResponse = new MockHttpServletResponse();
            
            // 调用 LLM 进行查询重写
            llmService.invokeLLM(invokeModelParam, mockResponse, result -> {
                resultRef.set(result);
                latch.countDown();
            });
            
            // 等待最多 10 秒
            boolean completed = latch.await(10, TimeUnit.SECONDS);
            
            if (!completed) {
                log.error("Rewrite timeout after 10 seconds");
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "查询重写超时");
            }
            
            LlmInvokeResult result = resultRef.get();
            
            if (result == null || !result.isSuccess()) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "查询重写失败");
            }
            
            // 解析 LLM 返回的 JSON
            return parseSearchInput(result.getAnswer());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Rewrite interrupted", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "查询重写被中断");
        }
    }
    
    /**
     * 解析 LLM 返回的 JSON 格式搜索查询
     * 期望格式: { "query": "关键词1,关键词2", "time": ["2025-01-01", "2025-03-06"] }
     */
    private SearchInput parseSearchInput(String answer) {
        try {
            // 清理可能的 markdown 代码块标记
            String jsonStr = answer.trim();
            if (jsonStr.startsWith("```json")) {
                jsonStr = jsonStr.substring(7);
            }
            if (jsonStr.startsWith("```")) {
                jsonStr = jsonStr.substring(3);
            }
            if (jsonStr.endsWith("```")) {
                jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
            }
            jsonStr = jsonStr.trim();
            
            log.info("Parsing search input from LLM response: {}", jsonStr);
            
            JSONObject jsonObject = JSONUtil.parseObj(jsonStr);
            
            SearchInput searchInput = new SearchInput();
            
            // 解析 query 字段
            String query = jsonObject.getStr("query");
            if (StrUtil.isNotBlank(query)) {
                searchInput.setQuery(query);
            }
            
            // 解析 time 字段
            if (jsonObject.containsKey("time")) {
                List<String> time = jsonObject.getBeanList("time", String.class);
                if (time != null && !time.isEmpty()) {
                    searchInput.setTime(time);
                }
            }
            
            log.info("Parsed search input: query={}, time={}", searchInput.getQuery(), searchInput.getTime());
            
            return searchInput;
            
        } catch (Exception e) {
            log.error("Failed to parse search input from answer: {}", answer, e);
            // 如果解析失败，返回空的 SearchInput
            return new SearchInput();
        }
    }
    
    private InvokeModelParam buildInvokeModelParam(List<ChatMessage> messages, CreateChatParam param) {
        ProductResult productResult = productService.getProduct(param.getProductId());
        
        // Get gateway IPs
        ProductRefResult productRef = productService.getProductRef(param.getProductId());
        String gatewayId = productRef.getGatewayId();
        List<String> gatewayIps = cache.get(gatewayId, gatewayService::fetchGatewayIps);
        
        CredentialContext credentialContext = consumerService.getDefaultCredential(contextHolder.getUser());
        
        return InvokeModelParam.builder()
                .product(productResult)
                .requestHeaders(credentialContext.getHeaders())
                .queryParams(credentialContext.getQueryParams())
                .chatMessages(messages)
                .stream(param.getStream())
                .gatewayIps(gatewayIps)
                .build();
    }
    
    private List<ChatMessage> buildRewriteMessagesFromHistory(List<ChatMessage> chatMessages) {
        String historyString = formatHistory(chatMessages);
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        // 注意：prompt 格式中先是日期，后是聊天记录
        String rewrittenQuery = String.format(queryRewritePrompt, today, historyString);
        ChatMessage message = ChatMessage.builder()
                .role(ChatRole.USER.getRole())
                .content(rewrittenQuery)
                .build();
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(message);
        return messages;
    }
    
    private String formatHistory(List<ChatMessage> chatMessages) {
        if (chatMessages == null || chatMessages.isEmpty()) {
            return "";
        }
        return chatMessages.stream()
                .map(m -> {
                    return String.format("%s: %s", m.getRole(), m.getContent());
                })
                .collect(Collectors.joining("\n"));
    }
}
