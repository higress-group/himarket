package com.alibaba.himarket.service.hicoding.cli;

import java.util.List;

/**
 * 将 aiProtocols 列表映射为 CustomModelConfig 的 protocolType 的工具类。
 *
 * <p>映射规则：
 * <ul>
 *   <li>列表为空或 null 时，返回 "openai"</li>
 *   <li>第一个元素包含 "openai"（不区分大小写）时，返回 "openai"</li>
 *   <li>第一个元素包含 "anthropic"（不区分大小写）时，返回 "anthropic"</li>
 *   <li>其他情况默认返回 "openai"</li>
 * </ul>
 */
public class ProtocolTypeMapper {

    /**
     * 将 aiProtocols 列表映射为协议类型字符串。
     *
     * @param aiProtocols AI 协议列表
     * @return 协议类型，"openai" 或 "anthropic"
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
