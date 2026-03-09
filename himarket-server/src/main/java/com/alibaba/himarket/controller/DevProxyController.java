package com.alibaba.himarket.controller;

import com.alibaba.himarket.core.annotation.AdminOrDeveloperAuth;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import com.alibaba.himarket.service.acp.AcpConnectionManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Tag(name = "DevProxy", description = "Reverse proxy to dev servers running in workspace")
@RestController
@RequestMapping("/workspace/proxy")
@Slf4j
@AdminOrDeveloperAuth
public class DevProxyController {

    private static final int MIN_PORT = 1024;
    private static final int MAX_PORT = 65535;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final AcpConnectionManager connectionManager;

    public DevProxyController(
            WebClient.Builder webClientBuilder, AcpConnectionManager connectionManager) {
        this.webClient =
                webClientBuilder
                        .codecs(
                                configurer ->
                                        configurer
                                                .defaultCodecs()
                                                .maxInMemorySize(10 * 1024 * 1024))
                        .build();
        this.connectionManager = connectionManager;
    }

    @Operation(summary = "Proxy requests to dev server running in user's sandbox")
    @RequestMapping("/{port}/**")
    public Mono<ResponseEntity<byte[]>> proxy(
            @PathVariable int port,
            HttpServletRequest request,
            @RequestBody(required = false) byte[] body) {

        if (port < MIN_PORT || port > MAX_PORT) {
            return Mono.just(
                    ResponseEntity.badRequest()
                            .body(
                                    ("Invalid port: "
                                                    + port
                                                    + ". Must be "
                                                    + MIN_PORT
                                                    + "-"
                                                    + MAX_PORT)
                                            .getBytes()));
        }

        String userId = getCurrentUserId();
        String sandboxHost = connectionManager.getSandboxHost(userId);
        if (sandboxHost == null) {
            return Mono.just(ResponseEntity.status(404).body("No active sandbox found".getBytes()));
        }

        String requestUri = request.getRequestURI();
        String prefix = "/workspace/proxy/" + port;
        String remainingPath =
                requestUri.length() > prefix.length() ? requestUri.substring(prefix.length()) : "/";
        if (remainingPath.isEmpty()) {
            remainingPath = "/";
        }

        String queryString = request.getQueryString();
        URI targetUri =
                UriComponentsBuilder.newInstance()
                        .scheme("http")
                        .host(sandboxHost)
                        .port(port)
                        .path(remainingPath)
                        .query(queryString)
                        .build(true)
                        .toUri();

        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        WebClient.RequestBodySpec reqSpec =
                webClient
                        .method(method)
                        .uri(targetUri)
                        .headers(headers -> copyHeaders(request, headers));

        WebClient.RequestHeadersSpec<?> headersSpec;
        if (body != null && body.length > 0) {
            headersSpec = reqSpec.bodyValue(body);
        } else {
            headersSpec = reqSpec;
        }

        return headersSpec
                .exchangeToMono(
                        clientResponse -> {
                            HttpHeaders responseHeaders = new HttpHeaders();
                            clientResponse
                                    .headers()
                                    .asHttpHeaders()
                                    .forEach(
                                            (name, values) -> {
                                                String lower = name.toLowerCase();
                                                if (!lower.equals("transfer-encoding")
                                                        && !lower.equals("connection")) {
                                                    responseHeaders.addAll(name, values);
                                                }
                                            });

                            return clientResponse
                                    .bodyToMono(byte[].class)
                                    .defaultIfEmpty(new byte[0])
                                    .map(
                                            responseBody ->
                                                    ResponseEntity.status(
                                                                    clientResponse
                                                                            .statusCode()
                                                                            .value())
                                                            .headers(h -> h.addAll(responseHeaders))
                                                            .body(responseBody));
                        })
                .timeout(TIMEOUT)
                .onErrorResume(
                        ex -> {
                            log.warn(
                                    "Proxy error for user {} port {}: {}",
                                    userId,
                                    port,
                                    ex.getMessage());
                            return Mono.just(
                                    ResponseEntity.status(502)
                                            .body(("Proxy error: " + ex.getMessage()).getBytes()));
                        });
    }

    private String getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String principal) {
            return principal;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未认证");
    }

    private static void copyHeaders(HttpServletRequest request, HttpHeaders target) {
        var headerNames = request.getHeaderNames();
        if (headerNames == null) return;
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            String lower = name.toLowerCase();
            if (lower.equals("host")
                    || lower.equals("authorization")
                    || lower.equals("cookie")
                    || lower.equals("connection")) {
                continue;
            }
            var values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                target.add(name, values.nextElement());
            }
        }
    }
}
