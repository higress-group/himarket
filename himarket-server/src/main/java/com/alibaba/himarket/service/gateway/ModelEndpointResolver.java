package com.alibaba.himarket.service.gateway;

import java.util.List;

/**
 * 模型 API 路径归一化工具类。
 *
 * <p>根据路由匹配类型（Exact/Prefix）和 AI 协议（OpenAI/Anthropic 等）智能补全路径，
 * 确保所有消费者（HiChat SDK、HiCoding CLI、前端 curl 示例）生成正确的 URL。
 *
 * <p>归一化规则：
 * <ul>
 *   <li>Exact: path 是完整端点。baseUrl = 去掉端点后缀；endpointUrl = 原样</li>
 *   <li>Prefix: path 只是前缀。如果不含版本前缀则追加；endpointUrl = baseUrl + 端点后缀</li>
 *   <li>Regex / null / Unknown: 不做处理，原样返回</li>
 * </ul>
 */
public class ModelEndpointResolver {

    private static final String OPENAI_VERSION_PREFIX = "/v1";
    private static final String OPENAI_ENDPOINT_SUFFIX = "/chat/completions";
    private static final String ANTHROPIC_VERSION_PREFIX = "/v1";
    private static final String ANTHROPIC_ENDPOINT_SUFFIX = "/messages";

    /**
     * 归一化路由路径为 SDK/CLI 使用的 baseUrl 路径部分。
     * SDK 会在 baseUrl 后追加 /chat/completions 等端点路径。
     *
     * @param pathValue   路由路径值
     * @param pathType    路由匹配类型（Exact/Prefix）
     * @param aiProtocols AI 协议列表
     * @return 归一化后的 baseUrl 路径
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

        if ("Prefix".equalsIgnoreCase(pathType)) {
            return ensureVersionPrefix(path, protocol);
        }

        if (pathType == null || "Exact".equalsIgnoreCase(pathType)) {
            return stripEndpointSuffix(path, protocol);
        }

        // Regex or other unknown types — return as-is
        return path;
    }

    /**
     * 归一化路由路径为完整的 API 端点路径（用于 curl 示例等）。
     *
     * @param pathValue   路由路径值
     * @param pathType    路由匹配类型（Exact/Prefix）
     * @param aiProtocols AI 协议列表
     * @return 归一化后的完整端点路径
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

        if ("Prefix".equalsIgnoreCase(pathType)) {
            String base = ensureVersionPrefix(path, protocol);
            return base + getEndpointSuffix(protocol);
        }

        if (pathType == null || "Exact".equalsIgnoreCase(pathType)) {
            // Exact mode: path is already the full endpoint
            return path;
        }

        // Regex or other unknown types — return as-is
        return path;
    }

    /**
     * 检测协议类型。复用 ProtocolTypeMapper 的逻辑。
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
     * Exact 模式下去掉端点后缀，得到 baseUrl 路径。
     */
    private static String stripEndpointSuffix(String path, String protocol) {
        String suffix = getEndpointSuffix(protocol);
        if (path.endsWith(suffix)) {
            return path.substring(0, path.length() - suffix.length());
        }
        return path;
    }

    /**
     * Prefix 模式下确保包含版本路径。
     */
    private static String ensureVersionPrefix(String path, String protocol) {
        String versionPrefix = getVersionPrefix(protocol);
        String endpointSuffix = getEndpointSuffix(protocol);

        // If path already ends with the full endpoint suffix (e.g., /v1/chat/completions),
        // strip it and return just the base with version
        if (path.endsWith(endpointSuffix)) {
            return path.substring(0, path.length() - endpointSuffix.length());
        }

        // If path already ends with version prefix, no need to append
        if (path.endsWith(versionPrefix)) {
            return path;
        }

        // Append version prefix
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
}
