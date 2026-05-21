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

import com.alibaba.himarket.service.hicoding.runtime.RuntimeAdapter;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeStatus;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxProvider;

/**
 * 建立到 Sidecar Server 的 WebSocket 连接。
 * 所有沙箱类型都通过 Sidecar WebSocket 桥接 CLI，逻辑完全一致。
 */
public class SidecarConnectPhase implements InitPhase {

    @Override
    public String name() {
        return "sidecar-connect";
    }

    @Override
    public int order() {
        return 400;
    }

    @Override
    public boolean shouldExecute(InitContext context) {
        return true;
    }

    @Override
    public void execute(InitContext context) throws InitPhaseException {
        try {
            SandboxProvider provider = context.getProvider();
            RuntimeAdapter adapter =
                    provider.connectSidecar(context.getSandboxInfo(), context.getRuntimeConfig());
            context.setRuntimeAdapter(adapter);
        } catch (Exception e) {
            throw new InitPhaseException(
                    "sidecar-connect", "Sidecar 连接失败: " + e.getMessage(), e, true);
        }
    }

    @Override
    public boolean verify(InitContext context) {
        RuntimeAdapter adapter = context.getRuntimeAdapter();
        return adapter != null && adapter.getStatus() == RuntimeStatus.RUNNING;
    }

    @Override
    public RetryPolicy retryPolicy() {
        return RetryPolicy.none();
    }
}
