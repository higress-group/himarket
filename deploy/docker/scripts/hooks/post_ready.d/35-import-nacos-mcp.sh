#!/usr/bin/env bash
# Nacos MCP 数据创建钩子 (Docker 环境)
# 由 deploy.sh 在部署就绪后自动调用
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="${SCRIPT_DIR}/../../data"

# 从 .env 加载环境变量
if [[ -f "${DATA_DIR}/.env" ]]; then
  set -a
  . "${DATA_DIR}/.env"
  set +a
fi

MCP_JSON_FILE="${DATA_DIR}/nacos-mcp.json"

# Nacos 默认用户名密码
NACOS_USERNAME="${NACOS_USERNAME:-nacos}"
NACOS_PASSWORD="${NACOS_PASSWORD:-nacos}"

# 固定配置
NAMESPACE_ID="public"

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
# 2. Docker 环境使用 localhost
########################################
HOST="localhost"
log "Nacos Service 地址: ${HOST}:8848"

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
# 创建单个 MCP 的函数
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

  log "调用 MCP 创建接口..."
  local create_url="http://${HOST}:8848/nacos/v3/admin/ai/mcp"

  local resp=$(curl -sS -w "\nHTTP_STATUS:%{http_code}\n" -X POST "$create_url" \
    -H "accessToken: $ACCESS_TOKEN" \
    -H "Content-Type: application/x-www-form-urlencoded; charset=UTF-8" \
    --data @"$body_file")

  local http_status=$(echo "$resp" | sed -n 's/^HTTP_STATUS:\(.*\)$/\1/p')
  local body=$(echo "$resp" | sed '/HTTP_STATUS:/d')

  log "接口返回："
  log "$body"
  log ""
  log "HTTP 状态码: $http_status"

  # 检查是否已存在（根据用户偏好，已存在视为成功）
  # 409 状态码表示资源冲突（已存在）
  if [ "$http_status" = "409" ] || echo "$body" | grep -q "has existed\|already exists"; then
    log "MCP '$mcp_name' 已存在，跳过创建"
    return 0
  fi

  if [ "$http_status" != "200" ]; then
    err "创建 MCP '$mcp_name' 失败（HTTP $http_status）"
    return 1
  fi

  log "MCP '$mcp_name' 创建成功"
  return 0
}

# 临时目录
TMP_DIR="$(mktemp -d -t nacos-mcp-import-XXXXXX)"

cleanup() {
  rm -rf "$TMP_DIR" || true
}
trap cleanup EXIT

log "登录 Nacos 获取 accessToken..."

LOGIN_URL="http://${HOST}:8848/nacos/v1/auth/login"

LOGIN_RESP=$(curl -sS -X POST "$LOGIN_URL" \
  -d "username=${NACOS_USERNAME}" \
  -d "password=${NACOS_PASSWORD}")

ACCESS_TOKEN=$(echo "$LOGIN_RESP" | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')

if [ -z "$ACCESS_TOKEN" ]; then
  err "登录失败,未能从响应中解析 accessToken。原始响应:"
  err "$LOGIN_RESP"
  exit 1
fi

log "登录成功，accessToken = $ACCESS_TOKEN"
log ""

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
      ((SUCCESS_COUNT++)) || true
    else
      ((FAIL_COUNT++)) || true
    fi
  done

  log ""
  log "=========================================="
  log "所有 MCP 创建请求已发送完成。"
  log "成功: $SUCCESS_COUNT, 失败: $FAIL_COUNT"
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
  create_single_mcp "$MCP_NAME" "$SERVER_SPEC" "$TOOL_SPEC" "$ENDPOINT_SPEC"
fi
