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
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** AI 服务配置 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiServiceConfig extends ServiceConfig {

    /** 模型提供商 */
    private String provider;

    /** 模型协议 */
    private String protocol;

    /** API Key */
    private String apiKey;

    /** OpenAI 自定义后端 URL */
    private String openaiCustomUrl;

    /** 响应 JSON Schema */
    private Map<String, Object> responseJsonSchema;

    /** Azure 服务 URL */
    private String azureServiceUrl;

    /** Moonshot 文件 ID */
    private String moonshotFileId;

    /** Qwen 是否启用搜索 */
    private Boolean qwenEnableSearch;

    /** Qwen 是否启用兼容模式 */
    private Boolean qwenEnableCompatible;

    /** Qwen 推理内容模式 */
    private String reasoningContentMode;

    /** Qwen 文件 ID 列表 */
    private List<String> qwenFileIds;

    /** Minimax API 类型 */
    private String minimaxApiType;

    /** Minimax Group ID */
    private String minimaxGroupId;

    /** Claude 版本 */
    private String claudeVersion;

    /** Ollama 服务器主机 */
    private String ollamaServerHost;

    /** Ollama 服务器端口 */
    private Integer ollamaServerPort;

    /** Hunyuan Auth ID */
    private String hunyuanAuthId;

    /** Hunyuan Auth Key */
    private String hunyuanAuthKey;

    /** Cloudflare Account ID */
    private String cloudflareAccountId;

    /** API 版本 (Gemini) */
    private String apiVersion;

    /** Gemini 安全设置 */
    private Map<String, String> geminiSafetySetting;

    /** Gemini 思考预算 */
    private Integer geminiThinkingBudget;

    /** DeepL 目标语言 */
    private String targetLang;

    /** Dify API URL */
    private String difyApiUrl;

    /** Dify 应用类型 */
    private String botType;

    /** Dify 输入变量 */
    private String inputVariable;

    /** Dify 输出变量 */
    private String outputVariable;

    /** Vertex 区域 */
    private String vertexRegion;

    /** Vertex 项目 ID */
    private String vertexProjectId;

    /** Vertex Auth 服务名称 */
    private String vertexAuthServiceName;

    /** Vertex Auth Key */
    private String vertexAuthKey;

    /** Vertex Token 刷新提前时间 */
    private Integer vertexTokenRefreshAhead;

    /** AWS 区域 */
    private String awsRegion;

    /** AWS Access Key */
    private String awsAccessKey;

    /** AWS Secret Key */
    private String awsSecretKey;

    /** Bedrock 额外字段 */
    private Map<String, Object> bedrockAdditionalFields;

    /** Triton Domain */
    private String tritonDomain;

    /** Triton Model Version */
    private String tritonModelVersion;

    /** Generic Host */
    private String genericHost;

    /** 超时时间 */
    private Integer timeout;

    /** Sub Path */
    private String subPath;

    /** 模型映射 */
    private Map<String, String> modelMapping;

    /** 上下文配置 */
    private Object context;
}
