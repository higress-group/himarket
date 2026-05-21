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

package com.alibaba.himarket.service.hicoding.sandbox.init;

/**
 * 初始化阶段接口。 每个阶段通过 InitContext.getProvider() 获取 SandboxProvider， 执行沙箱类型无关的初始化逻辑。
 */
public interface InitPhase {

    /** 阶段名称，用于日志和事件追踪。 */
    String name();

    /** 执行顺序，值越小越先执行。 */
    int order();

    /** 判断当前阶段是否需要执行。返回 false 时跳过并记录 PHASE_SKIP 事件。 */
    boolean shouldExecute(InitContext context);

    /** 执行阶段逻辑。 */
    void execute(InitContext context) throws InitPhaseException;

    /** 验证阶段执行结果是否就绪。verify 返回 true 后才执行下一阶段。 */
    boolean verify(InitContext context);

    /** 该阶段的重试策略。 */
    RetryPolicy retryPolicy();
}
