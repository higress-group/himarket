# 实施计划：HiWork 与 HiCoding 合并

## 概述

将 HiWork（Quest）功能整合到 HiCoding 页面，采用增量实施策略：先完成后端数据层，再逐步重构前端组件，最后清理废弃文件。前端使用 React + TypeScript，后端使用 Spring Boot + Java，数据库迁移使用 Flyway。

## 任务

- [x] 1. 后端：新增 coding_session 数据库表和 CRUD API
  - [x] 1.1 创建 Flyway 迁移文件 `V9__Add_coding_session_table.sql`
    - 在 `himarket-bootstrap/src/main/resources/db/migration/` 下创建迁移文件
    - 创建 `coding_session` 表，包含 `id`, `session_id`, `user_id`, `title`, `config`(json), `session_data`(json), `created_at`, `updated_at` 字段
    - 添加唯一索引 `uk_session_id`，普通索引 `idx_user_id` 和 `idx_updated_at`
    - _需求: 2.2, 2.6_

  - [x] 1.2 创建 CodingSession Entity、Repository、Service、Controller
    - 在 `himarket-server` 中创建 `CodingSessionEntity`（参考现有 Entity 模式）
    - 创建 JPA Repository 接口
    - 创建 `CodingSessionService` 接口和 `CodingSessionServiceImpl` 实现类
    - 创建 DTO：`CreateCodingSessionParam`、`UpdateCodingSessionParam`、`CodingSessionResult`
    - 创建 `CodingSessionController`，提供 5 个端点：POST/GET(list)/GET(detail)/PUT/DELETE `/coding-sessions`
    - 所有端点使用 `@DeveloperAuth` 认证注解
    - 会话列表按 `updated_at` 倒序返回，列表接口不返回 `session_data` 字段
    - _需求: 2.2, 2.3, 2.5, 2.6_

  - [ ]* 1.3 编写后端 CRUD 接口单元测试
    - 测试 CodingSessionService 的 CRUD 逻辑
    - 测试用户只能访问自己的会话数据
    - 测试会话列表按 updated_at 倒序返回
    - _需求: 2.2, 2.3, 2.5_

- [x] 2. 检查点 - 后端编译和接口验证
  - 确保后端编译通过，所有测试通过，如有问题请询问用户。

- [x] 3. 前端：路由与导航清理
  - [x] 3.1 修改路由配置，移除 Quest 页面路由
    - 在 `src/router.tsx` 中删除 `/quest` → `Quest` 组件的路由
    - 新增 `/quest` → `Navigate to /coding` 的重定向路由
    - 删除 Quest 页面的懒加载 import
    - _需求: 1.2, 8.1, 8.2_

  - [x] 3.2 修改 Header 导航，移除 HiWork 标签页
    - 在 `src/components/Header.tsx` 的 tabs 数组中移除 `{ path: "/quest", label: "HiWork" }` 条目
    - 确保 "HiCoding" 标签页保留，路径为 `/coding`
    - _需求: 1.1, 1.3, 8.3_

  - [ ]* 3.3 编写路由和导航单元测试
    - 测试 `/quest` 重定向到 `/coding`
    - 测试 Header 不包含 "HiWork" 标签
    - 测试 Header 包含 "HiCoding" 标签
    - _需求: 1.1, 1.2, 1.3, 8.2, 8.3_

- [x] 4. 前端：新增 CodingConfig 类型定义和 localStorage 持久化 Hook
  - [x] 4.1 定义 CodingConfig 类型和默认值
    - 在 `src/types/coding.ts` 中新增 `CodingConfig` 接口（modelId, cliProviderId, cliRuntime, skills, mcpServers）
    - 定义 `DEFAULT_CONFIG` 常量
    - 定义 `isConfigComplete` 工具函数（检查 modelId 和 cliProviderId 非空）
    - _需求: 4.6_

  - [x] 4.2 创建 `useCodingConfig` Hook
    - 在 `src/hooks/` 下创建 `useCodingConfig.ts`
    - 实现从 localStorage（key: `hicoding:config`）读取和写入配置
    - 处理 JSON.parse 异常时清除损坏数据并返回默认配置
    - 提供 `config`, `setConfig`, `isFirstTime`, `isComplete` 状态
    - _需求: 4.1, 4.2, 4.3_

  - [ ]* 4.3 编写配置持久化单元测试
    - 测试配置保存到 localStorage 后可正确读取
    - 测试 localStorage 数据损坏时降级为默认配置
    - 测试 `isConfigComplete` 验证逻辑（modelId 和 cliProviderId 均非空时返回 true）
    - _需求: 4.1, 4.2, 4.4, 4.5, 4.6_

