#!/usr/bin/env bash
# 商业化 Nacos 实例初始化钩子 (Docker 环境)
# 由 deploy.sh 在部署就绪后自动调用
# 依赖: 20-init-himarket-admin.sh (管理员账号已注册)
# 继承环境变量: ADMIN_USERNAME, ADMIN_PASSWORD, COMMERCIAL_NACOS_*

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="${SCRIPT_DIR}/../../data"

# 从 .env 加载环境变量
if [[ -f "${DATA_DIR}/.env" ]]; then
  set -a
  . "${DATA_DIR}/.env"
  set +a
fi

# HiMarket Admin 登录凭据
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin}"

# 商业化 Nacos 配置参数（从环境变量读取）
NACOS_NAME="${COMMERCIAL_NACOS_NAME:-}"
NACOS_SERVER_URL="${COMMERCIAL_NACOS_SERVER_URL:-}"
NACOS_USERNAME="${COMMERCIAL_NACOS_USERNAME:-}"
NACOS_PASSWORD="${COMMERCIAL_NACOS_PASSWORD:-}"
NACOS_ACCESS_KEY="${COMMERCIAL_NACOS_ACCESS_KEY:-}"
NACOS_SECRET_KEY="${COMMERCIAL_NACOS_SECRET_KEY:-}"
NACOS_ID="${COMMERCIAL_NACOS_ID:-}"

# 全局变量
AUTH_TOKEN=""
ADMIN_HOST="localhost:5174"

# 最大重试次数
MAX_RETRIES=3

log() { echo "[init-commercial-nacos $(date +'%H:%M:%S')] $*"; }
err() { echo "[ERROR] $*" >&2; }

########################################
# 调用 API 通用函数
########################################
call_api() {
  local api_name="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  
  local url="http://${ADMIN_HOST}${path}"
  
  log "调用接口 [${api_name}]: ${method} ${url}"
  
  local curl_cmd="curl -sS -w '\nHTTP_CODE:%{http_code}' -X ${method} '${url}'"
  curl_cmd="${curl_cmd} -H 'Content-Type: application/json'"
  curl_cmd="${curl_cmd} -H 'Accept: application/json, text/plain, */*'"
  
  if [[ -n "$AUTH_TOKEN" ]]; then
    curl_cmd="${curl_cmd} -H 'Authorization: Bearer ${AUTH_TOKEN}'"
  fi
  
  if [[ -n "$body" ]]; then
    curl_cmd="${curl_cmd} -d '${body}'"
  fi
  
  curl_cmd="${curl_cmd} --connect-timeout 10 --max-time 30"
  
  local result=$(eval "$curl_cmd" 2>&1 || echo "HTTP_CODE:000")
  
  local http_code=""
  local response=""
  
  if [[ "$result" =~ HTTP_CODE:([0-9]{3}) ]]; then
    http_code="${BASH_REMATCH[1]}"
    response=$(echo "$result" | sed '/HTTP_CODE:/d')
  else
    http_code="000"
    response="$result"
  fi
  
  log "接口 [${api_name}] 返回: HTTP ${http_code}"
  
  if [[ -n "$response" && "$response" != "000" ]]; then
    log "响应内容: ${response}"
  fi
  
  export API_RESPONSE="$response"
  export API_HTTP_CODE="$http_code"
  
  if [[ "$http_code" =~ ^2[0-9]{2}$ ]] || [[ "$http_code" == "409" ]]; then
    return 0
  else
    return 1
  fi
}

########################################
# 登录 HiMarket Admin 获取 Token
########################################
login_admin() {
  local body="{\"username\":\"${ADMIN_USERNAME}\",\"password\":\"${ADMIN_PASSWORD}\"}"
  
  local attempt=1
  while (( attempt <= MAX_RETRIES )); do
    log "执行登录 (第 ${attempt}/${MAX_RETRIES} 次)..."
    
    if call_api "管理员登录" "POST" "/api/v1/admins/login" "$body"; then
      # 从响应体中提取 token
      AUTH_TOKEN=$(echo "$API_RESPONSE" | grep -o '"access_token":"[^"]*"' | head -1 | sed 's/"access_token":"//' | sed 's/"//' || echo "")
      
      if [[ -z "$AUTH_TOKEN" ]]; then
        AUTH_TOKEN=$(echo "$API_RESPONSE" | grep -o '"token":"[^"]*"' | head -1 | sed 's/"token":"//' | sed 's/"//' || echo "")
      fi
      
      if [[ -z "$AUTH_TOKEN" ]]; then
        AUTH_TOKEN=$(echo "$API_RESPONSE" | grep -o '"accessToken":"[^"]*"' | head -1 | sed 's/"accessToken":"//' | sed 's/"//' || echo "")
      fi
      
      if [[ -z "$AUTH_TOKEN" ]]; then
        err "无法从登录响应中提取 token"
        err "响应内容: $API_RESPONSE"
        return 1
      fi
      
      log "登录成功，获取到 Token: ${AUTH_TOKEN:0:20}..."
      return 0
    fi
    
    if (( attempt < MAX_RETRIES )); then
      sleep 5
    fi
    attempt=$((attempt+1))
  done
  
  err "登录失败"
  return 1
}

