# 实现计划：HiCoding 终端与文件树 K8s 沙箱对接

## 概述

将 HiCoding 的终端（TerminalPanel）和文件树/文件内容（WorkspaceController）改造为根据 runtime 类型动态路由：本地模式保持现有行为不变，K8s 模式下终端通过 K8s Exec API 在 Pod 内 spawn 交互式 shell，文件操作通过 Sidecar HTTP API 读写 Pod 内文件。实现分为后端终端抽象、后端文件服务、Sidecar 扩展、前端参数传递、废弃代码清理五个阶段。

## 任务

- [x] 1. 后端 TerminalBackend 接口抽象与 LocalTerminalBackend 实现
  - [x] 1.1 创建 `TerminalBackend` 接口，定义 start、write、resize、output、isAlive、close 六个方法
    - 新建文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/terminal/TerminalBackend.java`
    - `start(int cols, int rows)` 启动终端
    - `write(String data)` 写入用户输入
    - `resize(int cols, int rows)` 调整终端大小
    - `output()` 返回 `Flux<byte[]>` 终端输出响应式流
    - `isAlive()` 返回终端是否存活
    - `close()` 关闭终端
    - _需求：1.3_

  - [x] 1.2 创建 `LocalTerminalBackend`，封装现有 `TerminalProcess` 逻辑
    - 新建文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/terminal/LocalTerminalBackend.java`
    - 实现 `TerminalBackend` 接口，内部委托给现有 `TerminalProcess`
    - 工作目录为本地 `{workspaceRoot}/{userId}`，shell 为 `/bin/zsh -l`
    - 行为与改造前完全一致
    - _需求：1.4, 11.1_

  - [ ]* 1.3 编写 LocalTerminalBackend 单元测试
    - 验证封装后的本地 PTY 行为与改造前一致
    - _需求：1.4, 11.1_

