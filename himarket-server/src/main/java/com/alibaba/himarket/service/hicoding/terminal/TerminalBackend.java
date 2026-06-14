package com.alibaba.himarket.service.hicoding.terminal;

import java.io.IOException;
import reactor.core.publisher.Flux;

/**
 * Terminal backend abstraction.
 * Provides a unified interface for terminal implementations.
 */
public interface TerminalBackend {

    /**
     * Starts the terminal.
     *
     * @param cols terminal columns
     * @param rows terminal rows
     * @throws IOException when startup fails
     */
    void start(int cols, int rows) throws IOException;

    /**
     * Writes user input.
     *
     * @param data user input
     * @throws IOException when writing fails
     */
    void write(String data) throws IOException;

    /**
     * Resizes the terminal.
     *
     * @param cols new column count
     * @param rows new row count
     */
    void resize(int cols, int rows);

    /**
     * Returns the reactive terminal output stream.
     *
     * @return terminal output byte stream
     */
    Flux<byte[]> output();

    /**
     * Checks whether the terminal is alive.
     *
     * @return true when the terminal process is still running
     */
    boolean isAlive();

    /**
     * Closes the terminal and releases resources.
     */
    void close();
}
