#!/usr/bin/env bash
# 批量上传 skills 到本地 HiMarket
# 用法: ./scripts/upload-skills.sh [skills目录] [HiMarket地址]
# 默认: ~/Downloads/skills  http://localhost:8080

set -e

SKILLS_DIR="${1:-$HOME/Downloads/skills}"
BASE_URL="${2:-http://localhost:8080}"

# ── 分类映射 ────────────────────────────────────────────────
declare -A CATEGORY_MAP=(
  [crawl]="category-skill-automation"
  [discord]="category-skill-automation"
  [docx]="category-skill-document"
  [extract]="category-skill-automation"
  [find-skill]="category-skill-skilldev"
  [find-skills]="category-skill-skilldev"
  [frontend-design]="category-skill-design"
  [notion-infographic]="category-skill-design"
  [pdf]="category-skill-document"
  [pptx]="category-skill-document"
  [remotion]="category-skill-design"
  [research]="category-skill-productivity"
  [search]="category-skill-automation"
  [tavily-best-practices]="category-skill-automation"
  [vite]="category-skill-development"
  [xlsx]="category-skill-document"
)

# ── 获取 token ───────────────────────────────────────────────
echo "🔑 获取管理员 token..."
TOKEN=$(curl -s -X POST "$BASE_URL/admins/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r '.data.access_token')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "❌ 获取 token 失败，请确认服务已启动"
  exit 1
fi
echo "✅ token 获取成功"

# ── 获取已有 skill 名称列表（避免重复创建）──────────────────
EXISTING=$(curl -s -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/products?type=AGENT_SKILL&size=100" | jq -r '.data.content[].name' 2>/dev/null || echo "")

# ── 解析 SKILL.md front matter ──────────────────────────────
parse_front_matter() {
  local file="$1"
  local key="$2"
  # 提取 --- 块内的 key: value
  awk '/^---/{f=!f;next} f' "$file" | grep "^${key}:" | head -1 \
    | sed "s/^${key}:[[:space:]]*//" | sed 's/^["'"'"']//' | sed 's/["'"'"']$//'
}

# ── 主循环 ──────────────────────────────────────────────────
TMPDIR_BASE=$(mktemp -d)
trap "rm -rf $TMPDIR_BASE" EXIT

success=0
skip=0
fail=0

for skill_dir in "$SKILLS_DIR"/*/; do
  skill_name=$(basename "$skill_dir")

  # 跳过空目录
  if [ -z "$(ls -A "$skill_dir" 2>/dev/null)" ]; then
    echo "⏭  $skill_name: 目录为空，跳过"
    ((skip++)) || true
    continue
  fi

  skill_md="$skill_dir/SKILL.md"
  if [ ! -f "$skill_md" ]; then
    echo "⏭  $skill_name: 无 SKILL.md，跳过"
    ((skip++)) || true
    continue
  fi

  # 解析 name / description
  name=$(parse_front_matter "$skill_md" "name")
  description=$(parse_front_matter "$skill_md" "description")
  [ -z "$name" ] && name="$skill_name"
  # description 截断到 256 字符
  description="${description:0:256}"

  # 检查是否已存在，已存在则跳过创建直接上传包
  if echo "$EXISTING" | grep -qx "$name"; then
    echo ""
    echo "📦 处理: $name（更新）"
    product_id=$(curl -s -H "Authorization: Bearer $TOKEN" \
      "$BASE_URL/products?type=AGENT_SKILL&size=100" \
      | jq -r --arg n "$name" '.data.content[] | select(.name == $n) | .productId' | head -1)
    if [ -z "$product_id" ]; then
      echo "  ❌ 获取 productId 失败"
      ((fail++)) || true
      continue
    fi
    echo "  ℹ️  product 已存在: $product_id"
  else
    echo ""
    echo "📦 处理: $name（新建）"

    # 读取 SKILL.md 全文作为 document
    document=$(cat "$skill_md")
    category="${CATEGORY_MAP[$skill_name]:-category-skill-productivity}"

    payload=$(jq -n \
      --arg name "$name" \
      --arg desc "$description" \
      --arg doc "$document" \
      --arg cat "$category" \
      '{name: $name, description: $desc, type: "AGENT_SKILL", document: $doc, autoApprove: true, categories: [$cat]}')

    create_resp=$(curl -s -X POST "$BASE_URL/products" \
      -H "Authorization: Bearer $TOKEN" \
      -H "Content-Type: application/json" \
      -d "$payload")

    product_id=$(echo "$create_resp" | jq -r '.data.productId // empty')
    if [ -z "$product_id" ]; then
      echo "  ❌ 创建 product 失败: $(echo "$create_resp" | jq -r '.message // .')"
      ((fail++)) || true
      continue
    fi
    echo "  ✅ product 创建成功: $product_id"
  fi

  # 打包 zip（-y 保留 symlink 而不跟随，避免循环递归；排除 .DS_Store）
  zip_file="$TMPDIR_BASE/${skill_name}.zip"
  (cd "$skill_dir" && zip -qry "$zip_file" . --exclude "*.DS_Store")

  # 上传 skill package
  upload_resp=$(curl -s -X POST "$BASE_URL/skills/$product_id/package" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@$zip_file;type=application/zip")

  file_count=$(echo "$upload_resp" | jq -r '.data.fileCount // empty')
  if [ -z "$file_count" ]; then
    echo "  ❌ 上传 package 失败: $(echo "$upload_resp" | jq -r '.message // .')"
    ((fail++)) || true
  else
    echo "  ✅ package 上传成功，共 $file_count 个文件"
    ((success++)) || true
  fi
done

echo ""
echo "────────────────────────────────"
echo "✅ 成功: $success  ⏭ 跳过: $skip  ❌ 失败: $fail"
