package com.alibaba.himarket.service.hicoding.filesystem;

/**
 * File metadata.
 */
public record FileInfo(
        String path,
        boolean isDirectory,
        long size,
        long lastModified,
        boolean readable,
        boolean writable) {}
