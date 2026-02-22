# 实现计划：沙箱多 CLI Sidecar

## 概述

将 websocat sidecar 替换为 Node.js 自研 WebSocket Server，支持单 Pod 多 CLI。实现分为三个阶段：Sidecar Server 开发、沙箱镜像改造、后端 Java 代码适配。

## 任务

- [x] 1. 创建 Sidecar Server 项目结构和核心模块
  - [x] 1.1 初始化 `sandbox/sidecar-server/` 项目
    - 创建 `package.json`，依赖 `ws` 库
    - 创建 `index.js` 入口文件，读取环境变量 `SIDECAR_PORT`、`ALLOWED_COMMANDS`、`GRACEFUL_TIMEOUT_MS`
    - 实现 HTTP 服务器 + WebSocket 升级处理
    - 实现 `/health` HTTP 端点，返回 `{"status":"ok","connections":<n>,"processes":<n>}`
    - _Requirements: 1.1, 8.1, 8.2, 8.3_

  - [x] 1.2 实现 WebSocket 连接处理和 CLI 进程管理
    - 解析 URL 查询参数 `command` 和 `args`
    - 校验 `command` 是否在白名单中，不在则返回关闭帧 (code=4403)
    - 缺少 `command` 参数时返回关闭帧 (code=4400)
    - 使用 `child_process.spawn()` 启动 CLI 子进程
    - 双向桥接：WebSocket 消息 → stdin，stdout → WebSocket 消息
    - 维护 Session Map，支持多并发连接
    - _Requirements: 1.2, 1.3, 1.4, 1.7, 2.1, 2.2, 2.3_

  - [x] 1.3 实现进程生命周期管理
    - WebSocket 关闭时：SIGTERM → 等待 gracefulTimeout → SIGKILL
    - CLI 进程退出时：关闭对应 WebSocket 连接
    - CLI 进程 spawn 失败时：发送错误消息并关闭连接
    - SIGTERM 信号处理：终止所有子进程，关闭所有连接，退出
    - _Requirements: 1.5, 1.6, 7.1, 7.2, 7.3_

  - [ ]* 1.4 编写 Sidecar Server 属性测试
    - **Property 1: WebSocket-CLI 消息 Round-Trip**
    - **Validates: Requirements 1.3, 1.4**

  - [ ]* 1.5 编写 Sidecar Server 属性测试
    - **Property 2: 非白名单命令被拒绝**
    - **Validates: Requirements 2.2**

  - [ ]* 1.6 编写 Sidecar Server 属性测试
    - **Property 3: 并发连接隔离性**
    - **Validates: Requirements 1.7**

  - [ ]* 1.7 编写 Sidecar Server 单元测试
    - 白名单解析逻辑
    - URL 参数解析
    - 健康检查端点响应格式
    - 边界情况：空 command、特殊字符
    - _Requirements: 2.1, 2.2, 2.3, 8.1, 8.2, 8.3_

- [x] 2. Checkpoint - 确保 Sidecar Server 测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 3. 改造沙箱镜像和入口脚本
  - [x] 3.1 修改 `sandbox/entrypoint.sh`
    - 移除 `CLI_COMMAND`/`CLI_ARGS` 环境变量校验和 websocat 启动逻辑
    - 改为启动 `node /usr/local/lib/sidecar-server/index.js`
    - 通过 `ALLOWED_COMMANDS` 环境变量传递白名单（默认 `qodercli,qwen`）
    - _Requirements: 3.2, 3.3, 3.4_

  - [x] 3.2 修改 `sandbox/Dockerfile`
    - 移除 websocat 二进制文件的 COPY 步骤
    - 新增 COPY `sidecar-server/` 到 `/usr/local/lib/sidecar-server/`
    - 在镜像构建时执行 `cd /usr/local/lib/sidecar-server && npm install --production`
    - _Requirements: 3.1_

