#!/usr/bin/env bash
# Himarket Skill 批量初始化钩子 (Docker 环境)
# 将 data/skills/ 目录下的 Skill 批量上传到 HiMarket 并发布到门户
# 依赖: 20-init-himarket-admin.sh (管理员账号已注册)
#        40-init-himarket-mcp.sh   (Nacos 已注册, Portal 已创建)

set -euo pipefail

# 保存从父进程继承的控制变量（优先级高于 env 文件）
_INHERITED_SKIP_SKILL_INIT="${SKIP_SKILL_INIT:-}"

# 从 ~/himarket-install.env 加载环境变量
ENV_FILE="${HOME}/himarket-install.env"
if [[ -f "${ENV_FILE}" ]]; then
  set -a; . "${ENV_FILE}"; set +a
fi

# 恢复继承变量（install.sh 导出值优先于 env 文件）
[[ -n "$_INHERITED_SKIP_SKILL_INIT" ]] && SKIP_SKILL_INIT="$_INHERITED_SKIP_SKILL_INIT"

# 允许通过环境变量跳过 Skill 初始化（默认不跳过）
if [[ "${SKIP_SKILL_INIT:-false}" == "true" ]]; then
  echo "[init-himarket-skills] SKIP_SKILL_INIT=true，跳过 Skill 初始化"
  exit 0
fi

# 共享数据目录（由 install.sh 传入，或自动推导）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SHARED_DATA_DIR="${SHARED_DATA_DIR:-$(cd "${SCRIPT_DIR}/../../../data" && pwd)}"
SKILLS_DIR="${SHARED_DATA_DIR}/skills"

# 默认登录凭据
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin}"

# 超时与重试配置
MAX_RETRIES=5
RETRY_DELAY=10
CONNECT_TIMEOUT=10
API_TIMEOUT=30
UPLOAD_TIMEOUT=120
MAX_UPLOAD_RETRIES=5

log() { echo "[init-himarket-skills $(date +'%H:%M:%S')] $*" >&2; }
err() { echo "[ERROR] $*" >&2; }

# 全局变量
AUTH_TOKEN=""
HIMARKET_HOST="localhost:5174"
PORTAL_ID=""

# 临时目录
TMPDIR_BASE=""

########################################
# 清理临时文件
########################################
cleanup() {
  if [[ -n "$TMPDIR_BASE" && -d "$TMPDIR_BASE" ]]; then
    rm -rf "$TMPDIR_BASE"
  fi
}
trap cleanup EXIT

########################################
# 等待服务可用
########################################
wait_for_service() {
  local max_wait=90
  local waited=0
  local interval=5

  log "检查 HiMarket Admin 服务可用性 (http://${HIMARKET_HOST})..."

  while (( waited < max_wait )); do
    local http_code
    http_code=$(curl -sS -o /dev/null -w '%{http_code}' \
      --connect-timeout 3 --max-time 5 \
      "http://${HIMARKET_HOST}/api/v1/products?size=1" \
      -H "Authorization: Bearer ${AUTH_TOKEN}" 2>/dev/null || echo "000")

    if [[ "$http_code" =~ ^[23] ]]; then
      log "服务可用 (HTTP ${http_code})"
      return 0
    fi

    log "服务暂不可用 (HTTP ${http_code})，等待 ${interval} 秒..."
    sleep $interval
    waited=$((waited + interval))
  done

  err "服务在 ${max_wait} 秒内未就绪"
  return 1
}

########################################
# 检查依赖
########################################
check_dependencies() {
  local missing=()
  if ! command -v jq >/dev/null 2>&1; then
    missing+=("jq")
  fi
  if ! command -v zip >/dev/null 2>&1; then
    missing+=("zip")
  fi
  if [[ ${#missing[@]} -gt 0 ]]; then
    err "缺少必需的命令: ${missing[*]}"
    err "请先安装: ${missing[*]}"
    exit 1
  fi
}

########################################
# Skill 分类映射 (兼容 Bash 3.2)
########################################
get_category() {
  local skill_name="$1"
  case "$skill_name" in
    crawl|discord|extract|search|tavily-best-practices)
      echo "category-skill-automation" ;;
    docx|pdf|pptx|xlsx)
      echo "category-skill-document" ;;
    find-skill|find-skills)
      echo "category-skill-skilldev" ;;
    frontend-design|notion-infographic|remotion)
      echo "category-skill-design" ;;
    research)
      echo "category-skill-productivity" ;;
    vite)
      echo "category-skill-development" ;;
    *)
      echo "category-skill-productivity" ;;
  esac
}

