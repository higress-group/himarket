---
name: publish-skills
description: Publish local Agent Skills to a HiMarket backend instance. Use when the user wants to upload, publish, deploy, or sync skills to HiMarket. Supports batch publishing all skills in a directory with automatic category selection, tag generation, and conflict avoidance.
---

# Publish Skills to HiMarket

将本地 Skill 目录批量发布到 HiMarket 后台，自动处理分类选择、Tag 生成、冲突检测和门户发布。

## Configuration

从 `~/.env` 读取以下变量（也可通过 shell 环境变量覆盖）：

```
HIMARKET_PUBLISH_URL=http://localhost:8080    # HiMarket 后台地址
HIMARKET_PUBLISH_USERNAME=admin               # 管理员用户名
HIMARKET_PUBLISH_PASSWORD=admin               # 管理员密码
```

如果 `~/.env` 中没有这些变量，提示用户配置后再执行。

## Invocation

用户调用方式：
- `publish-skills` — 发布默认目录 `~/Downloads/skills` 下所有 skill
- `publish-skills /path/to/skills` — 发布指定目录下所有 skill
- `publish-skills /path/to/skills/pdf` — 仅发布单个 skill

## Workflow

### Step 1: Load Config & Authenticate

```bash
# 加载环境变量
source ~/.env 2>/dev/null
HM_URL="${HIMARKET_PUBLISH_URL:-http://localhost:8080}"
HM_USER="${HIMARKET_PUBLISH_USERNAME:-admin}"
HM_PASS="${HIMARKET_PUBLISH_PASSWORD:-admin}"

# 获取 admin token
TOKEN=$(curl -s --connect-timeout 10 --max-time 15 -X POST "$HM_URL/admins/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$HM_USER\",\"password\":\"$HM_PASS\"}" | jq -r '.data.access_token')
```

验证 TOKEN 非空且非 `null`，否则报错退出。

### Step 2: Get Portal & Existing Data

```bash
# 获取默认门户 ID
PORTAL_ID=$(curl -s --max-time 15 -H "Authorization: Bearer $TOKEN" \
  "$HM_URL/portals?size=1" | jq -r '.data.content[0].portalId // empty')

# 获取已有 AGENT_SKILL 产品列表（避免重复）
curl -s -H "Authorization: Bearer $TOKEN" \
  "$HM_URL/products?type=AGENT_SKILL&size=200" | jq '.data.content[] | {name, productId}'

# 获取已有分类列表
curl -s -H "Authorization: Bearer $TOKEN" \
  "$HM_URL/product-categories?size=200" | jq '.data.content[] | {categoryId, name}'
```

记录已有产品名称和分类信息，用于后续冲突检测和分类匹配。

### Step 3: Scan Skills Directory

遍历目标目录下每个子目录，检查是否包含 `SKILL.md`：
- 有 `SKILL.md` → 有效 skill，继续处理
- 无 `SKILL.md` → 跳过，记录日志

从 `SKILL.md` front matter 中解析：
- `name` — skill 名称（必填）
- `description` — 描述（截断到 256 字符）

**冲突检测**：如果该 name 已存在于远端产品列表中，记录其 productId，后续走更新流程（仅重新上传 package），不重新创建产品。

### Step 4: Determine Category

根据 skill 的 `name`、`description` 和 `SKILL.md` 内容，为每个 skill 选择最合适的分类。

**预定义分类映射**（优先匹配）：

| Skill 关键词 | 分类名称 | 说明 |
|---|---|---|
| pdf, docx, pptx, xlsx | 文档处理 | 文档读写、格式转换 |
| frontend-design, notion-infographic, remotion | 设计创意 | UI 设计、图形生成、视频制作 |
| vite | 开发工具 | 开发框架、构建工具 |
| crawl, extract, search, tavily-best-practices, discord | 自动化 | 爬虫、搜索、集成 |
| find-skill, find-skills | 技能发现 | Skill 搜索和安装 |
| research | 效率提升 | 调研、信息整合 |

**如果 skill 不在预定义映射中**，阅读其 `SKILL.md` 内容后自行判断最佳分类。

**分类匹配逻辑**：
1. 先在已有分类列表中按名称模糊匹配
2. 匹配到 → 使用已有 categoryId
3. 未匹配到 → 创建新分类：

```bash
curl -s -X POST "$HM_URL/product-categories" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"分类名称","description":"分类描述"}'
```

### Step 5: Generate Tags

