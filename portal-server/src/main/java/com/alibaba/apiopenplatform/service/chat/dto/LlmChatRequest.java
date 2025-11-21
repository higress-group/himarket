package com.alibaba.apiopenplatform.service.chat.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.HttpMethod;

import java.util.Map;

@Data
@Builder
public class LlmChatRequest {

    /**
     * 请求URL
     */
    private String url;

    /**
     * HTTP方法
     */
    private HttpMethod method;

    /**
     * 请求头
     */
    private Map<String, String> headers;

    /**
     * 查询参数
     */
    private Map<String, String> queryParams;

    /**
     * 请求体
     */
    private Object body;
}