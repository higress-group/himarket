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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** AI 服务配置 */
@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiServiceConfig extends ServiceConfig {

    /** 模型提供商 */
    private String provider;

    /** 模型协议 */
    private String protocol;

    /** 服务地址 (通用地址字段,适用于大多数提供商) */
    private String address;

    /** API Key (通用认证字段,适用于大多数提供商) */
    private String apiKey;

    // ==================== Azure 专用字段 ====================

    /** Azure 服务 URL (Azure OpenAI) */
    private String azureServiceUrl;

    // ==================== Bedrock 专用字段 ====================

    /** AWS 区域 (Bedrock) */
    private String awsRegion;

    /** AWS Access Key (Bedrock AK/SK 认证) */
    private String awsAccessKey;

    /** AWS Secret Key (Bedrock AK/SK 认证) */
    private String awsSecretKey;

    /** Bedrock 认证方式 (API_KEY 或 AK_SK) */
    private String bedrockAuthType;

    // ==================== Vertex AI 专用字段 ====================

    /** Vertex 区域 (Vertex AI) */
    private String vertexRegion;

    /** Vertex 项目 ID (Vertex AI) */
    private String vertexProjectId;

    /** Vertex Auth 服务名称 (Vertex AI) */
    private String vertexAuthServiceName;

    /** Vertex Auth Key (Vertex AI) */
    private String vertexAuthKey;
}
