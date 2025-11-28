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

    public static ChatRole of(String role) {
        for (ChatRole value : values()) {
            if (value.getRole().equals(role)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Invalid role: " + role);
    }
}
