package com.alibaba.himarket.service.hicoding.filesystem;

import java.io.IOException;
import java.util.List;

/**
 * Unified file operation interface.
 *
 * <p>Provides consistent file access for different runtimes and hides lower-level file system
 * differences.
 */
public interface FileSystemAdapter {

    /**
     * Reads file content.
     *
     * @param relativePath path relative to the workspace root
     * @return file content
     * @throws IOException when the read fails
     */
    String readFile(String relativePath) throws IOException;

    /**
     * Writes file content.
     *
     * @param relativePath path relative to the workspace root
     * @param content file content
     * @throws IOException when the write fails
     */
    void writeFile(String relativePath, String content) throws IOException;

    /**
     * Lists directory content.
     *
     * @param relativePath path relative to the workspace root
     * @return directory entries
     * @throws IOException when listing fails
     */
    List<FileEntry> listDirectory(String relativePath) throws IOException;

    /**
     * Creates a directory.
     *
     * @param relativePath path relative to the workspace root
     * @throws IOException when creation fails
     */
    void createDirectory(String relativePath) throws IOException;

    /**
     * Deletes a file or directory.
     *
     * @param relativePath path relative to the workspace root
     * @throws IOException when deletion fails
     */
    void delete(String relativePath) throws IOException;

    /**
     * Gets file metadata.
     *
     * @param relativePath path relative to the workspace root
     * @return file metadata
     * @throws IOException when metadata lookup fails
     */
    FileInfo getFileInfo(String relativePath) throws IOException;
}
