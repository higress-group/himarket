package com.alibaba.himarket.support.enums;

import lombok.Getter;

/**
 * @author zh
 */
@Getter
public enum MCPTransportMode {

    STDIO("stdio"),

    SSE("sse"),

    STREAMABLE_HTTP("StreamableHTTP");

    private final String mode;

    MCPTransportMode(String mode) {
        this.mode = mode;
    }
}
