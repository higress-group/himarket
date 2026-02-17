package com.alibaba.himarket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * K8s 集群配置属性。
 * <p>
 * 当前仅提供 enabled 标志位，用于 RuntimeSelector 判断 K8s 运行时是否可用。
 * 后续 K8sConfigService 实现后，将扩展为完整的集群配置管理。
 */
@ConfigurationProperties(prefix = "acp.k8s")
public class K8sProperties {

    /**
     * 是否启用 K8s 集群支持。
     * 设为 true 表示已配置 K8s 集群，K8s 运行时可用。
     */
    private boolean enabled = false;

    /**
     * Pod 空闲超时时间（秒）。
     */
    private int idleTimeoutSeconds = 600;

    /**
     * 健康检查间隔（秒）。
     */
    private int healthCheckIntervalSeconds = 30;

    /**
     * Pod 所在的 K8s 命名空间。
     */
    private String podNamespace = "himarket";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    public void setIdleTimeoutSeconds(int idleTimeoutSeconds) {
        this.idleTimeoutSeconds = idleTimeoutSeconds;
    }

    public int getHealthCheckIntervalSeconds() {
        return healthCheckIntervalSeconds;
    }

    public void setHealthCheckIntervalSeconds(int healthCheckIntervalSeconds) {
        this.healthCheckIntervalSeconds = healthCheckIntervalSeconds;
    }

    public String getPodNamespace() {
        return podNamespace;
    }

    public void setPodNamespace(String podNamespace) {
        this.podNamespace = podNamespace;
    }
}
