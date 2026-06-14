package com.alibaba.himarket.service.hicoding.session;

import java.util.List;
import java.util.Set;
import lombok.Data;

/**
 * Custom model configuration with endpoint URL, API key, model ID, display name, and protocol.
 */
@Data
public class CustomModelConfig {

    private static final Set<String> ALLOWED_PROTOCOL_TYPES =
            Set.of("openai", "anthropic", "gemini");

    /**
     * Model endpoint URL.
     */
    private String baseUrl;

    /**
     * API Key
     */
    private String apiKey;

    /**
     * Model ID.
     */
    private String modelId;

    /**
     * Model display name.
     */
    private String modelName;

    /**
     * Protocol type: openai | anthropic | gemini. Defaults to openai.
     */
    private String protocolType = "openai";

    /**
     * Validates the configuration.
     *
     * @return validation errors; an empty list means the configuration is valid
     */
    public List<String> validate() {
        java.util.ArrayList<String> errors = new java.util.ArrayList<>();

        // Validate baseUrl: required and must start with http:// or https://.
        if (baseUrl == null || baseUrl.isBlank()) {
            errors.add("baseUrl must not be empty");
        } else if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            errors.add("baseUrl must start with http:// or https://");
        }

        // Validate apiKey: required.
        if (apiKey == null || apiKey.isBlank()) {
            errors.add("apiKey must not be empty");
        }

        // Validate modelId: required.
        if (modelId == null || modelId.isBlank()) {
            errors.add("modelId must not be empty");
        }

        // Validate protocolType: must be in the allowed set.
        if (protocolType == null || !ALLOWED_PROTOCOL_TYPES.contains(protocolType)) {
            errors.add("protocolType must be one of openai, anthropic, or gemini");
        }

        return errors;
    }
}
