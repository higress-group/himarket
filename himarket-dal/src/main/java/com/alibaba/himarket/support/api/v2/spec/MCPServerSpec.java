package com.alibaba.himarket.support.api.v2.spec;

import cn.hutool.core.annotation.Alias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
public class MCPServerSpec extends APISpec {

    /**
     * MCP bridge type: "DIRECT" or "HTTP_TO_MCP"
     * - DIRECT: Real MCP server, proxied directly
     * - HTTP_TO_MCP: HTTP to MCP bridge (default)
     */
    private String bridgeType = "HTTP_TO_MCP";

    private Server server;

    private List<Tool> tools;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Server implements Serializable {
        private String name;
        @Builder.Default private Map<String, Object> config = new HashMap<>();
        @Builder.Default private List<String> allowTools = new ArrayList<>();

        private String type;
        private String transport;
        private String mcpServerURL;
        private Integer timeout;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tool implements Serializable {
        private String name;
        private String description;
        @Builder.Default private List<Arg> args = new ArrayList<>();
        private RequestTemplate requestTemplate;
        private ResponseTemplate responseTemplate;
        private String errorResponseTemplate;
        private Map<String, Object> outputSchema;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Arg implements Serializable {
        private String name;
        private String description;
        private String type;
        private boolean required;

        @JsonProperty("default")
        @Alias("default")
        private String defaultValue;

        @JsonProperty("enum")
        @Alias("enum")
        private List<String> enumValues;

        private String position;
        private Map<String, Object> items;
        private Map<String, Object> properties;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestTemplate implements Serializable {
        private String url;
        private String method;
        private Map<String, String> queryParams;
        @Builder.Default private List<Header> headers = new ArrayList<>();
        private String body;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseTemplate implements Serializable {
        private String prependBody;
        private String appendBody;
        private String body;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Header implements Serializable {
        private String key;
        private String value;
    }
}
