# Bugfix Requirements Document

## Introduction

HiChat 的模型列表下拉选择器展示了市场中所有已发布的模型，而 HiCoding 只展示当前开发者已订阅的模型。需要将 HiChat 的模型查询逻辑统一为 HiCoding 的方式，即只展示已订阅的模型。

**根因分析：**
- HiChat（`Area.tsx`）通过 `useProducts({ type: "MODEL_API" })` 调用 `GET /products?type=MODEL_API`，该接口返回市场中所有已发布的 MODEL_API 类型产品，不做订阅状态过滤。
- HiCoding（`MarketModelSelector.tsx`）通过 `getMarketModels()` 调用 `GET /cli-providers/market-models`，该接口先获取当前开发者的 Primary Consumer，再查询其 APPROVED 状态的订阅列表，最后只返回已订阅的 MODEL_API 类型产品。

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN 开发者打开 HiChat 页面 THEN 模型列表下拉选择器展示市场中所有已发布的 MODEL_API 类型模型，包括未订阅的模型
1.2 WHEN 开发者在 HiChat 中选择一个未订阅的模型进行对话 THEN 可能因缺少订阅凭证导致调用失败或行为异常

### Expected Behavior (Correct)

2.1 WHEN 开发者打开 HiChat 页面 THEN 模型列表下拉选择器 SHALL 只展示当前开发者已订阅（APPROVED 状态）的 MODEL_API 类型模型，与 HiCoding 行为一致
2.2 WHEN 开发者在 HiChat 中选择模型进行对话 THEN 所有可选模型 SHALL 均为已订阅模型，确保调用时具备有效的订阅凭证

### Unchanged Behavior (Regression Prevention)

3.1 WHEN 开发者使用 HiCoding 的模型选择功能 THEN 系统 SHALL CONTINUE TO 只展示已订阅的模型
3.2 WHEN 开发者在 HiChat 中进行多模型对比 THEN 系统 SHALL CONTINUE TO 正常支持多模型对比功能，对比列表中的模型均来自已订阅模型
3.3 WHEN 开发者在 HiChat 中搜索或按分类筛选模型 THEN 系统 SHALL CONTINUE TO 支持搜索和分类筛选功能，筛选范围限定在已订阅模型内
3.4 WHEN 管理员在后台管理页面查看产品列表 THEN 系统 SHALL CONTINUE TO 展示所有产品，不受此修改影响
