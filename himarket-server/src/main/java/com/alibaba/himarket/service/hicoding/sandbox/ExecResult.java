package com.alibaba.himarket.service.hicoding.sandbox;

/**
 * 沙箱命令执行结果。
 */
public record ExecResult(int exitCode, String stdout, String stderr) {}
