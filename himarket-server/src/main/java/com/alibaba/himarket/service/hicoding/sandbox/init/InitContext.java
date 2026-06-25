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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.web.socket.WebSocketSession;

/**
 * Initialization context shared across phases.
 *
 * <p>Holds the {@link SandboxProvider} reference so phases can execute operations through the
 * provider.
 */
public class InitContext {

    // Core sandbox provider.
    private final SandboxProvider provider;

    // Input parameters.
    private final String userId;
    private final SandboxConfig sandboxConfig;
    private final RuntimeConfig runtimeConfig;
    private final CliProviderConfig providerConfig;
    private final CliSessionConfig sessionConfig;
    private final WebSocketSession frontendSession;

    // Phase outputs.
    private SandboxInfo sandboxInfo;
    private RuntimeAdapter runtimeAdapter;
    private List<ConfigFile> injectedConfigs = new ArrayList<>();
    private ResolvedSessionConfig resolvedSessionConfig;

    // Status tracking.
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

    /**
     * Records an initialization event.
     */
    public void recordEvent(String phase, InitEvent.EventType type, String message) {
        events.add(new InitEvent(Instant.now(), phase, type, message));
    }

    /**
     * Returns the names of all completed phases.
     */
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
