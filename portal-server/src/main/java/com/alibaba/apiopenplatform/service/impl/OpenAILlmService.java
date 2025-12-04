package com.alibaba.apiopenplatform.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.apiopenplatform.core.exception.ChatError;
import com.alibaba.apiopenplatform.core.security.ContextHolder;
import com.alibaba.apiopenplatform.dto.params.chat.ChatContext;
import com.alibaba.apiopenplatform.dto.params.chat.ChatRequestBody;
import com.alibaba.apiopenplatform.dto.params.chat.McpToolMeta;
import com.alibaba.apiopenplatform.dto.params.chat.ToolContext;
import com.alibaba.apiopenplatform.dto.result.chat.ChatAnswerMessage;
import com.alibaba.apiopenplatform.dto.result.chat.LlmChatRequest;
import com.alibaba.apiopenplatform.dto.result.chat.LlmInvokeResult;
import com.alibaba.apiopenplatform.dto.result.consumer.CredentialContext;
import com.alibaba.apiopenplatform.service.ConsumerService;
import com.alibaba.apiopenplatform.support.chat.ChatMessage;
import com.alibaba.apiopenplatform.support.chat.ChatUsage;
import com.alibaba.apiopenplatform.support.chat.mcp.McpServerConfig;
import com.alibaba.apiopenplatform.support.enums.AIProtocol;
import com.alibaba.apiopenplatform.support.enums.ChatRole;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static com.alibaba.apiopenplatform.dto.result.chat.ChatAnswerMessage.MessageType.*;

@Service
@Slf4j
public class OpenAILlmService extends AbstractLlmService {

    @Value("${spring.ai.openai.api-key}")
    private String defaultApiKey;

    @Resource
    private ToolCallingManager toolCallingManager;
    @Resource
    private McpClientFactory   mcpClientFactory;

    @Resource
    private ConsumerService consumerService;

    @Resource
    private ContextHolder contextHolder;



