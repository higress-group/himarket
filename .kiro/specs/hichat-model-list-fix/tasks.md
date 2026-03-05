# Tasks

## Task 1: 在 Area.tsx 中增加模型订阅过滤逻辑

- [x] 在 `Area.tsx` 中，利用已有的 `primaryConsumer` 和 `APIs.getConsumerSubscriptions` 获取当前开发者的订阅列表
- [x] 新增 state `modelSubscriptions` 存储 MODEL_API 类型的 APPROVED 订阅
- [x] 在已有的 `useEffect`（获取 primaryConsumer 的那个）中，同时获取订阅列表并按 APPROVED 状态过滤
- [x] 用 `useMemo` 创建 `subscribedModelList`，将 `modelList`（来自 `useProducts`）与 `modelSubscriptions` 做交集过滤，只保留 productId 在订阅列表中的模型
- [x] 将所有使用 `modelList` 的地方替换为 `subscribedModelList`（包括传给 `ModelSelector`、`MultiModelSelector` 的 `modelList` prop，以及 `modelMap` 的构建）

### 涉及文件
- `himarket-web/himarket-frontend/src/components/chat/Area.tsx`

### 验证标准
- HiChat 模型选择器只展示当前开发者已订阅（APPROVED 状态）的 MODEL_API 模型
- 模型搜索、分类筛选、多模型对比功能正常
- `webSearch` 和 `enableMultiModal` 等 feature 判断不受影响
- HiCoding 的模型选择行为不受影响
