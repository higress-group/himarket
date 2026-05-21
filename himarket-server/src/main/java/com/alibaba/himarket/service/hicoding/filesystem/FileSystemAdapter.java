/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.alibaba.himarket.service.hicoding.filesystem;

import java.io.IOException;
import java.util.List;

/**
 * 统一的文件操作接口。
 * <p>
 * 为不同运行时提供一致的文件访问能力，屏蔽底层文件系统差异
 * （本地文件系统、K8s Pod 文件系统）。
 */
public interface FileSystemAdapter {

    /**
     * 读取文件内容。
     *
     * @param relativePath 相对于工作空间根目录的路径
     * @return 文件内容
     * @throws IOException 读取失败时抛出
     */
    String readFile(String relativePath) throws IOException;

    /**
     * 写入文件内容。
     *
     * @param relativePath 相对于工作空间根目录的路径
     * @param content      文件内容
     * @throws IOException 写入失败时抛出
     */
    void writeFile(String relativePath, String content) throws IOException;

    /**
     * 列举目录内容。
     *
     * @param relativePath 相对于工作空间根目录的路径
     * @return 目录条目列表
     * @throws IOException 列举失败时抛出
     */
    List<FileEntry> listDirectory(String relativePath) throws IOException;

    /**
     * 创建目录。
     *
     * @param relativePath 相对于工作空间根目录的路径
     * @throws IOException 创建失败时抛出
     */
    void createDirectory(String relativePath) throws IOException;

    /**
     * 删除文件或目录。
     *
     * @param relativePath 相对于工作空间根目录的路径
     * @throws IOException 删除失败时抛出
     */
    void delete(String relativePath) throws IOException;

    /**
     * 获取文件元信息。
     *
     * @param relativePath 相对于工作空间根目录的路径
     * @return 文件元信息
     * @throws IOException 获取失败时抛出
     */
    FileInfo getFileInfo(String relativePath) throws IOException;
}
