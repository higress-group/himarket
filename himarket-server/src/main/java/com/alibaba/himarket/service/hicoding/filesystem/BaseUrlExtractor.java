package com.alibaba.himarket.service.hicoding.filesystem;

import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.dto.result.httpapi.HttpRouteResult;
import java.util.List;

/**
 * 从 MODEL_API 产品的路由配置中提取 baseUrl 的工具类。
 *
 * <p>提取规则：
 * <ul>
 *   <li>从 routes[0].domains[0] 中获取 protocol、domain、port</li>
 *   <li>从 routes[0].match.path.value 中获取 pathPrefix（去掉 /chat/completions 后缀）</li>
 *   <li>端口处理：null 或标准端口（http:80, https:443）时省略，非标准端口时包含</li>
 * </ul>
 *
 * <p>输出格式：{protocol}://{domain}[:{port}]{pathPrefix}
 */
public class BaseUrlExtractor {

    private static final String CHAT_COMPLETIONS_SUFFIX = "/chat/completions";
    private static final int HTTP_DEFAULT_PORT = 80;
    private static final int HTTPS_DEFAULT_PORT = 443;

    /**
     * 从产品的路由配置中提取 baseUrl。
     *
     * @param routes 产品的路由列表
     * @return 提取的 baseUrl，如果路由数据不完整则返回 null
     */
    public static String extract(List<HttpRouteResult> routes) {
        if (routes == null || routes.isEmpty()) {
            return null;
        }

        HttpRouteResult firstRoute = routes.get(0);

        // 提取 domain 信息
        List<DomainResult> domains = firstRoute.getDomains();
        if (domains == null || domains.isEmpty()) {
            return null;
        }

        DomainResult domain = domains.get(0);
        if (domain.getDomain() == null || domain.getProtocol() == null) {
            return null;
        }

        // 提取 path
        if (firstRoute.getMatch() == null
                || firstRoute.getMatch().getPath() == null
                || firstRoute.getMatch().getPath().getValue() == null) {
            return null;
        }

        String protocol = domain.getProtocol();
        String host = domain.getDomain();
        Integer port = domain.getPort();
        String pathValue = firstRoute.getMatch().getPath().getValue();

        // 拼接 baseUrl
        StringBuilder sb = new StringBuilder();
        sb.append(protocol).append("://").append(host);

        // 端口处理：null 或标准端口时省略
        if (port != null && !isStandardPort(protocol, port)) {
            sb.append(":").append(port);
        }

        // path 处理：去掉 /chat/completions 后缀
        String pathPrefix = stripChatCompletionsSuffix(pathValue);
        sb.append(pathPrefix);

        return sb.toString();
    }

    private static boolean isStandardPort(String protocol, int port) {
        return ("http".equalsIgnoreCase(protocol) && port == HTTP_DEFAULT_PORT)
                || ("https".equalsIgnoreCase(protocol) && port == HTTPS_DEFAULT_PORT);
    }

    private static String stripChatCompletionsSuffix(String path) {
        if (path.endsWith(CHAT_COMPLETIONS_SUFFIX)) {
            return path.substring(0, path.length() - CHAT_COMPLETIONS_SUFFIX.length());
        }
        return path;
    }
}
