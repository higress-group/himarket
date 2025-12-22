/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.support.chat.content;

import cn.hutool.core.annotation.Alias;
import com.alibaba.himarket.support.enums.ContentType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

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
