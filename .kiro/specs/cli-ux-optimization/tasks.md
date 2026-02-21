# 实现计划：CLI UX 优化

## 概述

将 HiWork、HiCoding、HiCli 三个模块的 UI 从 demo 级别提升到产品级别。核心改动包括：CLI Provider 排序工具函数、通用卡片/搜索组件、CliSelector 分步骤重构、MCP/Skill 卡片化重构、统一欢迎页组件。所有改动为纯前端变更。

## 任务

- [x] 1. 实现工具函数和通用基础组件
  - [x] 1.1 创建 `sortCliProviders` 排序工具函数
    - 创建 `src/lib/utils/cliProviderSort.ts`
    - 实现排序逻辑：Qwen Code（key 包含 "qwen"）排第一，其余可用工具保持原序，不可用工具排末尾
    - _Requirements: 1.1, 1.2_

  - [x]* 1.2 编写 `sortCliProviders` 属性测试
    - 创建 `src/lib/utils/__tests__/cliProviderSort.property.test.ts`
    - **Property 1: CLI Provider 排序不变量**
    - 使用 fast-check 生成随机 provider 列表，验证排序不变量
    - **Validates: Requirements 1.1**

  - [x] 1.3 创建 `filterByKeyword` 过滤工具函数
    - 创建 `src/lib/utils/filterUtils.ts`
    - 实现通用关键词模糊匹配过滤，支持指定多个字段，大小写不敏感
    - _Requirements: 2.4, 3.4_

  - [x]* 1.4 编写 `filterByKeyword` 属性测试
    - 创建 `src/lib/utils/__tests__/filterUtils.property.test.ts`
    - **Property 2: 关键词过滤正确性**
    - 使用 fast-check 生成随机对象列表和关键词，验证过滤结果正确性
    - **Validates: Requirements 2.4, 3.4**

  - [x] 1.5 创建 `SelectableCard` 通用可选卡片组件
    - 创建 `src/components/common/SelectableCard.tsx`
    - 实现选中/未选中/禁用三种视觉状态，选中时蓝色边框高亮
    - _Requirements: 2.1, 2.2, 3.1, 3.2, 7.1, 7.2_

  - [x] 1.6 创建 `SearchFilterInput` 搜索过滤组件
    - 创建 `src/components/common/SearchFilterInput.tsx`
    - 轻量搜索输入框，带搜索图标和清除按钮
    - _Requirements: 2.3, 3.3_

- [x] 2. 检查点 - 确保基础组件测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 3. 重构 CliSelector 为分步骤布局
  - [x] 3.1 实现步骤计算逻辑 `computeSteps`
    - 在 `src/components/common/CliSelector.tsx` 中添加步骤计算函数
    - 根据 provider 的 `supportsCustomModel`、`supportsMcp`、`supportsSkill` 动态计算可见步骤
    - _Requirements: 4.1, 4.3_

  - [x]* 3.2 编写步骤计算属性测试
    - 创建 `src/components/common/__tests__/stepCalculation.property.test.ts`
    - **Property 3: 动态步骤计算**
    - 使用 fast-check 生成随机 provider 能力组合，验证步骤数计算
    - **Validates: Requirements 4.3**

  - [x] 3.3 重构 CliSelector 组件主体
    - 将 `CliSelector` 内部改为分步骤渲染：步骤一（工具选择 + 运行时）、步骤二（模型配置）、步骤三（扩展配置）
    - 添加步骤指示器（顶部显示当前步骤/总步骤）
    - 添加"上一步"/"下一步"/"连接"按钮逻辑
    - 使用 `sortCliProviders` 对 provider 列表排序
    - 步骤一中用 `SelectableCard` 卡片式单选替代 `Select` 下拉框
    - _Requirements: 1.1, 4.1, 4.2, 4.4, 4.5, 4.6, 7.1, 7.2, 7.3_

  - [x] 3.4 重构模型配置为 Segmented Control
    - 在步骤二中使用 antd `Segmented` 组件替代 Switch 开关
    - 三个互斥选项：默认模型 / 自定义模型 / 市场模型
    - 移除原有的两个 Switch 开关
    - _Requirements: 6.1, 6.2, 6.3, 6.4_

  - [x]* 3.5 编写 CLI 工具单选属性测试
    - 创建 `src/components/common/__tests__/cliSelection.property.test.ts`
    - **Property 4: CLI 工具单选不变量**
    - 使用 fast-check 生成随机选择操作序列，验证单选不变量
    - **Validates: Requirements 7.2**

- [x] 4. 重构 MCP 和 Skill 选择器为卡片网格
  - [x] 4.1 重构 `MarketMcpSelector` 组件
    - 将 `Checkbox.Group` 替换为 `SelectableCard` 卡片网格布局
    - 移除 `enabled` prop 和 Switch 控制逻辑（由 CliSelector 步骤控制显隐）
    - 列表超过 4 项时显示 `SearchFilterInput`，使用 `filterByKeyword` 过滤
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [x] 4.2 重构 `MarketSkillSelector` 组件
    - 将 `Checkbox.Group` 替换为 `SelectableCard` 卡片网格布局
    - 移除 `enabled` prop 和 Switch 控制逻辑
    - 卡片额外展示 `skillTags` 标签
    - 下载中的卡片显示 Spin 加载指示器
    - 列表超过 4 项时显示搜索框，支持按名称、描述、标签过滤
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

- [x] 5. 检查点 - 确保选择器重构测试通过
  - 确保所有测试通过，如有问题请向用户确认。

- [x] 6. 创建统一欢迎页并集成到各模块
  - [x] 6.1 创建 `WelcomePage` 统一欢迎页组件
    - 创建 `src/components/common/WelcomePage.tsx`
    - 统一布局：居中容器 → 模块图标 → 模块名称 → 描述文案 → CliSelector 或操作按钮
    - 支持 `isConnected` 状态切换展示内容
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

  - [x] 6.2 重构 `QuestWelcome` 使用 `WelcomePage`
    - 修改 `src/components/quest/QuestWelcome.tsx`，使用 `WelcomePage` 组件
    - 传入 HiWork 特定的图标（lucide-react 的 Workflow 图标）、名称、描述
    - _Requirements: 5.1, 5.5_

  - [x] 6.3 重构 `HiCliWelcome` 使用 `WelcomePage`
    - 修改 `src/components/hicli/HiCliWelcome.tsx`，使用 `WelcomePage` 组件
    - 传入 HiCli 特定的图标（Terminal 图标）、名称、描述
    - 保留沙箱状态提示逻辑
    - _Requirements: 5.1, 5.5_

  - [x] 6.4 重构 `Coding.tsx` 欢迎页使用 `WelcomePage`
    - 修改 `src/pages/Coding.tsx` 中的内联欢迎页部分
    - 使用 `WelcomePage` 组件替代当前的简单 div 布局
    - 传入 HiCoding 特定的图标（Code2 图标）、名称、描述
    - _Requirements: 5.1, 5.5_

- [x] 7. 更新 HiCliSelector 适配新接口
  - 修改 `src/components/hicli/HiCliSelector.tsx`，确保 `showRuntimeSelector` 正确传递
  - 验证 HiCli 模块的完整连接流程正常工作
  - _Requirements: 4.1_

- [x] 8. 最终检查点 - 确保所有测试通过
  - 确保所有测试通过，如有问题请向用户确认。

## 备注

- 标记 `*` 的任务为可选任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号以确保可追溯性
- 属性测试验证通用正确性规则，单元测试验证具体场景和边界情况
- 所有改动为纯前端变更，不涉及后端 API 修改
