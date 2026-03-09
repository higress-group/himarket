# 实现计划：HiCoding 预览系统架构改造

## 概述

将 HiCoding 预览面板从"单端口 HTTP 预览"重构为"产物预览 + HTTP 服务预览"双模式架构。核心变更：前端状态从 `previewPort: number | null` 升级为多端口管理结构，PreviewPanel 拆分为 ArtifactPreviewPane 和 HttpPreviewPane，后端 DevProxyController 改造为按 userId 路由到对应沙箱的反向代理。

## 任务

- [x] 1. 后端：DevProxyController 改造 + 沙箱 Host 映射
  - [x] 1.1 AcpConnectionManager 新增 userSandboxHostMap
    - 新增 `ConcurrentHashMap<String, String> userSandboxHostMap`，key 为 userId，value 为 sandboxHost
    - 新增 `setSandboxHost(String userId, String host)` 和 `getSandboxHost(String userId)` 方法
    - 在 `cleanup()` 中根据 sessionId 反查 userId 并清理对应的 sandboxHost 映射
    - 文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/AcpConnectionManager.java`
    - _需求：4.2, 4.6_

  - [x] 1.2 在沙箱初始化完成时记录 sandboxHost
    - 在 `AcpSessionInitializer`（或沙箱 acquire 成功后的回调处）调用 `connectionManager.setSandboxHost(userId, sandboxHost)`
    - 确保 Remote 模式下 sandboxHost 正确写入（从 SandboxInfo 中获取）
    - 文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/AcpSessionInitializer.java`（或相关初始化流程文件）
    - _需求：4.2_

  - [x] 1.3 改造 DevProxyController
    - 注入 `AcpConnectionManager`
    - 保持路径格式 `/{port}/**`，通过 Spring Security 上下文获取当前认证用户的 userId
    - 用 userId 从 `connectionManager.getSandboxHost(userId)` 获取沙箱地址
    - 将 `targetUri` 的 host 从 `localhost` 改为查找到的 sandboxHost
    - userId 无对应沙箱时返回 404（"No active sandbox found"）
    - 保留现有的端口校验、超时、响应头过滤逻辑不变
    - 文件：`himarket-server/src/main/java/com/alibaba/himarket/controller/DevProxyController.java`
    - _需求：4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_

  - [ ]* 1.4 编写 DevProxyController 单元测试
    - 测试 userId 无对应沙箱时返回 404
    - 测试端口超出范围时返回 400
    - 测试正常代理请求的 URL 构建（sandboxHost + port + path）
    - _需求：4.5, 4.6_

- [x] 2. 检查点 - 后端编译通过
  - 确保后端代码编译通过（`mvn compile -pl himarket-server -am`），如有问题请向用户确认。

- [x] 3. 前端：QuestState 多端口状态改造
  - [x] 3.1 新增 PreviewPortState 类型并改造 QuestData
    - 在 `QuestSessionContext.tsx` 中新增 `PreviewPortState` 接口：`{ ports: number[]; selectedPort: number | null }`
    - 将 `QuestData.previewPort: number | null` 替换为 `previewPorts: PreviewPortState`
    - 初始值：`{ ports: [], selectedPort: null }`
    - _需求：3.1, 3.7_

  - [x] 3.2 改造 PREVIEW_PORT_DETECTED reducer
    - 行为变更：将端口追加到 `previewPorts.ports`（去重），首个端口自动设为 `selectedPort`
    - 端口校验：仅接受 1024-65535 范围
    - _需求：3.1, 3.2, 6.2, 6.3_

  - [x] 3.3 新增 PREVIEW_PORT_SELECTED 和 PREVIEW_PORT_ADDED actions
    - `PREVIEW_PORT_SELECTED`：更新 `selectedPort`（需校验端口在列表中）
    - `PREVIEW_PORT_ADDED`：手动添加端口到列表（去重 + 范围校验）
    - _需求：3.3, 3.4, 3.5, 8.1, 8.2_

  - [x] 3.4 更新 Coding.tsx 中对 previewPort 的引用
    - 将 `activeQuest?.previewPort` 改为 `activeQuest?.previewPorts.selectedPort`
    - 更新 `handleRefreshPreview` 和 `handleOpenExternal` 中的端口引用
    - 文件：`himarket-web/himarket-frontend/src/pages/Coding.tsx`
    - _需求：5.2_

  - [x] 3.5 改造 getPreviewUrl 函数
    - 移除 `sandboxHost` 参数，所有请求走后端反向代理
    - 返回格式：`/workspace/proxy/${port}/`
    - 文件：`himarket-web/himarket-frontend/src/lib/utils/workspaceApi.ts`
    - _需求：4.1, 5.1_

  - [ ]* 3.6 编写 reducer 属性测试
    - **Property 1**: 端口列表追加与去重
    - **Property 2**: 端口范围校验
    - **Property 6**: 端口切换状态更新
    - 使用 `fast-check` + `vitest`
    - _需求：3.1, 3.2, 3.5, 6.2, 6.3, 8.2_

