#!/usr/bin/env bash
# HiMarket 前台开发者账号注册初始化钩子
# 由 deploy.sh 在部署就绪后自动调用
# 继承环境变量: NS, FRONT_USERNAME, FRONT_PASSWORD

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="${SCRIPT_DIR}/../../data"

# 从 .env 加载凭据
if [[ -f "${DATA_DIR}/.env" ]]; then
  set -a
  . "${DATA_DIR}/.env"
  set +a
fi

NS="${NAMESPACE:-himarket}"

# 默认凭据
FRONT_USERNAME="${FRONT_USERNAME:-demo}"
FRONT_PASSWORD="${FRONT_PASSWORD:-demo123}"

# 最大重试次数
MAX_RETRIES=3

log() { echo "[init-himarket-front $(date +'%H:%M:%S')] $*"; }
err() { echo "[ERROR] $*" >&2; }

# 全局变量
FRONTEND_HOST=""

########################################
# 获取 HiMarket Frontend Service 地址
########################################
get_himarket_frontend_host() {
  log "获取 HiMarket Frontend Service 地址..." >&2
  
  # 优先尝试 LoadBalancer IP
  local host=$(kubectl get svc himarket-frontend -n "${NS}" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
  
  if [[ -z "$host" ]]; then
    log "未检测到 LoadBalancer IP，使用 ClusterIP 模式" >&2
    host="himarket-frontend.${NS}.svc.cluster.local"
  fi
  
  log "HiMarket Frontend 地址: ${host}" >&2
  echo "$host"
}

########################################
# 调用 API 通用函数
# 参数: $1=接口名称 $2=HTTP方法 $3=HOST $4=路径 $5=请求体(可选)
########################################
call_api() {
  local api_name="$1"
  local method="$2"
  local host="$3"
  local path="$4"
  local body="${5:-}"
  
  local url="http://${host}${path}"
  
  log "调用接口 [${api_name}]: ${method} ${url}"
  
  local curl_cmd="curl -sS -w '\nHTTP_CODE:%{http_code}' -X ${method} '${url}'"
  curl_cmd="${curl_cmd} -H 'Content-Type: application/json'"
  curl_cmd="${curl_cmd} -H 'Accept: application/json, text/plain, */*'"
  
  if [[ -n "$body" ]]; then
    curl_cmd="${curl_cmd} -d '${body}'"
  fi
  
  curl_cmd="${curl_cmd} --connect-timeout 10 --max-time 30"
  
  # 执行 curl
  local result=$(eval "$curl_cmd" 2>&1 || echo "HTTP_CODE:000")
  
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
  
  log "接口 [${api_name}] 返回: HTTP ${http_code}"
  
  if [[ -n "$response" && "$response" != "000" ]]; then
    log "响应内容: ${response}"
  fi
  
  # 导出供调用者使用
  export API_RESPONSE="$response"
  export API_HTTP_CODE="$http_code"
  
  # 2xx 视为成功
  if [[ "$http_code" =~ ^2[0-9]{2}$ ]]; then
    return 0
  # 409 冲突或重复数据也视为成功（幂等性）
  elif [[ "$http_code" == "409" ]] || [[ "$response" == *"Duplicate entry"* ]]; then
    log "资源已存在，视为成功（幂等性）"
    return 0
  else
    return 1
  fi
}

########################################
# 注册前台开发者账号（幂等）
########################################
register_developer_account() {
  local body="{\"username\":\"${FRONT_USERNAME}\",\"password\":\"${FRONT_PASSWORD}\"}"
  
  local attempt=1
  while (( attempt <= MAX_RETRIES )); do
    log "注册前台开发者账号 (第 ${attempt}/${MAX_RETRIES} 次)..."
    
    if call_api "注册开发者" "POST" "${FRONTEND_HOST}" "/api/v1/developers" "$body"; then
      log "前台开发者账号注册成功！"
      log "用户名: ${FRONT_USERNAME}"
      log "密码: ${FRONT_PASSWORD}"
      return 0
    else
      err "注册前台开发者账号失败 (第 ${attempt} 次)"
      if (( attempt < MAX_RETRIES )); then
        log "等待 5 秒后重试..."
        sleep 5
      fi
    fi
    
    attempt=$((attempt+1))
  done
  
  err "注册前台开发者账号失败，已达最大重试次数"
  return 1
}

########################################
# 主流程
########################################
main() {
  log "========================================"
  log "开始初始化 HiMarket 前台开发者账号"
  log "========================================"
  
  # 获取服务地址
  FRONTEND_HOST=$(get_himarket_frontend_host)
  
  # 注册前台开发者账号
  if register_developer_account; then
    log "========================================"
    log "✓ 前台开发者账号注册成功"
    log "  用户名: ${FRONT_USERNAME}"
    log "  前台地址: http://${FRONTEND_HOST}"
    log "========================================"
    exit 0
  else
    log "========================================"
    err "✗ 前台开发者账号注册失败"
    log "========================================"
    exit 1
  fi
}

main "$@"
