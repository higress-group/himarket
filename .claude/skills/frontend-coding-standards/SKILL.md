---
name: frontend-coding-standards
description: "HiMarket 前端（React + TypeScript + Vite）代码规范：Prettier/ESLint、严格 TypeScript、注释与 Fast Refresh、Design Token、Tailwind、验证命令。"
---

# HiMarket 前端代码规范（Frontend Coding Standards）

本 Skill 用于在修改 **HiMarket Web 前端** 时约束格式、类型、结构与可维护性，并与仓库内已配置的工具体系保持一致。

## 何时启用（When to Activate）

- 新增或修改 `himarket-web/himarket-frontend` 或 `himarket-web/himarket-admin` 下的源码、样式或类型定义
- Code Review、重构、或 AI 辅助编写前端代码时需要统一团队约定
- 提交前需要确认 `lint` / `type-check` / `format:check` 可通过

## 1. 适用范围（Scope）

| 目录 | 说明 |
|------|------|
| `himarket-web/himarket-frontend/` | 开发者门户（React 19 + Vite + TypeScript） |
| `himarket-web/himarket-admin/` | 管理门户（同上技术栈） |

**约定**：两个子项目各自在根目录执行 npm 脚本；若一次变更涉及两个应用，**分别在对应目录**运行验证命令（见第 9 节）。

---

## 2. 代码格式规范（Formatting）

### 2.1 Prettier

- **必须以各子项目根目录下的 `.prettierrc` 为准**，提交前执行 `npm run format` 或确保 `format:check` 通过。
- 两应用当前共享同一套核心配置（以仓库文件为准，若有差异以该应用目录内配置优先）：

| 项 | 要求 |
|----|------|
| 缩进 | **2 空格**（`tabWidth: 2`，`useTabs: false`） |
| 引号 | **单引号**（`singleQuote: true`） |
| 分号 | **使用分号**（`semi: true`） |
| 行尾逗号 | **多行末尾始终加逗号**（`trailingComma: "all"`） |
| 对象括号空格 | `bracketSpacing: true` |
| 箭头函数参数 | 单参数也保留括号（`arrowParens: "always"`） |
| 换行符 | **LF**（`endOfLine: "lf"`） |
| **最大行宽** | **`printWidth: 100`**（超出应换行，而非放宽配置） |

### 2.2 与 ESLint 的关系

- 项目使用 `eslint-plugin-prettier`（Prettier 问题在 ESLint 中报告）。**不要**在业务代码里用格式化风格与 Prettier 对抗；有争议以 `.prettierrc` 为准。

---

## 3. 代码规范（Linting & TypeScript）

### 3.1 ESLint

- **严格遵循各应用根目录 `eslint.config.js`**，禁止为“图省事”大面积 `eslint-disable`。
- 与业务强相关的规则包括但不限于：
  - **`@typescript-eslint/consistent-type-imports`: error** — 类型使用 `import type`。
  - **`@typescript-eslint/no-explicit-any`: error**
  - **`@typescript-eslint/no-non-null-assertion`: error**
  - **`eqeqeq`: `['error', 'always']`** — 必须使用 **`===` / `!==`**。
  - **`import/order`** — 分组、字母序、`@/**` 作为 internal；组间空行。
  - **`perfectionist/sort-jsx-props`**、**`perfectionist/sort-objects`** — 按字母序排序（保持与现有代码一致）。
  - **`jsx-a11y/*`** — 可访问性（如 `alt-text`、`click-events-have-key-events` 等）。
  - **`no-console`: error**（仅允许 `console.warn` / `console.error`）。
  - **`no-warning-comments`** — 以 `TODO` / `FIXME` 开头的注释会触发 **warn**（见第 4 节 TODO 约定）。
  - **`react-refresh/only-export-components`** — 见第 5.2 节 Fast Refresh。
  - **`react-hooks/exhaustive-deps`: error**

### 3.2 TypeScript 严格模式

- 两应用均启用 **`"strict": true`**，并包含如 **`noUnusedLocals`**、**`noUnusedParameters`**、**`noUncheckedIndexedAccess`**、**`noImplicitReturns`** 等加强选项（以各包 `tsconfig*.json` 为准）。
- 新增代码必须在不关闭严格选项的前提下通过 `tsc`。

### 3.3 禁止 `any`

- **禁止**使用 `any` 逃避类型检查（与 ESLint 一致）。
- **推荐模式**：先用 **`unknown`** 收窄，再辅以 **类型守卫** 或 **有依据的类型断言**（`as`），并尽量把断言收敛在边界层（API 解析、第三方回调等）。

```typescript
// 避免
function parse(input: any) {
  return input.foo;
}

// 更好：unknown + 收窄或明确类型
function parse(input: unknown) {
  if (typeof input === 'object' && input !== null && 'foo' in input) {
    return (input as { foo: string }).foo;
  }
  throw new TypeError('Invalid payload');
}
```

### 3.4 禁止非空断言 `!`

- **禁止**依赖 `value!` 假定非空；改用可选链、显式判空、早期返回或合理的数据结构（与 ESLint 一致）。

### 3.5 相等判断

- **始终使用严格相等**：`===` 与 `!==`（禁止 `==` / `!=`）。

---

## 4. 注释规范（Comments）

### 4.1 语言

- **推荐英文注释**（便于国际化协作与检索）；业务或产品强相关的简短中文说明可接受，但需清晰、短句。
- **`src/types/` 目录（及同类类型定义文件）**：**注释必须使用英文**（含 JSDoc / 行内说明）。

### 4.2 死代码

