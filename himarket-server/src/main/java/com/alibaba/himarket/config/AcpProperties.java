package com.alibaba.himarket.config;

import com.alibaba.himarket.service.acp.runtime.RuntimeType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "acp")
public class AcpProperties {

    /**
     * 默认使用的 CLI provider key（对应 providers map 中的 key）
     */
    private String defaultProvider = "qodercli";

    private String workspaceRoot = "./workspaces";

    /**
     * 默认运行时类型（local | k8s）。
     * 当用户未主动选择运行时方案时使用此默认值。
     */
    private String defaultRuntime = "local";

    /**
     * CLI provider 注册表，支持多种 ACP 兼容的 CLI 工具。
     * 每个 provider 定义了命令、参数和可选的环境变量。
     */
    private Map<String, CliProviderConfig> providers = new LinkedHashMap<>();

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public String getDefaultRuntime() {
        return defaultRuntime;
    }

    public void setDefaultRuntime(String defaultRuntime) {
        this.defaultRuntime = defaultRuntime;
    }

    public Map<String, CliProviderConfig> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, CliProviderConfig> providers) {
        this.providers = providers;
    }

    /**
     * 根据 provider key 获取配置，找不到则返回 null。
     */
    public CliProviderConfig getProvider(String key) {
        return providers.get(key);
    }

    /**
     * 获取默认 provider 配置。
     */
    public CliProviderConfig getDefaultProviderConfig() {
        return providers.get(defaultProvider);
    }

    /**
     * 单个 CLI provider 的配置。
     */
    public static class CliProviderConfig {

        private String displayName;
        private String command;
        private String args = "--acp";
        private Map<String, String> env = new LinkedHashMap<>();

        /**
         * 是否将 HOME 环境变量设为用户工作目录，实现凭证隔离。
         * 开启后 CLI 工具的凭证文件会存储在 ~/.himarket/workspaces/{userId}/ 下。
         */
        private boolean isolateHome = false;

        /**
         * 该 CLI 工具兼容的运行时列表。
         * 用于运行时选择器过滤不兼容的运行时方案。
         */
        private List<RuntimeType> compatibleRuntimes;

        /**
         * K8s 运行时使用的容器镜像地址。
         */
        private String containerImage;

        /**
         * 运行时分类：native（原生二进制）、nodejs（Node.js 生态）、python。
         */
        private String runtimeCategory;

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public String getArgs() {
            return args;
        }

        public void setArgs(String args) {
            this.args = args;
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

        public List<RuntimeType> getCompatibleRuntimes() {
            return compatibleRuntimes;
        }

        public void setCompatibleRuntimes(List<RuntimeType> compatibleRuntimes) {
            this.compatibleRuntimes = compatibleRuntimes;
        }

        public String getContainerImage() {
            return containerImage;
        }

        public void setContainerImage(String containerImage) {
            this.containerImage = containerImage;
        }

        public String getRuntimeCategory() {
            return runtimeCategory;
        }

        public void setRuntimeCategory(String runtimeCategory) {
            this.runtimeCategory = runtimeCategory;
        }
    }
}
