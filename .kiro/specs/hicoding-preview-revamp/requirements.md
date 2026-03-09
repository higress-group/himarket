# 需求文档：HiCoding 预览系统架构改造

## 简介

HiCoding 当前的预览系统存在多个架构缺陷：端口检测依赖正则匹配终端输出、只保存单个端口、Remote 沙箱模式下浏览器无法直连沙箱内部地址、产物预览与 HTTP 服务预览未分离。本次改造将预览系统重构为"产物预览优先、HTTP 服务预览辅助"的双模式架构，支持多端口管理，并通过后端反向代理解决 Remote 模式下的可达性问题。

## 术语表

- **Preview_Panel**：HiCoding IDE 右侧的预览面板组件，负责渲染产物内容或嵌入 HTTP 服务页面
- **Artifact_Preview**：产物预览模式，通过后端 API 读取沙箱文件内容（HTML、图片、PDF、PPT 等）并在前端渲染
- **HTTP_Preview**：HTTP 服务预览模式，通过 iframe 嵌入 dev server 页面（Remote 模式通过反向代理）
- **Dev_Proxy**：后端反向代理控制器，将前端请求按 session 路由到对应沙箱的指定端口
- **Port_Manager**：前端端口管理模块，维护已检测到的端口列表，支持手动输入和切换
- **Sidecar**：沙箱内的 sidecar-server 进程，提供文件操作和进程管理能力
- **Quest_State**：前端状态管理中的会话数据结构，包含预览相关状态
- **Remote_Sandbox**：Remote 沙箱，连接远程 Sidecar 服务的沙箱运行环境（K8s / Docker / 裸机均可），浏览器无法直连其内部地址

## 需求

### 需求 1：预览模式切换

**用户故事：** 作为开发者，我希望在产物预览和 HTTP 服务预览之间自由切换，以便根据当前任务选择合适的预览方式。

#### 验收标准

1. THE Preview_Panel SHALL 提供"产物"和"HTTP 服务"两种预览模式的切换入口
2. WHEN 用户切换到产物预览模式时，THE Preview_Panel SHALL 显示当前选中的产物文件内容
3. WHEN 用户切换到 HTTP 服务预览模式时，THE Preview_Panel SHALL 显示当前选中端口对应的 HTTP 服务页面
4. WHEN 没有可预览的产物且没有检测到端口时，THE Preview_Panel SHALL 显示空状态引导提示
5. WHEN Agent 生成新的产物文件时，THE Preview_Panel SHALL 自动切换到产物预览模式并展示该产物
6. WHEN 检测到新的 dev server 端口且当前没有产物正在预览时，THE Preview_Panel SHALL 自动切换到 HTTP 服务预览模式

### 需求 2：产物预览渲染

**用户故事：** 作为开发者，我希望直接在预览面板中查看 Agent 生成的文件产物（HTML、图片、PDF、PPT 等），以便快速验证生成结果。

#### 验收标准

1. THE Preview_Panel SHALL 通过后端 workspace API 读取产物文件内容并在前端渲染
2. WHEN 产物文件为 HTML 类型时，THE Preview_Panel SHALL 使用 iframe 的 srcdoc 属性渲染 HTML 内容
3. WHEN 产物文件为图片类型（PNG、JPG、SVG、GIF）时，THE Preview_Panel SHALL 使用 img 标签渲染图片内容
4. WHEN 产物文件为 PDF 类型时，THE Preview_Panel SHALL 使用 PDF 渲染组件展示 PDF 内容
5. WHEN 产物文件为 PPT/PPTX 类型时，THE Preview_Panel SHALL 调用 prepareArtifactPreview 接口转换后展示
6. IF 产物文件读取失败，THEN THE Preview_Panel SHALL 显示错误信息并提供重试按钮
7. WHEN 用户在产物列表中选择不同产物时，THE Preview_Panel SHALL 切换显示对应产物的内容

### 需求 3：多端口管理

**用户故事：** 作为开发者，我希望管理多个 dev server 端口，以便在前后端分离等多服务场景下切换预览不同的服务。

#### 验收标准

