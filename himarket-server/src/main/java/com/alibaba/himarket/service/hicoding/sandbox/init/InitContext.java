package com.alibaba.himarket.service.hicoding.sandbox.init;

import com.alibaba.himarket.config.AcpProperties.CliProviderConfig;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeAdapter;
import com.alibaba.himarket.service.hicoding.runtime.RuntimeConfig;
import com.alibaba.himarket.service.hicoding.sandbox.ConfigFile;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxConfig;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxInfo;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxProvider;
import com.alibaba.himarket.service.hicoding.session.CliSessionConfig;
import com.alibaba.himarket.service.hicoding.session.ResolvedSessionConfig;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.web.socket.WebSocketSession;

/**
 * 初始化上下文，各阶段通过此对象共享数据。 核心改动：持有 SandboxProvider 引用，各阶段通过 provider 执行操作。
 */
public class InitContext {

    // 核心：沙箱提供者
    private final SandboxProvider provider;

    // 输入参数
    private final String userId;
    private final SandboxConfig sandboxConfig;
    private final RuntimeConfig runtimeConfig;
    private final CliProviderConfig providerConfig;
    private final CliSessionConfig sessionConfig;
    private final WebSocketSession frontendSession;

    // 阶段产出
    private SandboxInfo sandboxInfo;
    private RuntimeAdapter runtimeAdapter;
    private List<ConfigFile> injectedConfigs = new ArrayList<>();
    private ResolvedSessionConfig resolvedSessionConfig;

    // 状态追踪
    private final Map<String, PhaseStatus> phaseStatuses = new ConcurrentHashMap<>();
    private final List<InitEvent> events = new CopyOnWriteArrayList<>();
    private String lastError;

    public InitContext(
            SandboxProvider provider,
            String userId,
            SandboxConfig sandboxConfig,
            RuntimeConfig runtimeConfig,
            CliProviderConfig providerConfig,
            CliSessionConfig sessionConfig,
            WebSocketSession frontendSession) {
        this.provider = provider;
        this.userId = userId;
        this.sandboxConfig = sandboxConfig;
        this.runtimeConfig = runtimeConfig;
        this.providerConfig = providerConfig;
        this.sessionConfig = sessionConfig;
        this.frontendSession = frontendSession;
    }

    // ========== Getters ==========

    public SandboxProvider getProvider() {
        return provider;
    }

    public String getUserId() {
        return userId;
    }

    public SandboxConfig getSandboxConfig() {
        return sandboxConfig;
    }

    public RuntimeConfig getRuntimeConfig() {
        return runtimeConfig;
    }

    public CliProviderConfig getProviderConfig() {
        return providerConfig;
    }

    public CliSessionConfig getSessionConfig() {
        return sessionConfig;
    }

    public WebSocketSession getFrontendSession() {
        return frontendSession;
    }

    public SandboxInfo getSandboxInfo() {
        return sandboxInfo;
    }

    public RuntimeAdapter getRuntimeAdapter() {
        return runtimeAdapter;
    }

    public List<ConfigFile> getInjectedConfigs() {
        return injectedConfigs;
    }

    public ResolvedSessionConfig getResolvedSessionConfig() {
        return resolvedSessionConfig;
    }

    public Map<String, PhaseStatus> getPhaseStatuses() {
        return phaseStatuses;
    }

    public List<InitEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public String getLastError() {
        return lastError;
    }

    // ========== Setters（阶段产出 + 状态） ==========

    public void setSandboxInfo(SandboxInfo sandboxInfo) {
        this.sandboxInfo = sandboxInfo;
    }

    public void setRuntimeAdapter(RuntimeAdapter runtimeAdapter) {
        this.runtimeAdapter = runtimeAdapter;
    }

    public void setInjectedConfigs(List<ConfigFile> injectedConfigs) {
        this.injectedConfigs = injectedConfigs;
    }

    public void setResolvedSessionConfig(ResolvedSessionConfig resolvedSessionConfig) {
        this.resolvedSessionConfig = resolvedSessionConfig;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    // ========== 辅助方法 ==========

    /** 记录初始化事件。 */
    public void recordEvent(String phase, InitEvent.EventType type, String message) {
        events.add(new InitEvent(Instant.now(), phase, type, message));
    }

    /** 返回所有已完成阶段的名称列表。 */
    public List<String> completedPhases() {
        List<String> completed = new ArrayList<>();
        for (Map.Entry<String, PhaseStatus> entry : phaseStatuses.entrySet()) {
            if (entry.getValue() == PhaseStatus.COMPLETED) {
                completed.add(entry.getKey());
            }
        }
        return completed;
    }
}
