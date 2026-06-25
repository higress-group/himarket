package com.alibaba.himarket.service.hicoding.sandbox;

import java.net.URI;

/**
 * Pod information transfer object returned by acquirePod.
 *
 * @param podName Pod name
 * @param podIp Pod IP address
 * @param serviceIp Service ClusterIP or LoadBalancer IP, nullable and present only when a Service
 *     was created
 * @param sidecarWsUri Sidecar WebSocket endpoint URI
 * @param reused whether an existing Pod was reused
 */
public record PodInfo(
        String podName, String podIp, String serviceIp, URI sidecarWsUri, boolean reused) {}
