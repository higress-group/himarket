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

import com.alibaba.himarket.service.hicoding.sandbox.SandboxInfo;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxProvider;

/**
 * 获取沙箱实例。
 * 通过 SandboxProvider.acquire() 统一处理，不直接依赖任何具体实现。
 */
public class SandboxAcquirePhase implements InitPhase {

    @Override
    public String name() {
        return "sandbox-acquire";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public boolean shouldExecute(InitContext context) {
        return true;
    }

    @Override
    public void execute(InitContext context) throws InitPhaseException {
        try {
            SandboxProvider provider = context.getProvider();
            SandboxInfo info = provider.acquire(context.getSandboxConfig());
            context.setSandboxInfo(info);
        } catch (Exception e) {
            throw new InitPhaseException("sandbox-acquire", "沙箱获取失败: " + e.getMessage(), e, false);
        }
    }

    @Override
    public boolean verify(InitContext context) {
        return context.getSandboxInfo() != null
                && context.getSandboxInfo().host() != null
                && !context.getSandboxInfo().host().isBlank();
    }

    @Override
    public RetryPolicy retryPolicy() {
        return RetryPolicy.none();
    }
}
