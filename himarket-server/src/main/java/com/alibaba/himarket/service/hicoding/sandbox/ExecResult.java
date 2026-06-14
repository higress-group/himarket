package com.alibaba.himarket.service.hicoding.sandbox;

/**
 * Sandbox command execution result.
 */
public record ExecResult(int exitCode, String stdout, String stderr) {}
