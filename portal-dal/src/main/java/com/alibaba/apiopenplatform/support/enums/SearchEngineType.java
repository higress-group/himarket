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

package com.alibaba.apiopenplatform.support.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 搜索引擎类型枚举
 * 当前仅支持 GOOGLE，未来可扩展 BING、知识库搜索等
 */
@Getter
public enum SearchEngineType {
    
    /**
     * Google搜索（通过SerpAPI）
     * 当前唯一支持的搜索引擎
     */
    GOOGLE("Google", "Google搜索引擎", true);
    
    // 未来可扩展的类型（示例，暂不启用）
    // BING("Bing", "Bing搜索引擎", false),
    // KNOWLEDGE_BASE("KnowledgeBase", "知识库搜索", false);

    private final String code;
    private final String description;
    private final boolean supported; // 标识是否当前支持

    SearchEngineType(String code, String description, boolean supported) {
        this.code = code;
        this.description = description;
        this.supported = supported;
    }
    
    /**
     * 获取所有当前支持的搜索引擎类型
     */
    public static Set<SearchEngineType> getSupportedTypes() {
        return Arrays.stream(values())
                .filter(type -> type.isSupported())
                .collect(Collectors.toSet());
    }
    
    /**
     * 检查指定类型是否支持
     */
    public static boolean isSupported(SearchEngineType type) {
        return type != null && type.isSupported();
    }
}
