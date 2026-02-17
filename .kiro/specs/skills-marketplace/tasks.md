# 实现计划：Agent Skills 市场

## 概述

将 AGENT_SKILL 作为新的 API 产品类型集成到 HiMarket 平台，涵盖后端枚举扩展、SkillConfig 数据模型、SKILL.md 解析器、技能下载接口、Admin 前端技能配置表单和 SKILL.md 编辑器、Developer 前端技能列表页和详情页（含安装命令与源码查看）。

## 任务

- [x] 1. 后端：ProductType 枚举扩展与 SkillConfig 数据模型
  - [x] 1.1 在 `ProductType.java` 枚举中新增 `AGENT_SKILL` 值
    - 文件：`himarket-dal/src/main/java/com/alibaba/himarket/support/enums/ProductType.java`
    - _Requirements: 1.1_
  - [x] 1.2 创建 `SkillConfig` 类，包含 `skillTags`（List\<String\>）和 `downloadCount`（Long）字段
    - 文件：`himarket-dal/src/main/java/com/alibaba/himarket/support/product/SkillConfig.java`
    - 扩展 `ProductFeature` 类新增 `skillConfig` 字段
    - _Requirements: 2.3_
  - [x] 1.3 扩展 `ProductResult` DTO，新增 `skillConfig` 字段用于返回技能配置
    - 文件：`himarket-server/src/main/java/com/alibaba/himarket/dto/result/product/ProductResult.java`
    - _Requirements: 1.3, 2.2_

- [x] 2. 后端：SKILL.md 解析器
  - [x] 2.1 创建 `SkillMdParser` 类，实现 `parse(String content)` 和 `serialize(SkillMdDocument doc)` 方法
    - 文件：`himarket-server/src/main/java/com/alibaba/himarket/core/skill/SkillMdParser.java`
    - 创建 `SkillMdDocument` 类（frontmatter: Map + body: String）
    - 解析逻辑：以 `---` 分隔符提取 YAML frontmatter，使用 SnakeYAML 解析
    - 序列化逻辑：将 frontmatter 序列化为 YAML，拼接 `---` 分隔符和 body
    - _Requirements: 8.1, 8.2_
  - [ ]* 2.2 编写 SKILL.md 解析器的属性测试
    - **Property 7: SKILL.md 解析往返一致性**
    - 使用 jqwik 生成随机 frontmatter（name, description 等字段）和 Markdown body
    - 验证 `parse(serialize(parse(content))) == parse(content)`
    - **Validates: Requirements 8.1, 8.2, 8.3**
  - [ ]* 2.3 编写 SKILL.md 解析器的单元测试
    - 测试合法输入：标准 SKILL.md、仅 frontmatter、仅 body
    - 测试非法输入：缺少 `---` 分隔符、YAML 语法错误、空内容
    - _Requirements: 8.3, 8.4_

- [x] 3. 后端：技能下载接口
  - [x] 3.1 创建 `SkillController`，实现 `GET /skills/{productId}/download` 端点
    - 文件：`himarket-server/src/main/java/com/alibaba/himarket/controller/SkillController.java`
    - 返回 Product 的 document 字段原始文本（Content-Type: text/markdown）
    - _Requirements: 7.1_
  - [x] 3.2 创建 `SkillService` 接口和实现类
    - 实现 `downloadSkill(String productId)` 方法
    - 查询 Product，校验类型为 AGENT_SKILL 且已发布
    - 递增 downloadCount 计数器（通过更新 feature JSON 字段）
    - 未找到或未发布时抛出 404 异常
    - _Requirements: 7.1, 7.2, 7.3_
  - [ ]* 3.3 编写下载计数递增的属性测试
    - **Property 6: 下载计数递增不变量**
    - 使用 jqwik 生成随机初始 downloadCount，调用下载后验证 count + 1
    - **Validates: Requirements 7.2**

- [x] 4. Checkpoint - 后端核心功能验证
  - 确保所有后端测试通过，ask the user if questions arise.

- [x] 5. Admin 前端：产品类型扩展与技能配置表单
  - [x] 5.1 扩展 Admin 前端类型定义，新增 AGENT_SKILL 相关类型
    - 在 `himarket-web/himarket-admin/src/types/api-product.ts` 中：
      - `ApiProduct.type` 联合类型新增 `'AGENT_SKILL'`
      - 新增 `ApiProductSkillConfig` 接口（skillTags, downloadCount）
      - `ApiProduct` 接口新增 `skillConfig` 可选字段
    - _Requirements: 1.2_
  - [x] 5.2 在 `ApiProductFormModal` 组件中新增 AGENT_SKILL 类型选项
    - 产品类型下拉框新增 "Agent Skill" 选项
    - 选择 AGENT_SKILL 时展示 SkillConfigForm 组件
    - _Requirements: 1.2, 2.1_
  - [x] 5.3 创建 `SkillConfigForm` 组件
    - 文件：`himarket-web/himarket-admin/src/components/api-product/SkillConfigForm.tsx`
    - 包含技能标签多选输入（Ant Design Tag/Select 组件）
    - _Requirements: 2.1, 2.2_

