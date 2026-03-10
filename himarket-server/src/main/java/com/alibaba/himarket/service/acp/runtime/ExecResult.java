package com.alibaba.himarket.service.acp.runtime;

/**
 * 沙箱命令执行结果。
 */
public record ExecResult(int exitCode, String stdout, String stderr) {}
