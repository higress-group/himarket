# 实施计划：HiCli POC 代码清理

## 概述

按照"先迁移共享组件 → 更新引用 → 删除专属代码 → 清理残留引用 → 编译验证"的顺序，安全地从前端移除 HiCli 相关代码。后端代码完全不动。

## 任务

- [x] 1. 迁移共享组件到 common/ 目录
  - [x] 1.1 将 `src/components/hicli/SandboxInitProgress.tsx` 移动到 `src/components/common/SandboxInitProgress.tsx`
    - 文件内容不变，仅移动位置
    - _需求: 4.1_

  - [x] 1.2 将 `src/components/hicli/CustomModelForm.tsx` 移动到 `src/components/common/CustomModelForm.tsx`
    - 文件内容不变，仅移动位置
    - _需求: 4.1_

  - [x] 1.3 将 `src/components/hicli/MarketModelSelector.tsx` 移动到 `src/components/common/MarketModelSelector.tsx`
    - 文件内容不变，仅移动位置
    - _需求: 4.1_

  - [x] 1.4 将 `src/components/hicli/MarketMcpSelector.tsx` 移动到 `src/components/common/MarketMcpSelector.tsx`
    - 文件内容不变，仅移动位置
    - _需求: 4.1_

  - [x] 1.5 将 `src/components/hicli/MarketSkillSelector.tsx` 移动到 `src/components/common/MarketSkillSelector.tsx`
    - 文件内容不变，仅移动位置
    - _需求: 4.1_

- [x] 2. 更新所有引用迁移组件的 import 路径
  - [x] 2.1 更新 `src/pages/Coding.tsx` 中 `SandboxInitProgress` 的 import 路径从 `../components/hicli/SandboxInitProgress` 改为 `../components/common/SandboxInitProgress`
    - _需求: 4.2, 4.3_

  - [x] 2.2 更新 `src/components/common/CliSelector.tsx` 中 `CustomModelForm`、`MarketModelSelector`、`MarketMcpSelector`、`MarketSkillSelector` 的 import 路径从 `../hicli/` 改为 `./`
    - _需求: 4.3_

  - [x] 2.3 全局搜索前端代码中所有 `from.*hicli/` 的 import 语句，确保没有遗漏的引用需要更新
    - 检查迁移组件内部是否有互相引用 hicli/ 路径的 import，如有则一并更新
    - _需求: 4.3, 7.5_

- [x] 3. 检查点 - 确保迁移后编译通过
  - 确保所有测试通过，如有问题请询问用户。
  - 在 `himarket-web/himarket-frontend/` 目录下运行 `npx tsc --noEmit` 验证 TypeScript 编译无错误
  - _需求: 4.4_

- [x] 4. 删除 HiCli 专属文件
  - [x] 4.1 删除 HiCli 页面文件 `src/pages/HiCli.tsx`
    - _需求: 1.3_

  - [x] 4.2 删除 HiCli Context 文件 `src/context/HiCliSessionContext.tsx`
    - _需求: 2.1_

  - [x] 4.3 删除 HiCli Hook 文件 `src/hooks/useHiCliSession.ts`
    - _需求: 2.2_

  - [x] 4.4 删除 hicli/ 目录下剩余的 HiCli 专属组件文件
    - 删除 `src/components/hicli/HiCliSelector.tsx`
    - 删除 `src/components/hicli/HiCliSidebar.tsx`
    - 删除 `src/components/hicli/HiCliTopBar.tsx`
    - 删除 `src/components/hicli/HiCliWelcome.tsx`
    - 删除 `src/components/hicli/AcpLogPanel.tsx`
    - 删除 `src/components/hicli/AgentInfoCard.tsx`
    - _需求: 3.1_

  - [x] 4.5 删除 HiCli 相关前端测试文件
    - 删除 `src/context/__tests__/HiCliSessionContext.test.ts`
    - 删除 `src/hooks/__tests__/useHiCliSession.lazySession.test.ts`
    - 删除 `src/hooks/__tests__/useHiCliSession.preservation.test.ts`
    - 删除 `src/components/hicli/__tests__/AcpLogPanel.test.tsx`
    - 删除 `src/components/hicli/__tests__/AgentInfoCard.test.tsx`（如存在）
    - _需求: 5.1, 5.2, 5.3_

  - [x] 4.6 删除 `src/components/hicli/__tests__/` 目录和 `src/components/hicli/` 目录（确认目录为空后整体删除）
    - _需求: 4.5_

- [x] 5. 移除路由和导航栏中的 HiCli 入口
  - [x] 5.1 修改 `src/router.tsx`：移除 `import HiCli from "./pages/HiCli"` 和 `<Route path="/hicli" element={<HiCli />} />` 路由定义
    - _需求: 1.1, 1.3, 1.4_

  - [x] 5.2 修改 `src/components/Header.tsx`：从 tabs 数组中移除 `{ path: "/hicli", label: "HiCli" }` 条目
    - _需求: 1.2_

- [x] 6. 清理代码中残留的 HiCli 注释引用
  - [x] 6.1 修改 `src/lib/utils/wsUrl.ts`：移除文件顶部注释中对 HiCli 的引用（将 `HiWork/Quest、HiCoding/Coding、HiCli` 改为 `HiWork/Quest、HiCoding/Coding`）
    - _需求: 8.1_

  - [x] 6.2 修改 `src/components/common/CliSelector.tsx`：移除 `showRuntimeSelector` 属性注释中对 HiCli 的引用（`// 默认 false，HiCli 传 true`）
    - _需求: 8.2_

  - [x] 6.3 全局搜索前端源代码中残留的 HiCli 相关 import 语句和注释引用，确保全部清理
    - 搜索关键词：`HiCli`、`hicli`、`useHiCliSession`、`HiCliSession`
    - 不修改后端代码和配置文件
    - _需求: 8.2, 2.3, 3.2_

- [x] 7. 最终检查点 - 前端编译验证
  - 在 `himarket-web/himarket-frontend/` 目录下运行 `npx tsc --noEmit` 确认 TypeScript 编译无错误
  - 运行 `npm run build` 确认构建成功
  - 确保所有测试通过，如有问题请询问用户
  - _需求: 4.4, 7.4_

## 备注

- 所有文件路径相对于 `himarket-web/himarket-frontend/` 目录
- 后端代码（Java、application.yml、后端测试）完全不做任何修改（需求 6）
- 前端 `RuntimeType` 中的 `'local'` 类型保留不变（需求 6.4）
- `useRuntimeSelection` Hook 中的 local 相关逻辑保留不变（需求 6.5）
- 任务按依赖顺序排列：先迁移共享组件 → 更新引用 → 编译验证 → 删除专属代码 → 清理入口 → 清理注释 → 最终验证
