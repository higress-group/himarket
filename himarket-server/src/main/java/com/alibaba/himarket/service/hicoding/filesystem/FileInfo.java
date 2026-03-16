package com.alibaba.himarket.service.hicoding.filesystem;

/**
 * 文件元信息。
 */
public record FileInfo(
        String path,
        boolean isDirectory,
        long size,
        long lastModified,
        boolean readable,
        boolean writable) {}
