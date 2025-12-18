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

package com.alibaba.himarket.support.api;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Model 配置 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ModelConfig extends EndpointConfig {

    /** 模型名称 */
    private String modelName;

    /** 模型类别 */
    private String modelCategory;

    /** 支持的 AI 协议 */
    private List<String> aiProtocols;

    /** 路由匹配配置 */
    private MatchConfig matchConfig;

    @Data
    public static class MatchConfig {
        /** 路径匹配 */
        private PathMatch path;

        /** HTTP 方法列表 */
        private List<String> methods;

        /** 请求头匹配 */
        private List<HeaderMatch> headers;

        /** 查询参数匹配 */
        private List<QueryMatch> queryParams;
    }

    @Data
    public static class PathMatch {
        /** 匹配类型：Prefix, Exact, Regex */
        private String type;

        /** 匹配值 */
        private String value;
    }

    @Data
    public static class HeaderMatch {
        /** 请求头名称 */
        private String name;

        /** 匹配类型：Exact, Prefix, Regex */
        private String type;

        /** 匹配值 */
        private String value;
    }

    @Data
    public static class QueryMatch {
        /** 查询参数名称 */
        private String name;

        /** 匹配类型：Exact, Prefix, Regex */
        private String type;

        /** 匹配值 */
        private String value;
    }
}
