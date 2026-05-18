#!/usr/bin/env bash
# =============================================================================
# Nacos Skills/Workers 同步与发布钩子 (Docker 环境)
# 由 install.sh post_ready hook 自动调用
#
# 功能:
#   1. 在 HiMarket 中注册 Nacos 实例
#   2. 从 Nacos 导入 Skills 和 Workers
#   3. 将导入的产品发布到默认门户
#
# 环境变量:
#   SKIP_NACOS_SYNC      - 设为 true 跳过执行 (默认 false)
#   ADMIN_USERNAME       - HiMarket 管理员用户名 (默认 admin)
#   ADMIN_PASSWORD       - HiMarket 管理员密码 (默认 admin)
#   NACOS_ADMIN_PASSWORD - Nacos 管理员密码 (默认 nacos)
# =============================================================================

set -euo pipefail

# ── 加载环境变量 ─────────────────────────────────────────────────────────────
ENV_FILE="${HOME}/himarket-install-docker.env"
if [[ -f "${ENV_FILE}" ]]; then
  set -a; . "${ENV_FILE}"; set +a
fi

# ── 跳过检查 ─────────────────────────────────────────────────────────────────
if [[ "${INSTALL_NACOS:-true}" != "true" ]]; then
  echo "[sync-nacos-market] INSTALL_NACOS=${INSTALL_NACOS}，跳过 Nacos 同步"
  exit 0
fi
if [[ "${SKIP_NACOS_SYNC:-false}" == "true" ]]; then
  echo "[sync-nacos-market] SKIP_NACOS_SYNC=true，跳过 Nacos 同步"
  exit 0
fi

# ── 凭据默认值 ───────────────────────────────────────────────────────────────
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin}"
NACOS_ADMIN_PASSWORD="${NACOS_ADMIN_PASSWORD:-nacos}"

# ── 全局变量（Docker 环境使用 localhost） ────────────────────────────────────
HIMARKET_HOST="http://localhost:${HIMARKET_ADMIN_PORT:-5174}"
AUTH_TOKEN=""
API_RESPONSE=""
API_HTTP_CODE=""
NACOS_ID=""
PORTAL_ID=""

MAX_RETRIES=3
RETRY_DELAY=5

# ── 统计 ─────────────────────────────────────────────────────────────────────
SKILL_SUCCESS=0
SKILL_SKIPPED=0
WORKER_SUCCESS=0
WORKER_SKIPPED=0
PUBLISH_SUCCESS=0
PUBLISH_SKIPPED=0
PUBLISH_FAILED=0

log() { echo "[sync-nacos-market $(date +'%H:%M:%S')] $*"; }
err() { echo "[sync-nacos-market ERROR] $*" >&2; }

########################################
# 检查依赖
########################################
check_dependencies() {
  for cmd in curl jq; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
      err "未找到 $cmd 命令，请先安装"
      exit 1
    fi
  done
}

