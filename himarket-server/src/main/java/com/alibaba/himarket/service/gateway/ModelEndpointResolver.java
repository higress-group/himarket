package com.alibaba.himarket.service.gateway;

import java.util.List;

/**
 * Normalizes model API paths for SDK, CLI, and generated curl examples.
 *
 * <p>The resolver uses route match type and AI protocol to decide whether a route path already
 * represents a full endpoint or only a base path.
 *
 * <p>Normalization rules:
 *
 * <ul>
 *   <li>Exact: the path is a full endpoint. Base URL strips the endpoint suffix, while endpoint
 *       URL keeps the path as-is.</li>
 *   <li>Prefix: the path is a prefix. The resolver appends a version prefix when needed and builds
 *       endpoint URL from base URL plus the endpoint suffix.</li>
 *   <li>Regex, null, or unknown: the resolver returns the original path.</li>
 * </ul>
 */
public class ModelEndpointResolver {

    private static final String OPENAI_VERSION_PREFIX = "/v1";
    private static final String OPENAI_ENDPOINT_SUFFIX = "/chat/completions";
    private static final String ANTHROPIC_VERSION_PREFIX = "/v1";
    private static final String ANTHROPIC_ENDPOINT_SUFFIX = "/messages";

    /**
     * Resolves a route path to the base URL path used by SDKs and CLIs.
     *
     * @param pathValue route path value
     * @param pathType route match type, such as Exact or Prefix
     * @param aiProtocols AI protocol values
     * @return normalized base URL path
     */
    public static String resolveBaseUrlPath(
            String pathValue, String pathType, List<String> aiProtocols) {
        if (pathValue == null) {
            return null;
        }
        String path = stripTrailingSlash(pathValue);
        String protocol = detectProtocol(aiProtocols);

        if ("unknown".equals(protocol)) {
            return path;
        }

        if (isPrefixType(pathType)) {
            return ensureVersionPrefix(path, protocol);
        }

        if (pathType == null || "Exact".equalsIgnoreCase(pathType)) {
            return stripEndpointSuffix(path, protocol);
        }

        // Regex or other unknown types: return as-is.
        return path;
    }

    /**
     * Resolves a route path to the full API endpoint path.
     *
     * @param pathValue route path value
     * @param pathType route match type, such as Exact or Prefix
     * @param aiProtocols AI protocol values
     * @return normalized full endpoint path
     */
    public static String resolveEndpointPath(
            String pathValue, String pathType, List<String> aiProtocols) {
        if (pathValue == null) {
            return null;
        }
        String path = stripTrailingSlash(pathValue);
        String protocol = detectProtocol(aiProtocols);

        if ("unknown".equals(protocol)) {
            return path;
        }

        if (isPrefixType(pathType)) {
            String base = ensureVersionPrefix(path, protocol);
            return base + getEndpointSuffix(protocol);
        }

        if (pathType == null || "Exact".equalsIgnoreCase(pathType)) {
            // Exact mode: path is already the full endpoint
            return path;
        }

        // Regex or other unknown types: return as-is.
        return path;
    }

    /**
     * Detects the model protocol family.
     */
    static String detectProtocol(List<String> aiProtocols) {
        if (aiProtocols == null || aiProtocols.isEmpty()) {
            return "openai";
        }
        String first = aiProtocols.get(0).toLowerCase();
        if (first.contains("openai")) {
            return "openai";
        }
        if (first.contains("anthropic")) {
            return "anthropic";
        }
        // DashScope or other unknown protocols — no normalization
        return "unknown";
    }

    /**
     * Strips the endpoint suffix from an Exact route path to get the base URL path.
     */
    private static String stripEndpointSuffix(String path, String protocol) {
        String suffix = getEndpointSuffix(protocol);
        if (path.endsWith(suffix)) {
            return path.substring(0, path.length() - suffix.length());
        }
        return path;
    }

    /**
     * Ensures a Prefix route path includes the protocol version path.
     */
    private static String ensureVersionPrefix(String path, String protocol) {
        String versionPrefix = getVersionPrefix(protocol);
        String endpointSuffix = getEndpointSuffix(protocol);

        // If path already ends with the full endpoint suffix (e.g., /v1/chat/completions),
        // strip it and return just the base with version.
        if (path.endsWith(endpointSuffix)) {
            return path.substring(0, path.length() - endpointSuffix.length());
        }

        // If path already ends with version prefix, no need to append.
        if (path.endsWith(versionPrefix)) {
            return path;
        }

        // Append version prefix.
        return path + versionPrefix;
    }

    private static String getVersionPrefix(String protocol) {
        return switch (protocol) {
            case "anthropic" -> ANTHROPIC_VERSION_PREFIX;
            default -> OPENAI_VERSION_PREFIX;
        };
    }

    private static String getEndpointSuffix(String protocol) {
        return switch (protocol) {
            case "anthropic" -> ANTHROPIC_ENDPOINT_SUFFIX;
            default -> OPENAI_ENDPOINT_SUFFIX;
        };
    }

    private static String stripTrailingSlash(String path) {
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    /**
     * Checks whether the route path type is a prefix match.
     */
    private static boolean isPrefixType(String pathType) {
        return "Prefix".equalsIgnoreCase(pathType) || "Pre".equalsIgnoreCase(pathType);
    }
}
