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

package com.alibaba.apiopenplatform.dto.result.search;

import com.alibaba.apiopenplatform.support.enums.SearchEngineType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可用搜索引擎结果
 * 供开发者查询当前 Portal 有哪些可用的搜索引擎
 * 不包含敏感信息（如 API Key）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailableSearchEngineResult {

    /**
     * 搜索引擎类型
     * 例如: GOOGLE
     */
    private SearchEngineType engineType;

    /**
     * 搜索引擎名称（展示用）
     * 例如: "Google搜索"
     */
    private String engineName;
}

