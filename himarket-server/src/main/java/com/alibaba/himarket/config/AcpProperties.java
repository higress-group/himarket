package com.alibaba.himarket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "acp")
public class AcpProperties {

    private String cliCommand = "qodercli";

    private String cliArgs = "--acp";

    private String workspaceRoot = "./workspaces";

    public String getCliCommand() {
        return cliCommand;
    }

    public void setCliCommand(String cliCommand) {
        this.cliCommand = cliCommand;
    }

    public String getCliArgs() {
        return cliArgs;
    }

    public void setCliArgs(String cliArgs) {
        this.cliArgs = cliArgs;
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }
}
