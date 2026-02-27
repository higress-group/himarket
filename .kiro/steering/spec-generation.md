---
inclusion: manual
---

# Spec 生成规则

## Design 文档精简

生成 design.md 时：

1. **保留正确性属性（Correctness Properties）**：作为 AI 实现时的验证点，每条属性关联到 requirements
2. **不生成测试策略章节**：跳过"测试策略"（Test Strategy）整个章节，包括单元测试、集成测试、属性测试的表格和代码示例
3. **不生成测试代码片段**：design 中不要包含任何测试代码（fast-check、jqwik、JUnit、Vitest 等）

## Tasks 文档精简

生成 tasks.md 时：

1. **不生成测试任务**：不要把"编写单元测试"、"编写属性测试"、"编写集成测试"作为独立任务
2. **实现任务中不包含测试子步骤**：每个实现任务聚焦于功能代码本身

## 目的

正确性属性用于 AI 自检实现是否满足设计要求，不需要落地为实际测试代码。测试由开发者按需手动编写。
