#!/usr/bin/env bash
# Nacos MCP 数据创建钩子
# 由 deploy.sh 在部署就绪后自动调用
# 继承环境变量: NS 等
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="${SCRIPT_DIR}/../../data"

# 从 .env 加载环境变量
if [[ -f "${DATA_DIR}/.env" ]]; then
  set -a
  . "${DATA_DIR}/.env"
  set +a
fi

NS="${NAMESPACE:-himarket}"
MCP_JSON_FILE="${DATA_DIR}/nacos-mcp.json"

# Nacos 默认用户名密码
NACOS_USERNAME="${NACOS_USERNAME:-nacos}"
NACOS_PASSWORD="${NACOS_PASSWORD:-nacos}"

# 商业化 Nacos 配置
USE_COMMERCIAL_NACOS="${USE_COMMERCIAL_NACOS:-false}"
COMMERCIAL_NACOS_SERVER_URL="${COMMERCIAL_NACOS_SERVER_URL:-}"
COMMERCIAL_NACOS_USERNAME="${COMMERCIAL_NACOS_USERNAME:-}"
COMMERCIAL_NACOS_PASSWORD="${COMMERCIAL_NACOS_PASSWORD:-}"

# 固定配置
NAMESPACE_ID="public"

# 重试配置
MAX_RETRIES=3
RETRY_DELAY=5

log() { echo "[import-mcp $(date +'%H:%M:%S')] $*"; }
err() { echo "[ERROR] $*" >&2; }

########################################
# 1. 检查 MCP 数据文件
########################################
if [ ! -f "$MCP_JSON_FILE" ]; then
  err "MCP 数据文件不存在: $MCP_JSON_FILE"
  exit 1
fi

log "检测到 MCP 数据文件: $MCP_JSON_FILE"

# 检查是否有 jq 命令
if ! command -v jq >/dev/null 2>&1; then
  err "当前环境没有 jq，无法解析 JSON 文件。"
  err "请先安装 jq: brew install jq"
  exit 1
fi

########################################
# 2. 获取 Nacos Service 地址（动态信息）
########################################
log "获取 Nacos Service 地址..."

IS_COMMERCIAL=false
if [ "$USE_COMMERCIAL_NACOS" = "true" ] && [ -n "$COMMERCIAL_NACOS_SERVER_URL" ]; then
  # 使用商业化 Nacos
  HOST="$COMMERCIAL_NACOS_SERVER_URL"
  IS_COMMERCIAL=true
  log "使用商业化 Nacos 地址: ${HOST}"
  
  # 商业化 Nacos 优先使用商业化配置的用户名密码
  if [ -n "$COMMERCIAL_NACOS_USERNAME" ]; then
    NACOS_USERNAME="$COMMERCIAL_NACOS_USERNAME"
  fi
  if [ -n "$COMMERCIAL_NACOS_PASSWORD" ]; then
    NACOS_PASSWORD="$COMMERCIAL_NACOS_PASSWORD"
  fi
