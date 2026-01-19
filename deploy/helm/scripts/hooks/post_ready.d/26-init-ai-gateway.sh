#!/usr/bin/env bash
# AI 网关初始化钩子
# 由 deploy.sh 在部署就绪后自动调用
# 依赖: 20-init-himarket-admin.sh (管理员账号已注册)
# 继承环境变量: NS, ADMIN_USERNAME, ADMIN_PASSWORD, AI_GATEWAY_*
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

# Himarket Admin 登录凭据
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin}"

# AI 网关配置参数（从环境变量读取）
GATEWAY_ID="${AI_GATEWAY_ID:-}"
GATEWAY_NAME="${AI_GATEWAY_NAME:-}"
GATEWAY_REGION="${AI_GATEWAY_REGION:-}"
GATEWAY_ACCESS_KEY="${AI_GATEWAY_ACCESS_KEY:-}"
GATEWAY_SECRET_KEY="${AI_GATEWAY_SECRET_KEY:-}"

# 全局变量
AUTH_TOKEN=""
HIMARKET_HOST=""

# 最大重试次数
MAX_RETRIES=3

log() { echo "[init-ai-gateway $(date +'%H:%M:%S')] $*"; }
err() { echo "[ERROR] $*" >&2; }

########################################
# 获取 Himarket Admin Service 地址
########################################
get_himarket_admin_host() {
  log "获取 Himarket Admin Service 地址..." >&2
  
  local host=$(kubectl get svc himarket-admin -n "${NS}" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
  
  if [[ -z "$host" ]]; then
    log "未检测到 LoadBalancer IP，使用 ClusterIP 模式" >&2
    host="himarket-admin.${NS}.svc.cluster.local"
  fi
  
  log "Himarket Admin 地址: ${host}" >&2
  echo "$host"
}

########################################
# 调用 API 通用函数
########################################
call_api() {
  local api_name="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  
  local url="http://${HIMARKET_HOST}${path}"
  
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
# 登录 Himarket Admin 获取 Token
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
  
  [[ -z "$GATEWAY_ID" ]] && missing_params+=("AI_GATEWAY_ID")
  [[ -z "$GATEWAY_NAME" ]] && missing_params+=("AI_GATEWAY_NAME")
  [[ -z "$GATEWAY_REGION" ]] && missing_params+=("AI_GATEWAY_REGION")
  [[ -z "$GATEWAY_ACCESS_KEY" ]] && missing_params+=("AI_GATEWAY_ACCESS_KEY")
  [[ -z "$GATEWAY_SECRET_KEY" ]] && missing_params+=("AI_GATEWAY_SECRET_KEY")
  
  if [[ ${#missing_params[@]} -gt 0 ]]; then
    err "缺少必需的环境变量:"
    for param in "${missing_params[@]}"; do
      err "  - ${param}"
    done
    exit 1
  fi
  
  log "参数验证通过"
  log "网关 ID: ${GATEWAY_ID}"
  log "网关名称: ${GATEWAY_NAME}"
  log "网关区域: ${GATEWAY_REGION}"
}

########################################
# 初始化 AI 网关
########################################
init_ai_gateway() {
  log "开始初始化 AI 网关..."
  
  local api_url="http://${HIMARKET_HOST}/api/v1/gateways"
  
  # 构建请求体
  local request_body=$(cat <<EOF
{
  "gatewayId": "${GATEWAY_ID}",
  "gatewayType": "APIG_AI",
  "gatewayName": "${GATEWAY_NAME}",
  "apigConfig": {
    "region": "${GATEWAY_REGION}",
    "accessKey": "${GATEWAY_ACCESS_KEY}",
    "secretKey": "${GATEWAY_SECRET_KEY}"
  },
  "adpAIGatewayConfig": null,
  "higressConfig": null,
  "createAt": null
}
EOF
)
  
  local attempt=1
  while (( attempt <= MAX_RETRIES )); do
    log "尝试初始化 AI 网关 (第 ${attempt}/${MAX_RETRIES} 次)..."
    
    local result=$(curl -sS -w "\nHTTP_CODE:%{http_code}" -X POST "${api_url}" \
      -H "Accept: application/json, text/plain, */*" \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer ${AUTH_TOKEN}" \
      --data "${request_body}" \
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
      log "AI 网关初始化成功！"
      log "网关 ID: ${GATEWAY_ID}"
      log "网关名称: ${GATEWAY_NAME}"
      log "网关区域: ${GATEWAY_REGION}"
      return 0
    fi
    
    # 409 冲突（已存在）也视为成功
    if [[ "$http_code" == "409" ]] || [[ "$response" == *"already exists"* ]] || [[ "$response" == *"已存在"* ]]; then
      log "AI 网关已存在，视为成功（幂等性）"
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
  
  err "初始化 AI 网关失败，已达最大重试次数"
  return 1
}

########################################
# 主流程
########################################
main() {
  log "========================================"
  log "开始初始化 AI 网关"
  log "========================================"

  # 验证参数
  validate_params

  # 获取 Himarket Admin 地址
  HIMARKET_HOST=$(get_himarket_admin_host)

  # 登录 Himarket Admin 获取 Token
  if ! login_admin; then
    err "登录 Himarket Admin 失败"
    exit 1
  fi

  # 初始化 AI 网关
  if ! init_ai_gateway; then
    err "初始化 AI 网关失败"
    exit 1
  fi
  
  log "========================================"
  log "✓ AI 网关初始化完成"
  log "  网关 ID: ${GATEWAY_ID}"
  log "  网关名称: ${GATEWAY_NAME}"
  log "  网关区域: ${GATEWAY_REGION}"
  log "========================================"
}

main "$@"
