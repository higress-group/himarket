package com.alibaba.himarket.service.hicoding.filesystem;

/**
 * 文件目录条目。
 */
public record FileEntry(String name, boolean isDirectory, long size, long lastModified) {}
