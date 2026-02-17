package com.alibaba.himarket.service.acp.runtime;

import java.util.List;
import java.util.Map;

/**
 * 运行时配置数据类，封装创建运行时实例所需的全部参数。
 */
public class RuntimeConfig {

    private String userId;
    private String providerKey;
    private String command;
    private List<String> args;
    private String cwd;
    private Map<String, String> env;
    private boolean isolateHome;

    // K8s 专用
    private String k8sConfigId;
    private String containerImage;
    private ResourceLimits resourceLimits;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getProviderKey() {
        return providerKey;
    }

    public void setProviderKey(String providerKey) {
        this.providerKey = providerKey;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public List<String> getArgs() {
        return args;
    }

    public void setArgs(List<String> args) {
        this.args = args;
    }

    public String getCwd() {
        return cwd;
    }

    public void setCwd(String cwd) {
        this.cwd = cwd;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
    }

    public boolean isIsolateHome() {
        return isolateHome;
    }

    public void setIsolateHome(boolean isolateHome) {
        this.isolateHome = isolateHome;
    }

    public String getK8sConfigId() {
        return k8sConfigId;
    }

    public void setK8sConfigId(String k8sConfigId) {
        this.k8sConfigId = k8sConfigId;
    }

    public String getContainerImage() {
        return containerImage;
    }

    public void setContainerImage(String containerImage) {
        this.containerImage = containerImage;
    }

    public ResourceLimits getResourceLimits() {
        return resourceLimits;
    }

    public void setResourceLimits(ResourceLimits resourceLimits) {
        this.resourceLimits = resourceLimits;
    }

    /**
     * 资源限制配置（K8s Pod 使用）。
     */
    public static class ResourceLimits {

        private String cpuLimit;
        private String memoryLimit;
        private String diskLimit;

        public String getCpuLimit() {
            return cpuLimit;
        }

        public void setCpuLimit(String cpuLimit) {
            this.cpuLimit = cpuLimit;
        }

        public String getMemoryLimit() {
            return memoryLimit;
        }

        public void setMemoryLimit(String memoryLimit) {
            this.memoryLimit = memoryLimit;
        }

        public String getDiskLimit() {
            return diskLimit;
        }

        public void setDiskLimit(String diskLimit) {
            this.diskLimit = diskLimit;
        }
    }
}
