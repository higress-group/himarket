package com.alibaba.himarket.service;

import com.alibaba.himarket.dto.params.chat.InvokeModelParam;
import com.alibaba.himarket.dto.result.chat.ChatAnswerMessage;
import com.alibaba.himarket.dto.result.chat.LlmInvokeResult;
import com.alibaba.himarket.support.enums.AIProtocol;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Consumer;
import reactor.core.publisher.Flux;

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
    Flux<ChatAnswerMessage> invokeLLM(
            InvokeModelParam param,
            HttpServletResponse response,
            Consumer<LlmInvokeResult> resultHandler);

    /**
     * Supported protocol
     *
     * @return
     */
    AIProtocol getProtocol();
}
