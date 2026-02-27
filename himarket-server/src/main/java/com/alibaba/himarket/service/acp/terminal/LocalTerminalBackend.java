package com.alibaba.himarket.service.acp.terminal;

import com.alibaba.himarket.service.terminal.TerminalProcess;
import java.io.IOException;
import reactor.core.publisher.Flux;

/**
 * 本地终端后端实现。
 * 封装现有 {@link TerminalProcess}（pty4j PTY 进程），保持与改造前完全一致的行为。
 * 工作目录为本地 {workspaceRoot}/{userId}，shell 为 /bin/zsh -l。
 */
public class LocalTerminalBackend implements TerminalBackend {

    private final TerminalProcess terminalProcess;

    /**
     * 创建本地终端后端。
     *
     * @param cwd 工作目录路径（本地 {workspaceRoot}/{userId}）
     */
    public LocalTerminalBackend(String cwd) {
        this.terminalProcess = new TerminalProcess(cwd);
    }

    @Override
    public void start(int cols, int rows) throws IOException {
        terminalProcess.start(cols, rows);
    }

    @Override
    public void write(String data) throws IOException {
        terminalProcess.write(data);
    }

    @Override
    public void resize(int cols, int rows) {
        terminalProcess.resize(cols, rows);
    }

    @Override
    public Flux<byte[]> output() {
        return terminalProcess.output();
    }

    @Override
    public boolean isAlive() {
        return terminalProcess.isAlive();
    }

    @Override
    public void close() {
        terminalProcess.close();
    }
}
