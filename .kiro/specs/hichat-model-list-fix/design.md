# HiChat 模型列表订阅过滤 Bugfix Design

## Overview

HiChat 的模型列表下拉选择器当前展示了市场中所有已发布的 MODEL_API 类型模型，包括用户未订阅的模型。这导致用户可能选择未订阅的模型进行对话，从而因缺少订阅凭证导致调用失败。修复方案是在 `Area.tsx` 中利用已有的 `primaryConsumer` 和 `getConsumerSubscriptions` API 获取当前开发者的 APPROVED 订阅列表，然后对 `useProducts` 返回的 `modelList` 做交集过滤，只保留已订阅的模型。

## Glossary

- **Bug_Condition (C)**: 模型列表中包含当前开发者未订阅的模型——即 `modelList` 中存在 `productId` 不在 APPROVED 订阅列表中的模型
- **Property (P)**: 模型列表应只包含当前开发者已订阅（APPROVED 状态）的 MODEL_API 类型模型
- **Preservation**: 现有的模型搜索、分类筛选、多模型对比、MCP 功能、webSearch/enableMultiModal feature 判断等行为不受影响
- **`useProducts`**: `Area.tsx` 中使用的 hook，调用 `GET /products?type=MODEL_API` 返回所有已发布的 MODEL_API 产品
- **`getConsumerSubscriptions`**: 消费者订阅 API，调用 `GET /consumers/{consumerId}/subscriptions` 返回订阅列表
- **`primaryConsumer`**: 当前开发者的默认消费者，通过 `GET /consumers/primary` 获取
- **`ISubscription`**: 订阅数据结构，包含 `productId`、`status`（PENDING/APPROVED/REJECTED）等字段

## Bug Details

### Fault Condition

当开发者打开 HiChat 页面时，`Area.tsx` 通过 `useProducts({ type: "MODEL_API" })` 获取模型列表。该 hook 调用 `GET /products?type=MODEL_API`，返回市场中所有已发布的 MODEL_API 产品，不做订阅状态过滤。因此模型选择器中会出现用户未订阅的模型。

**Formal Specification:**
```
FUNCTION isBugCondition(modelList, subscriptions)
  INPUT: modelList of type IProductDetail[], subscriptions of type ISubscription[]
  OUTPUT: boolean

  approvedProductIds := SET { s.productId | s IN subscriptions AND s.status == "APPROVED" }
  unsubscribedModels := FILTER(modelList, m => m.productId NOT IN approvedProductIds)

  RETURN LENGTH(unsubscribedModels) > 0
END FUNCTION
```

### Examples

- 开发者订阅了模型 A、B，市场中有模型 A、B、C、D → 当前行为：选择器展示 A、B、C、D；期望行为：选择器只展示 A、B
- 开发者订阅了模型 A（APPROVED）和模型 B（PENDING）→ 当前行为：展示所有模型；期望行为：只展示模型 A
- 开发者没有任何 APPROVED 订阅 → 当前行为：展示所有模型；期望行为：模型列表为空
- 开发者选择未订阅的模型 C 发起对话 → 当前行为：调用失败或行为异常；期望行为：模型 C 不出现在选择器中

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- HiCoding 的 `MarketModelSelector` 组件通过 `getMarketModels()` 获取已订阅模型的逻辑不受影响
- HiChat 中多模型对比功能正常工作，对比列表中的模型均来自已订阅模型
- HiChat 中模型搜索和分类筛选功能正常，筛选范围限定在已订阅模型内
- MCP 服务的订阅、添加、移除功能不受影响
- `webSearch` 和 `enableMultiModal` 等 feature 判断逻辑不受影响（基于 `modelMap` 查找，`modelMap` 改为基于过滤后的列表构建）
- 管理员后台产品列表展示不受影响
- `InputBox`、`Messages`、`SuggestedQuestions` 等子组件行为不受影响

**Scope:**
所有不涉及 HiChat 模型列表数据源的功能不受此修改影响，包括：
- MCP 服务列表和订阅管理
- 对话消息的发送和接收
- 会话管理和历史记录
- 管理员后台的所有功能

## Hypothesized Root Cause

基于 bug 分析，根因明确：

1. **数据源未做订阅过滤**: `Area.tsx` 使用 `useProducts({ type: "MODEL_API" })` 获取模型列表，该 hook 调用 `GET /products?type=MODEL_API`，返回所有已发布产品，不区分当前用户是否已订阅。而 HiCoding 的 `MarketModelSelector` 使用 `getMarketModels()` 调用 `GET /cli-providers/market-models`，该接口在服务端已做订阅过滤。

2. **前端缺少过滤层**: `Area.tsx` 中已有 `primaryConsumer` 和 `getConsumerSubscriptions` 的调用（用于 MCP 订阅管理），但未将订阅信息应用于模型列表的过滤。

3. **设计差异**: HiChat 和 HiCoding 在模型获取上采用了不同的 API 路径，HiChat 走的是通用产品列表接口，HiCoding 走的是专用的已订阅模型接口。

## Correctness Properties

Property 1: Fault Condition - 模型列表只包含已订阅模型

_For any_ 从 `useProducts` 返回的模型列表和从 `getConsumerSubscriptions` 返回的订阅列表，过滤后的 `subscribedModelList` SHALL 只包含 `productId` 存在于 APPROVED 状态订阅中的模型，且不遗漏任何已订阅模型。

**Validates: Requirements 2.1, 2.2**

Property 2: Preservation - 非模型列表功能行为不变

_For any_ 不涉及模型列表数据源的操作（MCP 管理、对话发送、feature 判断等），修复后的代码 SHALL 产生与修复前完全相同的行为，保持所有现有功能正常工作。