- [x] 2. K8sTerminalBackend 实现
  - [x] 2.1 创建 `K8sTerminalBackend`，通过 fabric8 K8s Exec API 在 Pod 内启动交互式 shell
    - 新建文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/terminal/K8sTerminalBackend.java`
    - 实现 `TerminalBackend` 接口
    - `start()` 通过 `k8sClient.pods().exec()` 启动 `/bin/sh -l`，启用 TTY 模式
    - `write()` 将数据写入 `ExecWatch.getInput()` 流
    - `resize()` 调用 `ExecWatch.resize(cols, rows)`
    - `output()` 从 `ExecWatch.getOutput()` 读取输出并通过 Flux 发布
    - `close()` 关闭 ExecWatch 连接并释放资源
    - _需求：2.1, 2.2, 2.3, 2.4, 2.5_

  - [x] 2.2 实现 K8sTerminalBackend 错误处理
    - K8s Exec 连接失败时关闭资源并通过 output 流发送错误通知
    - K8s Exec 连接中断时触发 output 流完成信号
    - _需求：9.4, 9.5_

  - [ ]* 2.3 编写 K8sTerminalBackend 单元测试
    - **Property 4: K8s 终端双向数据完整性**
    - **验证：需求 2.2, 2.3**
    - Mock K8s client，验证 exec 启动、输入输出转发、resize、关闭行为

- [x] 3. TerminalWebSocketHandler 改造 — runtime 路由
  - [x] 3.1 改造 `TerminalWebSocketHandler`，根据 session attributes 中的 runtime 参数创建不同 TerminalBackend
    - 修改文件：`TerminalWebSocketHandler.java`
    - `afterConnectionEstablished` 中解析 `runtime` 参数
    - runtime 为 "k8s" 时：从 PodReuseManager 获取 PodEntry，创建 K8sTerminalBackend
    - runtime 为 "local"、null 或其他值时：创建 LocalTerminalBackend（保持现有行为）
    - Pod 未找到时关闭 WebSocket 并发送 `{"type":"exit","code":-1}`
    - 将 backend 存入 backendMap，订阅 output 流转发到 WebSocket
    - _需求：1.1, 1.2, 9.1_

  - [x] 3.2 改造 `handleTextMessage` 和 `afterConnectionClosed`，使用 TerminalBackend 接口替代直接操作 TerminalProcess
    - 输入消息通过 `backend.write()` 转发
    - resize 消息通过 `backend.resize()` 处理
    - 连接关闭时调用 `backend.close()`
    - _需求：1.1, 1.2, 11.1_

  - [ ]* 3.3 编写 TerminalWebSocketHandler runtime 路由属性测试
    - **Property 1: 终端 runtime 路由正确性**
    - **验证：需求 1.1, 1.2**
    - 使用 jqwik 生成任意 runtime 字符串，验证 "k8s" 创建 K8sTerminalBackend，其他值创建 LocalTerminalBackend

- [x] 4. 检查点 — 终端后端改造验证
  - 确保所有后端终端相关测试通过，如有问题请询问用户。

- [x] 5. Sidecar /files/list 端点扩展
  - [x] 5.1 在 Sidecar 中新增 POST /files/list 端点
    - 请求体：`{"path":"/workspace","depth":3}`
    - 响应：`[{"name":"src","type":"dir","children":[...]},...]`
    - 每个条目包含 name、type（dir/file）字段，目录类型包含 children 数组
    - path 不存在时返回 404 和描述性错误信息
    - _需求：5.1, 5.2, 5.3_

- [x] 6. K8sWorkspaceService 实现
  - [x] 6.1 创建 `K8sWorkspaceService`，封装通过 Sidecar HTTP API 操作 Pod 内文件的逻辑
    - 新建文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/K8sWorkspaceService.java`
    - 注入 PodReuseManager、HttpClient、ObjectMapper
    - `getDirectoryTree(userId, cwd, depth)` — 调用 Sidecar `/files/list`，转换为前端期望的树形结构
    - `readFile(userId, filePath)` — 调用 Sidecar `/files/read`
    - `getChanges(userId, cwd, since)` — 调用 Sidecar API 获取文件变更
    - `resolveAccessHost(podEntry)` — serviceIp 非空时优先使用，否则使用 podIp
    - _需求：4.1, 4.2, 4.3, 4.4_

  - [x] 6.2 实现路径安全验证
    - 所有文件操作路径验证位于 /workspace 目录范围内
    - 包含路径遍历字符（如 "../"）且解析后超出 /workspace 范围的路径拒绝并返回错误
    - _需求：12.1, 12.2_

  - [x] 6.3 实现 K8sWorkspaceService 错误处理
    - PodReuseManager 返回 null 时抛出异常（由 Controller 转为 503）
    - Sidecar 不可达时返回 502 "沙箱连接失败"
    - Sidecar 返回错误时透传错误信息和状态码
    - _需求：9.2, 9.3_

  - [ ]* 6.4 编写 K8sWorkspaceService 属性测试
    - **Property 6: Sidecar 文件列表转换完整性**
    - **验证：需求 4.3**
    - 使用 jqwik 生成随机 Sidecar 文件列表 JSON，验证转换后不丢失条目且层级关系一致

  - [ ]* 6.5 编写 resolveAccessHost 属性测试
    - **Property 7: Pod 访问地址解析正确性**
    - **验证：需求 4.4**
    - 使用 jqwik 生成随机 PodEntry，验证 serviceIp 非空时返回 serviceIp，否则返回 podIp

  - [ ]* 6.6 编写路径安全验证属性测试
    - **Property 8: K8s 文件路径安全验证**
    - **验证：需求 12.1, 12.2**
    - 使用 jqwik 生成包含路径遍历字符的随机路径，验证超出 /workspace 范围的路径被拒绝

- [x] 7. WorkspaceController 改造 — runtime 路由
  - [x] 7.1 改造 `/workspace/tree` 端点，新增 runtime 查询参数
    - 修改文件：`WorkspaceController.java`
    - runtime 为 "k8s" 时通过 K8sWorkspaceService 获取目录树
    - runtime 为 "local" 或缺失时保持现有本地文件系统逻辑不变
    - Pod 未就绪时返回 503 "沙箱未就绪"
    - _需求：3.1, 3.2, 9.2, 11.2_

  - [x] 7.2 改造 `/workspace/file` 端点，新增 runtime 查询参数
    - runtime 为 "k8s" 时通过 K8sWorkspaceService 读取文件
    - runtime 为 "local" 或缺失时保持现有逻辑不变
    - _需求：3.3, 3.4, 11.3_

  - [x] 7.3 改造 `/workspace/changes` 端点，新增 runtime 查询参数
    - runtime 为 "k8s" 时通过 K8sWorkspaceService 获取文件变更
    - runtime 为 "local" 或缺失时保持现有逻辑不变
    - _需求：3.5, 3.6, 11.4_

  - [ ]* 7.4 编写 WorkspaceController runtime 路由属性测试
    - **Property 2: 文件 API runtime 路由正确性**
    - **验证：需求 3.1, 3.2, 3.3, 3.4, 3.5, 3.6**
    - 使用 jqwik 生成任意 runtime 参数值，验证 "k8s" 路由到 K8sWorkspaceService，其他值路由到本地文件系统

