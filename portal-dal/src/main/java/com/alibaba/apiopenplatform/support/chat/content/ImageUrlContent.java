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
public class ImageUrlContent extends MessageContent {

    @Alias("image_url")
    @JsonProperty("image_url")
    private ImageUrl imageUrl;

    @Data
    @Builder
    public static class ImageUrl {
        private String url;
    }

    public ImageUrlContent(String url) {
        super.type = ContentType.IMAGE_URL.getType();
        imageUrl = ImageUrl.builder().url(url).build();
    }
}