########################################
# 解析 SKILL.md YAML front matter
########################################
parse_front_matter() {
  local file="$1"
  local key="$2"
  awk '/^---/{f=!f;next} f' "$file" | grep "^${key}:" | head -1 \
    | sed "s/^${key}:[[:space:]]*//" | sed 's/^["'"'"']//' | sed 's/["'"'"']$//'
}

########################################
# 调用 API 通用函数 (JSON)
########################################
call_api() {
  local api_name="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local max_attempts="${5:-$MAX_RETRIES}"

  local url="http://${HIMARKET_HOST}${path}"

  local attempt=1
  while (( attempt <= max_attempts )); do
    if (( max_attempts > 1 )); then
      log "调用接口 [${api_name}]: ${method} ${url} (第 ${attempt}/${max_attempts} 次)"
    else
      log "调用接口 [${api_name}]: ${method} ${url}"
    fi

    local -a curl_args=(-sS -w $'\nHTTP_CODE:%{http_code}' -X "$method" "$url")
    curl_args+=(-H 'Content-Type: application/json')
    curl_args+=(-H 'Accept: application/json, text/plain, */*')

    if [[ -n "$AUTH_TOKEN" ]]; then
      curl_args+=(-H "Authorization: Bearer ${AUTH_TOKEN}")
    fi

    if [[ -n "$body" ]]; then
      curl_args+=(-d "$body")
    fi

    curl_args+=(--connect-timeout "$CONNECT_TIMEOUT" --max-time "$API_TIMEOUT")

    local result
    result=$(curl "${curl_args[@]}" 2>&1 || echo "HTTP_CODE:000")

    local http_code=""
    local response=""

    if [[ "$result" =~ HTTP_CODE:([0-9]{3}) ]]; then
      http_code="${BASH_REMATCH[1]}"
      response=$(echo "$result" | sed '/HTTP_CODE:/d')
    else
      http_code="000"
      response="$result"
    fi

    if (( max_attempts > 1 )); then
      log "接口 [${api_name}] 返回: HTTP ${http_code}"
    fi

    if [[ -n "$response" ]] && [[ "$response" != "000" ]] && [[ ${#response} -lt 500 ]]; then
      log "响应内容: ${response}"
    fi

    export API_RESPONSE="$response"
    export API_HTTP_CODE="$http_code"

    # 成功的状态码
    if [[ "$http_code" =~ ^2[0-9]{2}$ ]] || [[ "$http_code" == "409" ]]; then
      return 0
    fi

    # 连接错误重试
    if [[ "$http_code" == "000" ]] && (( attempt < max_attempts )); then
      log "连接失败，${RETRY_DELAY}秒后重试..."
      sleep $RETRY_DELAY
      attempt=$((attempt + 1))
      continue
    fi

    if (( attempt >= max_attempts )); then
      return 1
    fi

    sleep $RETRY_DELAY
    attempt=$((attempt + 1))
  done

  return 1
}

########################################
# 登录获取 Token
########################################
login_admin() {
  local body="{\"username\":\"${ADMIN_USERNAME}\",\"password\":\"${ADMIN_PASSWORD}\"}"

  local attempt=1
  while (( attempt <= MAX_RETRIES )); do
    log "执行登录 (第 ${attempt}/${MAX_RETRIES} 次)..."

    if call_api "管理员登录" "POST" "/api/v1/admins/login" "$body" 1; then
      AUTH_TOKEN=$(echo "$API_RESPONSE" | grep -o '"access_token":"[^"]*"' | head -1 | sed 's/"access_token":"//' | sed 's/"//' || echo "")

      if [[ -z "$AUTH_TOKEN" ]]; then
        AUTH_TOKEN=$(echo "$API_RESPONSE" | grep -o '"token":"[^"]*"' | head -1 | sed 's/"token":"//' | sed 's/"//' || echo "")
      fi

      if [[ -z "$AUTH_TOKEN" ]]; then
        AUTH_TOKEN=$(echo "$API_RESPONSE" | sed -n 's/.*"access_token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' || echo "")
      fi

      if [[ -z "$AUTH_TOKEN" ]]; then
        err "无法从登录响应中提取 token"
        if (( attempt < MAX_RETRIES )); then
          sleep $RETRY_DELAY
          attempt=$((attempt + 1))
          continue
        fi
        return 1
      fi

      log "登录成功，获取到 Token: ${AUTH_TOKEN:0:20}..."
      return 0
    fi

    if (( attempt < MAX_RETRIES )); then
      log "登录失败，${RETRY_DELAY}秒后重试..."
      sleep $RETRY_DELAY
    fi
    attempt=$((attempt + 1))
  done

  err "登录失败（已重试 ${MAX_RETRIES} 次）"
  return 1
}

########################################
# 获取默认 Portal ID
########################################
get_default_portal_id() {
  log "获取默认 Portal..."

  local attempt=1
  while (( attempt <= 3 )); do
    if call_api "查询Portal列表" "GET" "/api/v1/portals" "" 1; then
      local portal_id
      portal_id=$(echo "$API_RESPONSE" | jq -r '.data.content[0].portalId // empty' 2>/dev/null || echo "")

      if [[ -n "$portal_id" ]]; then
        log "获取到 Portal ID: ${portal_id}"
        echo "$portal_id"
        return 0
      fi
    fi

    if (( attempt < 3 )); then
      sleep 3
    fi
    attempt=$((attempt + 1))
  done

  log "未找到 Portal，上传后不会自动发布"
  return 1
}

########################################
# 获取或创建 Product (AGENT_SKILL)
# 返回码: 0=新建成功, 3=已存在, 1=失败
########################################
get_or_create_skill_product() {
  local name="$1"
  local description="$2"
  local document="$3"
  local category="$4"

  # 先查询是否已存在
  if call_api "查询Skill产品" "GET" "/api/v1/products?type=AGENT_SKILL&size=200" "" "$MAX_RETRIES"; then
    local existing_id
    existing_id=$(echo "$API_RESPONSE" | jq -r --arg n "$name" '.data.content[] | select(.name == $n) | .productId' 2>/dev/null | head -1 || echo "")

    if [[ -n "$existing_id" ]]; then
      log "[${name}] 产品已存在: ${existing_id}"
      echo "$existing_id"
      return 3
    fi
  fi

  # 创建新产品
  local body
  body=$(jq -n \
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

  if call_api "创建Skill产品" "POST" "/api/v1/products" "$body" "$MAX_RETRIES"; then
    local product_id
    product_id=$(echo "$API_RESPONSE" | jq -r '.data.productId // empty' 2>/dev/null || echo "")

    if [[ -n "$product_id" ]]; then
      log "[${name}] 产品创建成功: ${product_id}"
      echo "$product_id"
      return 0
    fi
  fi

  # 创建可能返回 409（已存在），再查一次
  if call_api "重新查询Skill产品" "GET" "/api/v1/products?type=AGENT_SKILL&size=200" "" "$MAX_RETRIES"; then
    local retry_id
    retry_id=$(echo "$API_RESPONSE" | jq -r --arg n "$name" '.data.content[] | select(.name == $n) | .productId' 2>/dev/null | head -1 || echo "")

    if [[ -n "$retry_id" ]]; then
      log "[${name}] 产品已存在: ${retry_id}"
      echo "$retry_id"
      return 3
    fi
  fi

  err "[${name}] 无法获取或创建产品"
  return 1
}

########################################
# 上传 Skill ZIP 包
########################################
upload_skill_zip() {
  local product_id="$1"
  local zip_file="$2"
  local skill_name="$3"

  local zip_size
  zip_size=$(wc -c < "$zip_file" | tr -d ' ')
  log "[${skill_name}] 上传 ZIP ($(( zip_size / 1024 ))KB)..."

  local url="http://${HIMARKET_HOST}/api/v1/skills/${product_id}/package"

  local attempt=1
  while (( attempt <= MAX_UPLOAD_RETRIES )); do
    local result
    result=$(curl -sS -w '\nHTTP_CODE:%{http_code}' \
      -X POST "$url" \
      -H "Authorization: Bearer ${AUTH_TOKEN}" \
      -F "file=@${zip_file};type=application/zip" \
      --connect-timeout "$CONNECT_TIMEOUT" \
      --max-time "$UPLOAD_TIMEOUT" 2>&1 || echo "HTTP_CODE:000")

    local http_code=""
    local response=""

    if [[ "$result" =~ HTTP_CODE:([0-9]{3}) ]]; then
      http_code="${BASH_REMATCH[1]}"
      response=$(echo "$result" | sed '/HTTP_CODE:/d')
    else
      http_code="000"
      response="$result"
    fi

    if [[ "$http_code" =~ ^2[0-9]{2}$ ]]; then
      local upload_code
      upload_code=$(echo "$response" | jq -r '.code // empty' 2>/dev/null || echo "")
      local skill_name_resp
      skill_name_resp=$(echo "$response" | jq -r '.data // empty' 2>/dev/null || echo "")

      if [[ "$upload_code" == "SUCCESS" ]] && [[ -n "$skill_name_resp" ]]; then
        log "[${skill_name}] ZIP 上传成功 (skill: ${skill_name_resp})"
        return 0
      fi
    fi

    local err_msg
    err_msg=$(echo "$response" | jq -r '.message // .' 2>/dev/null || echo "$response")

    if (( attempt < MAX_UPLOAD_RETRIES )); then
      local backoff=$((attempt * 3))
      log "[${skill_name}] 第 ${attempt} 次上传失败: ${err_msg}，${backoff}秒后重试..."
      sleep "$backoff"
    else
      err "[${skill_name}] 上传 ZIP 失败（已重试 ${MAX_UPLOAD_RETRIES} 次）: ${err_msg}"
    fi

    attempt=$((attempt + 1))
  done

  return 1
}

########################################
# 发布产品到 Portal
########################################
publish_to_portal() {
  local product_id="$1"
  local portal_id="$2"
  local skill_name="$3"

  local body="{\"portalId\":\"${portal_id}\"}"

  if call_api "发布到门户" "POST" "/api/v1/products/${product_id}/publications" "$body" "$MAX_RETRIES"; then
    if [[ "$API_HTTP_CODE" =~ ^2[0-9]{2}$ ]]; then
      log "[${skill_name}] 发布到门户成功"
      return 0
    elif [[ "$API_HTTP_CODE" == "409" ]]; then
      log "[${skill_name}] 已发布到门户（跳过）"
      return 0
    fi
  fi

  log "[${skill_name}] 发布到门户失败（可能已发布）"
  return 0  # 允许失败，继续执行
}

########################################
# 处理单个 Skill
########################################
process_single_skill() {
  local skill_dir="$1"
  local skill_dir_name
  skill_dir_name=$(basename "$skill_dir")

  # 跳过空目录
  if [[ -z "$(ls -A "$skill_dir" 2>/dev/null)" ]]; then
    log "[${skill_dir_name}] 目录为空，跳过"
    return 2  # 2 = skipped
  fi

  local skill_md="${skill_dir}/SKILL.md"
  if [[ ! -f "$skill_md" ]]; then
    log "[${skill_dir_name}] 无 SKILL.md，跳过"
    return 2
  fi

  log "========================================"
  log "处理 Skill: ${skill_dir_name}"
  log "========================================"

  # 解析 name / description
  local name
  name=$(parse_front_matter "$skill_md" "name")
  local description
  description=$(parse_front_matter "$skill_md" "description")
  [[ -z "$name" ]] && name="$skill_dir_name"
  # 截断 description 到 256 字符
  description="${description:0:256}"

  # 读取完整 SKILL.md 作为 document
  local document
  document=$(cat "$skill_md")

  # 获取分类
  local category
  category=$(get_category "$skill_dir_name")

  log "[${name}] description: ${description:0:80}..."
  log "[${name}] category: ${category}"

  # 1. 创建或获取产品（返回码: 0=新建, 3=已存在, 1=失败）
  local product_id
  local create_ret=0
  product_id=$(get_or_create_skill_product "$name" "$description" "$document" "$category") || create_ret=$?
  if [[ $create_ret -eq 1 ]] || [[ -z "$product_id" ]]; then
    err "[${name}] 无法获取产品 ID"
    return 1
  fi
  log "[${name}] Product ID: ${product_id}"

  # 幂等：产品已存在说明之前已成功上传和发布，跳过后续步骤
  if [[ $create_ret -eq 3 ]]; then
    log "[${name}] 产品已存在，跳过上传和发布（幂等）"
    return 3  # 3 = existed (idempotent skip)
  fi

  # 2. 打包 ZIP
  local zip_file="${TMPDIR_BASE}/${skill_dir_name}.zip"
  (cd "$skill_dir" && zip -qry "$zip_file" . --exclude "*.DS_Store")

  # 3. 上传 ZIP
  if ! upload_skill_zip "$product_id" "$zip_file" "$name"; then
    err "[${name}] ZIP 上传失败"
    return 1
  fi

  # 4. 发布到 Portal（如果有 Portal）
  if [[ -n "$PORTAL_ID" ]]; then
    publish_to_portal "$product_id" "$PORTAL_ID" "$name"
  fi

  log "[${name}] Skill 处理完成"
  return 0
}

########################################
# 主流程
########################################
main() {
  log "========================================"
  log "开始初始化 Himarket Skills"
  log "========================================"

  # 检查 skills 目录
  if [[ ! -d "$SKILLS_DIR" ]]; then
    log "Skills 目录不存在: $SKILLS_DIR，跳过初始化"
    exit 0
  fi

  # 检查是否有 skill 子目录
  local skill_count=0
  for d in "$SKILLS_DIR"/*/; do
    [[ -d "$d" ]] && skill_count=$((skill_count + 1))
  done

  if [[ $skill_count -eq 0 ]]; then
    log "Skills 目录为空，跳过初始化"
    exit 0
  fi

  log "检测到 ${skill_count} 个 Skill 目录"

  # 检查依赖
  check_dependencies

  # 创建临时目录
  TMPDIR_BASE=$(mktemp -d)

  # 登录
  if ! login_admin; then
    err "登录失败，无法继续"
    exit 1
  fi

  # 等待服务稳定可用
  if ! wait_for_service; then
    err "HiMarket Admin 服务不可用，无法继续"
    exit 1
  fi

  # 获取默认 Portal
  PORTAL_ID=$(get_default_portal_id || echo "")

  # 遍历处理每个 Skill
  local success_count=0
  local existed_count=0
  local skip_count=0
  local fail_count=0
  local failed_list=""

  for skill_dir in "$SKILLS_DIR"/*/; do
    [[ ! -d "$skill_dir" ]] && continue

    local ret=0
    process_single_skill "$skill_dir" || ret=$?

    case $ret in
      0) success_count=$((success_count + 1)) ;;
      2) skip_count=$((skip_count + 1)) ;;
      3) existed_count=$((existed_count + 1)) ;;
      *)
        fail_count=$((fail_count + 1))
        failed_list="${failed_list}  - $(basename "$skill_dir")\n"
        # 失败后等待服务恢复再继续
        log "上一个 Skill 处理失败，等待服务恢复..."
        wait_for_service || true
        ;;
    esac
  done

  log "========================================"
  log "Himarket Skills 初始化完成报告"
  log "========================================"
  log "总计: ${skill_count} 个 Skill"
  log "新增: ${success_count} 个"
  log "已存在(幂等跳过): ${existed_count} 个"
  log "跳过: ${skip_count} 个"
  log "失败: ${fail_count} 个"

  if [[ $fail_count -gt 0 ]]; then
    log ""
    log "失败的 Skill:"
    echo -e "$failed_list" | while IFS= read -r line; do
      [[ -n "$line" ]] && log "$line"
    done
    exit 1
  fi
}

main "$@"
