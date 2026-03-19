package com.alibaba.himarket.service.hicoding.sandbox;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 沙箱类型枚举。
 * 统一标识 CLI Agent 运行在哪种沙箱环境中。
 * JSON 序列化值分别为 "remote"、"open-sandbox"、"e2b"，与前端类型定义一致。
 */
public enum SandboxType {

    /** 远程沙箱：连接远程 Sidecar 服务（K8s / Docker / 裸机均可） */
    REMOTE("remote"),

    /** OpenSandbox 沙箱：通过 OpenSandbox Server API 管理 */
    OPEN_SANDBOX("open-sandbox"),

    /** E2B 云沙箱：通过 E2B SDK 管理 */
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
        // 兼容旧值
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
        throw new IllegalArgumentException("未知的沙箱类型: " + value);
    }
}