- **删除**被整段注释掉的废弃实现；若连续 **`//` 注释达到 3 行及以上**且仅为旧代码备份，**必须删除**或改为版本控制中的历史记录，不要留在主干。

### 4.3 TODO / FIXME

- ESLint 对以 **`TODO` / `FIXME` 开头**的注释有警告；请仅在有真实跟进需求时使用，且**必须带具体描述**（负责人、工单链接、或明确下一步），避免 `// TODO: fix` 这类无意义占位。

```typescript
// 可接受：具体可执行
// TODO(himarket#123): migrate to new API response shape once backend deploys

// 避免
// TODO: handle error
```

### 4.4 避免噪声注释

- 不要注释“显而易见”的代码；**复杂分支、非显而易见的算法、或跨模块约定**再写注释。

---

## 5. 代码风格（React & Modules）

### 5.1 组件写法

- **函数组件优先使用箭头函数**声明（与团队统一风格）；顶层导出名称清晰（如 `export const UserPanel = () => { ... }`）。
- 若文件内仍有历史 `function` 声明，新代码优先箭头函数；大范围改写时可在同一 PR 内局部统一，避免无关重排。

### 5.2 Fast Refresh（`react-refresh/only-export-components`）

- **导出 React 组件的模块**应以组件为主；非组件导出可能破坏 Fast Refresh。
- 配置中通常 **`allowConstantExport: true`** — 允许与组件同文件的 **常量** 导出；**hooks、工具函数、大型非组件逻辑** 仍应 **拆到独立文件**（如 `useXxx.ts`、`xxxUtils.ts`）。

### 5.3 Named export 优先

- **优先使用命名导出**（`export const` / `export function` / `export type`），**避免默认导出**（`export default`），以利于静态分析与 **tree-shaking**、以及 IDE 重构。
- 路由懒加载等若必须使用 `default`，保持该文件职责单一、导出唯一。

### 5.4 Hooks 与工具

- 自定义 Hook 以 **`use` 前缀**命名，放在 **`hooks/`** 或与领域就近的 `useXxx.ts` 文件中。
- 纯函数工具放在 **`utils/`** 或 **`lib/`** 等现有约定目录，**不与组件混在一个巨型文件**里。

---

## 6. UI/UX 规范

### 6.1 Design Token（Ant Design）

- Ant Design 主题应通过各应用中的 **`aliyunThemeToken`**（见 `src/aliyunThemeToken` 及 `App.tsx` 内 `ConfigProvider`）统一衍生，**避免在业务里硬编码大量散落的色值/圆角**；局部覆盖优先使用 `theme` / `token` 扩展或组件 `styles` / `className` 与 Token 一致。

### 6.2 Tailwind CSS

- 两应用均已接入 Tailwind：**布局、间距、响应式、原子化样式优先用 Tailwind 类名**，与 Ant Design 组件组合时保持视觉与 Design Token 一致；避免内联 style 堆砌（除非动态计算不可避免）。

### 6.3 组件 Props

- **必须为 props 定义明确的 `interface`（或 `type`）**，命名清晰（如 `UserPanelProps`）；避免 `props: any` 或过度宽泛的对象类型。

### 6.4 表单

- **受控组件为主**：值由 React state（或表单库）驱动，`value` + `onChange`（或等价 API）清晰可追溯；避免非受控与受控混用导致不可预期的同步问题。

---

## 7. 代码简洁性（Simplicity）

- **DRY**：重复出现两次以上的逻辑，提取函数、Hook 或小组件。
- **单一职责**：每个函数/组件只做一件事；过大则拆分。
- **避免深层嵌套**：通过 **提前返回（early return）**、卫语句、或抽取子函数将缩进控制在可读范围内。
- **提前返回**：非法输入、空数据、无权限等路径优先 return，减少 `else` 金字塔。

---

## 8. 可读性（Readability）

- **变量与函数名**应表达意图，避免 `data`、`temp`、`handler1` 等模糊命名。
- **复杂逻辑**在关键分支处用**简短英文注释**说明“为什么”，而非“做什么”。
- **重复 UI 逻辑**用 **自定义 Hook** 提取；重复视图块用 **子组件** 提取。

---

## 9. 验证要求（Mandatory Checks）

在对应子项目根目录执行（路径示例）：

```bash
cd himarket-web/himarket-admin   # 或 himarket-web/himarket-frontend

npm run lint
npm run type-check
npm run format:check
```

| 脚本 | 作用 |
|------|------|
| `npm run lint` | ESLint（含 Prettier 集成规则） |
| `npm run type-check` | TypeScript 类型检查（admin：`tsc --noEmit`；frontend：`tsc -b`，以各包 `package.json` 为准） |
| `npm run format:check` | Prettier 仅检查（不写入） |

**合并前要求**：对上述**已修改的应用目录**，三项均须通过。必要时可运行 `npm run lint:fix` 与 `npm run format` 自动修复可修复项，再复查 diff。

---

## 10. 自检清单（Quick Self-Review）

- [ ] Prettier（100 列、单引号、分号、LF）无争议
- [ ] 无 `any`、无 `!` 非空断言、无 `==`
- [ ] `types/` 下注释为英文；无大块注释死代码
- [ ] TODO 有具体描述；新增 `console.log` 已移除
- [ ] 组件文件符合 Fast Refresh 习惯；hooks/utils 已拆分
- [ ] UI 使用 `aliyunThemeToken` + Tailwind；props 有明确 interface
- [ ] 在目标应用目录下 `lint`、`type-check`、`format:check` 均已通过
