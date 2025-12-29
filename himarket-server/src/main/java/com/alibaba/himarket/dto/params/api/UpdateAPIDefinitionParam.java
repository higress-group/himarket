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

package com.alibaba.himarket.dto.params.api;

import cn.hutool.json.JSONUtil;
import com.alibaba.himarket.dto.converter.InputConverter;
import com.alibaba.himarket.entity.APIDefinition;
import com.alibaba.himarket.support.api.BaseAPIProperty;
import com.alibaba.himarket.support.enums.APIStatus;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class UpdateAPIDefinitionParam implements InputConverter<APIDefinition> {

    @Size(max = 100, message = "API名称长度不能超过100个字符")
    private String name;

    @Size(max = 500, message = "API描述长度不能超过500个字符")
    private String description;

    private APIStatus status;

    @Size(max = 50, message = "版本号长度不能超过50个字符")
    private String version;

    private List<BaseAPIProperty> properties;

    private Map<String, Object> metadata;

    /** 端点配置列表，更新 API 时可以同时更新多个端点 */
    @jakarta.validation.Valid private List<CreateEndpointParam> endpoints;

    @Override
    public void update(APIDefinition domain) {
        InputConverter.super.update(domain);

        // 处理 properties JSON 序列化
        if (properties != null) {
            if (properties.isEmpty()) {
                domain.setProperties(null);
            } else {
                domain.setProperties(JSONUtil.toJsonStr(properties));
            }
        }

        // 处理 metadata JSON 序列化
        if (metadata != null) {
            if (metadata.isEmpty()) {
                domain.setMetadata(null);
            } else {
                domain.setMetadata(JSONUtil.toJsonStr(metadata));
            }
        }
    }
}
