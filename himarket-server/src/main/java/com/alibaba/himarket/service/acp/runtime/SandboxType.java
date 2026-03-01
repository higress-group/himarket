package com.alibaba.himarket.service.acp.runtime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 沙箱类型枚举。
 * 统一标识 CLI Agent 运行在哪种沙箱环境中。
 * JSON 序列化为小写（"local"、"k8s"、"e2b"），与前端类型定义一致。
 */
public enum SandboxType {

    /** 本地 Mac 沙箱：本地启动 Sidecar + CLI */
    LOCAL("local"),

    /** K8s Pod 沙箱：Pod 内运行 Sidecar + CLI */
    K8S("k8s"),

    /** E2B 云沙箱：通过 E2B SDK 管理（未来扩展） */
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
        for (SandboxType type : values()) {
            if (type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的沙箱类型: " + value);
    }
}
