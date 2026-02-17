# Implementation Plan: 沙箱运行时策略

## Overview

基于策略模式 + 工厂模式，在现有 AcpProcess/AcpWebSocketHandler 架构上引入 RuntimeAdapter 抽象层，支持 Local（本地进程）和 K8s（Kubernetes Pod）两种运行时方案。后端使用 Java（Spring Boot + Fabric8 K8s Client），前端使用 TypeScript（React）。两种运行时均由后端统一管理。

## Tasks

- [x] 1. 定义运行时抽象层核心接口和类型
  - [x] 1.1 创建 RuntimeAdapter 接口、RuntimeType 枚举、RuntimeStatus 枚举、RuntimeConfig 数据类
    - 在 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/runtime/` 目录下创建
    - RuntimeAdapter 接口包含 start、send、stdout、getStatus、isAlive、close、getFileSystem 方法
    - RuntimeType 枚举包含 LOCAL、K8S 两个值
    - RuntimeConfig 包含 userId、providerKey、command、args、cwd、env、isolateHome、k8sConfigId、containerImage、resourceLimits
    - _Requirements: 1.1, 1.2, 1.3, 1.5_

  - [x] 1.2 创建 FileSystemAdapter 接口和 FileEntry/FileInfo 记录类
    - 定义 readFile、writeFile、listDirectory、createDirectory、delete、getFileInfo 六个方法
    - 实现路径安全校验工具方法 validatePath（防止路径遍历）
    - _Requirements: 1.4, 5.1, 5.4_

  - [ ]* 1.3 编写路径遍历防护属性测试
    - **Property 3: 路径遍历防护**
    - 使用 jqwik 生成包含 `../`、绝对路径、`..\\` 等模式的随机路径
    - **Validates: Requirements 5.4**

  - [x] 1.4 创建前端运行时类型定义
    - 在 `himarket-web/himarket-frontend/src/types/runtime.ts` 创建 RuntimeType、RuntimeInfo、CliProviderWithRuntime 类型
    - RuntimeType 包含 'local' | 'k8s' 两个值
    - _Requirements: 1.6_

- [x] 2. 实现 LocalRuntimeAdapter（封装现有 AcpProcess）
  - [x] 2.1 创建 LocalRuntimeAdapter 类实现 RuntimeAdapter 接口
    - 封装现有 AcpProcess，委托 start/send/stdout/close 调用
    - 管理 RuntimeStatus 状态转换
    - 支持 HOME 环境变量隔离机制
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.6_

  - [x] 2.2 创建 LocalFileSystemAdapter 类实现 FileSystemAdapter 接口
    - 使用 Java NIO 操作本地文件系统
    - 集成路径安全校验
    - 实现统一错误格式（包含 errorType 和 runtimeType）
    - _Requirements: 2.5, 5.2, 5.4, 5.5_

  - [ ]* 2.3 编写文件操作错误格式一致性属性测试
    - **Property 4: 文件操作错误格式一致性**
    - 使用 jqwik 生成随机运行时类型和无效文件操作
    - **Validates: Requirements 5.5**

- [x] 3. Checkpoint - 确保 Local 运行时适配完成
  - 确保所有测试通过，ask the user if questions arise.

- [x] 4. 实现 RuntimeFactory 和 AcpWebSocketHandler 改造
  - [x] 4.1 创建 RuntimeFactory 组件
    - 根据 RuntimeType（LOCAL、K8S）创建对应的 RuntimeAdapter 实例
    - 注入 K8sConfigService
    - _Requirements: 1.5_

  - [x] 4.2 改造 AcpWebSocketHandler 使用 RuntimeAdapter
    - 从 WebSocket URL 参数中解析 runtime 类型
    - 使用 RuntimeFactory 创建运行时实例替代直接创建 AcpProcess
    - 保持现有 cwd 重写和消息转发逻辑
    - _Requirements: 1.5, 6.1, 6.2_

  - [x] 4.3 扩展 AcpProperties.CliProviderConfig
    - 新增 containerImage、runtimeCategory 字段
    - 更新 application.yml 配置
    - _Requirements: 4.1_

  - [x] 4.4 扩展 AcpHandshakeInterceptor 解析 runtime 参数
    - 从 WebSocket 握手 URL 中提取 runtime 查询参数
    - 存入 WebSocketSession attributes
    - _Requirements: 4.3_

- [x] 5. 实现运行时选择逻辑
  - [x] 5.1 创建 RuntimeSelector 服务类
    - 根据环境可用性（K8s 是否配置）过滤运行时选项
    - 实现默认运行时优先级配置
    - 实现可用性验证
    - _Requirements: 4.2, 4.3, 4.4, 4.5, 4.6_

  - [ ]* 5.2 编写运行时选择过滤与可用性验证属性测试
    - **Property 5: 运行时选择过滤与可用性验证**
    - 使用 jqwik 生成随机环境状态（K8s 配置状态）
    - **Validates: Requirements 4.2, 4.4, 9.4**

  - [ ]* 5.3 编写默认运行时优先级属性测试
    - **Property 6: 默认运行时优先级**
    - 使用 jqwik 生成随机优先级配置和环境可用性
    - **Validates: Requirements 4.6**

  - [x] 5.4 创建 CliProvider REST API 扩展
    - 扩展 `/api/cli-providers` 接口返回 runtimeCategory 和 containerImage 字段
    - 新增 `/api/runtime/available` 接口返回当前环境可用的运行时列表
    - _Requirements: 4.1, 4.2_

- [x] 6. Checkpoint - 确保运行时选择和工厂模式完成
  - 确保所有测试通过，ask the user if questions arise.

- [x] 7. 实现 K8s 集群配置管理
  - [x] 7.1 创建 K8sConfigService 服务类
    - 实现 kubeconfig 注册、验证、存储、检索
    - 使用 Fabric8 KubernetesClient 验证集群可达性
    - 支持多集群管理
    - _Requirements: 9.1, 9.2, 9.3, 9.5_

  - [ ]* 7.2 编写无效 Kubeconfig 验证属性测试
    - **Property 8: 无效 Kubeconfig 验证**
    - 使用 jqwik 生成随机无效 kubeconfig 字符串
    - **Validates: Requirements 3.10, 9.2**

  - [x] 7.3 创建 K8s 配置 REST API
    - POST `/api/k8s/config` 注册 kubeconfig
    - GET `/api/k8s/clusters` 列出已注册集群
    - DELETE `/api/k8s/config/{configId}` 删除集群配置
    - _Requirements: 9.1, 9.5_

- [x] 8. 实现 K8sRuntimeAdapter
  - [x] 8.1 创建 K8sRuntimeAdapter 类实现 RuntimeAdapter 接口
    - 使用 Fabric8 K8s Client 创建/管理 Pod
    - 实现 Pod Spec 构建（容器镜像、资源限制、PV 挂载、Sidecar）
    - 实现 Pod Ready 等待和 WebSocket 连接到 Pod 内 Sidecar
    - 启动健康检查
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.7_

  - [ ]* 8.2 编写 Pod Spec 完整性属性测试
    - **Property 7: Pod Spec 完整性**
    - 使用 jqwik 生成随机 RuntimeConfig，验证生成的 Pod Spec 包含正确的资源限制、镜像和 PVC 挂载
    - **Validates: Requirements 3.7, 3.9, 8.2**

  - [x] 8.3 创建 PodFileSystemAdapter 类实现 FileSystemAdapter 接口
    - 通过 K8s exec API 或 Pod 内 Sidecar 的文件 API 操作容器内文件
    - 集成路径安全校验
    - _Requirements: 3.6, 5.3_

  - [x] 8.4 实现 Pod 空闲超时清理和健康检查
    - 定时检查 Pod 最后活跃时间，超时自动删除
    - 定时健康检查，连续失败超过阈值则强制销毁
    - _Requirements: 3.8, 7.1, 7.3_

  - [ ]* 8.5 编写健康检查失败阈值销毁属性测试
    - **Property 10: 健康检查失败阈值销毁**
    - 使用 jqwik 生成随机阈值和失败序列
    - **Validates: Requirements 7.5**

- [x] 9. Checkpoint - 确保 K8s 运行时完成
  - 确保所有测试通过，ask the user if questions arise.

- [x] 10. 实现前端 RuntimeSelector 组件和 Hook 改造
  - [x] 10.1 创建 RuntimeSelector React 组件
    - 展示 Local 和 K8s 两种运行时选项
    - K8s 未配置时标记为 disabled 并显示原因
    - 仅有一种可用运行时时自动选中
    - _Requirements: 4.2, 4.4, 4.7, 9.4_

  - [x] 10.2 集成 RuntimeSelector 到 HiWork、HiCoding、HiCli 页面
    - 在 CLI 工具选择旁边添加运行时选择
    - 将选中的 runtime 类型传递给 useAcpSession hook
    - _Requirements: 4.7_

  - [x] 10.3 扩展 useAcpSession Hook 和 WebSocket URL 构建
    - 新增 runtimeType 参数
    - 在 WebSocket URL 中添加 runtime 查询参数
    - 两种运行时均通过 WebSocket 与后端通信
    - _Requirements: 4.3, 6.3, 6.4_

- [x] 11. 实现运行时健康检查与异常通知
  - [x] 11.1 实现后端运行时健康检查框架
    - LocalRuntimeAdapter: 检测子进程是否存活
    - K8sRuntimeAdapter: 检测 Pod 状态和 WebSocket 连通性
    - 统一的异常通知格式（faultType、runtimeType、suggestedAction）
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

  - [ ]* 11.2 编写运行时异常通知一致性属性测试
    - **Property 9: 运行时异常通知一致性**
    - 使用 jqwik 生成随机运行时类型和异常场景
    - **Validates: Requirements 6.5, 7.3, 7.4**

- [x] 12. Final checkpoint - 确保所有测试通过
  - 确保所有测试通过，ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties using jqwik (Java) and fast-check (TypeScript)
- Unit tests validate specific examples and edge cases
- LocalRuntimeAdapter 是对现有代码的最小改造，优先实现以保持向后兼容
- 两种运行时均由后端统一管理，前端仅负责运行时选择 UI 和 WebSocket 通信
