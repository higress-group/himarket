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

package com.alibaba.himarket.service.hicoding.terminal;

import java.io.IOException;
import reactor.core.publisher.Flux;

/**
 * 终端后端抽象接口。
 * 统一本地 PTY 和 K8s Exec 两种终端实现的调用方式。
 */
public interface TerminalBackend {

    /**
     * 启动终端。
     *
     * @param cols 终端列数
     * @param rows 终端行数
     * @throws IOException 启动失败时抛出
     */
    void start(int cols, int rows) throws IOException;

    /**
     * 写入用户输入。
     *
     * @param data 用户输入数据
     * @throws IOException 写入失败时抛出
     */
    void write(String data) throws IOException;

    /**
     * 调整终端大小。
     *
     * @param cols 新的列数
     * @param rows 新的行数
     */
    void resize(int cols, int rows);

    /**
     * 终端输出的响应式流。
     *
     * @return 终端输出字节数组的 Flux 流
     */
    Flux<byte[]> output();

    /**
     * 终端是否存活。
     *
     * @return true 表示终端进程仍在运行
     */
    boolean isAlive();

    /**
     * 关闭终端，释放相关资源。
     */
    void close();
}
