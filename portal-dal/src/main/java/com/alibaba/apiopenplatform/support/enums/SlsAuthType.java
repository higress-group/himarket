package com.aliyun.csb.model.enums;

import lombok.Getter;

/**
 * SLS认证类型枚举
 *
 * @author jingfeng.xjf
 * @date 2025/11/08
 */
@Getter
public enum SlsAuthType {
    /**
     * 使用STS临时凭证
     */
    STS("sts", "STS authentication"),

    /**
     * 使用AK/SK
     */
    AK_SK("ak_sk", "AK/SK authentication");

    private final String code;
    private final String description;

    SlsAuthType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