- [x] 8. 检查点 — 后端文件操作改造验证
  - 确保所有后端文件操作相关测试通过，如有问题请询问用户。

- [x] 9. 前端 workspaceApi 改造 — runtime 参数传递
  - [x] 9.1 改造 `fetchDirectoryTree` 函数，新增可选 runtime 参数
    - 修改文件：`workspaceApi.ts`
    - runtime 非空时在 HTTP 请求中附加 runtime 查询参数
    - runtime 为 undefined 时不附加 runtime 参数
    - _需求：7.1, 7.4_

  - [x] 9.2 改造 `fetchArtifactContent` 函数，新增可选 runtime 参数
    - runtime 非空时附加 runtime 查询参数
    - _需求：7.2, 7.4_

  - [x] 9.3 改造 `fetchWorkspaceChanges` 函数，新增可选 runtime 参数
    - runtime 非空时附加 runtime 查询参数
    - _需求：7.3, 7.4_

  - [ ]* 9.4 编写 workspaceApi runtime 参数传递属性测试
    - **Property 5: workspaceApi runtime 参数传递正确性**
    - **验证：需求 7.1, 7.2, 7.3, 7.4**
    - 使用 fast-check 生成随机 runtime 值，验证非空时请求包含 runtime 参数，undefined 时不包含

- [x] 10. 前端 TerminalPanel 改造 — runtime 参数传递
  - [x] 10.1 TerminalPanel 组件新增 runtime prop，构建 WebSocket URL 时附加 runtime 查询参数
    - 修改文件：`TerminalPanel.tsx`
    - 修改 `TerminalPanelProps` 接口，新增 `runtime?: string`
    - 修改 `buildTerminalWsUrl` 函数，runtime 非空时附加到 URL
    - _需求：6.1, 6.2, 6.3_

  - [x] 10.2 实现 runtime 变化时 WebSocket 重连
    - 当 runtime prop 值变化时，断开当前 WebSocket 连接并使用新 URL 重新建立连接
    - 通过 wsUrl 状态变化触发 useTerminalWebSocket 重连
    - _需求：6.4_

  - [ ]* 10.3 编写 buildTerminalWsUrl 属性测试
    - **Property 3: 终端 WebSocket URL 参数完整性**
    - **验证：需求 6.2, 6.3**
    - 使用 fast-check 生成随机 runtime 值，验证非空时 URL 包含 runtime 参数且值一致，undefined 时不包含

- [x] 11. Coding.tsx 改造 — runtime 状态传递
  - [x] 11.1 将 `currentRuntimeRef.current` 传递给 TerminalPanel 的 runtime prop
    - 修改文件：`Coding.tsx`
    - `<TerminalPanel runtime={currentRuntimeRef.current} ... />`
    - _需求：8.1_

  - [x] 11.2 在 `fetchDirectoryTree` 调用处传递 runtime 参数
    - `fetchDirectoryTree(cwd, 5, currentRuntimeRef.current)`
    - _需求：8.2_

  - [x] 11.3 在 `fetchArtifactContent` 调用处传递 runtime 参数
    - `fetchArtifactContent(path, { raw: true, runtime: currentRuntimeRef.current })`
    - _需求：8.3_

  - [x] 11.4 在 `fetchWorkspaceChanges` 调用处传递 runtime 参数
    - `fetchWorkspaceChanges(cwd, since, 200, currentRuntimeRef.current)`
    - _需求：8.4_

- [x] 12. 检查点 — 前端改造验证
  - 确保所有前端测试通过，如有问题请询问用户。

- [x] 13. 废弃代码清理
  - [x] 13.1 删除 `PodFileSystemAdapter.java` 文件
    - 删除文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/PodFileSystemAdapter.java`（或实际路径）
    - _需求：10.1_

  - [x] 13.2 删除所有对 `PodFileSystemAdapter` 的引用和导入语句
    - 搜索代码库中所有 `PodFileSystemAdapter` 引用并移除
    - 确保编译通过，无残留依赖
    - _需求：10.2, 10.3_

- [x] 14. 最终检查点 — 全量验证
  - 确保所有前端和后端测试通过，如有问题请询问用户。

## 说明

- 标记 `*` 的任务为可选测试任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号，确保可追溯性
- 检查点任务确保增量验证
- 属性测试验证正确性属性的普遍性，单元测试验证具体示例和边界情况
- 后端使用 Java（Spring Boot + fabric8 K8s client），前端使用 TypeScript（React）
