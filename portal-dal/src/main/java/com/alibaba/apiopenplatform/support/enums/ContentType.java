package com.alibaba.apiopenplatform.support.enums;

import lombok.Getter;

/**
 * @author zh
 */
@Getter
public enum ContentType {

    TEXT("text"),

    IMAGE_URL("image_url"),

    AUDIO_URL("audio_url"),

    VIDEO_URL("video_url"),

    ;

    private final String type;

    ContentType(String type) {
        this.type = type;
    }
}