- [x] 4. 检查点 - 前端编译通过
  - 确保前端代码编译通过（`npm run build`），如有问题请向用户确认。

- [x] 5. 前端：PreviewPanel 双模式改造
  - [x] 5.1 重构 PreviewPanel 为双模式容器
    - 新增 `PreviewMode` 类型：`"artifact" | "http"`
    - PreviewPanel 内部管理当前模式状态
    - 根据模式渲染 ArtifactPreviewPane 或 HttpPreviewPane
    - 接收 `previewPorts: PreviewPortState`、`artifacts`、`activeArtifactId` 等 props
    - 文件：`himarket-web/himarket-frontend/src/components/coding/PreviewPanel.tsx`
    - _需求：1.1, 1.2, 1.3, 1.4_

  - [x] 5.2 实现 PreviewToolbar
    - 模式切换按钮（产物/HTTP 服务）
    - HTTP 模式：端口选择下拉 + 手动输入 + 刷新 + 外部打开
    - 产物模式：当前产物文件名 + 产物切换选择器（多产物时）
    - 可作为 PreviewPanel 内部子组件或独立文件
    - _需求：7.1, 7.2, 7.3, 7.4, 7.5_

  - [x] 5.3 实现 HttpPreviewPane
    - iframe 嵌入 `getPreviewUrl(selectedPort)` 生成的反向代理 URL
    - 无端口时显示空状态提示
    - _需求：1.3, 5.1_

  - [x] 5.4 实现 ArtifactPreviewPane
    - 复用现有产物渲染逻辑（已有 ArtifactPreview 组件可参考）
    - HTML → srcdoc，图片 → img，PDF → pdf viewer，PPT → prepareArtifactPreview 转换
    - 文件读取失败时显示错误信息 + 重试按钮
    - _需求：2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_

  - [x] 5.5 自动模式切换逻辑
    - Agent 生成新产物时自动切换到产物模式
    - 检测到新端口且无产物预览时自动切换到 HTTP 模式
    - 文件：`himarket-web/himarket-frontend/src/pages/Coding.tsx`（或 PreviewPanel 内部）
    - _需求：1.5, 1.6_

  - [x] 5.6 更新 Coding.tsx 集成新 PreviewPanel
    - 将 PreviewPanel 的 props 从 `port + sandboxHost` 改为 `previewPorts + artifacts + activeArtifactId` 等
    - 移除工具栏中旧的刷新/外部打开按钮（已移入 PreviewToolbar）
    - 连接 `onPortSelect`、`onAddPort`、`onSelectArtifact` 等回调到 dispatch
    - _需求：1.1, 3.3, 3.4, 7.1_

- [x] 6. 检查点 - 前端编译通过 + 基本渲染验证
  - 确保前端编译通过，如有问题请向用户确认。

- [x] 7. 端口检测优化
  - [x] 7.1 优化终端输出端口检测正则
    - 确保匹配 `localhost:{port}` 和 `127.0.0.1:{port}` 模式
    - 仅接受 1024-65535 范围
    - 同一端口多次检测时去重（由 reducer 保证）
    - 文件：`himarket-web/himarket-frontend/src/hooks/useAcpSession.ts`（两处端口检测逻辑）
    - _需求：6.1, 6.2, 6.3_

  - [x] 7.2 新端口检测时的 UI 提示
    - 在端口列表 UI 中高亮或提示新检测到的端口
    - _需求：6.4, 3.6_

  - [ ]* 7.3 编写端口检测正则属性测试
    - **Property 3**: 终端输出端口检测
    - 使用 `fast-check` 生成包含/不包含端口模式的字符串
    - _需求：6.1_

- [x] 8. 最终检查点 - 全量验证
  - 后端编译通过
  - 前端编译通过
  - 如有问题请向用户确认

## 备注

- 标记 `*` 的任务为可选任务（测试），可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号，确保可追溯性
- 后端改造（任务 1）和前端状态改造（任务 3）可并行开发，互不依赖
- DevProxyController 保持 `/{port}/**` 路径格式，通过认证 token 中的 userId 查找沙箱 host，无需前端传 sessionId
- 产物预览复用现有 workspace API 和 ArtifactPreview 组件，不需要新增后端接口
