package com.alibaba.himarket.config;

import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "acp")
public class AcpProperties {

    /**
     * Whether terminal features are enabled.
     */
    private boolean terminalEnabled = true;

    /**
     * Default CLI provider key in the providers map.
     */
    private String defaultProvider = "qwen-code";

    /**
     * Default runtime type used when the user does not select one explicitly.
     */
    private String defaultRuntime = "remote";

    /**
     * CLI provider registry for ACP-compatible tools.
     */
    private Map<String, CliProviderConfig> providers = new LinkedHashMap<>();

    /**
     * Remote sandbox runtime configuration.
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
     * Gets a provider configuration by key.
     */
    public CliProviderConfig getProvider(String key) {
        return providers.get(key);
    }

    /**
     * Gets the default provider configuration.
     */
    public CliProviderConfig getDefaultProviderConfig() {
        return providers.get(defaultProvider);
    }

    /**
     * Configuration for a single CLI provider.
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
     * Remote sandbox runtime configuration.
     */
    public static class RemoteConfig {

        /**
         * Sidecar service host. Leave it empty when remote sandbox is not configured.
         */
        private String host = "";

        /**
         * Sidecar service port.
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
         * Checks whether a remote sandbox host is configured.
         */
        public boolean isConfigured() {
            return host != null && !host.isBlank();
        }
    }
}