########################################
# HiMarket API 通用调用
########################################
call_himarket_api() {
  local api_name="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local max_attempts="${5:-$MAX_RETRIES}"
  local max_time="${6:-30}"

  local url="${HIMARKET_HOST}${path}"
  local attempt=1

  while (( attempt <= max_attempts )); do
    if (( max_attempts > 1 )); then
      log "调用 [${api_name}]: ${method} ${url} (第 ${attempt}/${max_attempts} 次)"
    else
      log "调用 [${api_name}]: ${method} ${url}"
    fi

    local curl_args=(-sS -w "\nHTTP_CODE:%{http_code}" -X "${method}" "${url}"
      -H "Content-Type: application/json"
      -H "Accept: application/json, text/plain, */*"
      --connect-timeout 10 --max-time "${max_time}")

    if [[ -n "${AUTH_TOKEN}" ]]; then
      curl_args+=(-H "Authorization: Bearer ${AUTH_TOKEN}")
    fi

    if [[ -n "${body}" ]]; then
      curl_args+=(--data "${body}")
    fi

    local result
    result=$(curl "${curl_args[@]}" 2>&1 || echo "HTTP_CODE:000")

    local http_code="" response=""
    if [[ "$result" =~ HTTP_CODE:([0-9]{3}) ]]; then
      http_code="${BASH_REMATCH[1]}"
      response=$(echo "$result" | sed '/HTTP_CODE:/d')
    else
      http_code="000"
      response="$result"
    fi

    API_RESPONSE="$response"
    API_HTTP_CODE="$http_code"

    # 成功或幂等
    if [[ "$http_code" =~ ^2[0-9]{2}$ ]] || [[ "$http_code" == "409" ]]; then
      return 0
    fi

    # 连接失败时重试
    if [[ "$http_code" == "000" ]] && (( attempt < max_attempts )); then
      log "连接失败，${RETRY_DELAY}秒后重试..."
      sleep "${RETRY_DELAY}"
      attempt=$((attempt + 1))
      continue
    fi

    if (( attempt >= max_attempts )); then
      return 1
    fi

    sleep "${RETRY_DELAY}"
    attempt=$((attempt + 1))
  done
  return 1
}

########################################
# Step 0: 初始化 HiMarket 管理员（幂等）
########################################
ensure_admin_initialized() {
  log "确保 HiMarket 管理员已初始化..."

  local body
  body=$(jq -n \
    --arg username "${ADMIN_USERNAME}" \
    --arg password "${ADMIN_PASSWORD}" \
    '{username: $username, password: $password}')

  # 先检查是否需要初始化
  if call_himarket_api "检查初始化" "GET" "/api/v1/admins/need-init" "" 1; then
    if [[ "$API_RESPONSE" == *"true"* ]]; then
      log "HiMarket 尚未初始化管理员，正在创建..."
      if call_himarket_api "初始化管理员" "POST" "/api/v1/admins/init" "$body" 1; then
        log "HiMarket 管理员初始化成功"
      else
        err "HiMarket 管理员初始化失败 (HTTP ${API_HTTP_CODE})"
        return 1
      fi
    else
      log "HiMarket 管理员已存在，跳过初始化"
    fi
  else
    err "无法检查 HiMarket 初始化状态 (HTTP ${API_HTTP_CODE})"
    return 1
  fi
}

########################################
# Step 1: 登录 HiMarket
########################################
login_himarket() {
  log "登录 HiMarket Admin..."

  local body
  body=$(jq -n \
    --arg username "${ADMIN_USERNAME}" \
    --arg password "${ADMIN_PASSWORD}" \
    '{username: $username, password: $password}')

  local attempt=1
  while (( attempt <= MAX_RETRIES )); do
    if call_himarket_api "管理员登录" "POST" "/api/v1/admins/login" "$body" 1; then
      AUTH_TOKEN=$(echo "$API_RESPONSE" | jq -r '.data.access_token // empty' 2>/dev/null || echo "")
      if [[ -z "${AUTH_TOKEN}" ]]; then
        AUTH_TOKEN=$(echo "$API_RESPONSE" | jq -r '.data.token // .data.accessToken // empty' 2>/dev/null || echo "")
      fi

      if [[ -n "${AUTH_TOKEN}" ]]; then
        log "HiMarket 登录成功"
        return 0
      fi
    fi

    if (( attempt < MAX_RETRIES )); then
      log "登录失败，${RETRY_DELAY}秒后重试..."
      sleep "${RETRY_DELAY}"
    fi
    attempt=$((attempt + 1))
  done

  err "HiMarket 登录失败"
  return 1
}

