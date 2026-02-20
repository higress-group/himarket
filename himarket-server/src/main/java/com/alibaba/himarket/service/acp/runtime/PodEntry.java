package com.alibaba.himarket.service.acp.runtime;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pod 缓存条目，存储用户级沙箱 Pod 的元信息。
 * <p>
 * connectionCount 使用 AtomicInteger 保证并发安全；
 * idleTimer 使用 volatile 保证可见性。
 */
public class PodEntry {

    private final String podName;
    private final String podIp;
    private volatile String serviceIp;
    private final Instant createdAt;
    private final AtomicInteger connectionCount;
    private volatile ScheduledFuture<?> idleTimer;

    public PodEntry(String podName, String podIp) {
        this(podName, podIp, null);
    }

    public PodEntry(String podName, String podIp, String serviceIp) {
        this.podName = podName;
        this.podIp = podIp;
        this.serviceIp = serviceIp;
        this.createdAt = Instant.now();
        this.connectionCount = new AtomicInteger(0);
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

    public AtomicInteger getConnectionCount() {
        return connectionCount;
    }

    public ScheduledFuture<?> getIdleTimer() {
        return idleTimer;
    }

    public void setIdleTimer(ScheduledFuture<?> idleTimer) {
        this.idleTimer = idleTimer;
    }
}
