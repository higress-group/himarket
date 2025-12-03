package com.alibaba.apiopenplatform.support.chat.content;

import cn.hutool.core.annotation.Alias;
import com.alibaba.apiopenplatform.support.enums.ContentType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * @author zh
 */
@Data
public class AudioUrlContent extends MessageContent {

    @Alias("audio_url")
    @JsonProperty("audio_url")
    private AudioUrl audioUrl;

    @Data
    @Builder
    public static class AudioUrl {
        private String url;
    }

    public AudioUrlContent(String url) {
        super.type = ContentType.AUDIO_URL.getType();
        this.audioUrl = AudioUrl.builder().url(url).build();
    }
}