- [x] 5. 前端：新增 ConfigSidebar 组件
  - [x] 5.1 创建 ConfigSidebar 组件
    - 在 `src/components/coding/` 下创建 `ConfigSidebar.tsx`
    - 以 Drawer 侧边栏形式实现，包含模型选择、CLI 选择、Skill 选择、MCP 选择四个区域
    - 复用现有 `CliProviderSelect` 组件的 CLI 选择逻辑
    - 复用现有模型列表数据（从 QuestSessionContext 获取）
    - `isFirstTime` 为 true 时禁止关闭侧边栏
    - 配置变更时调用 `onConfigChange` 回调并持久化到 localStorage
    - _需求: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

  - [ ]* 5.2 编写 ConfigSidebar 单元测试
    - 测试包含模型、CLI、Skill、MCP 四个选择区
    - 测试首次使用时不可关闭
    - 测试配置变更触发回调
    - _需求: 3.1, 3.2, 3.3, 3.4, 3.6, 3.7, 4.3_

- [x] 6. 前端：新增 Session_Sidebar 组件
  - [x] 6.1 创建 Session_Sidebar 组件（基于 QuestSidebar 改造）
    - 在 `src/components/coding/` 下创建 `SessionSidebar.tsx`
    - 固定宽度 240px（`w-60`），不可拖拽调整
    - 展示会话列表（按创建时间倒序），每个条目显示标题和创建时间
    - 高亮当前活跃会话
    - 提供新建会话、切换会话、关闭会话操作
    - 底部增加 ConfigSidebar 入口按钮（齿轮图标）
    - 标题显示 "HiCoding"
    - _需求: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 7.5_

  - [ ]* 6.2 编写 Session_Sidebar 单元测试
    - 测试固定宽度 240px
    - 测试会话列表按创建时间倒序排列
    - 测试新建会话按钮调用回调
    - 测试点击会话切换活跃会话
    - 测试高亮活跃会话
    - _需求: 2.1, 2.2, 2.3, 2.4, 2.7, 7.5_

- [x] 7. 前端：新增 ConversationTopBar 和 InlineArtifact 组件
  - [x] 7.1 创建 ConversationTopBar 组件
    - 在 `src/components/coding/` 下创建 `ConversationTopBar.tsx`
    - 简化版顶部栏，只显示会话标题、连接状态和用量信息
    - 模型选择已移入 ConfigSidebar，此处不再包含
    - _需求: 7.1_

  - [x] 7.2 创建 InlineArtifact 组件
    - 在 `src/components/coding/` 下创建 `InlineArtifact.tsx`
    - 支持三种类型：`artifact`、`diff`、`terminal`
    - 顶部显示类型标签（Artifact / Changes / Terminal）
    - 支持展开/折叠操作，默认展开
    - `artifact` 类型支持点击预览回调（切换 IDE 到 Preview 模式）
    - _需求: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [ ]* 7.3 编写 InlineArtifact 单元测试
    - 测试三种类型渲染对应的类型标签
    - 测试默认展开状态
    - 测试展开/折叠切换
    - 测试 artifact 类型点击触发预览回调
    - _需求: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

- [x] 8. 前端：新增前端 API 层（coding-sessions 接口调用）
  - [x] 8.1 创建 coding-session API 模块
    - 在 `src/lib/apis/` 下创建 `codingSession.ts`
    - 封装 5 个接口调用函数：createCodingSession, listCodingSessions, getCodingSession, updateCodingSession, deleteCodingSession
    - 复用现有 `src/lib/request.ts` 的请求封装
    - _需求: 2.2, 2.3, 2.5_

