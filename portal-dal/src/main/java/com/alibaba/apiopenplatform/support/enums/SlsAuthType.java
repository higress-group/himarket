package com.alibaba.apiopenplatform.support.enums;

import lombok.Getter;

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
