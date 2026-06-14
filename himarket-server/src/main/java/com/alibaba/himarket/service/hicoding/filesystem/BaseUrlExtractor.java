package com.alibaba.himarket.service.hicoding.filesystem;

import com.alibaba.himarket.dto.result.common.DomainResult;
import com.alibaba.himarket.dto.result.httpapi.HttpRouteResult;
import com.alibaba.himarket.service.gateway.ModelEndpointResolver;
import java.util.List;

/**
 * Extracts baseUrl from MODEL_API product route configuration.
 *
 * <p>Extraction rules:
 * <ul>
 *   <li>Read protocol, domain, and port from routes[0].domains[0].</li>
 *   <li>Read pathPrefix from routes[0].match.path and normalize it through ModelEndpointResolver.</li>
 *   <li>Omit null or standard ports (http:80, https:443); include non-standard ports.</li>
 * </ul>
 *
 * <p>Output format: {protocol}://{domain}[:{port}]{pathPrefix}
 */
public class BaseUrlExtractor {

    private static final int HTTP_DEFAULT_PORT = 80;
    private static final int HTTPS_DEFAULT_PORT = 443;

    /**
     * Extracts baseUrl from product route configuration.
     *
     * @param routes product routes
     * @param aiProtocols AI protocol list
     * @return extracted baseUrl, or null when route data is incomplete
     */
    public static String extract(List<HttpRouteResult> routes, List<String> aiProtocols) {
        if (routes == null || routes.isEmpty()) {
            return null;
        }

        HttpRouteResult firstRoute = routes.get(0);

        // Extract domain details.
        List<DomainResult> domains = firstRoute.getDomains();
        if (domains == null || domains.isEmpty()) {
            return null;
        }

        DomainResult domain = domains.get(0);
        if (domain.getDomain() == null || domain.getProtocol() == null) {
            return null;
        }

        // Extract path.
        if (firstRoute.getMatch() == null
                || firstRoute.getMatch().getPath() == null
                || firstRoute.getMatch().getPath().getValue() == null) {
            return null;
        }

        String protocol = domain.getProtocol();
        String host = domain.getDomain();
        Integer port = domain.getPort();
        String pathValue = firstRoute.getMatch().getPath().getValue();
        String pathType = firstRoute.getMatch().getPath().getType();

        // Build baseUrl.
        StringBuilder sb = new StringBuilder();
        sb.append(protocol).append("://").append(host);

        // Omit null or standard ports.
        if (port != null && !isStandardPort(protocol, port)) {
            sb.append(":").append(port);
        }

        // Normalize the path through ModelEndpointResolver.
        String pathPrefix =
                ModelEndpointResolver.resolveBaseUrlPath(pathValue, pathType, aiProtocols);
        sb.append(pathPrefix);

        return sb.toString();
    }

    private static boolean isStandardPort(String protocol, int port) {
        return ("http".equalsIgnoreCase(protocol) && port == HTTP_DEFAULT_PORT)
                || ("https".equalsIgnoreCase(protocol) && port == HTTPS_DEFAULT_PORT);
    }
}
