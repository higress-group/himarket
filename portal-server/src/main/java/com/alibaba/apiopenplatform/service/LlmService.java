package com.alibaba.apiopenplatform.service;

import com.alibaba.apiopenplatform.dto.params.chat.InvokeModelParam;
import com.alibaba.apiopenplatform.dto.result.chat.ChatAnswerMessage;
import com.alibaba.apiopenplatform.dto.result.chat.LlmInvokeResult;
import com.alibaba.apiopenplatform.support.enums.AIProtocol;

import jakarta.servlet.http.HttpServletResponse;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

/**
 * @author zh
 */
public interface LlmService {

    /**
     * Chat with LLM
     *
     * @param param
     * @param response
     * @param resultHandler
     * @return
     */
    Flux<ChatAnswerMessage> invokeLLM(InvokeModelParam param, HttpServletResponse response, Consumer<LlmInvokeResult> resultHandler);


    /**
     * Supported protocol
     *
     * @return
     */
    AIProtocol getProtocol();
}