- [x] 6. Admin 前端：SKILL.md 在线编辑器
  - [x] 6.1 创建 `SkillMdEditor` 组件
    - 文件：`himarket-web/himarket-admin/src/components/api-product/SkillMdEditor.tsx`
    - 左侧 Monaco Editor（Markdown 模式），右侧 react-markdown 实时预览
    - 空内容时禁用保存按钮并显示提示
    - _Requirements: 3.1, 3.2, 3.4_
  - [x] 6.2 将 SkillMdEditor 集成到 ApiProductDetail 的 Usage Guide tab
    - 当产品类型为 AGENT_SKILL 时，Usage Guide tab 展示 SkillMdEditor
    - 保存时调用 `PUT /products/{productId}` 更新 document 字段
    - _Requirements: 3.3_

- [x] 7. Checkpoint - Admin 前端功能验证
  - 确保 Admin 前端编译通过，技能创建和编辑流程完整，ask the user if questions arise.

- [x] 8. Developer 前端：类型扩展与导航
  - [x] 8.1 扩展 Developer 前端类型定义
    - 在 `himarket-web/himarket-frontend/src/types/index.ts` 中：
      - `ProductType` 常量新增 `AGENT_SKILL: 'AGENT_SKILL'`
      - 新增 `ApiProductSkillConfig` 接口
      - `ApiProduct` 接口新增 `skillConfig` 可选字段
    - _Requirements: 1.1_
  - [x] 8.2 在 Header 导航栏新增 Skills 标签页
    - 在 `himarket-web/himarket-frontend/src/components/Header.tsx` 的 tabs 数组中新增 `{ path: "/skills", label: "Skills" }`
    - _Requirements: 5.4_
  - [x] 8.3 在 router.tsx 中新增 Skills 路由
    - 新增 `<Route path="/skills" element={<Square activeType="AGENT_SKILL" />} />`
    - 新增 `<Route path="/skills/:skillProductId" element={<SkillDetail />} />`
    - 在 Square 组件的 `handleViewDetail` 中新增 AGENT_SKILL 的跳转逻辑
    - _Requirements: 5.4_

- [x] 9. Developer 前端：技能详情页
  - [x] 9.1 创建 `SkillDetail` 页面组件
    - 文件：`himarket-web/himarket-frontend/src/pages/SkillDetail.tsx`
    - 调用 `GET /products/{productId}` 获取技能详情
    - 展示技能名称、描述、标签
    - 使用 react-markdown 渲染 SKILL.md 内容（document 字段）
    - _Requirements: 6.1_
  - [x] 9.2 创建 `InstallCommand` 组件
    - 文件：`himarket-web/himarket-frontend/src/components/skill/InstallCommand.tsx`
    - 展示 curl 下载命令：`curl -o .agents/skills/<name>/SKILL.md <download-url>`
    - 提供"复制命令"和"复制 SKILL.md 内容"两个复制按钮
    - 使用 `navigator.clipboard.writeText()` 实现复制
    - _Requirements: 6.2, 6.3_
  - [x] 9.3 创建 `SkillMdViewer` 组件
    - 文件：`himarket-web/himarket-frontend/src/components/skill/SkillMdViewer.tsx`
    - 使用 react-syntax-highlighter 以代码高亮方式展示 SKILL.md 原始内容
    - 提供 Markdown 渲染视图和源码视图的切换
    - _Requirements: 6.4_
  - [ ]* 9.4 编写安装命令格式的属性测试
    - **Property 5: 安装命令格式正确性**
    - 使用 fast-check 生成随机技能名称和产品 ID
    - 验证生成的 curl 命令包含正确的 URL 和文件路径
    - **Validates: Requirements 6.2**

- [x] 10. Developer 前端：搜索与过滤增强
  - [x] 10.1 确保 Square 组件对 AGENT_SKILL 类型的搜索和分类过滤正常工作
    - 验证 `getProducts` API 调用传递 `type=AGENT_SKILL` 参数
    - 验证搜索框的客户端过滤逻辑对技能产品生效
    - _Requirements: 5.1, 5.2, 5.3_
  - [ ]* 10.2 编写搜索过滤的属性测试
    - **Property 3: 搜索过滤正确性**
    - 使用 fast-check 生成随机关键词和产品列表
    - 验证过滤结果中每个产品的名称或描述包含关键词
    - **Validates: Requirements 5.2**

- [x] 11. Final checkpoint - 全链路验证
  - 确保所有测试通过，ask the user if questions arise.

## 备注

- 标记 `*` 的任务为可选测试任务，可跳过以加速 MVP
- 每个任务引用了具体的需求编号以确保可追溯性
- Checkpoint 任务确保增量验证
- 属性测试验证通用正确性属性，单元测试验证具体示例和边界条件
- 后端使用 jqwik 作为属性测试库，前端使用 fast-check