1. THE Port_Manager SHALL 维护一个已检测到的端口列表，而非仅保存最后一个端口
2. WHEN 终端输出中匹配到新的 localhost 端口时，THE Port_Manager SHALL 将该端口追加到端口列表中（去重）
3. THE Port_Manager SHALL 提供端口选择下拉菜单，允许用户在已检测到的端口之间切换
4. THE Port_Manager SHALL 提供手动输入端口号的入口，允许用户添加未被自动检测到的端口
5. WHEN 用户手动输入的端口号不在 1024-65535 范围内时，THE Port_Manager SHALL 显示输入校验错误提示
6. WHEN 端口列表中有多个端口时，THE Port_Manager SHALL 高亮显示当前选中的端口
7. THE Quest_State SHALL 将 previewPort 字段从 `number | null` 改为包含端口列表和当前选中端口的结构

### 需求 4：Remote 模式 HTTP 预览（反向代理）

**用户故事：** 作为使用 Remote 沙箱的开发者，我希望通过后端反向代理预览 dev server 页面，以便在浏览器无法直连沙箱的情况下仍能预览。

#### 验收标准

1. THE Preview_Panel SHALL 通过后端 Dev_Proxy 反向代理访问 HTTP 服务，而非直接使用沙箱内部地址
2. THE Dev_Proxy SHALL 接受 sessionId 参数，根据 sessionId 查找对应沙箱的连接信息（host 和 port）
3. THE Dev_Proxy SHALL 将请求转发到对应沙箱的 `http://{sandboxHost}:{port}/{path}` 地址
4. THE Dev_Proxy SHALL 正确转发 HTTP 响应头（排除 transfer-encoding 和 connection）
5. IF Dev_Proxy 无法连接到目标沙箱端口，THEN THE Dev_Proxy SHALL 返回 502 状态码和错误描述
6. IF 传入的 sessionId 无效或对应的沙箱信息不存在，THEN THE Dev_Proxy SHALL 返回 404 状态码
7. THE Dev_Proxy SHALL 对请求设置 30 秒超时限制

### 需求 5：预览 URL 生成策略

**用户故事：** 作为开发者，我希望系统自动生成正确的预览 URL，以便预览功能能正常工作。

#### 验收标准

1. THE Preview_Panel SHALL 生成 `/workspace/proxy/{sessionId}/{port}/{path}` 格式的反向代理 URL
2. WHEN 用户切换端口时，THE Preview_Panel SHALL 使用新端口重新生成预览 URL

### 需求 6：端口检测优化

**用户故事：** 作为开发者，我希望端口检测更加可靠，减少误匹配和漏匹配的情况。

#### 验收标准

1. THE Port_Manager SHALL 使用正则表达式匹配终端输出中的 `localhost:{port}` 或 `127.0.0.1:{port}` 模式来检测端口
2. THE Port_Manager SHALL 仅接受 1024-65535 范围内的端口号
3. WHEN 同一端口被多次检测到时，THE Port_Manager SHALL 仅保留一条记录（去重）
4. WHEN 检测到新端口时，THE Port_Manager SHALL 在端口列表 UI 中显示新端口提示

### 需求 7：预览面板工具栏

**用户故事：** 作为开发者，我希望预览面板提供实用的工具栏操作，以便高效地管理预览内容。

#### 验收标准

1. THE Preview_Panel SHALL 在工具栏中显示当前预览模式（产物/HTTP 服务）的切换按钮
2. WHILE 处于 HTTP 服务预览模式，THE Preview_Panel SHALL 在工具栏中显示端口选择器和刷新按钮
3. WHILE 处于 HTTP 服务预览模式，THE Preview_Panel SHALL 在工具栏中显示"在新窗口打开"按钮
4. WHILE 处于产物预览模式，THE Preview_Panel SHALL 在工具栏中显示当前产物文件名
5. WHILE 处于产物预览模式且产物列表有多个产物时，THE Preview_Panel SHALL 在工具栏中显示产物切换选择器

### 需求 8：状态持久化与恢复

**用户故事：** 作为开发者，我希望预览面板的状态在页面刷新或重新连接后能够恢复，以便不中断工作流。

#### 验收标准

1. WHEN 新端口被检测到或用户手动添加端口时，THE Quest_State SHALL 将端口列表更新到会话状态中
2. WHEN 用户切换选中端口时，THE Quest_State SHALL 更新当前选中端口
3. WHEN 页面重新连接到已有会话时，THE Quest_State SHALL 从会话状态中恢复端口列表和选中端口
