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
public class VideoUrlContent extends MessageContent {

    @Alias("video_url")
    @JsonProperty("video_url")
    private VideoUrl videoUrl;

    @Data
    @Builder
    public static class VideoUrl {
        private String url;
    }

    public VideoUrlContent(String url) {
        super.type = ContentType.VIDEO_URL.getType();
        this.videoUrl = VideoUrl.builder().url(url).build();
    }
}
