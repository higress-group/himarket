package com.alibaba.himarket.service.hicoding.session;

import java.util.List;
import java.util.Set;
import lombok.Data;

/**
 * 自定义模型配置，包含模型接入点 URL、API Key、模型 ID、显示名称和协议类型。
 */
@Data
public class CustomModelConfig {

    private static final Set<String> ALLOWED_PROTOCOL_TYPES =
            Set.of("openai", "anthropic", "gemini");

    /** 模型接入点 URL */
    private String baseUrl;

    /** API Key */
    private String apiKey;

    /** 模型 ID */
    private String modelId;

    /** 模型显示名称 */
    private String modelName;

    /** 协议类型: openai | anthropic | gemini，默认 openai */
    private String protocolType = "openai";

    /**
     * 校验配置的合法性。
     *
     * @return 校验错误信息列表，为空表示校验通过
     */
    public List<String> validate() {
        java.util.ArrayList<String> errors = new java.util.ArrayList<>();

        // 校验 baseUrl：非空且为合法 URL（以 http:// 或 https:// 开头）
        if (baseUrl == null || baseUrl.isBlank()) {
            errors.add("baseUrl 不能为空");
        } else if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            errors.add("baseUrl 格式不合法，必须以 http:// 或 https:// 开头");
        }

        // 校验 apiKey：非空
        if (apiKey == null || apiKey.isBlank()) {
            errors.add("apiKey 不能为空，缺少凭证");
        }

        // 校验 modelId：非空
        if (modelId == null || modelId.isBlank()) {
            errors.add("modelId 不能为空，缺少模型标识");
        }

        // 校验 protocolType：必须在允许范围内
        if (protocolType == null || !ALLOWED_PROTOCOL_TYPES.contains(protocolType)) {
            errors.add("protocolType 必须是 openai、anthropic、gemini 之一");
        }

        return errors;
    }
}