- [x] 9. 前端：重构 Coding.tsx 页面（核心整合）
  - [x] 9.1 重构 Coding.tsx 为三栏布局
    - 改造 `src/pages/Coding.tsx`，实现 Session_Sidebar → Conversation_Panel → IDE_Panel 三栏布局
    - Session_Sidebar 固定 240px，Conversation_Panel 和 IDE_Panel 之间使用 `useResizable` 实现可拖拽分隔线
    - 面板尺寸持久化到 localStorage（key: `hicoding:conversationWidth` 等）
    - _需求: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [x] 9.2 集成 ConfigSidebar 和配置持久化逻辑
    - 在 Coding.tsx 中集成 `useCodingConfig` Hook 和 `ConfigSidebar` 组件
    - 首次使用时（localStorage 无配置）强制展开 ConfigSidebar，禁用消息输入
    - 配置完成后启用消息输入，保存配置到 localStorage
    - 配置变更时将新模型应用到当前 ACP_Session
    - _需求: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 3.5_

  - [x] 9.3 集成 Session_Sidebar 和会话管理
    - 在 Coding.tsx 中集成 `SessionSidebar` 组件
    - 连接 QuestSessionProvider 的会话创建、切换、关闭操作
    - 页面加载时调用 `GET /coding-sessions` 恢复会话列表
    - 会话变更时调用后端 API 进行持久化（防抖处理）
    - _需求: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_

  - [x] 9.4 集成 ConversationTopBar 替代 CodingTopBar
    - 用 `ConversationTopBar` 替换现有的 `CodingTopBar`
    - 显示会话标题、连接状态和用量信息
    - _需求: 7.1_

  - [x] 9.5 IDE_Panel 默认模式改为 Preview
    - 将 IDE_Panel 的默认模式从 `code` 改为 `preview`
    - 保留 Code/Preview 切换按钮和现有切换逻辑
    - 保留 Agent 编辑文件时自动切换到 Code 模式的逻辑
    - _需求: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_

  - [ ]* 9.6 编写 Coding.tsx 布局和集成单元测试
    - 测试三栏布局结构（Session_Sidebar + Conversation_Panel + IDE_Panel）
    - 测试可拖拽分隔线存在
    - 测试 IDE_Panel 默认 Preview 模式
    - 测试首次使用时 ConfigSidebar 强制展开
    - _需求: 7.1, 7.3, 5.2, 4.3_

- [x] 10. 前端：ChatStream 内联 Artifacts/Diff/Terminal 展示
  - [x] 10.1 修改 ChatStream 集成 InlineArtifact 组件
    - 在 `src/components/quest/ChatStream.tsx` 中集成 `InlineArtifact` 组件
    - Agent 生成 Artifact 时，在对应消息位置内联显示 Artifact 预览块
    - Agent 执行文件修改时，在对应消息位置内联显示 Diff 变更视图
    - Agent 执行终端命令时，在对应消息位置内联显示 Terminal 输出
    - 点击 Artifact 预览块时，通过回调切换 IDE_Panel 到 Preview 模式
    - _需求: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [ ]* 10.2 编写 ChatStream 内联展示单元测试
    - 测试 Artifact 类型消息渲染 InlineArtifact 组件
    - 测试 Diff 类型消息渲染 InlineArtifact 组件
    - 测试 Terminal 类型消息渲染 InlineArtifact 组件
    - _需求: 6.1, 6.2, 6.3_

- [x] 11. 检查点 - 前端核心功能验证
  - 确保前端编译通过，所有测试通过，如有问题请询问用户。

- [x] 12. 前端：删除 Quest 专用文件
  - [x] 12.1 删除 Quest 页面及其专用组件
    - 删除 `src/pages/Quest.tsx`
    - 删除 `src/components/quest/QuestWelcome.tsx`
    - 删除 `src/components/quest/QuestTopBar.tsx`
    - 删除 `src/components/quest/QuestSidebar.tsx`
    - 删除 `src/components/quest/RightPanel.tsx`
    - 确认删除后无编译错误（所有引用已在前序任务中移除）
    - _需求: 1.1, 1.2_

- [x] 13. 最终检查点 - 全量验证
  - 确保前后端编译通过，所有测试通过，如有问题请询问用户。

## 备注

- 标记 `*` 的子任务为可选测试任务，可跳过以加速 MVP 开发
- 每个任务引用了具体的需求编号，确保需求可追溯
- 检查点任务用于阶段性验证，确保增量开发的正确性
- 本项目使用 Vitest + React Testing Library 进行常规单元测试，不使用属性测试
- 后端测试使用 Spring Boot 标准测试框架
