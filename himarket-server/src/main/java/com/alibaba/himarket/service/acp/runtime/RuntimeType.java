package com.alibaba.himarket.service.acp.runtime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 运行时类型枚举，标识 CLI Agent 运行在哪种环境中。
 * JSON 序列化为小写（"local"、"k8s"），与前端 RuntimeType 类型定义一致。
 *
 * @deprecated 已被 {@link SandboxType} 替代。
 * 请使用 SandboxType 枚举代替 RuntimeType。
 */
@Deprecated
public enum RuntimeType {

    /** POC 本地进程运行时 */
    LOCAL("local"),

    /** K8s Pod 沙箱运行时 */
    K8S("k8s");

    private final String value;

    RuntimeType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static RuntimeType fromValue(String value) {
        for (RuntimeType type : values()) {
            if (type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的运行时类型: " + value);
    }
}
