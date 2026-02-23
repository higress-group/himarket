package com.alibaba.himarket.service.acp.runtime;

/**
 * 沙箱类型枚举。
 * 替代原有的 RuntimeType（LOCAL/K8S），支持更多沙箱类型。
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

    public String getValue() {
        return value;
    }

    public static SandboxType fromValue(String value) {
        for (SandboxType type : values()) {
            if (type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的沙箱类型: " + value);
    }
}
