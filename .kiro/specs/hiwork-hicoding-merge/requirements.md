# 需求文档

## 简介

将 HiWork（Quest）的功能整合到 HiCoding 页面中，移除 HiWork 独立标签页。新的 HiCoding 以对话为核心入口，左侧为固定宽度的会话列表，中间为对话区域，右侧为 IDE 面板（默认预览模式）。配置通过 ConfigSidebar 侧边栏完成（市场模型选择 + Coding CLI 选择 + Skill + MCP 选择），替代原有的 CliSelector。Agent 生成的 Artifacts、Diff 变更、Terminal 输出等内容以内联方式嵌入对话消息流中显示（类似 ChatGPT Artifacts），不再使用独立的右侧辅助面板。配置支持 localStorage 持久化，首次使用时强制完成配置，后续自动加载上次配置。

## 术语表

- **HiCoding_Page**: 整合后的 HiCoding 页面，包含原 HiWork 和 HiCoding 的全部功能
- **Session_Sidebar**: 左侧会话列表侧边栏，固定宽度 240px，显示所有会话的列表，支持创建、切换、关闭会话
- **Conversation_Panel**: 对话面板，包含聊天消息流和用户输入区域，Artifacts/Diff/Terminal 内容以内联方式嵌入消息流
- **IDE_Panel**: IDE 面板，支持 Code/预览 两种模式切换，默认展示预览模式
- **ConfigSidebar**: 配置侧边栏，替代原有的 CliSelector 和 Config_Bar，用于选择市场模型、Coding CLI、Skill 和 MCP
- **Preview_Mode**: 预览模式，IDE_Panel 的默认模式，智能预览 HTML、PDF、Node.js 网站等内容
- **Code_Mode**: 代码模式，IDE_Panel 的可选模式，展示文件树、代码编辑器和终端
- **Inline_Artifact**: 内联展示块，在对话消息流中内联显示 Artifacts 预览、Diff 变更或 Terminal 输出
- **Header_Nav**: 顶部导航栏，包含各功能标签页的导航链接
- **ACP_Session**: Agent Communication Protocol 会话，HiCoding 和 HiWork 共用的 WebSocket 通信协议层

## 需求

### 需求 1：移除 HiWork 独立标签页

**用户故事：** 作为开发者，我希望 HiWork 的功能被整合到 HiCoding 中，这样我不需要在两个相似的页面之间切换。

#### 验收标准

1. WHEN 用户访问 HiCoding_Page 时，THE Header_Nav SHALL 不再显示 "HiWork" 标签页入口
2. WHEN 用户访问原 HiWork 路由 `/quest` 时，THE HiCoding_Page SHALL 将用户重定向到 `/coding` 路径
3. THE Header_Nav SHALL 保留 "HiCoding" 标签页入口，路径为 `/coding`

### 需求 2：会话列表侧边栏

**用户故事：** 作为开发者，我希望在 HiCoding 左侧看到固定宽度的会话列表，这样我可以方便地管理和切换多个编码会话。

#### 验收标准

1. THE Session_Sidebar SHALL 显示在 HiCoding_Page 的左侧区域，固定宽度为 240px，不可拖拽调整宽度
2. THE Session_Sidebar SHALL 展示所有已创建会话的列表，按创建时间倒序排列
3. WHEN 用户点击 Session_Sidebar 中的"新建会话"按钮时，THE HiCoding_Page SHALL 创建一个新的 ACP_Session 会话
4. WHEN 用户点击 Session_Sidebar 中的某个会话条目时，THE HiCoding_Page SHALL 切换到该会话，并在 Conversation_Panel 中显示对应的消息历史
5. WHEN 用户在 Session_Sidebar 中关闭某个会话时，THE HiCoding_Page SHALL 关闭该 ACP_Session 并从列表中移除
6. THE Session_Sidebar SHALL 显示每个会话的标题和创建时间
7. THE Session_Sidebar SHALL 高亮显示当前活跃的会话条目

### 需求 3：ConfigSidebar 配置侧边栏

**用户故事：** 作为开发者，我希望通过一个统一的配置侧边栏来选择市场模型、Coding CLI、Skill 和 MCP，这样我可以在一个地方完成所有配置。

#### 验收标准

1. THE ConfigSidebar SHALL 提供市场模型选择功能，允许用户从可用模型列表中选择当前使用的模型
2. THE ConfigSidebar SHALL 提供 Coding CLI 工具选择功能，显示可用的 CLI 工具列表并允许用户选择和连接
3. THE ConfigSidebar SHALL 提供 Skill 选择功能，允许用户选择和配置可用的 Agent Skill
4. THE ConfigSidebar SHALL 提供 MCP 选择功能，允许用户选择和配置可用的 MCP Server
5. WHEN 用户在 ConfigSidebar 中切换模型时，THE HiCoding_Page SHALL 将新模型应用到当前 ACP_Session
6. WHEN 用户点击配置入口按钮时，THE ConfigSidebar SHALL 以侧边栏形式展开显示
7. WHEN 用户完成配置后，THE ConfigSidebar SHALL 支持收起操作，回到对话界面

