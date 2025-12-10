package com.alibaba.himarket.core.exception;

import java.util.concurrent.TimeoutException;
import lombok.Getter;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * @author zh
 */
@Getter
public enum ChatError {
    WEB_RESPONSE_ERROR("Web response error"),

    TIMEOUT("Timeout"),

    UNKNOWN_ERROR("Unknown error"),

    LLM_ERROR("LLM error, please check the input"),
    ;

    private final String description;

    ChatError(String description) {
        this.description = description;
    }

    public static ChatError from(Throwable error) {
        if (error instanceof WebClientResponseException) {
            return WEB_RESPONSE_ERROR;
        }
        if (error instanceof TimeoutException) {
            return TIMEOUT;
        }
        return UNKNOWN_ERROR;
    }
}
