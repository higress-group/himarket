package com.alibaba.himarket.config;

import com.alibaba.himarket.service.acp.runtime.SandboxType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "acp")
public class AcpProperties {

    /**
     * 是否启用本地沙箱模式（LocalSandboxProvider）。
     * 服务端部署时应设为 false 以禁用本地 Sidecar 进程启动。
     * 默认 true，适用于本地开发。
     */
    private boolean localEnabled = true;

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

    /**
     * K8s 运行时相关配置。
     */
    private K8sConfig k8s = new K8sConfig();

    public boolean isLocalEnabled() {
        return localEnabled;
    }

    public void setLocalEnabled(boolean localEnabled) {
        this.localEnabled = localEnabled;
    }

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

    public K8sConfig getK8s() {
        return k8s;
    }

    public void setK8s(K8sConfig k8s) {
        this.k8s = k8s;
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
        private List<SandboxType> compatibleRuntimes;

        /**
         * K8s 运行时使用的容器镜像地址。
         */
        private String containerImage;

        /**
         * 运行时分类：native（原生二进制）、nodejs（Node.js 生态）、python。
         */
        private String runtimeCategory;

        /**
         * 该 CLI 工具是否支持自定义模型配置。
         * 开启后前端会展示自定义模型配置表单，允许用户配置自定义 LLM 接入点。
         */
        private boolean supportsCustomModel = false;

        /**
         * 该 CLI 工具是否支持 MCP Server 配置注入。
         * 开启后前端会展示 MCP Server 选择器，允许用户选择市场中已订阅的 MCP Server。
         */
        private boolean supportsMcp = false;

        /**
         * 该 CLI 工具是否支持 Skill 配置注入。
         * 开启后前端会展示 Skill 选择器，允许用户选择市场中已发布的 Skill。
         */
        private boolean supportsSkill = false;

        /**
         * 该 CLI 工具支持的认证方案列表（可选）。
         * 如 ["default", "personal_access_token"]，前端根据此列表渲染认证方案选择 UI。
         */
        private List<String> authOptions;

        /**
         * 该 CLI 工具的 Token/API Key 对应的环境变量名（可选）。
         * 如 QoderCli 对应 QODER_PERSONAL_ACCESS_TOKEN，Claude Code 对应 ANTHROPIC_API_KEY。
         */
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

        public boolean isIsolateHome() {
            return isolateHome;
        }

        public void setIsolateHome(boolean isolateHome) {
            this.isolateHome = isolateHome;
        }

        public List<SandboxType> getCompatibleRuntimes() {
            return compatibleRuntimes;
        }

        public void setCompatibleRuntimes(List<SandboxType> compatibleRuntimes) {
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
     * K8s 运行时相关配置项。
     */
    public static class K8sConfig {

        /**
         * K8s 命名空间，所有沙箱 Pod、Service、PVC 均创建在此命名空间下。
         * 默认 "himarket"。
         */
        private String namespace = "default";

        /**
         * 用户级沙箱 Pod 空闲超时秒数。
         * 当 Pod 上所有连接断开后，经过此时间仍无新连接则自动删除 Pod。
         * 默认 1800 秒（30 分钟）。
         */
        private long reusePodIdleTimeout = 1800;

        /**
         * 是否通过 LoadBalancer Service IP 访问沙箱 Pod。
         * 开启后创建 Pod 时会同时创建 LoadBalancer 类型的 Service，
         * 并通过 Service 的 External IP 构建 WebSocket URI。
         * 适用于本地开发调试场景（Pod IP 在本地不可达）。
         * 生产环境可关闭此选项，直接使用 Pod IP。
         * 默认 true（通过 Service 访问）。
         */
        private boolean sandboxAccessViaService = true;

        /**
         * Sidecar Server 允许启动的 CLI 命令白名单，逗号分隔。
         * 传递给 Pod 的 ALLOWED_COMMANDS 环境变量。
         */
        private String allowedCommands = "qodercli,qwen,npx,kiro-cli,opencode";

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public long getReusePodIdleTimeout() {
            return reusePodIdleTimeout;
        }

        public void setReusePodIdleTimeout(long reusePodIdleTimeout) {
            this.reusePodIdleTimeout = reusePodIdleTimeout;
        }

        public boolean isSandboxAccessViaService() {
            return sandboxAccessViaService;
        }

        public void setSandboxAccessViaService(boolean sandboxAccessViaService) {
            this.sandboxAccessViaService = sandboxAccessViaService;
        }

        public String getAllowedCommands() {
            return allowedCommands;
        }

        public void setAllowedCommands(String allowedCommands) {
            this.allowedCommands = allowedCommands;
        }
    }
}