########################################
# Step 2: 注册 Nacos 实例（幂等）
########################################
register_nacos_instance() {
  log "注册 Nacos 实例..."

  local nacos_server_url="http://nacos:8848"

  local body
  body=$(jq -n \
    --arg nacosName "default-nacos" \
    --arg serverUrl "${nacos_server_url}" \
    --arg username "nacos" \
    --arg password "${NACOS_ADMIN_PASSWORD}" \
    --arg description "Built-in Nacos instance" \
    '{
      nacosName: $nacosName,
      serverUrl: $serverUrl,
      username: $username,
      password: $password,
      description: $description
    }')

  call_himarket_api "创建Nacos实例" "POST" "/api/v1/nacos" "$body" 1 >/dev/null 2>&1 || true

  # 查询获取 nacosId
  local attempt=1
  while (( attempt <= 3 )); do
    call_himarket_api "查询Nacos实例" "GET" "/api/v1/nacos" "" 1 >/dev/null 2>&1 || true
    NACOS_ID=$(echo "$API_RESPONSE" | jq -r '.data.content[0].nacosId // empty' 2>/dev/null || echo "")

    if [[ -n "$NACOS_ID" ]]; then
      log "Nacos 实例 ID: ${NACOS_ID}"
      return 0
    fi
    sleep 3
    attempt=$((attempt + 1))
  done

  err "无法获取 Nacos 实例 ID"
  return 1
}

########################################
# Step 3: 导入 Skills
########################################
import_skills() {
  log "从 Nacos 导入 Skills..."

  if call_himarket_api "导入Skills" "POST" "/api/v1/skills/import?nacosId=${NACOS_ID}&namespace=public" "" 1 120; then
    SKILL_SUCCESS=$(echo "$API_RESPONSE" | jq -r '.data.successCount // 0' 2>/dev/null || echo "0")
    SKILL_SKIPPED=$(echo "$API_RESPONSE" | jq -r '.data.skippedCount // 0' 2>/dev/null || echo "0")
    log "Skills 导入完成: ${SKILL_SUCCESS} 成功, ${SKILL_SKIPPED} 跳过"
    return 0
  else
    err "Skills 导入失败 (HTTP ${API_HTTP_CODE})"
    return 1
  fi
}

########################################
# Step 4: 导入 Workers
########################################
import_workers() {
  log "从 Nacos 导入 Workers..."

  if call_himarket_api "导入Workers" "POST" "/api/v1/workers/import?nacosId=${NACOS_ID}&namespace=public" "" 1 120; then
    WORKER_SUCCESS=$(echo "$API_RESPONSE" | jq -r '.data.successCount // 0' 2>/dev/null || echo "0")
    WORKER_SKIPPED=$(echo "$API_RESPONSE" | jq -r '.data.skippedCount // 0' 2>/dev/null || echo "0")
    log "Workers 导入完成: ${WORKER_SUCCESS} 成功, ${WORKER_SKIPPED} 跳过"
    return 0
  else
    err "Workers 导入失败 (HTTP ${API_HTTP_CODE})"
    return 1
  fi
}

########################################
# Step 5: 获取或创建 Portal
########################################
get_or_create_portal() {
  local portal_name="${1:-demo}"

  local body="{\"name\":\"${portal_name}\"}"
  call_himarket_api "创建Portal" "POST" "/api/v1/portals" "$body" 1 >/dev/null 2>&1 || true

  # 查询获取 portalId
  local attempt=1 p_id=""
  while (( attempt <= 3 )); do
    call_himarket_api "查询Portal" "GET" "/api/v1/portals" "" 1 >/dev/null 2>&1 || true
    p_id=$(echo "$API_RESPONSE" | jq -r '.data.content[]? // .[]? | select(.name=="'"${portal_name}"'") | .portalId' 2>/dev/null | head -1 || echo "")

    if [[ -n "$p_id" ]]; then
      echo "$p_id"
      return 0
    fi
    sleep 3
    attempt=$((attempt + 1))
  done

  return 1
}

