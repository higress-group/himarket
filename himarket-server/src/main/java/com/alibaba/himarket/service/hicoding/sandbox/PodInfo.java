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

import java.net.URI;

/**
 * Pod 信息传递对象，作为 acquirePod 的返回值。
 *
 * @param podName      Pod 名称
 * @param podIp        Pod IP 地址
 * @param serviceIp    Service ClusterIP/LoadBalancer IP（可为 null，仅在创建了 Service 时有值）
 * @param sidecarWsUri Sidecar WebSocket 端点 URI
 * @param reused       是否为复用的已有 Pod
 */
public record PodInfo(
        String podName, String podIp, String serviceIp, URI sidecarWsUri, boolean reused) {}