为每个 skill 生成 3-6 个有意义的 tag。Tag 要求：
- 英文小写，用连字符分隔（如 `pdf-parsing`、`web-scraping`）
- 反映 skill 的核心能力和使用场景
- 不要过于宽泛（避免 `tool`、`utility` 这类无意义 tag）

**Tag 生成参考**：

| Skill | 推荐 Tags |
|---|---|
| pdf | `pdf-parsing`, `document-generation`, `form-filling`, `text-extraction`, `page-manipulation` |
| docx | `word-document`, `docx-generation`, `content-extraction`, `template-processing` |
| pptx | `presentation`, `slide-generation`, `powerpoint`, `content-modification` |
| xlsx | `spreadsheet`, `excel`, `data-manipulation`, `formula-processing` |
| vite | `web-development`, `react`, `frontend-scaffold`, `build-tool` |
| frontend-design | `ui-design`, `frontend`, `visual-design`, `web-components` |
| notion-infographic | `infographic`, `visual-content`, `notion-style`, `social-media` |
| remotion | `video-creation`, `react-video`, `animation`, `media-production` |
| crawl | `web-crawling`, `content-download`, `site-archival`, `markdown-conversion` |
| search | `web-search`, `tavily`, `content-discovery`, `real-time-data` |
| extract | `content-extraction`, `url-parsing`, `web-content`, `markdown` |
| research | `ai-research`, `topic-synthesis`, `citations`, `knowledge-base` |
| find-skills | `skill-discovery`, `skill-installation`, `capability-search` |
| discord | `discord-bot`, `messaging`, `community-management`, `automation` |
| tavily-best-practices | `tavily-integration`, `search-api`, `rag-pipeline`, `agentic-workflow` |

**如果 skill 不在上表中**，阅读 `SKILL.md` 内容后自行生成合理的 tag。

### Step 6: Create or Update Product

**新建产品**（远端不存在同名产品时）：

```bash
# document 是 SKILL.md 的全文内容
PAYLOAD=$(jq -n \
  --arg name "$SKILL_NAME" \
  --arg desc "$DESCRIPTION" \
  --arg doc "$DOCUMENT" \
  --argjson cats '["categoryId1"]' \
  --argjson tags '["tag1","tag2","tag3"]' \
  '{
    name: $name,
    description: $desc,
    type: "AGENT_SKILL",
    document: $doc,
    autoApprove: true,
    categories: $cats,
    feature: { skillConfig: { skillTags: $tags } }
  }')

curl -s -X POST "$HM_URL/products" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD" | jq '.data.productId'
```

**已存在产品**：直接使用已有 productId，跳到 Step 7 上传 package。

### Step 7: Zip & Upload Package

```bash
# 打包 skill 目录为 zip（排除 .DS_Store）
TMPZIP=$(mktemp /tmp/skill-XXXXXX.zip)
(cd "$SKILL_DIR" && zip -qry "$TMPZIP" . --exclude "*.DS_Store")

# 上传到 HiMarket（支持重试，Nacos 写入可能较慢）
curl -s --max-time 120 -X POST "$HM_URL/skills/$PRODUCT_ID/package" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$TMPZIP;type=application/zip"

rm -f "$TMPZIP"
```

上传失败时最多重试 3 次，每次间隔递增（1s、2s、3s）。

### Step 8: Publish to Portal

```bash
curl -s -X POST "$HM_URL/products/$PRODUCT_ID/publications" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"portalId\":\"$PORTAL_ID\"}"
```

如果产品已发布（返回错误），忽略该错误继续。

### Step 9: Summary

处理完所有 skill 后，输出汇总表格：

| Skill | 状态 | 分类 | Tags | 备注 |
|---|---|---|---|---|
| pdf | 新建并发布 | 文档处理 | pdf-parsing, ... | |
| vite | 已存在，更新包 | 开发工具 | web-development, ... | |

## Error Handling

- **Token 获取失败**：检查 URL 和凭据，提示用户检查 `~/.env` 配置
- **Portal 不存在**：警告用户需先在 HiMarket 后台创建门户
- **上传超时**：自动重试，最终失败记录到汇总
- **分类创建失败**：跳过分类关联，继续其他步骤
- **产品创建失败**：记录错误，继续处理下一个 skill

## Important Notes

- 执行前始终先列出将要处理的 skill 清单，等用户确认后再开始
- 每处理完一个 skill 立即输出进度
- 使用 TodoWrite 跟踪每个 skill 的处理状态
- description 截断到 256 字符以符合 API 限制
