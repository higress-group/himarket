package com.alibaba.apiopenplatform.support.enums;

import lombok.Getter;

/**
 * @author zh
 */
@Getter
public enum ChatRole {

    USER("user"),

    ASSISTANT("assistant"),

    SYSTEM("system"),

    ;

    private final String role;

    ChatRole(String role) {
        this.role = role;
    }
}
