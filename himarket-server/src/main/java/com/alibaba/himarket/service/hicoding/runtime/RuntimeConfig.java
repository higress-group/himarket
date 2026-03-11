package com.alibaba.himarket.service.hicoding.runtime;

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
}
