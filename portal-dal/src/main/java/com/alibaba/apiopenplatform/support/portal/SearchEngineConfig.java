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

package com.alibaba.apiopenplatform.support.portal;

import com.alibaba.apiopenplatform.support.common.Encrypted;
import com.alibaba.apiopenplatform.support.enums.SearchEngineType;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 搜索引擎配置（POJO）
 * 完全参考 OidcConfig 的设计模式
 * 作为 Portal 配置的一部分，存储在 portal_setting_config JSON 字段中
 * 一个 Portal 只能配置一个搜索引擎
 */
@Data
public class SearchEngineConfig {

    /**
     * 搜索引擎类型
     * 当前仅支持 GOOGLE
     */
    private SearchEngineType engineType;

    /**
     * 搜索引擎名称（展示用）
     */
    private String engineName;

    /**
     * API Key
     * 标记 @Encrypted 注解后，系统会自动加密/解密
     */
    @Encrypted
    private String apiKey;

    /**
     * 是否启用，默认启用
     */
    private boolean enabled = true;

    /**
     * 额外配置
     * 使用 Map 灵活存储扩展配置
     * 例如：{"timeout": 30, "domain": "google.com"}
     */
    private Map<String, Object> extraConfig = new HashMap<>();
}
