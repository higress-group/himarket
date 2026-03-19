package com.alibaba.himarket.config;

import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "acp")
public class AcpProperties {

    /**
     * 是否启用终端功能。
     * 设为 false 时后端拒绝 Terminal WebSocket 连接，前端隐藏终端面板。
     */
    private boolean terminalEnabled = true;

    /**
     * 默认使用的 CLI provider key（对应 providers map 中的 key）
     */
    private String defaultProvider = "qwen-code";

    /**
     * 默认运行时类型。
     * 当用户未主动选择运行时方案时使用此默认值。
     */
    private String defaultRuntime = "remote";

    /**
     * CLI provider 注册表，支持多种 ACP 兼容的 CLI 工具。
     * 每个 provider 定义了命令、参数和可选的环境变量。
     */
    private Map<String, CliProviderConfig> providers = new LinkedHashMap<>();

    /**
     * 远程沙箱运行时配置。
     * 支持 K8s Service、Docker 容器、裸机部署等任意可达的 Sidecar 服务。
     */
    private RemoteConfig remote = new RemoteConfig();

    public boolean isTerminalEnabled() {
        return terminalEnabled;
    }

    public void setTerminalEnabled(boolean terminalEnabled) {
        this.terminalEnabled = terminalEnabled;
    }

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
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

    public RemoteConfig getRemote() {
        return remote;
    }

    public void setRemote(RemoteConfig remote) {
        this.remote = remote;
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
        private List<SandboxType> compatibleRuntimes;
        private boolean supportsCustomModel = false;
        private boolean supportsMcp = false;
        private boolean supportsSkill = false;
        private List<String> authOptions;
        private String authEnvVar;

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

        public List<SandboxType> getCompatibleRuntimes() {
            return compatibleRuntimes;
        }

        public void setCompatibleRuntimes(List<SandboxType> compatibleRuntimes) {
            this.compatibleRuntimes = compatibleRuntimes;
        }

        public boolean isSupportsCustomModel() {
            return supportsCustomModel;
        }

        public void setSupportsCustomModel(boolean supportsCustomModel) {
            this.supportsCustomModel = supportsCustomModel;
        }

        public boolean isSupportsMcp() {
            return supportsMcp;
        }

        public void setSupportsMcp(boolean supportsMcp) {
            this.supportsMcp = supportsMcp;
        }

        public boolean isSupportsSkill() {
            return supportsSkill;
        }

        public void setSupportsSkill(boolean supportsSkill) {
            this.supportsSkill = supportsSkill;
        }

        public List<String> getAuthOptions() {
            return authOptions;
        }

        public void setAuthOptions(List<String> authOptions) {
            this.authOptions = authOptions;
        }

        public String getAuthEnvVar() {
            return authEnvVar;
        }

        public void setAuthEnvVar(String authEnvVar) {
            this.authEnvVar = authEnvVar;
        }
    }

    /**
     * 远程沙箱运行时配置。
     * 不依赖 K8s API，只需 Sidecar 服务地址可达即可。
     */
    public static class RemoteConfig {

        /**
         * Sidecar 服务地址。
         * 可以是：
         * - K8s Service DNS: sandbox-shared.default.svc.cluster.local
         * - Docker 容器名: sandbox
         * - IP 地址: 192.168.1.100
         * - localhost（本地 Docker 部署）
         * 留空表示未配置远程沙箱。
         */
        private String host = "";

        /**
         * Sidecar 服务端口，默认 8080。
         */
        private int port = 8080;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        /**
         * 判断远程沙箱是否已配置（host 非空）。
         */
        public boolean isConfigured() {
            return host != null && !host.isBlank();
        }
    }
}
