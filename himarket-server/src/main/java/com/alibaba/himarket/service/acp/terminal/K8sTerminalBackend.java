package com.alibaba.himarket.service.acp.terminal;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * K8s Pod 内的终端后端。
 * 通过 fabric8 K8s client 的 exec API 在 Pod 容器内 spawn 交互式 shell，
 * 将 stdin/stdout 双向桥接到 WebSocket。
 */
public class K8sTerminalBackend implements TerminalBackend {

    private static final Logger logger = LoggerFactory.getLogger(K8sTerminalBackend.class);

    private final KubernetesClient k8sClient;
    private final String podName;
    private final String namespace;
    private final String containerName;

    private ExecWatch execWatch;
    private volatile boolean closed = false;

    private final Sinks.Many<byte[]> outputSink = Sinks.many().multicast().onBackpressureBuffer();
    private Scheduler readerScheduler;

    /**
     * 创建 K8s 终端后端。
     *
     * @param k8sClient     fabric8 KubernetesClient 实例
     * @param podName       目标 Pod 名称
     * @param namespace     Pod 所在命名空间
     * @param containerName Pod 内目标容器名称
     */
    public K8sTerminalBackend(
            KubernetesClient k8sClient, String podName, String namespace, String containerName) {
        this.k8sClient = k8sClient;
        this.podName = podName;
        this.namespace = namespace;
        this.containerName = containerName;
    }

    @Override
    public void start(int cols, int rows) throws IOException {
        logger.info(
                "Starting K8s terminal: pod={}, namespace={}, container={}, size={}x{}",
                podName,
                namespace,
                containerName,
                cols,
                rows);

        try {
            execWatch =
                    k8sClient
                            .pods()
                            .inNamespace(namespace)
                            .withName(podName)
                            .inContainer(containerName)
                            .redirectingInput()
                            .redirectingOutput()
                            .redirectingError()
                            .withTTY()
                            .exec("/bin/sh", "-l");
        } catch (Exception e) {
            logger.error(
                    "Failed to establish K8s exec connection for pod {}: {}",
                    podName,
                    e.getMessage());
            outputSink.tryEmitNext("\r\n[终端连接失败]\r\n".getBytes(StandardCharsets.UTF_8));
            outputSink.tryEmitComplete();
            close();
            throw new IOException("K8s exec connection failed for pod " + podName, e);
        }

        // 启动后台线程从 ExecWatch 的 output 流读取数据并推送到 Flux sink
        this.readerScheduler =
                Schedulers.fromExecutorService(
                        Executors.newSingleThreadExecutor(
                                r -> {
                                    Thread t = new Thread(r, "k8s-terminal-reader-" + podName);
                                    t.setDaemon(true);
                                    return t;
                                }));

        readerScheduler.schedule(
                () -> {
                    byte[] buffer = new byte[4096];
                    try (InputStream is = execWatch.getOutput()) {
                        int bytesRead;
                        while (!closed && (bytesRead = is.read(buffer)) != -1) {
                            byte[] data = new byte[bytesRead];
                            System.arraycopy(buffer, 0, data, 0, bytesRead);
                            outputSink.tryEmitNext(data);
                        }
                    } catch (IOException e) {
                        if (!closed) {
                            logger.error(
                                    "K8s terminal connection interrupted for pod {}: {}",
                                    podName,
                                    e.getMessage());
                            outputSink.tryEmitNext(
                                    "\r\n[Shell exited]\r\n".getBytes(StandardCharsets.UTF_8));
                            outputSink.tryEmitComplete();
                            close();
                            return;
                        }
                    }
                    outputSink.tryEmitComplete();
                });

        logger.info("K8s terminal started for pod {}", podName);
    }

    @Override
    public void write(String data) throws IOException {
        if (closed || execWatch == null) {
            throw new IOException("K8s terminal is closed");
        }
        execWatch.getInput().write(data.getBytes(StandardCharsets.UTF_8));
        execWatch.getInput().flush();
    }

    @Override
    public void resize(int cols, int rows) {
        if (execWatch == null || closed) return;
        try {
            execWatch.resize(cols, rows);
            logger.trace("K8s terminal resized to {}x{} for pod {}", cols, rows, podName);
        } catch (Exception e) {
            logger.warn("Failed to resize K8s terminal for pod {}: {}", podName, e.getMessage());
        }
    }

    @Override
    public Flux<byte[]> output() {
        return outputSink.asFlux();
    }

    @Override
    public boolean isAlive() {
        return execWatch != null && !closed;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        logger.info("Closing K8s terminal for pod {}", podName);

        outputSink.tryEmitComplete();

        if (execWatch != null) {
            try {
                execWatch.close();
            } catch (Exception e) {
                logger.warn("Error closing ExecWatch for pod {}: {}", podName, e.getMessage());
            }
        }

        if (readerScheduler != null) {
            readerScheduler.dispose();
        }

        logger.info("K8s terminal closed for pod {}", podName);
    }
}
