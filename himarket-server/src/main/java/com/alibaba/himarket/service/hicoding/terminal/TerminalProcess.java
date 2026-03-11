package com.alibaba.himarket.service.hicoding.terminal;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Manages an interactive shell process with PTY support.
 * Uses pty4j to provide a proper pseudo-terminal so that interactive
 * programs (tab completion, colors, Ctrl+C, etc.) work correctly.
 */
public class TerminalProcess {

    private static final Logger logger = LoggerFactory.getLogger(TerminalProcess.class);

    private final String cwd;
    private PtyProcess process;
    private OutputStream stdin;
    private volatile boolean closed = false;

    private final Sinks.Many<byte[]> outputSink = Sinks.many().multicast().onBackpressureBuffer();
    private Scheduler readerScheduler;

    public TerminalProcess(String cwd) {
        this.cwd = cwd;
    }

    /**
     * Start the shell process with PTY.
     */
    public void start(int initialCols, int initialRows) throws IOException {
        String shell = "/bin/zsh";

        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("TERM", "xterm-256color");
        env.put("COLORTERM", "truecolor");

        PtyProcessBuilder builder =
                new PtyProcessBuilder(new String[] {shell, "-l"})
                        .setDirectory(cwd)
                        .setEnvironment(env)
                        .setInitialColumns(initialCols)
                        .setInitialRows(initialRows);

        this.process = builder.start();
        this.stdin = process.getOutputStream();

        logger.info(
                "Terminal process started: shell={}, cwd={}, pid={}, size={}x{}",
                shell,
                cwd,
                process.pid(),
                initialCols,
                initialRows);

        // Background thread to read PTY output
        this.readerScheduler =
                Schedulers.fromExecutorService(
                        Executors.newSingleThreadExecutor(
                                r -> {
                                    Thread t = new Thread(r, "terminal-reader");
                                    t.setDaemon(true);
                                    return t;
                                }));

        readerScheduler.schedule(
                () -> {
                    byte[] buffer = new byte[4096];
                    try (InputStream is = process.getInputStream()) {
                        int bytesRead;
                        while (!closed && (bytesRead = is.read(buffer)) != -1) {
                            byte[] data = new byte[bytesRead];
                            System.arraycopy(buffer, 0, data, 0, bytesRead);
                            outputSink.tryEmitNext(data);
                        }
                    } catch (IOException e) {
                        if (!closed) {
                            logger.error("Error reading terminal output", e);
                        }
                    } finally {
                        outputSink.tryEmitComplete();
                    }
                });
    }

    /**
     * Write user input to the shell stdin.
     */
    public synchronized void write(String data) throws IOException {
        if (closed || stdin == null) {
            throw new IOException("Terminal process is closed");
        }
        stdin.write(data.getBytes(StandardCharsets.UTF_8));
        stdin.flush();
    }

    /**
     * Resize the terminal.
     */
    public void resize(int cols, int rows) {
        if (process == null || !process.isAlive()) return;
        try {
            process.setWinSize(new WinSize(cols, rows));
            logger.trace("Terminal resized to {}x{}", cols, rows);
        } catch (Exception e) {
            logger.warn("Failed to resize terminal: {}", e.getMessage());
        }
    }

    /**
     * Reactive stream of terminal output (raw bytes).
     */
    public Flux<byte[]> output() {
        return outputSink.asFlux();
    }

    /**
     * Check if the shell process is still alive.
     */
    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    /**
     * Gracefully close the shell process.
     */
    public void close() {
        if (closed) return;
        closed = true;
        logger.info("Closing terminal process");

        outputSink.tryEmitComplete();

        if (process != null) {
            process.destroy();
            try {
                boolean exited = process.waitFor(5, TimeUnit.SECONDS);
                if (!exited) {
                    logger.warn("Terminal process did not exit in time, force killing");
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            logger.info(
                    "Terminal process stopped (exit={})",
                    process.isAlive() ? "still running" : process.exitValue());
        }

        if (readerScheduler != null) readerScheduler.dispose();
    }
}
