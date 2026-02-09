package com.alibaba.himarket.service.acp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Manages a qodercli --acp subprocess.
 * Provides reactive stdin/stdout access for transparent proxying.
 */
public class AcpProcess {

    private static final Logger logger = LoggerFactory.getLogger(AcpProcess.class);

    private final String command;
    private final List<String> args;
    private final String cwd;
    private Process process;
    private OutputStream stdin;
    private volatile boolean closed = false;

    private final Sinks.Many<String> stdoutSink = Sinks.many().multicast().onBackpressureBuffer();
    private final Sinks.Many<String> stderrSink = Sinks.many().multicast().onBackpressureBuffer();

    private Scheduler stdoutScheduler;
    private Scheduler stderrScheduler;

    public AcpProcess(String command, List<String> args, String cwd) {
        this.command = command;
        this.args = args;
        this.cwd = cwd;
    }

    /**
     * Start the qodercli subprocess.
     */
    public void start() throws IOException {
        List<String> fullCommand = new ArrayList<>();
        fullCommand.add(command);
        fullCommand.addAll(args);

        logger.info("Starting ACP process: {}", String.join(" ", fullCommand));

        ProcessBuilder pb = new ProcessBuilder(fullCommand);
        pb.redirectErrorStream(false);
        if (cwd != null) {
            pb.directory(new java.io.File(cwd));
        }
        this.process = pb.start();
        this.stdin = process.getOutputStream();

        // Dedicated daemon threads for reading stdout/stderr
        this.stdoutScheduler =
                Schedulers.fromExecutorService(
                        Executors.newSingleThreadExecutor(
                                r -> {
                                    Thread t = new Thread(r, "acp-stdout");
                                    t.setDaemon(true);
                                    return t;
                                }));
        this.stderrScheduler =
                Schedulers.fromExecutorService(
                        Executors.newSingleThreadExecutor(
                                r -> {
                                    Thread t = new Thread(r, "acp-stderr");
                                    t.setDaemon(true);
                                    return t;
                                }));

        // Read stdout lines → stdoutSink
        stdoutScheduler.schedule(
                () -> {
                    try (BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            process.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while (!closed && (line = reader.readLine()) != null) {
                            logger.trace("STDOUT: {}", line);
                            stdoutSink.tryEmitNext(line);
                        }
                    } catch (IOException e) {
                        if (!closed) {
                            logger.error("Error reading stdout", e);
                        }
                    } finally {
                        stdoutSink.tryEmitComplete();
                    }
                });

        // Read stderr lines → log
        stderrScheduler.schedule(
                () -> {
                    try (BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            process.getErrorStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while (!closed && (line = reader.readLine()) != null) {
                            logger.debug("STDERR: {}", line);
                            stderrSink.tryEmitNext(line);
                        }
                    } catch (IOException e) {
                        if (!closed) {
                            logger.error("Error reading stderr", e);
                        }
                    } finally {
                        stderrSink.tryEmitComplete();
                    }
                });

        logger.info("ACP process started (pid={})", process.pid());
    }

    /**
     * Send a raw JSON line to the subprocess stdin.
     */
    public synchronized void send(String jsonLine) throws IOException {
        if (closed || stdin == null) {
            throw new IOException("Process is closed");
        }
        // Escape embedded newlines per ACP spec
        String escaped = jsonLine.replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
        logger.trace("STDIN: {}", escaped);
        stdin.write(escaped.getBytes(StandardCharsets.UTF_8));
        stdin.write('\n');
        stdin.flush();
    }

    /**
     * Reactive stream of stdout lines (each line is a JSON-RPC message from qodercli).
     */
    public Flux<String> stdout() {
        return stdoutSink.asFlux();
    }

    /**
     * Reactive stream of stderr lines (for debugging).
     */
    public Flux<String> stderr() {
        return stderrSink.asFlux();
    }

    /**
     * Check if the underlying process is still alive.
     */
    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    /**
     * Gracefully close the subprocess.
     */
    public void close() {
        if (closed) return;
        closed = true;
        logger.info("Closing ACP process");

        stdoutSink.tryEmitComplete();
        stderrSink.tryEmitComplete();

        if (process != null) {
            process.destroy();
            try {
                boolean exited = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                if (!exited) {
                    logger.warn("Process did not exit in time, force killing");
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            logger.info(
                    "ACP process stopped (exit={})",
                    process.isAlive() ? "still running" : process.exitValue());
        }

        if (stdoutScheduler != null) stdoutScheduler.dispose();
        if (stderrScheduler != null) stderrScheduler.dispose();
    }
}
