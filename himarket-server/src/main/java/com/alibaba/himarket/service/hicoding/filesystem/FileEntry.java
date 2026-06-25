package com.alibaba.himarket.service.hicoding.filesystem;

/**
 * File or directory entry.
 */
public record FileEntry(String name, boolean isDirectory, long size, long lastModified) {}