else
  # 获取开源 Nacos Service LoadBalancer IP
  HOST=$(kubectl get svc nacos -n "${NS}" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
  log "使用开源 Nacos Service 地址: ${HOST}"
fi

# 临时目录
TMP_DIR="$(mktemp -d -t nacos-mcp-import-XXXXXX)"

cleanup() {
  rm -rf "$TMP_DIR" || true
}
trap cleanup EXIT

########################################
# URL 编码函数
########################################
url_encode() {
  local input="$1"
  if command -v python3 >/dev/null 2>&1; then
    python3 -c "import urllib.parse, sys; print(urllib.parse.quote_plus(sys.stdin.read()))" <<< "$input"
  elif command -v python >/dev/null 2>&1; then
    python -c "import urllib, sys; print(urllib.quote_plus(sys.stdin.read()))" <<< "$input"
  else
    err "当前环境没有 python3/python，无法安全做 URL 编码。"
    exit 1
  fi
}

########################################
# 创建单个 MCP 的函数（带重试）
########################################
create_single_mcp() {
  local mcp_name="$1"
  local server_spec="$2"
  local tool_spec="$3"
  local endpoint_spec="$4"

  log "构建请求参数..."

  # 编码各字段
  local enc_server_spec=$(url_encode "$server_spec")

  # 构建基本表单数据
  local form_body="serverSpecification=${enc_server_spec}"

  # 如果有 toolSpecification，添加到表单
  if [ -n "$tool_spec" ]; then
    local enc_tool_spec=$(url_encode "$tool_spec")
    form_body="${form_body}&toolSpecification=${enc_tool_spec}"
  fi

  # 如果有 endpointSpecification，添加到表单
  if [ -n "$endpoint_spec" ]; then
    local enc_endpoint_spec=$(url_encode "$endpoint_spec")
    form_body="${form_body}&endpointSpecification=${enc_endpoint_spec}"
  fi

  local body_file="$TMP_DIR/body_$$"
  echo "$form_body" > "$body_file"

  local create_url="http://${HOST}:8848/nacos/v3/admin/ai/mcp"

  # 带重试的 API 调用
  local attempt=1
  while (( attempt <= MAX_RETRIES )); do
    log "调用 MCP 创建接口 (第 ${attempt}/${MAX_RETRIES} 次)..."

    local resp=$(curl -sS -w "\nHTTP_STATUS:%{http_code}\n" -X POST "$create_url" \
      -H "accessToken: $ACCESS_TOKEN" \
      -H "Content-Type: application/x-www-form-urlencoded; charset=UTF-8" \
      --connect-timeout 10 \
      --max-time 30 \
      --data @"$body_file" 2>&1 || echo "HTTP_STATUS:000")

    local http_status=$(echo "$resp" | sed -n 's/^HTTP_STATUS:\(.*\)$/\1/p' | tr -d '[:space:]')
    local body=$(echo "$resp" | sed '/HTTP_STATUS:/d')

    # 处理空状态码或无效状态码
    if [[ -z "$http_status" ]] || [[ ! "$http_status" =~ ^[0-9]{3}$ ]]; then
      http_status="000"
    fi

    log "HTTP 状态码: $http_status"
    if [[ -n "$body" ]] && [[ "$body" != "000" ]]; then
      log "响应内容: $body"
    fi

    # 检查是否已存在（根据用户偏好，已存在视为成功）
    if [ "$http_status" = "409" ] || echo "$body" | grep -q "has existed\|already exists"; then
      log "MCP '$mcp_name' 已存在，跳过创建"
      return 0
    fi

    # 成功
    if [ "$http_status" = "200" ] || [ "$http_status" = "201" ]; then
      log "MCP '$mcp_name' 创建成功"
      return 0
    fi

    # 如果是连接错误且还有重试次数，继续重试
    if [ "$http_status" = "000" ] && (( attempt < MAX_RETRIES )); then
      log "连接失败，${RETRY_DELAY}秒后重试..."
      sleep $RETRY_DELAY
      attempt=$((attempt + 1))
      continue
    fi

    # 其他错误，最后一次尝试才报错
    if (( attempt >= MAX_RETRIES )); then
      err "创建 MCP '$mcp_name' 失败（HTTP $http_status），已重试 ${MAX_RETRIES} 次"
      return 1
    fi

    sleep $RETRY_DELAY
    attempt=$((attempt + 1))
  done

  err "创建 MCP '$mcp_name' 失败"
  return 1
}

########################################
# 3. 登录 Nacos（带重试）
########################################
log "登录 Nacos 获取 accessToken..."

LOGIN_URL="http://${HOST}:8848/nacos/v1/auth/login"

ACCESS_TOKEN=""
attempt=1

while (( attempt <= MAX_RETRIES )); do
  log "尝试登录 Nacos (第 ${attempt}/${MAX_RETRIES} 次)..."

  LOGIN_RESP=$(curl -sS -X POST "$LOGIN_URL" \
    --connect-timeout 10 \
    --max-time 30 \
    -d "username=${NACOS_USERNAME}" \
    -d "password=${NACOS_PASSWORD}" 2>&1 || echo "curl_error")

  # 检查是否为 curl 错误
  if echo "$LOGIN_RESP" | grep -q "curl.*error\|Failed to connect\|Couldn't connect"; then
    if (( attempt < MAX_RETRIES )); then
      log "连接失败，${RETRY_DELAY}秒后重试..."
      sleep $RETRY_DELAY
      attempt=$((attempt + 1))
      continue
    else
      err "登录失败，连接 Nacos 失败（已重试 ${MAX_RETRIES} 次）"
      err "响应: $LOGIN_RESP"
      
      # 如果是商业化 Nacos，登录失败则跳过
      if [ "$IS_COMMERCIAL" = "true" ]; then
        log "商业化 Nacos 登录失败，跳过 MCP 导入"
        exit 0
      fi
      
      exit 1
    fi
  fi

  ACCESS_TOKEN=$(echo "$LOGIN_RESP" | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')

  if [ -n "$ACCESS_TOKEN" ]; then
    log "登录成功，accessToken = $ACCESS_TOKEN"
    break
  fi

  if (( attempt < MAX_RETRIES )); then
    log "未获取到 accessToken，${RETRY_DELAY}秒后重试..."
    sleep $RETRY_DELAY
  else
    err "登录失败，未能从响应中解析 accessToken（已重试 ${MAX_RETRIES} 次）"
    err "原始响应: $LOGIN_RESP"
    
    # 如果是商业化 Nacos，登录失败则跳过
    if [ "$IS_COMMERCIAL" = "true" ]; then
      log "商业化 Nacos 登录失败，跳过 MCP 导入"
      exit 0
    fi
    
    exit 1
  fi

  attempt=$((attempt + 1))
done

########################################
# 4. 解析并创建 MCP
########################################
log "解析 MCP JSON 文件: $MCP_JSON_FILE"

# 检查是否为数组
IS_ARRAY=$(jq 'type == "array"' "$MCP_JSON_FILE")

if [ "$IS_ARRAY" = "true" ]; then
  # 数组格式，遍历所有 MCP
  ARRAY_LENGTH=$(jq 'length' "$MCP_JSON_FILE")
  log "检测到数组格式，共 $ARRAY_LENGTH 个 MCP 配置"
  
  SUCCESS_COUNT=0
  FAIL_COUNT=0
  
  for ((i=0; i<ARRAY_LENGTH; i++)); do
    log ""
    log "========== 处理第 $((i+1))/$ARRAY_LENGTH 个 MCP =========="
    
    # 提取 serverSpecification
    SERVER_SPEC=$(jq -c ".[$i].serverSpecification" "$MCP_JSON_FILE")
    if [ "$SERVER_SPEC" = "null" ] || [ -z "$SERVER_SPEC" ]; then
      err "警告: 第 $((i+1)) 个配置未找到 serverSpecification，跳过"
      ((FAIL_COUNT++))
      continue
    fi

    # 提取 MCP 名称用于显示
    MCP_NAME=$(echo "$SERVER_SPEC" | jq -r '.name // "unknown"')
    log "正在创建 MCP: $MCP_NAME"

    # 提取 toolSpecification (可选)
    TOOL_SPEC=$(jq -c ".[$i].toolSpecification // empty" "$MCP_JSON_FILE" || echo "")

    # 提取 endpointSpecification (可选)
    ENDPOINT_SPEC=$(jq -c ".[$i].endpointSpecification // empty" "$MCP_JSON_FILE" || echo "")

    # 调用创建函数
    if create_single_mcp "$MCP_NAME" "$SERVER_SPEC" "$TOOL_SPEC" "$ENDPOINT_SPEC"; then
      ((SUCCESS_COUNT++))
    else
      ((FAIL_COUNT++))
    fi
  done
  
  log ""
  log "=========================================="
  log "所有 MCP 创建请求已发送完成。"
  log "成功: $SUCCESS_COUNT, 失败: $FAIL_COUNT"
  
  # 如果是商业化 Nacos 且有失败，跳过错误
  if [ "$IS_COMMERCIAL" = "true" ] && [ $FAIL_COUNT -gt 0 ]; then
    log "商业化 Nacos 部分 MCP 创建失败，但继续执行后续流程"
    exit 0
  fi
  
  # 开源 Nacos 如果有失败则报错
  if [ $FAIL_COUNT -gt 0 ]; then
    exit 1
  fi
else
  # 单个对象格式
  SERVER_SPEC=$(jq -c ".serverSpecification" "$MCP_JSON_FILE")
  if [ "$SERVER_SPEC" = "null" ] || [ -z "$SERVER_SPEC" ]; then
    err "错误: 未找到 serverSpecification"
    exit 1
  fi

  # 提取 MCP 名称用于显示
  MCP_NAME=$(echo "$SERVER_SPEC" | jq -r '.name // "unknown"')
  log "正在创建 MCP: $MCP_NAME"

  # 提取 toolSpecification (可选)
  TOOL_SPEC=$(jq -c ".toolSpecification // empty" "$MCP_JSON_FILE" || echo "")

  # 提取 endpointSpecification (可选)
  ENDPOINT_SPEC=$(jq -c ".endpointSpecification // empty" "$MCP_JSON_FILE" || echo "")
  
  log ""
  log "创建 MCP..."
  
  # 调用创建函数
  if create_single_mcp "$MCP_NAME" "$SERVER_SPEC" "$TOOL_SPEC" "$ENDPOINT_SPEC"; then
    log "MCP 创建成功"
  else
    # 如果是商业化 Nacos 且创建失败，跳过错误
    if [ "$IS_COMMERCIAL" = "true" ]; then
      log "商业化 Nacos MCP 创建失败，但继续执行后续流程"
      exit 0
    fi
    exit 1
  fi
fi
