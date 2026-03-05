package com.alibaba.himarket.service.acp.runtime;

import java.time.Instant;

/**
 * Pod 缓存条目，存储用户级沙箱 Pod 的元信息。
 * Pod 生命周期与 WebSocket 连接无关，由 PodReuseManager 统一管理。
 */
public class PodEntry {

    private final String podName;
    private final String podIp;
    private volatile String serviceIp;
    private final Instant createdAt;

    public PodEntry(String podName, String podIp) {
        this(podName, podIp, null);
    }

    public PodEntry(String podName, String podIp, String serviceIp) {
        this.podName = podName;
        this.podIp = podIp;
        this.serviceIp = serviceIp;
        this.createdAt = Instant.now();
    }

    public String getPodName() {
        return podName;
    }

    public String getPodIp() {
        return podIp;
    }

    public String getServiceIp() {
        return serviceIp;
    }

    public void setServiceIp(String serviceIp) {
        this.serviceIp = serviceIp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