    private final WebClient.Builder webClientBuilder = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024));


    private List<Message> convertMessages(ChatRequestBody chatRequestBody) {
        List<ChatMessage> messages = chatRequestBody.getMessages();
        List<Message> contextMessages = new ArrayList<>();
        messages.forEach(chatMessage -> {
            String role = chatMessage.getRole();
            ChatRole chatRole = ChatRole.of(role);
            switch (chatRole) {
                case USER:
                    contextMessages.add(new UserMessage(chatMessage.getContent().toString()));
                    break;
                case SYSTEM:
                    contextMessages.add(new SystemMessage(chatMessage.getContent().toString()));
                    break;
                case ASSISTANT:
                    contextMessages.add(new AssistantMessage(chatMessage.getContent().toString()));
                    break;
                default:
                    break;
            }
        });
        return contextMessages;
    }


    private void cleanContext(ChatContext chatContext) {
        chatContext.getMcpClientHolders().forEach(h -> {
            try {
                h.close();
            } catch (Exception e) {
                log.error("close mcp client error", e);
            }
        });
    }

    private ChatClient newChatClient(LlmChatRequest request) {
        MultiValueMap<String, String> headers = new HttpHeaders();
        Optional.ofNullable(request.getHeaders()).ifPresent(headerMap -> {
            headerMap.forEach(headers::add);
        });
        URL url = request.getUrl();
        String baseUrl = String.format("%s://%s", url.getProtocol(), url.getHost());
        if (url.getPort() > 0) {
            baseUrl += ":" + url.getPort();
        }
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .completionsPath(url.getPath())
                .headers(headers)
                .apiKey(request.getCredentialContext() != null ? request.getCredentialContext().getApiKey() : defaultApiKey)
                .webClientBuilder(webClientBuilder)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .toolCallingManager(toolCallingManager)
                .defaultOptions(OpenAiChatOptions.builder()
                        .streamUsage(true)
                        .build())
                .build();

        return ChatClient.builder(chatModel).build();
    }


    private ChatContext initChatContext(LlmChatRequest request) {
        ChatRequestBody chatRequestBody = request.getChatRequest();
        List<McpServerConfig> mcpServerConfigs = chatRequestBody.getMcpServerConfigs();
        Map<McpToolMeta, ToolCallback> toolsMap = new HashMap<>();
        List<McpClientHolder> mcpClientHolders = new ArrayList<>();
        if (CollUtil.isNotEmpty(mcpServerConfigs)) {
            mcpServerConfigs.forEach(mcpServerConfig -> {
                mcpServerConfig.getMcpServers().forEach((serverName, config) -> {
                    // Get authentication info (use applicationContext to get bean to avoid circular dependency)
                    CredentialContext credentialContext = consumerService.getDefaultCredential(contextHolder.getUser());

                    McpClientHolder mcpClientHolder = mcpClientFactory.initClient(config.getType(), config.getUrl(), credentialContext.getHeaders(), credentialContext.getQueryParams());
                    if (mcpClientHolder == null) {
                        return;
                    }
                    mcpClientHolders.add(mcpClientHolder);
                    List<McpSchema.Tool> tools = mcpClientHolder.listTools();
                    Optional.ofNullable(tools).orElse(Collections.emptyList()).forEach(tool -> {
                        McpToolMeta mcpToolMeta = new McpToolMeta();
                        mcpToolMeta.setToolName(tool.name());
                        mcpToolMeta.setToolNameCn(tool.title());
                        mcpToolMeta.setMcpName(serverName);
                        mcpToolMeta.setMcpNameCn(serverName);
                        toolsMap.put(mcpToolMeta, new SyncMcpToolCallback(mcpClientHolder.getMcpSyncClient(), tool));
                    });
                });
            });
        }
        OpenAiChatOptions.Builder chatOptionsBuilder = OpenAiChatOptions.builder()
                .temperature(chatRequestBody.getTemperature())
                .maxTokens(chatRequestBody.getMaxTokens())
                .topP(chatRequestBody.getTopP())
                .model(chatRequestBody.getModel())
                .webSearchOptions(chatRequestBody.getWebSearchOptions())
                .internalToolExecutionEnabled(false);
        ToolContext toolContext = ToolContext.of(toolsMap);

        if (CollectionUtils.isNotEmpty(toolContext.getToolCallbacks())) {
            chatOptionsBuilder.toolCallbacks(toolContext.getToolCallbacks());
        }
        ChatOptions chatOptions = chatOptionsBuilder.build();
        return ChatContext.builder()
                .chatId(request.getChatId())
                .chatOptions(chatOptions)
                .toolContext(toolContext)
                .mcpClientHolders(mcpClientHolders)
                .build();
    }

    @Override
    protected Flux<ChatAnswerMessage> call(LlmChatRequest request, HttpServletResponse response, Consumer<LlmInvokeResult> resultHandler) {
        log.debug("request: {}", JSONUtil.toJsonStr(request));
        request.tryResolveDns();

        ChatRequestBody chatRequestBody = request.getChatRequest();
        List<Message> messages = convertMessages(chatRequestBody);
        AtomicInteger modelRequestCount = new AtomicInteger(1);

        ChatClient chatClient = newChatClient(request);

        ChatContext chatContext = initChatContext(request);
        chatContext.setChatClient(chatClient);

        chatContext.start();

        ChatClient.ChatClientRequestSpec clientRequestSpec = chatClient.prompt(new Prompt(messages, chatContext.getChatOptions()))
                .options(chatContext.getChatOptions());
        Flux<ChatAnswerMessage> fluxResponse = clientRequestSpec.stream()
                .chatResponse()
                .flatMap(chatResponse -> {
                    if (chatResponse.getResult() == null) {
                        return Flux.empty();
                    }

                    if (chatResponse.hasToolCalls()) {
                        // 处理工具调用的情况
                        return handleToolCallsInStream(chatContext, chatResponse, messages, modelRequestCount, resultHandler);
                    } else {
                        // 没有工具调用，直接返回响应
                        return handleAnswerInStream(chatResponse, messages, chatContext);
                    }
                })
                .startWith(newChatAnswerMessage(null, chatRequestBody.getUserQuestion(), USER, chatContext))
                .doOnNext(chatAnswerMessage -> {
                    chatContext.recordFirstByteTimeout();
                    log.debug("chatId={} chatAnswerMessage: {}", request.getChatId(), JSONUtil.toJsonStr(chatAnswerMessage));
                })
                .doOnComplete(() -> {
                    resultHandler.accept(LlmInvokeResult.of(chatContext));
                });
        return applyErrorHandling(fluxResponse, chatContext, resultHandler)
                .doFinally(s -> {
                    cleanContext(chatContext);
                    chatContext.stop();
                });
    }

    private Flux<ChatAnswerMessage> handleToolCallsInStream(ChatContext chatContext, ChatResponse chatResponse, List<Message> messages,
                                                            AtomicInteger modelRequestCount, Consumer<LlmInvokeResult> resultHandler) {
        Usage usage = chatResponse.getMetadata().getUsage();
        return Flux.fromIterable(chatResponse.getResults())
                .flatMap(generation -> {
                    AssistantMessage assistantMessage = generation.getOutput();
                    messages.add(assistantMessage);
                    if (assistantMessage.hasToolCalls()) {
                        List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
                        Flux<ChatAnswerMessage> calls = buildToolCallMessages(toolCalls, usage, chatContext);

                        // 工具调用
                        Flux<ChatAnswerMessage> responses = executeToolCalls(usage, messages, chatContext, chatResponse);
                        // 限制模型调用次数，防止无限循环
                        if (modelRequestCount.incrementAndGet() > MAX_MODEL_REQUEST_PER_CHAT) {
                            return Flux.concat(
                                    calls,
                                    responses,
                                    // 强制返回一个STOP
                                    Flux.just(newChatAnswerMessage(usage, null, STOP, chatContext))
                            );
                        }

                        // 下一轮对话
                        Flux<ChatAnswerMessage> nextAnswers = initiateNextCall(messages, chatContext, modelRequestCount, resultHandler);
                        return Flux.concat(
                                calls,
                                responses,
                                applyErrorHandling(nextAnswers, chatContext, resultHandler)
                        );
                    } else {
                        log.warn("chatResponse.hasToolCalls == true, but also exists AssistantMessage that has no toolCalls");
                        return Flux.just(buildChatAnswerMessageVO(usage, generation, chatContext));
                    }
                });
    }

    private Flux<ChatAnswerMessage> initiateNextCall(List<Message> messages, ChatContext chatContext, AtomicInteger modelRequestCount, Consumer<LlmInvokeResult> resultHandler) {
        ChatOptions chatOptions = chatContext.getChatOptions();
        return Flux.defer(() -> chatContext.getChatClient().prompt(new Prompt(messages, chatOptions))
                .options(chatOptions)
                .toolCallbacks(chatContext.getToolContext().getToolCallbacks())
                .stream()
                .chatResponse()
                .flatMap(nextChatResponse -> {
                    if (nextChatResponse.getResult() == null) {
                        log.warn("chatResponse.generation is null, which is unexpected");
                        return Flux.empty();
                    }
                    if (nextChatResponse.hasToolCalls()) {
                        return handleToolCallsInStream(chatContext, nextChatResponse, messages, modelRequestCount, resultHandler);
                    }
                    return handleAnswerInStream(nextChatResponse, messages, chatContext);
                })
        );
    }

    private Flux<ChatAnswerMessage> applyErrorHandling(Flux<ChatAnswerMessage> flux, ChatContext chatContext,
                                                       Consumer<LlmInvokeResult> resultHandler) {
        String chatId = chatContext.getChatId();

        return flux
                .doOnCancel(() -> {
                    log.warn("Chat [{}] was canceled", chatId);
                    resultHandler.accept(LlmInvokeResult.of(chatContext));
                })
                .doOnError(e -> {
                    log.error("Chat [{}] encountered error: {}", chatId, e.getMessage(), e);
                    // Append error message to answer
                    chatContext.appendAnswer(e.getMessage());
                    chatContext.setSuccess(false);
                    resultHandler.accept(LlmInvokeResult.of(chatContext));
                })
                .onErrorResume(error -> {
                    ChatError chatError = ChatError.from(error);
                    log.error("Chat [{}] failed with {}: {}", chatId, chatError, error.getMessage(), error);

                    return Flux.just(
                            ChatAnswerMessage.builder()
                                    .chatId(chatId)
                                    .error(chatError.name())
                                    .message(error.getMessage())
                                    .msgType(ERROR)
                                    .build(),
                            newChatAnswerMessage(null, null, STOP, chatContext)
                    );
                });
    }

    private Flux<ChatAnswerMessage> buildToolCallMessages(List<AssistantMessage.ToolCall> toolCalls, Usage usage, ChatContext chatContext) {
        List<ChatAnswerMessage> callMessages = toolCalls.stream().map(toolCall -> {
            ChatAnswerMessage.ToolCall tc = this.constructToolCall(toolCall, chatContext.getToolContext());
            return newChatAnswerMessage(usage, tc, TOOL_CALL, chatContext);
        }).toList();
        return Flux.fromIterable(callMessages);
    }

    private Flux<ChatAnswerMessage> executeToolCalls(Usage usage, List<Message> messages, ChatContext chatContext, ChatResponse chatResponse) {
        return Flux.defer(() -> {
            // 工具调用
            ToolExecutionResult toolExecutionResult = null;
            long startTime = System.currentTimeMillis();
            AtomicLong costMillis = new AtomicLong(0);
            try {
                toolExecutionResult = toolCallingManager.executeToolCalls(new Prompt(messages, chatContext.getChatOptions()), chatResponse);
            } catch (Throwable t) {
                log.error("toolCallingManager.executeToolCalls has error", t);
                return Flux.error(t);
            } finally {
                costMillis.set(System.currentTimeMillis() - startTime);
                log.info("toolCallingManager.executeToolCalls cost: {}ms", costMillis.get());
            }
            List<Message> conversationHistory = toolExecutionResult.conversationHistory();
            Message lastMessage = conversationHistory.get(conversationHistory.size() - 1);

            // 工具调用结果需要加到上下文中
            messages.add(lastMessage);
            List<ChatAnswerMessage> toolResponseMessages = new ArrayList<>();
            if (lastMessage instanceof ToolResponseMessage toolResponseMessage) {
                List<ToolResponseMessage.ToolResponse> toolResponses = toolResponseMessage.getResponses();
                toolResponses.forEach(toolResponse -> {
                    ChatAnswerMessage.ToolResponse tr = this.constructToolResponse(toolResponse, chatContext.getToolContext(), costMillis.get());
                    toolResponseMessages.add(newChatAnswerMessage(usage, tr, TOOL_RESPONSE, chatContext));
                });
            }
            return Flux.fromIterable(toolResponseMessages);
        });
    }

    protected ChatAnswerMessage.ToolResponse constructToolResponse(ToolResponseMessage.ToolResponse toolResponse, ToolContext toolContext, long costMillis) {
        ChatAnswerMessage.ToolResponse tr = new ChatAnswerMessage.ToolResponse();
        tr.setId(toolResponse.id());
        tr.setName(toolResponse.name());
        tr.setResponseData(toolResponse.responseData());
        tr.setOutput(toolResponse.responseData());
        tr.setCostMillis(costMillis);
        String innerToolName = toolResponse.name();
        Optional.ofNullable(toolContext.getToolMeta(innerToolName))
                .ifPresent(tr::setToolMeta);
        return tr;
    }

    private ChatAnswerMessage newErrorMessage(String chatId, String error, String message) {
        return ChatAnswerMessage.builder()
                .chatId(chatId)
                .error(error)
                .message(message)
                .msgType(ERROR)
                .build();
    }

    protected ChatAnswerMessage newChatAnswerMessage(Usage usage, Object content, ChatAnswerMessage.MessageType messageType, ChatContext chatContext) {
        // Append to answer content and reset current content
        if (messageType == ANSWER && content instanceof String strContent) {
            chatContext.getAnswerContent().append(strContent);
        }

        ChatUsage chatUsage = (usage != null && !(usage instanceof EmptyUsage)) ?
                ChatUsage.builder()
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

    private ChatAnswerMessage.ToolCall constructToolCall(AssistantMessage.ToolCall toolCall, ToolContext toolContext) {
        ChatAnswerMessage.ToolCall toolCallVO = new ChatAnswerMessage.ToolCall();
        toolCallVO.setId(toolCall.id());
        toolCallVO.setName(toolCall.name());
        toolCallVO.setType(toolCall.type());
        toolCallVO.setArguments(toolCall.arguments());
        toolCallVO.setInput(toolCall.arguments());

        String innerToolName = toolCall.name();
        Optional.ofNullable(toolContext.getToolDefinition(innerToolName))
                .ifPresent(toolDefinition -> {
                    toolCallVO.setInputSchema(toolDefinition.inputSchema());
                });
        Optional.ofNullable(toolContext.getToolMeta(innerToolName))
                .ifPresent(toolCallVO::setToolMeta);
        return toolCallVO;
    }

    protected ChatAnswerMessage buildChatAnswerMessageVO(Usage usage, Generation generation, ChatContext chatContent) {
        AssistantMessage message = generation.getOutput();
        ChatGenerationMetadata metadata = generation.getMetadata();
        boolean isStop = StringUtils.equalsIgnoreCase("STOP", metadata.getFinishReason());
        ChatAnswerMessage.MessageType messageType = isStop ? STOP : ANSWER;
        return newChatAnswerMessage(usage, message.getText(), messageType, chatContent);
    }

    private Flux<ChatAnswerMessage> handleAnswerInStream(ChatResponse chatResponse, List<Message> messages, ChatContext chatContext) {
        Usage usage = chatResponse.getMetadata().getUsage();
        return Flux.fromIterable(chatResponse.getResults()).map(generation -> {
            AssistantMessage assistantMessage = generation.getOutput();
            messages.add(assistantMessage);
            ChatGenerationMetadata metadata = generation.getMetadata();
            boolean isStop = StringUtils.equalsIgnoreCase("STOP", metadata.getFinishReason());
            ChatAnswerMessage.MessageType messageType = isStop ? STOP : ANSWER;
            return newChatAnswerMessage(usage, assistantMessage.getText(), messageType, chatContext);
        });
    }

    @Override
    public AIProtocol getProtocol() {
        return AIProtocol.OPENAI;
    }
}
