# 需求文档

## 简介

HiCli 作为 POC（概念验证）模块已完成历史使命，需要从前端代码中移除 HiCli 相关逻辑。清理范围严格限定在前端：删除 HiCli 专属页面、组件、Hook、Context、路由和导航入口，迁移被 HiCoding 共用的组件到 common/ 目录。后端代码（包括 LOCAL 沙箱模式）全部保留不做任何修改，确保 HiCoding 功能不受影响。

## 术语表

- **HiCli_Page**：HiCli 页面组件（`src/pages/HiCli.tsx`），POC 阶段用于验证 ACP 协议和 CLI Agent 交互的前端页面
- **HiCliSessionContext**：HiCli 状态管理 Context（`src/context/HiCliSessionContext.tsx`），扩展自 QuestState，为 HiCli 页面提供会话状态
- **useHiCliSession_Hook**：HiCli WebSocket 会话 Hook（`src/hooks/useHiCliSession.ts`），管理 HiCli 的 WebSocket 连接和会话生命周期
- **HiCli_Components**：位于 `src/components/hicli/` 目录下的 HiCli 专属组件集合，包括 HiCliSelector、HiCliSidebar、HiCliTopBar、HiCliWelcome、AcpLogPanel、AgentInfoCard
- **Shared_Components**：位于 `src/components/hicli/` 目录下但被 HiCoding 共用的组件，包括 SandboxInitProgress、CustomModelForm、MarketModelSelector、MarketMcpSelector、MarketSkillSelector
- **Common_Directory**：`src/components/common/` 目录，用于存放跨功能模块共享的通用组件
- **Router**：前端路由配置文件（`src/router.tsx`），定义应用的页面路由映射
- **Header**：导航栏组件（`src/components/Header.tsx`），包含应用顶部导航 Tab
- **RuntimeType**：前端运行时类型定义（`src/types/runtime.ts`），包含 `'local'` 和 `'k8s'` 两种类型
- **HiCoding_Page**：HiCoding 页面组件（`src/pages/Coding.tsx`），主要的代码开发页面
- **Frontend_Build**：前端 TypeScript 编译构建过程（`npm run build`）

## 需求

### 需求 1：删除 HiCli 专属页面和入口

**用户故事：** 作为开发者，我希望移除 HiCli 页面及其路由和导航入口，以消除已废弃的 POC 功能对用户的干扰。

#### 验收标准

1. WHEN 前端清理完成后，THE Router SHALL 不包含 `/hicli` 路径的路由定义
2. WHEN 前端清理完成后，THE Header SHALL 不包含指向 HiCli 的导航 Tab
3. WHEN 前端清理完成后，THE Frontend_Build SHALL 不包含对 HiCli_Page 模块的引用
4. WHEN 用户访问 `/hicli` 路径，THE Router SHALL 返回 404 或重定向到有效页面

### 需求 2：删除 HiCli 专属状态管理和 Hook

**用户故事：** 作为开发者，我希望移除 HiCli 专属的 Context 和 Hook，以减少代码库中的废弃代码。

#### 验收标准

1. WHEN 前端清理完成后，THE Frontend_Build SHALL 不包含 HiCliSessionContext 模块
2. WHEN 前端清理完成后，THE Frontend_Build SHALL 不包含 useHiCliSession_Hook 模块
3. WHEN 前端清理完成后，THE Frontend_Build SHALL 不存在对 HiCliSessionContext 或 useHiCliSession_Hook 的 import 语句

### 需求 3：删除 HiCli 专属组件

**用户故事：** 作为开发者，我希望移除 HiCli 专属的 UI 组件，以保持代码库整洁。

#### 验收标准

1. WHEN 前端清理完成后，THE Frontend_Build SHALL 不包含 HiCliSelector、HiCliSidebar、HiCliTopBar、HiCliWelcome、AcpLogPanel、AgentInfoCard 组件模块
2. WHEN 前端清理完成后，THE Frontend_Build SHALL 不存在对 HiCli_Components 中任何组件的 import 语句

### 需求 4：迁移共享组件到 Common 目录

**用户故事：** 作为开发者，我希望将 hicli/ 目录下被 HiCoding 共用的组件迁移到 common/ 目录，以确保删除 hicli/ 目录后共享功能仍然可用。

#### 验收标准

1. WHEN 共享组件迁移完成后，THE Common_Directory SHALL 包含 SandboxInitProgress、CustomModelForm、MarketModelSelector、MarketMcpSelector、MarketSkillSelector 组件文件
2. WHEN 共享组件迁移完成后，THE HiCoding_Page SHALL 通过 Common_Directory 路径引用 SandboxInitProgress 组件
3. WHEN 共享组件迁移完成后，所有引用 Shared_Components 的文件 SHALL 更新 import 路径指向 Common_Directory
4. WHEN 共享组件迁移完成后，THE Frontend_Build SHALL 编译成功且无 TypeScript 错误
5. WHEN 所有共享组件迁移完成且 HiCli 专属组件删除后，THE `src/components/hicli/` 目录 SHALL 被整体删除

### 需求 5：删除 HiCli 相关前端测试文件

**用户故事：** 作为开发者，我希望移除 HiCli 相关的前端测试文件，以避免测试套件中存在针对已删除代码的测试。

#### 验收标准

1. WHEN 前端清理完成后，THE 前端测试目录 SHALL 不包含 HiCliSessionContext 的测试文件
2. WHEN 前端清理完成后，THE 前端测试目录 SHALL 不包含 useHiCliSession 的测试文件
3. WHEN 前端清理完成后，THE 前端测试目录 SHALL 不包含 AcpLogPanel 的测试文件

### 需求 6：保留后端代码和 LOCAL 模式

**用户故事：** 作为开发者，我希望后端代码和 LOCAL 沙箱模式完全不受前端清理影响，以确保本地开发调试能力和未来沙箱扩展性。

#### 验收标准

1. THE 后端 Java 源代码 SHALL 在清理过程中保持零修改
2. THE 后端配置文件（application.yml）SHALL 在清理过程中保持零修改
3. THE 后端测试文件 SHALL 在清理过程中保持零修改
4. THE RuntimeType SHALL 保留 `'local'` 类型定义
5. THE useRuntimeSelection Hook SHALL 保留 local 相关逻辑

### 需求 7：HiCoding 功能完整性保障

**用户故事：** 作为用户，我希望 HiCoding 的所有功能在清理后仍然正常工作，以确保我的开发体验不受影响。

#### 验收标准

1. WHEN 前端清理完成后，THE HiCoding_Page SHALL 正常加载并渲染
2. WHEN 前端清理完成后，THE HiCoding_Page 的 CLI 选择器 SHALL 正常工作
3. WHEN 前端清理完成后，THE HiCoding_Page 的 WebSocket 连接 SHALL 正常建立（LOCAL 和 K8s 运行时均可用）
4. WHEN 前端清理完成后，THE Frontend_Build SHALL 编译成功且无 TypeScript 错误
5. WHEN 前端清理完成后，THE 前端代码 SHALL 不包含任何指向 `src/components/hicli/` 的 import 路径

### 需求 8：清理代码中的 HiCli 引用

**用户故事：** 作为开发者，我希望代码中不再残留 HiCli 相关的注释和引用，以保持代码库的一致性。

#### 验收标准

1. WHEN 前端清理完成后，THE `src/lib/utils/wsUrl.ts` SHALL 不包含对 HiCli 的注释引用
2. WHEN 前端清理完成后，THE 前端源代码文件 SHALL 不包含对已删除 HiCli 模块的 import 语句
