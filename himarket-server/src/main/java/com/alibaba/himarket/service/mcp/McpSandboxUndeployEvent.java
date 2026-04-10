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
 * 沙箱取消部署事件：在事务提交后触发旧 CRD 资源的清理。
 *
 * <p>与 {@link McpSandboxDeployEvent} 配合使用：重新部署沙箱时，
 * 旧 CRD 的清理和新 CRD 的部署都在事务提交后异步执行，
 * 避免在事务内执行耗时的 K8s 操作。
 *
 * <p>注意：本类是 POJO，Spring Boot 3.x 会自动包装为
 * {@link org.springframework.context.PayloadApplicationEvent}。
 */
@Data
@Builder
public class McpSandboxUndeployEvent {

    /** 沙箱实例 ID */
    private String sandboxId;

    /** MCP Server 名称（用于 CRD 资源标识） */
    private String mcpName;

    /** 执行操作的用户 ID */
    private String userId;

    /** K8s namespace */
    private String namespace;

    /** CRD 资源名称（用于精确删除，避免名称计算不一致） */
    private String resourceName;

    /** K8s Secret 名称（为空时跳过 Secret 删除） */
    private String secretName;
}