########################################
# Step 6: 发布产品到门户
########################################
publish_products_to_portal() {
  local portal_id="$1"

  log "获取 AGENT_SKILL 和 WORKER 产品列表..."

  local product_ids=()

  # 获取 AGENT_SKILL 产品
  if call_himarket_api "查询Skills产品" "GET" "/api/v1/products?type=AGENT_SKILL&size=200" "" 1; then
    local skill_ids
    skill_ids=$(echo "$API_RESPONSE" | jq -r '.data.content[]?.productId // empty' 2>/dev/null || echo "")
    if [[ -n "$skill_ids" ]]; then
      while IFS= read -r pid; do
        [[ -n "$pid" ]] && product_ids+=("$pid")
      done <<< "$skill_ids"
    fi
  fi

  # 获取 WORKER 产品
  if call_himarket_api "查询Workers产品" "GET" "/api/v1/products?type=WORKER&size=200" "" 1; then
    local worker_ids
    worker_ids=$(echo "$API_RESPONSE" | jq -r '.data.content[]?.productId // empty' 2>/dev/null || echo "")
    if [[ -n "$worker_ids" ]]; then
      while IFS= read -r pid; do
        [[ -n "$pid" ]] && product_ids+=("$pid")
      done <<< "$worker_ids"
    fi
  fi

  local total=${#product_ids[@]}
  if (( total == 0 )); then
    log "没有需要发布的产品"
    return 0
  fi

  log "共 ${total} 个产品待发布到门户..."

  local body
  for pid in "${product_ids[@]}"; do
    body="{\"portalId\":\"${portal_id}\"}"
    if call_himarket_api "发布产品" "POST" "/api/v1/products/${pid}/publications" "$body" 1; then
      if [[ "$API_HTTP_CODE" == "409" ]]; then
        PUBLISH_SKIPPED=$((PUBLISH_SKIPPED + 1))
      else
        PUBLISH_SUCCESS=$((PUBLISH_SUCCESS + 1))
      fi
    else
      PUBLISH_FAILED=$((PUBLISH_FAILED + 1))
      log "产品 ${pid} 发布失败 (HTTP ${API_HTTP_CODE})"
    fi
  done
}

########################################
# main
########################################
main() {
  log ""
  log "========================================"
  log "  Nacos Skills/Workers 同步与发布"
  log "========================================"
  log ""

  # 0. 检查依赖
  check_dependencies

  # 1. 确保 HiMarket 管理员已初始化（全新安装/重装时 administrator 表为空）
  if ! ensure_admin_initialized; then
    err "HiMarket 管理员初始化失败，终止同步"
    exit 1
  fi

  # 2. 登录 HiMarket
  if ! login_himarket; then
    err "HiMarket Admin 登录失败，终止同步"
    exit 1
  fi

  # 3. 注册 Nacos 实例
  if ! register_nacos_instance; then
    err "Nacos 实例注册失败，终止同步"
    exit 1
  fi

  # 4. 导入 Skills（失败不阻断）
  import_skills || log "Skills 导入失败，继续执行..."

  # 5. 导入 Workers（失败不阻断）
  import_workers || log "Workers 导入失败，继续执行..."

  # 6. 获取或创建 Portal
  log "获取 Portal ID..."
  PORTAL_ID=$(get_or_create_portal "demo")
  if [[ -z "${PORTAL_ID}" ]]; then
    err "无法获取 Portal ID，跳过产品发布"
  else
    log "Portal ID: ${PORTAL_ID}"

    # 7. 发布产品到门户
    publish_products_to_portal "${PORTAL_ID}"
  fi

  # 汇总
  log ""
  log "========================================"
  log "  同步完成"
  log "  Skills 导入:  ${SKILL_SUCCESS} 成功, ${SKILL_SKIPPED} 跳过"
  log "  Workers 导入: ${WORKER_SUCCESS} 成功, ${WORKER_SKIPPED} 跳过"
  log "  产品发布:     ${PUBLISH_SUCCESS} 成功, ${PUBLISH_SKIPPED} 跳过, ${PUBLISH_FAILED} 失败"
  log "========================================"
  log ""
}

main "$@"
