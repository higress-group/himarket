package com.alibaba.himarket.service.hicoding.cli;

import java.util.List;

/**
 * Maps aiProtocols to CustomModelConfig protocolType.
 *
 * <p>Mapping rules:
 * <ul>
 *   <li>Return "openai" when the list is null or empty.</li>
 *   <li>Return "openai" when the first item contains "openai" case-insensitively.</li>
 *   <li>Return "anthropic" when the first item contains "anthropic" case-insensitively.</li>
 *   <li>Return "openai" otherwise.</li>
 * </ul>
 */
public class ProtocolTypeMapper {

    /**
     * Maps aiProtocols to a protocol type string.
     *
     * @param aiProtocols AI protocol list
     * @return protocol type, either "openai" or "anthropic"
     */
    public static String map(List<String> aiProtocols) {
        if (aiProtocols == null || aiProtocols.isEmpty()) {
            return "openai";
        }
        String first = aiProtocols.get(0);
        if (first.toLowerCase().contains("openai")) {
            return "openai";
        }
        if (first.toLowerCase().contains("anthropic")) {
            return "anthropic";
        }
        return "openai";
    }
}