- [x] 4. 改造后端 Java 代码 - Pod 创建逻辑
  - [x] 4.1 修改 `K8sRuntimeAdapter.buildPodSpec()`
    - 移除 `CLI_COMMAND` 和 `CLI_ARGS` 环境变量设置
    - 新增 `ALLOWED_COMMANDS` 环境变量（从配置中获取）
    - _Requirements: 4.1, 4.2_

  - [x] 4.2 修改 `PodReuseManager.createNewPod()`
    - 移除 `CLI_COMMAND` 和 `CLI_ARGS` 环境变量设置
    - 移除 Pod 标签中的 `provider` 字段
    - 新增 `ALLOWED_COMMANDS` 环境变量
    - _Requirements: 4.3, 4.4_

  - [x] 4.3 修改 `PodReuseManager.queryPodFromK8sApi()`
    - 移除标签查询中的 `provider` 过滤条件（仅保留 `app`、`userId`、`sandboxMode`）
    - _Requirements: 5.1, 5.2_

  - [x] 4.4 新增 `AcpProperties.K8sConfig.allowedCommands` 配置项
    - 在 `K8sConfig` 类中新增 `allowedCommands` 字段（默认 `qodercli,qwen`）
    - 在 `application.yml` 中新增 `acp.k8s.allowed-commands` 配置
    - _Requirements: 4.2_

  - [ ]* 4.5 编写后端属性测试
    - **Property 6: Pod 规格不含旧环境变量且包含白名单**
    - **Validates: Requirements 4.1, 4.2, 4.3, 4.4**

- [x] 5. 改造后端 Java 代码 - Sidecar 连接逻辑
  - [x] 5.1 新增 `K8sRuntimeAdapter.buildSidecarWsUri()` 方法
    - 接受 `ip`、`command`、`args` 参数
    - 构建 `ws://ip:8080/?command=xxx&args=xxx` 格式的 URI
    - 对参数进行 URL 编码
    - _Requirements: 6.1_

  - [x] 5.2 修改 `K8sRuntimeAdapter.startWithExistingPod()`
    - 接受额外的 `command` 和 `args` 参数（或通过 RuntimeConfig 传递）
    - 使用 `buildSidecarWsUri()` 构建带 CLI 参数的 WebSocket URI
    - _Requirements: 6.3_

  - [x] 5.3 修改 `K8sRuntimeAdapter.start()`
    - 使用 `buildSidecarWsUri()` 替代原有的简单 URI 构建
    - 从 RuntimeConfig 中获取 command 和 args
    - _Requirements: 6.1_

  - [x] 5.4 修改 `AcpWebSocketHandler.initK8sPodAsync()`
    - 确保 RuntimeConfig 中的 command/args 被传递到 K8sRuntimeAdapter
    - PodInfo 中的 sidecarWsUri 改为基础 URI（不含 CLI 参数）
    - _Requirements: 6.2_

  - [ ]* 5.5 编写后端属性测试
    - **Property 7: Sidecar WebSocket URI 包含 CLI 参数**
    - **Validates: Requirements 6.1, 6.3**

- [x] 6. Checkpoint - 确保所有后端测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 7. 集成验证和收尾
  - [x] 7.1 修改 `PodReuseManager.acquirePod()` 返回的 sidecarWsUri
    - 改为基础 URI `ws://ip:8080/`（不含 CLI 参数），CLI 参数由 K8sRuntimeAdapter 在连接时拼接
    - _Requirements: 5.1, 6.1_

  - [x] 7.2 清理 `K8sRuntimeAdapter.buildPodSpec()` 中的 `provider` 标签
    - 移除 Pod metadata labels 中的 `provider` 字段
    - _Requirements: 4.4_

  - [ ]* 7.3 编写端到端集成测试
    - 验证 Sidecar Server 启动 → WebSocket 连接 → CLI 进程启动 → 消息往返
    - 验证多连接并发场景
    - 验证非白名单命令拒绝
    - _Requirements: 1.1-1.7, 2.1-2.3_

- [x] 8. 最终 Checkpoint - 确保所有测试通过
  - 确保所有测试通过，如有问题请向用户确认。

## 备注

- 标记 `*` 的任务为可选测试任务，可跳过以加速 MVP
- 每个任务引用了具体的需求编号以便追溯
- Checkpoint 确保增量验证
- 属性测试验证通用正确性属性，单元测试验证具体示例和边界情况
