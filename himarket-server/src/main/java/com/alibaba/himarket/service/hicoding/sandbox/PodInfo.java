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
