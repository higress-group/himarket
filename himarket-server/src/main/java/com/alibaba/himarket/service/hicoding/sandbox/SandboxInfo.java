package com.alibaba.himarket.service.hicoding.sandbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Sandbox instance information returned by {@link SandboxProvider#acquire(SandboxConfig)}.
 * Contains all details required to connect to the sandbox.
 */
public record SandboxInfo(
        SandboxType type,
        String sandboxId,
        String host,
        int sidecarPort,
        String workspacePath,
        boolean reused,
        Map<String, String> metadata) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Builds the Sidecar WebSocket URI.
     *
     * @param command command to execute
     * @param args command arguments, nullable or blank
     * @return complete WebSocket URI with URL-encoded args
     */
    public URI sidecarWsUri(String command, String args) {
        return sidecarWsUri(command, args, null);
    }

    /**
     * Builds the Sidecar WebSocket URI with environment variables and working directory.
     *
     * @param command command to execute
     * @param args command arguments, nullable or blank
     * @param env environment variables, nullable
     * @param cwd working directory path, nullable or blank
     * @return complete WebSocket URI with URL-encoded args, env, and cwd
     */
    public URI sidecarWsUri(String command, String args, Map<String, String> env, String cwd) {
        return sidecarWsUri(command, args, env, cwd, null);
    }

    /**
     * Builds the Sidecar WebSocket URI with environment variables, working directory, and sessionId.
     *
     * <p>When sessionId is present, this creates an attach URI without command parameters. The
     * Sidecar will attach to the existing session instead of starting a new process.
     *
     * @param command command to execute, required for new sessions and ignored for attach mode
     * @param args command arguments, nullable or blank
     * @param env environment variables, nullable
     * @param cwd working directory path, nullable or blank
     * @param sessionId Sidecar session ID; present means attach mode
     * @return complete WebSocket URI
     */
    public URI sidecarWsUri(
            String command, String args, Map<String, String> env, String cwd, String sessionId) {
        // Attach mode only needs the sessionId.
        if (sessionId != null && !sessionId.isBlank()) {
            return URI.create(
                    "ws://"
                            + host
                            + ":"
                            + sidecarPort
                            + "/?sessionId="
                            + URLEncoder.encode(sessionId, StandardCharsets.UTF_8));
        }
        // New-session mode requires command parameters.
        String query = "command=" + command;
        if (args != null && !args.isBlank()) {
            query += "&args=" + URLEncoder.encode(args, StandardCharsets.UTF_8);
        }
        if (env != null && !env.isEmpty()) {
            try {
                String envJson = OBJECT_MAPPER.writeValueAsString(env);
                query += "&env=" + URLEncoder.encode(envJson, StandardCharsets.UTF_8);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize env to JSON: " + e.getMessage(), e);
            }
        }
        if (cwd != null && !cwd.isBlank()) {
            query += "&cwd=" + URLEncoder.encode(cwd, StandardCharsets.UTF_8);
        }
        return URI.create("ws://" + host + ":" + sidecarPort + "/?" + query);
    }

    /**
     * Builds the Sidecar WebSocket URI with environment variables.
     *
     * @param command command to execute
     * @param args command arguments, nullable or blank
     * @param env environment variables, nullable
     * @return complete WebSocket URI with URL-encoded args and env
     */
    public URI sidecarWsUri(String command, String args, Map<String, String> env) {
        return sidecarWsUri(command, args, env, null);
    }
}
