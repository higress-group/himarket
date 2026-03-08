# 实现计划：沙箱多形态架构改造（shared-sandbox-pod）

## 概述

将沙箱类型从 `LOCAL`/`K8S`/`E2B` 重构为 `LOCAL`/`SHARED_K8S`/`OPEN_SANDBOX`/`E2B` 四种形态。移除旧的 `K8sSandboxProvider` 和动态 Pod 逻辑，以 `SharedK8sSandboxProvider` 作为默认实现连接 Helm 预置的共享 Pod，同时为 OpenSandbox 和 E2B 预留空实现接口。

## 任务

- [x] 1. 重构 SandboxType 枚举与配置
  - [x] 1.1 重构 SandboxType 枚举
    - 将枚举值改为 `LOCAL("local")`、`SHARED_K8S("shared-k8s")`、`OPEN_SANDBOX("open-sandbox")`、`E2B("e2b")`
    - 移除旧的 `K8S("k8s")` 枚举值
    - 修改 `fromValue()` 方法：`"k8s"` 映射到 `SHARED_K8S`（向后兼容），`"shared-k8s"` 映射到 `SHARED_K8S`，`"open-sandbox"` 映射到 `OPEN_SANDBOX`，未知值抛出 `IllegalArgumentException`
    - 文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/runtime/SandboxType.java`
    - _需求：1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7_

  - [ ]* 1.2 编写 SandboxType 属性测试
    - **Property 1: SandboxType 序列化 round-trip**
    - **Property 2: fromValue 对未知值的拒绝**
    - **验证需求：1.6, 1.7**

  - [x] 1.3 更新 AcpProperties 配置类
    - 移除 `K8sConfig` 中的 `sharedMode`、`reusePodIdleTimeout`、`sandboxAccessViaService` 字段
    - 新增 `sharedServiceName` 字段，默认值 `"sandbox-shared"`
    - 文件：`himarket-server/src/main/java/com/alibaba/himarket/config/AcpProperties.java`
    - _需求：7.1, 7.2, 7.3, 7.4_

  - [x] 1.4 更新 application.yml 配置
    - 移除 `acp.k8s.reuse-pod-idle-timeout`、`acp.k8s.sandbox-access-via-service` 配置项
    - 新增 `acp.k8s.shared-service-name` 配置项
    - 将 `acp.default-runtime` 默认值改为 `shared-k8s`
    - 将所有 `compatible-runtimes` 中的 `K8S` 改为 `SHARED_K8S`
    - 文件：`himarket-bootstrap/src/main/resources/application.yml`
    - _需求：7.1, 7.2, 7.3, 7.4, 7.5, 8.4_

- [x] 2. 检查点 - 确保编译通过
  - 确保所有代码编译通过，测试通过，如有问题请向用户确认。

- [x] 3. 实现 SharedK8sSandboxProvider
  - [x] 3.1 创建 SharedK8sSandboxProvider 类
    - 实现 `SandboxProvider` 接口，`getType()` 返回 `SHARED_K8S`
    - `acquire()`：不调用 K8s API，直接返回共享 Pod 的 Service DNS 地址（`{sharedServiceName}.{namespace}.svc.cluster.local`），根据 `config.userId()` 构建 `/workspace/{userId}` 工作目录
    - `acquire()` 中校验 `userId` 不为空且不包含 `..` 或 `/` 等路径穿越字符
    - `release()`：空操作
    - `healthCheck()`：委托 `SandboxHttpClient` 调用 `/health`
    - `writeFile()`/`readFile()`/`extractArchive()`：委托 `SandboxHttpClient`
    - `connectSidecar()`：构建 WebSocket URI，将 `workspacePath` 作为 cwd 参数传递，复用 `K8sRuntimeAdapter`
    - 使用 `@Component` 注解，始终注册到 Spring 容器
    - 文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/runtime/SharedK8sSandboxProvider.java`
    - _需求：2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 9.1, 9.2, 9.3, 9.4_

  - [ ]* 3.2 编写 SharedK8sSandboxProvider 属性测试
    - **Property 3: acquire 输出正确性**
    - **Property 4: workspacePath 由 userId 唯一确定**
    - **Property 5: 路径穿越输入拒绝**
    - **验证需求：2.2, 2.3, 9.1, 9.2, 9.3**

  - [ ]* 3.3 编写 SharedK8sSandboxProvider 单元测试
    - 测试 `acquire()` 返回正确的 SandboxInfo（host 格式、sandboxId、type）
    - 测试 `release()` 不抛异常
    - 测试 `healthCheck()` 委托给 SandboxHttpClient
    - 测试 userId 为 null/空/含路径穿越字符时拒绝请求
    - _需求：2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 9.3, 9.4_

