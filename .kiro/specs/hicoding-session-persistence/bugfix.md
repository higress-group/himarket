# Bugfix 需求文档

## 简介

HiCoding 的会话信息没有被持久化到数据库中。后端已经有完整的 `coding_session` 数据库表（Flyway V9 迁移）和 CRUD REST API（`CodingSessionController`），前端也已经定义了对应的 API 函数（`codingSession.ts`），但前端从未实际调用这些 API。会话数据完全存储在内存中的 `QuestSessionContext`（React Context + useReducer），导致页面刷新或重新登录后所有会话丢失。

## Bug 分析

### 当前行为（缺陷）

1.1 WHEN 用户在 HiCoding 页面创建新会话时 THEN 系统仅在前端内存（`QuestSessionContext`）中创建会话对象，不调用后端 `POST /coding-sessions` 接口，`coding_session` 数据库表中无任何记录写入

1.2 WHEN 用户在 HiCoding 页面进行对话（发送消息、接收回复）后 THEN 系统不调用后端 `PUT /coding-sessions/{sessionId}` 接口更新会话数据，对话内容和会话标题变更不会持久化到数据库

1.3 WHEN 用户刷新 HiCoding 页面或重新登录后 THEN 系统不调用后端 `GET /coding-sessions` 接口加载历史会话，侧边栏显示"暂无历史会话"，所有之前的会话数据丢失

1.4 WHEN 用户在 HiCoding 侧边栏关闭（删除）一个会话时 THEN 系统仅从前端内存中移除会话对象，不调用后端 `DELETE /coding-sessions/{sessionId}` 接口，数据库中不会有对应的删除操作（因为本来就没有写入）

### 期望行为（正确）

2.1 WHEN 用户在 HiCoding 页面创建新会话时 THEN 系统 SHALL 调用后端 `POST /coding-sessions` 接口将会话信息（标题、配置）持久化到 `coding_session` 数据库表中，并将返回的 `sessionId` 与前端内存中的会话对象关联

2.2 WHEN 用户在 HiCoding 页面进行对话后会话标题发生变更时 THEN 系统 SHALL 调用后端 `PUT /coding-sessions/{sessionId}` 接口将更新后的标题持久化到数据库

2.3 WHEN 用户刷新 HiCoding 页面或重新登录后 THEN 系统 SHALL 调用后端 `GET /coding-sessions` 接口加载历史会话列表，并在侧边栏中展示这些历史会话记录

2.4 WHEN 用户在 HiCoding 侧边栏删除一个会话时 THEN 系统 SHALL 调用后端 `DELETE /coding-sessions/{sessionId}` 接口将该会话从数据库中删除

### 不变行为（回归防护）

3.1 WHEN 用户在 HiCoding 页面进行实时对话（发送消息、接收回复、工具调用等）时 THEN 系统 SHALL CONTINUE TO 通过 WebSocket 连接和 `QuestSessionContext` 内存状态管理实时消息流，不因持久化逻辑影响对话的实时性和流畅性

3.2 WHEN 用户在 HiCoding 页面切换会话时 THEN 系统 SHALL CONTINUE TO 通过前端内存状态（`QuestSessionContext`）即时切换活跃会话，不因持久化逻辑引入额外延迟

3.3 WHEN 用户通过 ConfigSidebar 配置并连接沙箱环境时 THEN 系统 SHALL CONTINUE TO 正常建立 WebSocket 连接、初始化沙箱、创建 Quest 会话，现有的连接和初始化流程不受影响

3.4 WHEN 后端 `CodingSessionController` 的 CRUD 接口被直接调用时 THEN 系统 SHALL CONTINUE TO 正确执行创建、查询、更新、删除操作，现有后端逻辑不受影响
