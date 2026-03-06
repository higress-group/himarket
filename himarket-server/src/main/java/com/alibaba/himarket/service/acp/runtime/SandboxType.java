package com.alibaba.himarket.service.acp.runtime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 沙箱类型枚举。
 * 统一标识 CLI Agent 运行在哪种沙箱环境中。
 * JSON 序列化为小写（"local"、"k8s"、"e2b"），与前端类型定义一致。
 *
 * <h3>扩展指南：新增 OpenSandbox 类型</h3>
 * <p>若需对接 <a href="https://github.com/alibaba/OpenSandbox">OpenSandbox</a>，
 * 按以下步骤新增沙箱类型：
 * <ol>
 *   <li>在本枚举中添加 {@code OPEN_SANDBOX("open-sandbox")} 枚举值</li>
 *   <li>创建 {@code OpenSandboxProvider implements SandboxProvider}，
 *       在 {@code acquire()} 中调用 OpenSandbox Server API（POST /sandboxes）创建沙箱，
 *       文件操作可复用 {@link SandboxHttpClient}（OpenSandbox execd 的 /files/* API 兼容）</li>
 *   <li>在 {@link SandboxProviderRegistry} 中注册新 Provider</li>
 *   <li>前端传入 {@code runtime=open-sandbox} 即可路由到新 Provider</li>
 * </ol>
 */
public enum SandboxType {

    /** 本地 Mac 沙箱：本地启动 Sidecar + CLI */
    LOCAL("local"),

    /** K8s Pod 沙箱：Pod 内运行 Sidecar + CLI */
    K8S("k8s"),

    /** E2B 云沙箱：通过 E2B SDK 管理（未来扩展） */
    E2B("e2b");

    // TODO: 新增 OpenSandbox 类型时，添加 OPEN_SANDBOX("open-sandbox") 枚举值

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
