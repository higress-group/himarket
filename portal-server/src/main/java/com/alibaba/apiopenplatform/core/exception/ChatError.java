package com.alibaba.apiopenplatform.core.exception;

import lombok.Getter;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.concurrent.TimeoutException;

/**
 * @author zh
 */
@Getter
public enum ChatError {

    WEB_RESPONSE_ERROR("Web Response Error"),

    TIMEOUT("Timeout"),

    UNKNOWN_ERROR("Unknown Error"),

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