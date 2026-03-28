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

package com.alibaba.himarket.service.mcp;

import lombok.Builder;
import lombok.Data;

/**
 * 沙箱部署事件：在事务提交后触发实际的 K8s CRD 部署。
 *
 * <p>解决 K8s 资源泄漏问题：如果 DB 事务回滚，CRD 不会被部署；
 * 只有事务成功提交后才执行 K8s 操作。
 *
 * <p>注意：本类是 POJO，未继承 {@link org.springframework.context.ApplicationEvent}。
 * Spring Boot 3.x 支持 POJO 事件，会自动包装为 {@link org.springframework.context.PayloadApplicationEvent}，
 * 配合 {@code @TransactionalEventListener} 正常工作。
 */
@Data
@Builder
public class McpSandboxDeployEvent {

    private String sandboxId;
    private String mcpServerId;
    private String mcpName;
    private String adminUserId;
    private String transportType;
    private String metaProtocolType;
    private String connectionConfig;
    private String authType;
    private String paramValues;
    private String extraParams;
    private String namespace;
    private String resourceSpec;

    /** 预创建的 endpoint ID，部署成功后更新 URL；失败时删除 */
    private String endpointId;

    /** 生成的 API Key（authType 为 "apikey" 时非空） */
    private String apiKey;
}
