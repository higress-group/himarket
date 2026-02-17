package com.alibaba.himarket.service.acp.runtime;

import java.time.Instant;

/**
 * K8s 集群信息记录，描述已注册的 K8s 集群连接状态。
 *
 * @param configId     配置 ID，用于唯一标识一个已注册的 kubeconfig
 * @param clusterName  集群名称，从 kubeconfig 中解析
 * @param serverUrl    集群 API Server 地址
 * @param connected    当前是否可连接
 * @param registeredAt 注册时间
 */
public record K8sClusterInfo(
        String configId,
        String clusterName,
        String serverUrl,
        boolean connected,
        Instant registeredAt) {}