- [x] 4. 实现空实现 Provider
  - [x] 4.1 创建 OpenSandboxProvider 类
    - 实现 `SandboxProvider` 接口，`getType()` 返回 `OPEN_SANDBOX`
    - `acquire()`/`writeFile()`/`readFile()`/`connectSidecar()` 抛出 `UnsupportedOperationException`
    - `healthCheck()` 返回 `false`，`release()` 空操作
    - 使用 `@ConditionalOnProperty(name = "acp.open-sandbox.enabled", havingValue = "true")` 控制注册
    - 文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/runtime/OpenSandboxProvider.java`
    - _需求：3.1, 3.3, 3.4, 3.5, 3.6_

  - [x] 4.2 创建 E2BSandboxProvider 类
    - 实现 `SandboxProvider` 接口，`getType()` 返回 `E2B`
    - 所有方法行为同 OpenSandboxProvider
    - 使用 `@ConditionalOnProperty(name = "acp.e2b.enabled", havingValue = "true")` 控制注册
    - 文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/runtime/E2BSandboxProvider.java`
    - _需求：3.2, 3.3, 3.4, 3.5, 3.7_

  - [ ]* 4.3 编写空实现 Provider 单元测试
    - 测试 `OpenSandboxProvider.acquire()` 抛出 `UnsupportedOperationException`
    - 测试 `E2BSandboxProvider.healthCheck()` 返回 `false`
    - 测试 `release()` 不抛异常
    - _需求：3.3, 3.4, 3.5_

- [x] 5. 适配 Registry 和 RuntimeSelector
  - [x] 5.1 更新 SandboxProviderRegistry
    - 移除旧的 `K8sSandboxProvider` 相关的 `@ConditionalOnProperty` 互斥逻辑（如有）
    - 确保 Spring 自动注入所有 `SandboxProvider` Bean，构建 `SandboxType → SandboxProvider` 映射
    - 文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/runtime/SandboxProviderRegistry.java`
    - _需求：4.1, 4.2, 4.3, 4.4, 4.5_

  - [ ]* 5.2 编写 SandboxProviderRegistry 属性测试
    - **Property 6: Registry 路由正确性**
    - **验证需求：4.2, 4.3**

  - [x] 5.3 更新 RuntimeSelector
    - 更新 `isSandboxAvailable()` 的 switch 语句覆盖四种新类型：`SHARED_K8S` 检查 K8s 集群配置，`OPEN_SANDBOX` 和 `E2B` 返回 `false`
    - 更新 `getLabelForType()` 和 `getDescriptionForType()` 为四种类型提供标签和描述
    - 文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/runtime/RuntimeSelector.java`
    - _需求：5.1, 5.2, 5.3, 5.4, 5.5_

  - [ ]* 5.4 编写 RuntimeSelector 属性测试
    - **Property 7: RuntimeSelector 标签完备性**
    - **验证需求：5.4**

- [x] 6. 检查点 - 确保所有测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 7. 移除旧代码与迁移引用
  - [x] 7.1 移除 K8sSandboxProvider 类
    - 删除 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/runtime/K8sSandboxProvider.java`
    - 删除 `himarket-server/src/test/java/com/alibaba/himarket/service/acp/runtime/K8sSandboxProviderPropertyTest.java`
    - _需求：8.1, 8.6_

  - [x] 7.2 迁移 TerminalWebSocketHandler 中的 PodReuseManager 引用
    - 将对 `PodReuseManager` 的引用改为通过 `SharedK8sSandboxProvider` 获取共享 Pod 信息
    - 文件：`himarket-server/src/main/java/com/alibaba/himarket/service/terminal/TerminalWebSocketHandler.java`
    - _需求：8.2_

  - [x] 7.3 迁移 K8sWorkspaceService 中的 PodReuseManager 引用
    - 将 `PodReuseManager.getHealthyPodEntryWithDefaultClient()` 改为使用共享 Pod 的固定地址
    - 文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/K8sWorkspaceService.java`
    - _需求：8.3_

  - [x] 7.4 更新所有引用 SandboxType.K8S 的测试代码
    - 更新 `RuntimeSelectorTest`、`SandboxProviderRegistryTest`、`AcpPropertiesTest` 等测试中的 `SandboxType.K8S` 引用为新枚举值
    - 更新 `RuntimeSelectionFilterPropertyTest`、`SandboxInitPipelinePropertyTest`、`ConfigInjectionPhasePropertyTest` 等属性测试
    - _需求：8.5_

- [x] 8. 新增 Helm Chart 共享沙箱模板
  - [x] 8.1 创建 sandbox-shared Deployment 模板
    - `replicas: 1`，使用沙箱镜像，挂载 `emptyDir` 到 `/workspace`，标签 `app: sandbox-shared`
    - 通过 `{{ if .Values.sandbox.enabled }}` 控制是否渲染
    - 支持通过 values.yaml 自定义镜像、资源限制、允许的命令白名单
    - 文件：`deploy/helm/templates/sandbox-shared-deployment.yaml`
    - _需求：6.1, 6.2, 6.3, 6.5_

  - [x] 8.2 创建 sandbox-shared Service 模板
    - 类型 `ClusterIP`，端口映射 `8080 → 8080`，选择器 `app: sandbox-shared`
    - 通过 `{{ if .Values.sandbox.enabled }}` 控制是否渲染
    - 文件：`deploy/helm/templates/sandbox-shared-service.yaml`
    - _需求：6.1, 6.2, 6.4_

  - [x] 8.3 更新 values.yaml 新增 sandbox 配置段
    - 新增 `sandbox.enabled`、`sandbox.image`、`sandbox.resources`、`sandbox.allowedCommands` 配置
    - 文件：`deploy/helm/values.yaml`
    - _需求：6.5_

- [x] 9. 最终检查点 - 确保所有测试通过
  - 确保所有测试通过，如有问题请向用户确认。

## 备注

- 标记 `*` 的任务为可选任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号，确保可追溯性
- 属性测试验证设计文档中定义的正确性属性
- 检查点确保增量验证，避免问题累积
