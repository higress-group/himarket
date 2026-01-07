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

package com.alibaba.himarket.service.impl;

import static com.alibaba.himarket.dto.result.chat.ChatAnswerMessage.MessageType;
import static com.alibaba.himarket.dto.result.chat.ChatAnswerMessage.MessageType.*;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.core.exception.ChatError;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.dto.params.chat.ChatContext;
import com.alibaba.himarket.dto.params.chat.InvokeModelParam;
import com.alibaba.himarket.dto.params.chat.McpToolMeta;
import com.alibaba.himarket.dto.params.chat.ToolContext;
import com.alibaba.himarket.dto.result.chat.ChatAnswerMessage;
import com.alibaba.himarket.dto.result.chat.LlmChatRequest;
import com.alibaba.himarket.dto.result.chat.LlmInvokeResult;
import com.alibaba.himarket.dto.result.consumer.CredentialContext;
import com.alibaba.himarket.dto.result.model.ModelConfigResult;
import com.alibaba.himarket.dto.result.product.ProductResult;
import com.alibaba.himarket.service.LlmService;
import com.alibaba.himarket.support.chat.ChatMessage;
import com.alibaba.himarket.support.chat.ChatUsage;
import com.alibaba.himarket.support.enums.ChatRole;
import com.alibaba.himarket.support.product.ModelFeature;
import com.alibaba.himarket.support.product.ProductFeature;
import com.google.common.base.Stopwatch;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest.WebSearchOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Flux;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractLlmService implements LlmService {

    protected final ToolCallingManager toolCallingManager;

    /** Maximum number of tool-calling rounds allowed in a single chat conversation */
    private static final int MAX_ROUNDS_PER_CHAT = 10;

    /**
     * Invoke LLM with the given parameters and stream the response.
     *
     * @param param
     * @param response
     * @param resultHandler
     * @return
     */
    @Override
    public Flux<ChatAnswerMessage> invokeLLM(
            InvokeModelParam param,
            HttpServletResponse response,
            Consumer<LlmInvokeResult> resultHandler) {
        // ResultHandler is mainly used to record answer and usage
        try {
            LlmChatRequest request = composeRequest(param);

            return call(request, resultHandler);
        } catch (Exception e) {
            log.error("Failed to invoke LLM, chatId: {}", param.getChatId(), e);
            response.setStatus(ErrorCode.INTERNAL_ERROR.getStatus().value());
            return Flux.just(
                    ChatAnswerMessage.ofError(
                            ErrorCode.INTERNAL_ERROR.getMessage(e.getMessage()),
                            "Failed to invoke LLM"));
        }
    }

    /**
     * Execute the chat request and handle the streaming response.
     *
     * @param request
     * @param resultHandler
     * @return
     */
    private Flux<ChatAnswerMessage> call(
            LlmChatRequest request, Consumer<LlmInvokeResult> resultHandler) {
        request.tryResolveDns();

        ChatContext chatContext = initChatContext(request);
        chatContext.start();

        Flux<ChatAnswerMessage> resp =
                chatContext
                        .getChatClient()
                        .prompt(new Prompt(chatContext.getMessages(), chatContext.getChatOptions()))
                        .options(chatContext.getChatOptions())
                        .stream()
                        .chatResponse()
                        .flatMap(
                                chatResponse -> {
                                    if (chatResponse.getResult() == null) {
                                        log.warn(
                                                "LLM returned null result, chatId: {}, request: {}",
                                                chatContext.getChatId(),
                                                JSONUtil.toJsonStr(request));

                                        //                        return Flux.just(
                                        //
                                        // newErrorMessage(chatContext.getChatId(),
                                        // ChatError.LLM_ERROR.name(),
                                        // ChatError.LLM_ERROR.getDescription()),
                                        //                                newChatAnswerMessage(null,
                                        // null, STOP,
                                        // chatContext)
                                        //                        );
                                        return Flux.empty();
                                    }

                                    if (chatResponse.hasToolCalls()) {
                                        /* Process LLM response that contains tool/function calls:
                                         * - Execute the requested tools
                                         * - Stream the results back to LLM
                                         * - Handle multiple tool calls in sequence
                                         */
                                        return streamToolCalls(
                                                chatContext, chatResponse, resultHandler);
                                    } else {
                                        /* Process direct text response from LLM:
                                         * - No tool execution needed
                                         * - Stream the plain text response
                                         */
                                        return streamAnswer(chatResponse, chatContext);
                                    }
                                })
                        .startWith(
                                newChatAnswerMessage(
                                        null, request.getUserQuestion(), USER, chatContext))
                        .doOnNext(chatAnswerMessage -> chatContext.recordFirstByteTimeout())
                        // Execute result handler after complete
                        .doOnComplete(() -> resultHandler.accept(LlmInvokeResult.of(chatContext)));

        return applyErrorHandling(resp, chatContext, resultHandler)
                .doFinally(
                        s -> {
                            chatContext.stop();
                            chatContext.close();
                        });
    }

    /**
     * Initialize chat context with MCP tools and chat client.
     *
     * @param request
     * @return
     */
    private ChatContext initChatContext(LlmChatRequest request) {
        Map<McpToolMeta, ToolCallback> toolsMap = new HashMap<>();
        List<McpClientWrapper> mcpClientWrappers = new ArrayList<>();

        // Build MCP client and tool configs
        ChatClient chatClient = newChatClient(request);

        Optional.ofNullable(request.getMcpConfigs()).stream()
                .flatMap(Collection::stream)
                .forEach(
                        mcpConfig -> {
                            McpClientWrapper holder =
                                    McpClientFactory.newClient(
                                            mcpConfig, request.getCredentialContext());
                            if (holder != null) {
                                mcpClientWrappers.add(holder);

                                List<McpSchema.Tool> tools = holder.listTools();
                                if (tools != null) {
                                    tools.forEach(
                                            tool -> {
                                                // Tools meta
                                                McpToolMeta toolMeta =
                                                        McpToolMeta.builder()
                                                                .mcpName(
                                                                        mcpConfig
                                                                                .getMcpServerName())
                                                                .mcpNameCn(
                                                                        mcpConfig
                                                                                .getMcpServerName())
                                                                .toolName(tool.name())
                                                                .toolNameCn(tool.title())
                                                                .build();
                                                toolsMap.put(
                                                        toolMeta,
                                                        new SyncMcpToolCallback(
                                                                holder.getMcpSyncClient(), tool));
                                            });
                                }
                            }
                        });

        ToolContext toolContext = ToolContext.of(toolsMap);

        // ChatOptions for tool calling
        ChatOptions chatOptions =
                Optional.of(request.getModelFeature())
                        .map(
                                feature ->
                                        OpenAiChatOptions.builder()
                                                .temperature(feature.getTemperature())
                                                .maxTokens(feature.getMaxTokens())
                                                .model(feature.getModel())
                                                .internalToolExecutionEnabled(false)
                                                .webSearchOptions(request.getWebSearchOptions())
                                                .toolCallbacks(
                                                        Optional.ofNullable(
                                                                        toolContext
                                                                                .getToolCallbacks())
                                                                .filter(CollUtil::isNotEmpty)
                                                                .orElseGet(ArrayList::new))
                                                .build())
                        .get();

        List<Message> messages = transformMessages(request.getChatMessages());

        return ChatContext.builder()
                .chatId(request.getChatId())
                .messages(messages)
                .chatClient(chatClient)
                .chatOptions(chatOptions)
                .toolContext(toolContext)
                .mcpClientWrappers(mcpClientWrappers)
                .build();
    }

    /**
     * Compose LLM chat request from invoke parameters.
     *
     * @param param
     * @return
     */
    private LlmChatRequest composeRequest(InvokeModelParam param) {
        // Not be null
        ProductResult product = param.getProduct();
        ModelConfigResult modelConfig = product.getModelConfig();
        // Get request URL (with query params)
        CredentialContext credentialContext = param.getCredentialContext();

        URI uri = getUri(modelConfig, credentialContext.copyQueryParams(), param.getGatewayUris());
        Map<String, String> headers = Optional.ofNullable(getMatchHeader(modelConfig)).orElse(new HashMap<>());

        // Model feature
        ModelFeature modelFeature = getOrDefaultModelFeature(product);

        // Web search config
        WebSearchOptions webSearchOptions =
                (BooleanUtil.isTrue(modelFeature.getWebSearch())
                                && BooleanUtil.isTrue(param.getEnableWebSearch()))
                        ? new WebSearchOptions(WebSearchOptions.SearchContextSize.MEDIUM, null)
                        : null;

        // add additional header match
        headers.putAll(credentialContext.copyHeaders());

        return LlmChatRequest.builder()
                .chatId(param.getChatId())
                .userQuestion(param.getUserQuestion())
                .uri(uri)
                .chatMessages(param.getChatMessages())
                .headers(headers)
                .gatewayUris(param.getGatewayUris())
                .credentialContext(param.getCredentialContext())
                .mcpConfigs(param.getMcpConfigs())
                .modelFeature(modelFeature)
                .webSearchOptions(webSearchOptions)
                .build();
    }

    /**
     * Get model feature from product or return default values.
     *
     * @param product
     * @return
     */
    private ModelFeature getOrDefaultModelFeature(ProductResult product) {
        ModelFeature modelFeature =
                Optional.ofNullable(product)
                        .map(ProductResult::getFeature)
                        .map(ProductFeature::getModelFeature)
                        .orElseGet(() -> ModelFeature.builder().build());

        // Get model feature from product or return default values if any field is null/blank
        // Default values: model="qwen-max", maxTokens=5000, temperature=0.9, streaming=true,
        // webSearch=false
        return ModelFeature.builder()
                .model(StrUtil.blankToDefault(modelFeature.getModel(), "qwen-max"))
                .maxTokens(ObjectUtil.defaultIfNull(modelFeature.getMaxTokens(), 5000))
                .temperature(ObjectUtil.defaultIfNull(modelFeature.getTemperature(), 0.9))
                .streaming(ObjectUtil.defaultIfNull(modelFeature.getStreaming(), true))
                .webSearch(ObjectUtil.defaultIfNull(modelFeature.getWebSearch(), false))
                .build();
    }

    /**
     * Process and stream tool calls from LLM response.
     *
     * @param chatContext
     * @param chatResponse
     * @param resultHandler
     * @return
     */
    private Flux<ChatAnswerMessage> streamToolCalls(
            ChatContext chatContext,
            ChatResponse chatResponse,
            Consumer<LlmInvokeResult> resultHandler) {
        Usage usage = chatResponse.getMetadata().getUsage();

        return Flux.fromIterable(chatResponse.getResults())
                .flatMap(
                        generation -> {
                            AssistantMessage assistantMessage = generation.getOutput();

                            if (!assistantMessage.hasToolCalls()) {
                                chatContext.getMessages().add(assistantMessage);
                                log.warn(
                                        "Unexpected: chatResponse.hasToolCalls is true but"
                                                + " AssistantMessage has no toolCalls");
                                return Flux.just(
                                        newChatAnswerMessage(
                                                usage,
                                                assistantMessage.getText(),
                                                StrUtil.equalsIgnoreCase(
                                                                "STOP",
                                                                generation
                                                                        .getMetadata()
                                                                        .getFinishReason())
                                                        ? STOP
                                                        : ANSWER,
                                                chatContext));
                            }

                            /*
                             * Handle streaming tool calls fragmentation issue with Higress models:
                             *
                             * Problem: Higress models may split a single tool call into fragments during streaming:
                             *   Fragment 1: ToolCall(id="123", args="{\"lat\":")
                             *   Fragment 2: ToolCall(id="123", args=" 39.9}")
                             *
                             * Solution: Group by id, merge fragments, reconstruct complete tool calls while preserving single ones
                             */
                            List<AssistantMessage.ToolCall> toolCalls =
                                    assistantMessage.getToolCalls();
                            Map<String, List<AssistantMessage.ToolCall>> tooCallGroups =
                                    toolCalls.stream()
                                            .collect(
                                                    Collectors.groupingBy(
                                                            AssistantMessage.ToolCall::id));

                            List<AssistantMessage.ToolCall> mergedToolCalls = new ArrayList<>();
                            boolean hasFragment = false;

                            for (var entry : tooCallGroups.entrySet()) {
                                List<AssistantMessage.ToolCall> group = entry.getValue();

                                if (group.size() == 1) {
                                    mergedToolCalls.add(group.get(0));
                                    continue;
                                }

                                // Merge fragmented tool calls
                                hasFragment = true;
                                AssistantMessage.ToolCall firstCall = group.get(0);
                                String accumulatedArgs =
                                        group.stream()
                                                .map(AssistantMessage.ToolCall::arguments)
                                                .collect(Collectors.joining(""));

                                mergedToolCalls.add(
                                        new AssistantMessage.ToolCall(
                                                firstCall.id(),
                                                firstCall.type(),
                                                firstCall.name(),
                                                accumulatedArgs));
                                log.info(
                                        "Merged {} fragments for tool: {}",
                                        group.size(),
                                        firstCall.name());
                            }

                            ChatResponse responseToExecute;
                            AssistantMessage messageToAdd;

                            if (hasFragment) {
                                // Reconstructed message
                                AssistantMessage mergedMessage =
                                        AssistantMessage.builder()
                                                .content(assistantMessage.getText())
                                                .media(assistantMessage.getMedia())
                                                .properties(assistantMessage.getMetadata())
                                                .toolCalls(mergedToolCalls)
                                                .build();

                                responseToExecute =
                                        new ChatResponse(
                                                List.of(
                                                        new Generation(
                                                                mergedMessage,
                                                                generation.getMetadata())),
                                                chatResponse.getMetadata());
                                messageToAdd = mergedMessage;
                            } else {
                                // Use original response
                                responseToExecute = chatResponse;
                                messageToAdd = assistantMessage;
                            }

                            chatContext.getMessages().add(messageToAdd);

                            Flux<ChatAnswerMessage> toolProcess =
                                    Flux.concat(
                                            buildToolCallMessages(
                                                    mergedToolCalls, usage, chatContext),
                                            executeToolCalls(
                                                    usage, chatContext, responseToExecute));

                            // Check if exceeded max request count
                            if (chatContext.nextRound() > MAX_ROUNDS_PER_CHAT) {
                                return Flux.concat(
                                        toolProcess,
                                        Flux.just(
                                                newChatAnswerMessage(
                                                        usage, null, STOP, chatContext)));
                            }

                            // Continue with next call
                            return Flux.concat(
                                    toolProcess,
                                    applyErrorHandling(
                                            continueNextCall(chatContext, resultHandler),
                                            chatContext,
                                            resultHandler));
                        });
    }

    /**
     * Stream direct text answer from LLM response.
     *
     * @param chatResponse
     * @param chatContext
     * @return
     */
    private Flux<ChatAnswerMessage> streamAnswer(
            ChatResponse chatResponse, ChatContext chatContext) {
        Usage usage = chatResponse.getMetadata().getUsage();
        return Flux.fromIterable(chatResponse.getResults())
                .map(
                        generation -> {
                            AssistantMessage assistantMessage = generation.getOutput();
                            chatContext.getMessages().add(assistantMessage);

                            return newChatAnswerMessage(
                                    usage,
                                    assistantMessage.getText(),
                                    StrUtil.equalsIgnoreCase(
                                                    "STOP",
                                                    generation.getMetadata().getFinishReason())
                                            ? STOP
                                            : ANSWER,
                                    chatContext);
                        });
    }

    /**
     * Continue with the next LLM call after tool execution.
     *
     * @param chatContext
     * @param resultHandler
     * @return
     */
    private Flux<ChatAnswerMessage> continueNextCall(
            ChatContext chatContext, Consumer<LlmInvokeResult> resultHandler) {

        ChatOptions chatOptions = chatContext.getChatOptions();
        return Flux.defer(
                () ->
                        chatContext
                                .getChatClient()
                                .prompt(new Prompt(chatContext.getMessages(), chatOptions))
                                .options(chatOptions)
                                .toolCallbacks(chatContext.getToolContext().getToolCallbacks())
                                .stream()
                                .chatResponse()
                                .flatMap(
                                        nextChatResponse -> {
                                            if (nextChatResponse.getResult() == null) {
                                                log.warn(
                                                        "Unexpected: chatResponse.generation is"
                                                                + " null");
                                                return Flux.empty();
                                            }

                                            // If the message has tool calls
                                            return nextChatResponse.hasToolCalls()
                                                    ? streamToolCalls(
                                                            chatContext,
                                                            nextChatResponse,
                                                            resultHandler)
                                                    : streamAnswer(nextChatResponse, chatContext);
                                        }));
    }

    /**
     * Apply error handling and logging to the chat response stream.
     *
     * @param flux
     * @param chatContext
     * @param resultHandler
     * @return
     */
    private Flux<ChatAnswerMessage> applyErrorHandling(
            Flux<ChatAnswerMessage> flux,
            ChatContext chatContext,
            Consumer<LlmInvokeResult> resultHandler) {
        String chatId = chatContext.getChatId();

        return flux.doOnCancel(
                        () -> {
                            log.warn("Chat was canceled, chatId: {}", chatId);
                            resultHandler.accept(LlmInvokeResult.of(chatContext));
                        })
                .doOnError(
                        e -> {
                            log.error("Chat encountered error, chatId: {}", chatId, e);
                            // Append error message to answer
                            chatContext.appendAnswer(e.getMessage());
                            chatContext.setSuccess(false);
                            resultHandler.accept(LlmInvokeResult.of(chatContext));
                        })
                .onErrorResume(
                        error -> {
                            ChatError chatError = ChatError.from(error);
                            log.error(
                                    "Chat execution failed, chatId: {}, error type: {}",
                                    chatId,
                                    chatError,
                                    error);

                            return Flux.just(
                                    newErrorMessage(chatId, chatError.name(), error.getMessage()),
                                    newChatAnswerMessage(null, null, STOP, chatContext));
                        });
    }

    /**
     * Build tool call messages from assistant's tool call requests.
     *
     * @param toolCalls
     * @param usage
     * @param chatContext
     * @return
     */
    private Flux<ChatAnswerMessage> buildToolCallMessages(
            List<AssistantMessage.ToolCall> toolCalls, Usage usage, ChatContext chatContext) {
        return Flux.fromIterable(toolCalls)
                .map(
                        toolCall -> {
                            String toolName = toolCall.name();
                            ToolContext toolContext = chatContext.getToolContext();

                            ChatAnswerMessage.ToolCall tc =
                                    ChatAnswerMessage.ToolCall.builder()
                                            .id(toolCall.id())
                                            .name(toolName)
                                            .type(toolCall.type())
                                            // Tool arguments
                                            .arguments(toolCall.arguments())
                                            .input(toolCall.arguments())
                                            // Tool schema
                                            .inputSchema(
                                                    Optional.ofNullable(
                                                                    toolContext.getToolDefinition(
                                                                            toolName))
                                                            .map(ToolDefinition::inputSchema)
                                                            .orElse(null))
                                            .toolMeta(toolContext.getToolMeta(toolName))
                                            .build();

                            return newChatAnswerMessage(usage, tc, TOOL_CALL, chatContext);
                        });
    }

    /**
     * Execute tool calls and return the results as messages.
     *
     * @param usage
     * @param chatContext
     * @param chatResponse
     * @return
     */
    private Flux<ChatAnswerMessage> executeToolCalls(
            Usage usage, ChatContext chatContext, ChatResponse chatResponse) {
        return Flux.defer(
                () -> {
                    Stopwatch stopwatch = Stopwatch.createUnstarted();
                    stopwatch.start();

                    try {
                        // Execute tool calls
                        ToolExecutionResult result =
                                toolCallingManager.executeToolCalls(
                                        new Prompt(
                                                chatContext.getMessages(),
                                                chatContext.getChatOptions()),
                                        chatResponse);

                        // Append tool calls to messages
                        Message lastMessage =
                                result.conversationHistory()
                                        .get(result.conversationHistory().size() - 1);
                        chatContext.getMessages().add(lastMessage);

                        // Build tool call messages
                        if (lastMessage instanceof ToolResponseMessage toolResponseMessage) {
                            long costMillis = stopwatch.elapsed().getSeconds();
                            log.info("Tool execution completed in {} ms", costMillis);

                            return Flux.fromIterable(toolResponseMessage.getResponses())
                                    .map(
                                            response ->
                                                    newChatAnswerMessage(
                                                            usage,
                                                            ChatAnswerMessage.ToolResponse.builder()
                                                                    .id(response.id())
                                                                    .name(response.name())
                                                                    .responseData(
                                                                            response.responseData())
                                                                    .output(response.responseData())
                                                                    .costMillis(costMillis)
                                                                    .toolMeta(
                                                                            chatContext
                                                                                    .getToolContext()
                                                                                    .getToolMeta(
                                                                                            response
                                                                                                    .name()))
                                                                    .build(),
                                                            TOOL_RESPONSE,
                                                            chatContext));
                        }

                        return Flux.empty();
                    } catch (Throwable t) {
                        log.error(
                                "Tool execution failed after {} ms",
                                stopwatch.elapsed().toMillis(),
                                t);
                        return Flux.error(t);
                    } finally {
                        stopwatch.stop();
                    }
                });
    }

    /**
     * Create a new chat answer message with usage information.
     *
     * @param usage
     * @param content
     * @param messageType
     * @param chatContext
     * @return
     */
    private ChatAnswerMessage newChatAnswerMessage(
            Usage usage, Object content, MessageType messageType, ChatContext chatContext) {
        // Append to answer content
        if (messageType == ANSWER && content instanceof String strContent) {
            chatContext.appendAnswer(strContent);
        }

        ChatUsage chatUsage =
                (usage != null && !(usage instanceof EmptyUsage))
                        ? ChatUsage.builder()
                                .firstByteTimeout(chatContext.getFirstByteTimeout())
                                .promptTokens(usage.getPromptTokens())
                                .completionTokens(usage.getCompletionTokens())
                                .totalTokens(usage.getTotalTokens())
                                .build()
                        : null;

        if (messageType == STOP) {
            chatContext.setChatUsage(chatUsage);
            chatContext.stop();
        }

        return ChatAnswerMessage.builder()
                .chatId(chatContext.getChatId())
                .content(content)
                .chatUsage(chatUsage)
                .msgType(messageType)
                .build();
    }

    /**
     * Create a new error message.
     *
     * @param chatId
     * @param error
     * @param message
     * @return
     */
    private ChatAnswerMessage newErrorMessage(String chatId, String error, String message) {
        return ChatAnswerMessage.builder()
                .chatId(chatId)
                .error(error)
                .message(message)
                .msgType(ERROR)
                .build();
    }

    /**
     * Transform OpenAI-compatible ChatMessage objects to SDK-compatible Message objects.
     *
     * @param messages
     * @return
     */
    private List<Message> transformMessages(List<ChatMessage> messages) {
        List<Message> contextMessages = new ArrayList<>();
        messages.forEach(
                chatMessage -> {
                    String role = chatMessage.getRole();
                    ChatRole chatRole = ChatRole.of(role);
                    switch (chatRole) {
                        case USER:
                            contextMessages.add(
                                    new UserMessage(chatMessage.getContent().toString()));
                            break;
                        case SYSTEM:
                            contextMessages.add(
                                    new SystemMessage(chatMessage.getContent().toString()));
                            break;
                        case ASSISTANT:
                            contextMessages.add(
                                    new AssistantMessage(chatMessage.getContent().toString()));
                            break;
                        default:
                            break;
                    }
                });
        return contextMessages;
    }

    /**
     * Constructs a URI based on the model configuration and query parameters.
     *
     * @param modelConfig
     * @param queryParams
     * @param gatewayUris
     * @return
     */
    protected abstract URI getUri(
            ModelConfigResult modelConfig, Map<String, String> queryParams, List<URI> gatewayUris);

    /**
     * Get route match headers.
     *
     * @param modelConfig
     * @return
     */
    protected abstract Map<String, String> getMatchHeader(ModelConfigResult modelConfig);

    /**
     * Builds a ChatClient instance according to the specified protocol (e.g., OpenAI, etc.) based
     * on the given LlmChatRequest.
     *
     * @param request
     * @return
     */
    protected abstract ChatClient newChatClient(LlmChatRequest request);
}