### 需求 4：配置持久化与首次使用引导

**用户故事：** 作为开发者，我希望系统记住我上次的配置，这样我不需要每次都重新选择模型和工具；同时首次使用时引导我完成必要配置。

#### 验收标准

1. WHEN 用户完成 ConfigSidebar 中的配置时，THE HiCoding_Page SHALL 将配置信息持久化到 localStorage
2. WHEN 用户再次访问 HiCoding_Page 时，THE HiCoding_Page SHALL 从 localStorage 自动加载上次保存的配置
3. WHEN 用户首次使用 HiCoding_Page 且 localStorage 中无历史配置时，THE HiCoding_Page SHALL 强制展开 ConfigSidebar 并要求用户完成配置
4. WHILE 首次用户未完成 ConfigSidebar 中的必要配置项时，THE Conversation_Panel SHALL 禁用消息输入功能，阻止用户发送消息
5. WHEN 首次用户完成所有必要配置项后，THE HiCoding_Page SHALL 启用 Conversation_Panel 的消息输入功能并允许用户开始对话
6. THE HiCoding_Page SHALL 将以下配置项视为必要配置：市场模型选择和 Coding CLI 选择

### 需求 5：IDE 面板（Code/预览切换）

**用户故事：** 作为开发者，我希望 IDE 面板默认展示预览模式，并可以切换到代码模式，这样我可以快速查看运行效果，需要时再切换到代码编辑。

#### 验收标准

1. THE IDE_Panel SHALL 提供 Code 和预览两种模式的切换按钮
2. WHEN 用户首次进入 HiCoding_Page 时，THE IDE_Panel SHALL 默认展示 Preview_Mode
3. WHILE IDE_Panel 处于 Preview_Mode 时，THE IDE_Panel SHALL 智能预览内容，支持 HTML 页面、PDF 文档和 Node.js 网站的渲染展示
4. WHILE IDE_Panel 处于 Code_Mode 时，THE IDE_Panel SHALL 展示文件树浏览、代码编辑器和终端面板
5. WHEN 用户点击 Code 切换按钮时，THE IDE_Panel SHALL 从 Preview_Mode 切换到 Code_Mode
6. WHEN 用户点击预览切换按钮时，THE IDE_Panel SHALL 从 Code_Mode 切换到 Preview_Mode
7. WHEN Agent 编辑文件时，THE IDE_Panel SHALL 自动切换到 Code_Mode 并打开被编辑的文件

### 需求 6：对话内联展示 Artifacts/Diff/Terminal

**用户故事：** 作为开发者，我希望 Agent 生成的 Artifacts、代码变更 Diff 和 Terminal 输出直接在对话消息流中内联显示，这样我可以在对话上下文中直接查看结果，无需切换面板。

#### 验收标准

1. WHEN Agent 生成 Artifact 时，THE Conversation_Panel SHALL 在对应的消息位置内联显示 Artifact 预览块
2. WHEN Agent 执行文件修改操作时，THE Conversation_Panel SHALL 在对应的消息位置内联显示 Diff 变更视图
3. WHEN Agent 执行终端命令时，THE Conversation_Panel SHALL 在对应的消息位置内联显示 Terminal 输出内容
4. THE Inline_Artifact SHALL 支持展开和折叠操作，默认以展开状态显示
5. THE Inline_Artifact SHALL 在展示块顶部显示类型标签（Artifact / Changes / Terminal）以区分内容类型
6. WHEN 用户点击 Inline_Artifact 中的 Artifact 预览块时，THE IDE_Panel SHALL 切换到 Preview_Mode 并显示对应的完整预览内容

### 需求 7：整体布局

**用户故事：** 作为开发者，我希望新的 HiCoding 页面有清晰合理的布局，这样我可以高效地在对话和代码编辑之间工作。

#### 验收标准

1. THE HiCoding_Page SHALL 采用从左到右的三栏布局：Session_Sidebar（固定 240px） → Conversation_Panel → IDE_Panel
2. THE HiCoding_Page SHALL 支持 Conversation_Panel 和 IDE_Panel 之间的拖拽调整宽度
3. THE HiCoding_Page SHALL 在 Conversation_Panel 和 IDE_Panel 之间显示可拖拽的分隔线
4. THE HiCoding_Page SHALL 记住用户调整的面板尺寸，在页面刷新后恢复
5. THE Session_Sidebar SHALL 固定宽度 240px，不参与拖拽调整

### 需求 8：路由与导航清理

**用户故事：** 作为开发者，我希望路由结构简洁清晰，移除不再需要的 HiWork 相关路由。

#### 验收标准

1. THE HiCoding_Page SHALL 使用 `/coding` 作为主路由路径
2. WHEN 用户访问 `/quest` 路径时，THE HiCoding_Page SHALL 重定向到 `/coding`
3. THE Header_Nav SHALL 移除 "HiWork" 标签页，保留其余标签页不变
