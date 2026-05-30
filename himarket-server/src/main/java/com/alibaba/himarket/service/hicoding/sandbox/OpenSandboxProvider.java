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

package com.alibaba.himarket.service.hicoding.sandbox;

import com.alibaba.himarket.service.hicoding.runtime.RuntimeAdapter;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeConfig;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * OpenSandbox 沙箱提供者（空实现）。
 *
 * <p>预留 OpenSandbox 对接接口，当前所有操作方法抛出 {@link UnsupportedOperationException}。
 * 仅在 {@code acp.open-sandbox.enabled=true} 时注册到 Spring 容器。
 */
@Component
@ConditionalOnProperty(name = "acp.open-sandbox.enabled", havingValue = "true")
public class OpenSandboxProvider implements SandboxProvider {

    @Override
    public SandboxType getType() {
        return SandboxType.OPEN_SANDBOX;
    }

    @Override
    public SandboxInfo acquire(SandboxConfig config) {
        throw new UnsupportedOperationException("OpenSandbox 尚未实现");
    }

    @Override
    public void release(SandboxInfo info) {
        // 空实现
    }

    @Override
    public boolean healthCheck(SandboxInfo info) {
        return false;
    }

    @Override
    public void writeFile(SandboxInfo info, String relativePath, String content)
            throws IOException {
        throw new UnsupportedOperationException("OpenSandbox 尚未实现");
    }

    @Override
    public String readFile(SandboxInfo info, String relativePath) throws IOException {
        throw new UnsupportedOperationException("OpenSandbox 尚未实现");
    }

    @Override
    public RuntimeAdapter connectSidecar(SandboxInfo info, RuntimeConfig config) {
        throw new UnsupportedOperationException("OpenSandbox 尚未实现");
    }
}
