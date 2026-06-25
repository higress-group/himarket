package com.alibaba.himarket.service.hicoding.sandbox;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Sandbox type enum.
 *
 * <p>Identifies the sandbox environment where the CLI agent runs. JSON values are
 * {@code "remote"}, {@code "open-sandbox"}, and {@code "e2b"}, matching the frontend type
 * definitions.
 */
public enum SandboxType {

    /**
     * Remote sandbox connected to a remote Sidecar service.
     */
    REMOTE("remote"),

    /**
     * OpenSandbox sandbox managed through the OpenSandbox Server API.
     */
    OPEN_SANDBOX("open-sandbox"),

    /**
     * E2B cloud sandbox managed through the E2B SDK.
     */
    E2B("e2b");

    private final String value;

    SandboxType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static SandboxType fromValue(String value) {
        // Keep compatibility with legacy values.
        if ("local".equalsIgnoreCase(value)
                || "k8s".equalsIgnoreCase(value)
                || "shared-k8s".equalsIgnoreCase(value)
                || "shared_k8s".equalsIgnoreCase(value)) {
            return REMOTE;
        }
        for (SandboxType type : values()) {
            if (type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported sandbox type: " + value);
    }
}
