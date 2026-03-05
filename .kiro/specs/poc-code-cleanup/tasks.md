# 实施计划：POC 残留代码清理

## 概述

分三步完成清理：先修复 P0 安全问题（WebSocket 匿名访问、WorkspaceController 匿名回退），再清理 P2 前端 POC 文案，最后更新所有相关测试用例。使用 Java（后端）和 TypeScript（前端测试）。

## 任务

- [x] 1. P0：移除 AcpHandshakeInterceptor 匿名访问模式
  - [x] 1.1 修改 AcpHandshakeInterceptor，将无 token 时的匿名放行逻辑改为拒绝连接
    - 文件：`himarket-server/src/main/java/com/alibaba/himarket/service/acp/AcpHandshakeInterceptor.java`
    - 将 `StrUtil.isBlank(token)` 分支中的 `attributes.put("userId", "anonymous"); return true;` 改为 `logger.warn("WebSocket handshake rejected: missing token"); return false;`
    - 移除 POC mode 相关注释和 info 日志
    - _需求：1.1, 1.3, 1.4_

  - [x] 1.2 更新 AcpHandshakeInterceptorTest，验证无 token 连接被拒绝
    - 文件：`himarket-server/src/test/java/com/alibaba/himarket/service/acp/AcpHandshakeInterceptorTest.java`
    - 将 `beforeHandshake_noToken_allowsAnonymous` 测试重命名为 `beforeHandshake_noToken_rejectsConnection`
    - 断言改为 `assertFalse(result)` 且 `userId` 属性不存在
    - 新增测试：验证有效 token 通过握手并正确设置 userId（需求 1.2）
    - _需求：1.1, 1.2, 1.3_

  - [ ]* 1.3 编写属性测试：非有效 token 连接一律拒绝
    - **属性 1：非有效 token 连接一律拒绝**
    - 对于所有空白/null token 的握手请求，`beforeHandshake` 应返回 false
    - **验证：需求 1.1, 1.3**

- [x] 2. P0：移除 WorkspaceController 匿名用户回退
  - [x] 2.1 修改 WorkspaceController 的 getCurrentUserId() 方法
    - 文件：`himarket-server/src/main/java/com/alibaba/himarket/controller/WorkspaceController.java`
    - 将 `return "anonymous"` 改为 `throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户未认证")`
    - 添加必要的 import（BusinessException、ErrorCode）
    - _需求：2.1, 2.2, 2.3_

  - [ ]* 2.2 编写属性测试：匿名访问不可达
    - **属性 3：匿名访问不可达**
    - 对于所有可能的认证状态，`getCurrentUserId()` 要么返回非 "anonymous" 的有效 userId，要么抛出 BusinessException
    - **验证：需求 2.2, 2.3**

- [x] 3. 检查点 - 确保后端编译通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 4. P2：清理前端 RuntimeSelector 测试 POC 文案
  - [x] 4.1 更新 RuntimeSelector 测试数据和断言
    - 文件：`himarket-web/himarket-frontend/src/components/common/__tests__/RuntimeSelector.test.tsx`
    - 将 `localRuntime` 的 `label` 从 `'POC 本地启动'` 改为 `'本地运行'`
    - 将 `localRuntime` 的 `description` 从 `'通过 ProcessBuilder 在本机启动 CLI 子进程'` 改为正式描述（如 `'在本地环境运行 CLI 进程'`）
    - 同步更新所有引用旧文案的 `expect` 断言（如 `screen.getByText('POC 本地启动')` → `screen.getByText('本地运行')`）
    - _需求：3.1, 3.2, 3.3_

- [x] 5. 最终检查点 - 确保所有测试通过
  - 确保所有测试通过，如有问题请向用户确认。

## 备注

- 标记 `*` 的子任务为可选，可跳过以加速 MVP
- 每个任务引用了具体的需求编号以确保可追溯性
- 属性测试验证设计文档中定义的正确性属性
