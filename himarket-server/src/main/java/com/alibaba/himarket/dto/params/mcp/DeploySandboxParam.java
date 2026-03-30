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

package com.alibaba.himarket.dto.params.mcp;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 管理员手动部署沙箱的请求参数。 */
@Data
public class DeploySandboxParam {

    @NotBlank(message = "沙箱实例ID不能为空")
    private String sandboxId;

    private String transportType;

    private String authType;

    private String paramValues;

    private String namespace;

    private String resourceSpec;

    /** 转换为 Service 层使用的 SaveMcpMetaParam。 */
    public SaveMcpMetaParam toSaveMcpMetaParam() {
        SaveMcpMetaParam param = new SaveMcpMetaParam();
        param.setSandboxId(sandboxId);
        param.setTransportType(transportType);
        param.setAuthType(authType);
        param.setParamValues(paramValues);
        param.setNamespace(namespace);
        param.setResourceSpec(resourceSpec);
        return param;
    }
}
