package com.alibaba.himarket.service.hicoding.runtime;

import com.alibaba.himarket.service.hicoding.filesystem.FileSystemAdapter;
import com.alibaba.himarket.service.hicoding.sandbox.SandboxType;
import java.io.IOException;
import reactor.core.publisher.Flux;

/**
 * 运行时抽象层核心接口。
 * <p>
 * 定义所有运行时实现（Local、K8s）必须遵循的统一契约，
 * 屏蔽底层运行时差异，使上层业务代码无需感知具体运行时类型。
 */
public interface RuntimeAdapter {

    /**
     * 获取运行时类型标识。
     */
    SandboxType getType();

    /**
     * 启动运行时实例。
     *
     * @param config 运行时配置
     * @return 实例 ID
     * @throws RuntimeException 启动失败时抛出
     */
    String start(RuntimeConfig config) throws RuntimeException;

    /**
     * 发送 JSON-RPC 消息到 CLI 进程。
     *
     * @param jsonLine JSON-RPC 消息字符串
     * @throws IOException 发送失败时抛出
     */
    void send(String jsonLine) throws IOException;

    /**
     * 获取 CLI 进程输出的响应式流。
     * <p>
     * 每个元素是一行 JSON-RPC 响应消息。
     *
     * @return stdout 响应式流
     */
    Flux<String> stdout();

    /**
     * 查询运行时实例当前状态。
     *
     * @return 运行时状态
     */
    RuntimeStatus getStatus();

    /**
     * 检查运行时实例是否存活。
     *
     * @return true 表示存活
     */
    boolean isAlive();

    /**
     * 优雅关闭运行时实例，释放相关资源。
     */
    void close();

    /**
     * 获取文件系统适配器，用于操作运行时工作空间中的文件。
     *
     * @return 文件系统适配器
     */
    FileSystemAdapter getFileSystem();
}