########################################
# 参数验证
########################################
validate_params() {
  local missing_params=()
  
  [[ -z "$NACOS_NAME" ]] && missing_params+=("COMMERCIAL_NACOS_NAME")
  [[ -z "$NACOS_SERVER_URL" ]] && missing_params+=("COMMERCIAL_NACOS_SERVER_URL")

  # 检查是否提供了其中一组凭据
  local auth_group1_provided=true
  local auth_group2_provided=true
  
  [[ -z "$NACOS_USERNAME" ]] && auth_group1_provided=false
  [[ -z "$NACOS_PASSWORD" ]] && auth_group1_provided=false
  
  [[ -z "$NACOS_ACCESS_KEY" ]] && auth_group2_provided=false
  [[ -z "$NACOS_SECRET_KEY" ]] && auth_group2_provided=false
  
  if [[ "$auth_group1_provided" == false && "$auth_group2_provided" == false ]]; then
    err "必须提供以下其中一组凭据:"
    err "  1. COMMERCIAL_NACOS_USERNAME 和 COMMERCIAL_NACOS_PASSWORD"
    err "  2. COMMERCIAL_NACOS_ACCESS_KEY 和 COMMERCIAL_NACOS_SECRET_KEY"
    exit 1
  fi
  
  if [[ ${#missing_params[@]} -gt 0 ]]; then
    err "缺少必需的环境变量:"
    for param in "${missing_params[@]}"; do
      err "  - ${param}"
    done
    exit 1
  fi
  
  log "参数验证通过"
  log "Nacos 实例名称: ${NACOS_NAME}"
  log "Nacos 服务地址: ${NACOS_SERVER_URL}"
}

########################################
# 初始化商业化 Nacos 实例
########################################
init_commercial_nacos() {
  log "开始初始化商业化 Nacos 实例..."
  
  local api_url="http://${ADMIN_HOST}/api/v1/nacos"
  
  # 构建请求体
  local request_body=$(cat <<EOF
{
  "nacosName": "${NACOS_NAME}",
  "serverUrl": "${NACOS_SERVER_URL}",
  "username": "${NACOS_USERNAME}",
  "password": "${NACOS_PASSWORD}",
  "accessKey": "${NACOS_ACCESS_KEY}",
  "secretKey": "${NACOS_SECRET_KEY}",
  "nacosId": "${NACOS_ID}"
}
EOF
)
  
  local attempt=1
  while (( attempt <= MAX_RETRIES )); do
    log "尝试初始化商业化 Nacos 实例 (第 ${attempt}/${MAX_RETRIES} 次)..."
    
    local result=$(curl -sS -w "\nHTTP_CODE:%{http_code}" -X POST "${api_url}" \
      -H "Accept: application/json, text/plain, */*" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer ${AUTH_TOKEN}" \
      --data-raw "${request_body}" \
      --connect-timeout 10 --max-time 30 2>/dev/null || echo "HTTP_CODE:000")
    
    # 提取 HTTP 状态码和响应体
    local http_code=""
    local response=""
    
    if [[ "$result" =~ HTTP_CODE:([0-9]{3}) ]]; then
      http_code="${BASH_REMATCH[1]}"
      response=$(echo "$result" | sed '/HTTP_CODE:/d')
    else
      http_code="000"
      response="$result"
    fi
    
    log "API 返回: HTTP ${http_code}"
    
    if [[ -n "$response" && "$response" != "000" ]]; then
      log "响应内容: ${response}"
    fi
    
    # 2xx 成功
    if [[ "$http_code" =~ ^2[0-9]{2}$ ]]; then
      log "商业化 Nacos 实例初始化成功！"
      log "实例名称: ${NACOS_NAME}"
      log "服务地址: ${NACOS_SERVER_URL}"
      return 0
    fi
    
    # 409 冲突（已存在）也视为成功
    if [[ "$http_code" == "409" ]] || [[ "$response" == *"already exists"* ]] || [[ "$response" == *"已存在"* ]]; then
      log "Nacos 实例已存在，视为成功（幂等性）"
      return 0
    fi
    
    # 4xx 客户端错误，不重试
    if [[ "$http_code" =~ ^4[0-9]{2}$ ]]; then
      err "初始化失败: HTTP ${http_code}"
      err "请检查参数配置或 Token 是否有效"
      return 1
    fi
    
    # 5xx 或网络错误，重试
    if (( attempt < MAX_RETRIES )); then
      log "初始化失败 (HTTP ${http_code})，等待 5 秒后重试..."
      if [[ -n "$response" && "$response" != "000" ]]; then
        log "错误信息: ${response}"
      fi
      sleep 5
    fi
    
    attempt=$((attempt+1))
  done
  
  err "初始化商业化 Nacos 实例失败，已达最大重试次数"
  return 1
}

########################################
# 主流程
########################################
main() {
  log "========================================"
  log "开始初始化商业化 Nacos 实例"
  log "========================================"
  
  # 验证参数（如果缺少必需参数则跳过）
  validate_params
  
  # 登录 HiMarket Admin 获取 Token
  if ! login_admin; then
    err "登录 HiMarket Admin 失败"
    exit 1
  fi
  
  # 初始化 Nacos 实例
  if ! init_commercial_nacos; then
    err "初始化商业化 Nacos 实例失败"
    exit 1
  fi
  
  log "========================================"
  log "✓ 商业化 Nacos 实例初始化完成"
  log "  实例名称: ${NACOS_NAME}"
  log "  服务地址: ${NACOS_SERVER_URL}"
  log "========================================"
}

main "$@"