**Validates: Requirements 3.1, 3.2, 3.3, 3.4**

## Fix Implementation

### Changes Required

假设根因分析正确（`useProducts` 返回未过滤的全量模型列表）：

**File**: `himarket-web/himarket-frontend/src/components/chat/Area.tsx`

**Function**: `ChatArea`

**Specific Changes**:

1. **新增 state `modelSubscriptions`**: 存储当前开发者 APPROVED 状态的 MODEL_API 类型订阅列表
   - `const [modelSubscriptions, setModelSubscriptions] = useState<ISubscription[]>([])`

2. **在已有 `useEffect` 中获取订阅列表**: 在获取 `primaryConsumer` 后，调用 `APIs.getConsumerSubscriptions` 并按 `status === 'APPROVED'` 过滤，将结果存入 `modelSubscriptions`
   - 复用已有的 `primaryConsumer.current` 和 `APIs.getConsumerSubscriptions` 调用
   - 当前 `useEffect` 已获取 MCP 订阅（`setMcpSubscripts`），在同一位置增加模型订阅的获取

3. **新增 `useMemo` 创建 `subscribedModelList`**: 将 `modelList` 与 `modelSubscriptions` 做交集过滤
   - `subscribedModelList = modelList.filter(m => approvedProductIds.has(m.productId))`
   - 其中 `approvedProductIds` 从 `modelSubscriptions` 中提取

4. **替换所有 `modelList` 引用为 `subscribedModelList`**:
   - 传给 `ModelSelector` 的 `modelList` prop
   - 传给 `MultiModelSelector` 的 `modelList` prop
   - `modelMap` 的构建（`useMemo` 中遍历 `subscribedModelList`）
   - `currentModel` 的查找（`subscribedModelList.find(...)`）

5. **确保 feature 判断不受影响**: `showWebSearch` 和 `enableMultiModal` 依赖 `modelMap`，`modelMap` 改为基于 `subscribedModelList` 构建后，feature 判断自然限定在已订阅模型范围内，逻辑正确

## Testing Strategy

### Validation Approach

测试策略分两阶段：首先在未修复代码上验证 bug 存在，然后验证修复后行为正确且不引入回归。

### Exploratory Fault Condition Checking

**Goal**: 在实施修复前，确认 bug 存在并理解根因。

**Test Plan**: 检查 `Area.tsx` 中 `useProducts` 返回的 `modelList` 是否包含未订阅的模型。对比 `modelList` 和 `getConsumerSubscriptions` 返回的 APPROVED 订阅列表。

**Test Cases**:
1. **全量模型 vs 订阅模型对比**: 在浏览器中打开 HiChat，检查模型选择器中的模型数量是否大于已订阅模型数量（在未修复代码上会失败）
2. **未订阅模型可见性**: 确认模型选择器中出现了用户未订阅的模型（在未修复代码上会失败）
3. **未订阅模型调用**: 选择未订阅模型发起对话，观察是否调用失败（在未修复代码上可能失败）

**Expected Counterexamples**:
- 模型选择器展示的模型数量 > 用户 APPROVED 订阅数量
- 可能原因：`useProducts` 不做订阅过滤，直接返回所有已发布产品

### Fix Checking

**Goal**: 验证修复后，所有满足 bug condition 的输入都产生正确行为。

**Pseudocode:**
```
FOR ALL modelList, subscriptions WHERE isBugCondition(modelList, subscriptions) DO
  subscribedModelList := filterBySubscriptions(modelList, subscriptions)
  ASSERT ALL model IN subscribedModelList: model.productId IN approvedProductIds(subscriptions)
  ASSERT LENGTH(subscribedModelList) <= LENGTH(modelList)
  ASSERT LENGTH(subscribedModelList) == COUNT(model IN modelList WHERE model.productId IN approvedProductIds)
END FOR
```

### Preservation Checking

**Goal**: 验证修复后，所有不涉及 bug condition 的功能行为不变。

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT mcpFunctionality_original(input) == mcpFunctionality_fixed(input)
  ASSERT messageSending_original(input) == messageSending_fixed(input)
  ASSERT compareMode_original(input) == compareMode_fixed(input)
END FOR
```

**Testing Approach**: 由于此 bug 修复仅涉及前端数据过滤逻辑，建议通过手动 UI 测试和代码审查验证保持性。属性基测试适用于验证过滤函数的纯逻辑正确性。

**Test Cases**:
1. **MCP 功能保持**: 验证 MCP 服务的添加、移除、订阅功能在修复后正常工作
2. **多模型对比保持**: 验证多模型对比功能正常，对比列表中的模型均来自已订阅模型
3. **搜索筛选保持**: 验证模型搜索和分类筛选功能正常，筛选范围限定在已订阅模型内
4. **Feature 判断保持**: 验证 `webSearch` 和 `enableMultiModal` 判断逻辑在修复后正确

### Unit Tests

- 测试 `subscribedModelList` 过滤逻辑：给定 `modelList` 和 `subscriptions`，验证输出只包含 APPROVED 订阅的模型
- 测试边界情况：空订阅列表、空模型列表、所有模型均已订阅、无模型已订阅
- 测试 `modelMap` 基于过滤后列表构建的正确性

### Property-Based Tests

- 生成随机的 `modelList` 和 `subscriptions`，验证过滤结果是两者的正确交集
- 生成随机配置，验证过滤后的列表是原列表的子集
- 验证过滤函数的幂等性：对已过滤的列表再次过滤，结果不变

### Integration Tests

- 端到端验证：登录开发者账号，打开 HiChat，确认模型选择器只展示已订阅模型
- 对比验证：对比 HiChat 和 HiCoding 的模型列表，确认一致性
- 多模型对比流程：添加对比模型时，可选列表只包含已订阅模型
