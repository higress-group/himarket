#!/usr/bin/env bash
# 批量上传 skills 到本地 HiMarket
# 用法: ./scripts/upload-skills.sh [skills目录] [HiMarket地址]
# 默认: deploy/helm/data/skills  http://localhost:8080

set -e

# 自动检测默认 skills 目录（优先使用项目内的 deploy/helm/data/skills）
_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
_PROJECT_DIR="$(cd "${_SCRIPT_DIR}/.." && pwd)"
_DEFAULT_SKILLS_DIR="${_PROJECT_DIR}/deploy/helm/data/skills"
if [ ! -d "$_DEFAULT_SKILLS_DIR" ]; then
  _DEFAULT_SKILLS_DIR="$HOME/Downloads/skills"
fi

SKILLS_DIR="${1:-$_DEFAULT_SKILLS_DIR}"
BASE_URL="${2:-http://localhost:8080}"

# ── 超时配置（秒）──────────────────────────────────────────
CONNECT_TIMEOUT=10
# Nacos 上传可能较慢，给足超时
API_TIMEOUT=15
UPLOAD_TIMEOUT=120
MAX_UPLOAD_RETRIES=3

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

# ── 带超时的 curl 封装 ──────────────────────────────────────
# $1 = max-time，其余参数透传给 curl
curl_t() {
  local max_time="$1"; shift
  curl --connect-timeout "$CONNECT_TIMEOUT" --max-time "$max_time" "$@"
}

# ── 获取 token ───────────────────────────────────────────────
echo "🔑 获取管理员 token..."
TOKEN=$(curl_t "$API_TIMEOUT" -s -X POST "$BASE_URL/admins/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r '.data.access_token')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "❌ 获取 token 失败，请确认服务已启动"
  exit 1
fi
echo "✅ token 获取成功"

# ── 获取默认门户（用于上传后自动发布）────────────────────────
PORTAL_ID=$(curl_t "$API_TIMEOUT" -s -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/portals?size=1" | jq -r '.data.content[0].portalId // empty')

if [ -n "$PORTAL_ID" ]; then
  echo "🌐 默认门户: $PORTAL_ID"
else
  echo "⚠️  未找到门户，上传后不会自动发布"
fi

# ── 获取已有 skill 名称列表（避免重复创建）──────────────────
EXISTING=$(curl_t "$API_TIMEOUT" -s -H "Authorization: Bearer $TOKEN" \
  "$BASE_URL/products?type=AGENT_SKILL&size=200" | jq -r '.data.content[].name' 2>/dev/null || echo "")

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
    product_id=$(curl_t "$API_TIMEOUT" -s -H "Authorization: Bearer $TOKEN" \
      "$BASE_URL/products?type=AGENT_SKILL&size=200" \
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

    # 创建产品（后端会自动关联默认 Nacos 实例）
    payload=$(jq -n \
      --arg name "$name" \
      --arg desc "$description" \
      --arg doc "$document" \
      --arg cat "$category" \
      '{
        name: $name,
        description: $desc,
        type: "AGENT_SKILL",
        document: $doc,
        autoApprove: true,
        categories: [$cat]
      }')

    create_resp=$(curl_t "$API_TIMEOUT" -s -X POST "$BASE_URL/products" \
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
  zip_size=$(wc -c < "$zip_file" | tr -d ' ')

  # 上传 skill package（Nacos 写入可能较慢，带重试）
  echo "  ⬆️  上传 ${skill_name}.zip ($(( zip_size / 1024 ))KB)..."
  uploaded=false
  for attempt in $(seq 1 "$MAX_UPLOAD_RETRIES"); do
    upload_resp=$(curl_t "$UPLOAD_TIMEOUT" -s -X POST "$BASE_URL/skills/$product_id/package" \
      -H "Authorization: Bearer $TOKEN" \
      -F "file=@$zip_file;type=application/zip")

    upload_code=$(echo "$upload_resp" | jq -r '.code // empty')
    skill_name_resp=$(echo "$upload_resp" | jq -r '.data // empty')
    if [ "$upload_code" = "SUCCESS" ] && [ -n "$skill_name_resp" ]; then
      echo "  ✅ package 上传成功 (skill: $skill_name_resp)"
      uploaded=true
      break
    fi

    err_msg=$(echo "$upload_resp" | jq -r '.message // .')
    if [ "$attempt" -lt "$MAX_UPLOAD_RETRIES" ]; then
      echo "  ⚠️  第 ${attempt} 次上传失败: $err_msg，${attempt}s 后重试..."
      sleep "$attempt"
    else
      echo "  ❌ 上传 package 失败（已重试 ${MAX_UPLOAD_RETRIES} 次）: $err_msg"
    fi
  done

  if $uploaded; then
    ((success++)) || true

    # 上传成功后自动发布到门户
    if [ -n "$PORTAL_ID" ]; then
      pub_resp=$(curl_t "$API_TIMEOUT" -s -X POST "$BASE_URL/products/$product_id/publications" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d "{\"portalId\":\"$PORTAL_ID\"}")
      pub_code=$(echo "$pub_resp" | jq -r '.code // empty')
      if [ "$pub_code" = "SUCCESS" ]; then
        echo "  🌐 已发布到门户"
      else
        echo "  ⚠️  发布失败: $(echo "$pub_resp" | jq -r '.message // .')"
      fi
    fi
  else
    ((fail++)) || true
  fi
done

echo ""
echo "────────────────────────────────"
echo "✅ 成功: $success  ⏭ 跳过: $skip  ❌ 失败: $fail"
