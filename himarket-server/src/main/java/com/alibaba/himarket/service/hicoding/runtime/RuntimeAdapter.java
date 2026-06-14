package com.alibaba.himarket.service.hicoding.runtime;

import com.alibaba.himarket.service.hicoding.filesystem.FileSystemAdapter;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;
import java.io.IOException;
import reactor.core.publisher.Flux;

/**
 * Core runtime abstraction interface.
 * <p>
 * Defines the contract all runtime implementations must follow and hides lower-level runtime
 * differences from upper-layer business code.
 */
public interface RuntimeAdapter {

    /**
     * Returns the runtime type.
     */
    SandboxType getType();

    /**
     * Starts a runtime instance.
     *
     * @param config runtime configuration
     * @return instance ID
     * @throws RuntimeException when startup fails
     */
    String start(RuntimeConfig config) throws RuntimeException;

    /**
     * Sends a JSON-RPC message to the CLI process.
     *
     * @param jsonLine JSON-RPC message string
     * @throws IOException when sending fails
     */
    void send(String jsonLine) throws IOException;

    /**
     * Returns the reactive stream of CLI process output.
     * <p>
     * Each element is one JSON-RPC response line.
     *
     * @return stdout stream
     */
    Flux<String> stdout();

    /**
     * Gets the current runtime instance status.
     *
     * @return runtime status
     */
    RuntimeStatus getStatus();

    /**
     * Checks whether the runtime instance is alive.
     *
     * @return true when alive
     */
    boolean isAlive();

    /**
     * Gracefully closes the runtime instance and releases resources.
     */
    void close();

    /**
     * Gets the file system adapter for runtime workspace operations.
     *
     * @return file system adapter
     */
    FileSystemAdapter getFileSystem();
}
