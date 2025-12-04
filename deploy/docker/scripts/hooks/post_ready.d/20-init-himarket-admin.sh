#!/usr/bin/env bash
# HiMarket 管理员账号注册初始化钩子 (Docker 环境)
# 由 deploy.sh 在部署就绪后自动调用

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="${SCRIPT_DIR}/../../data"

# 从 .env 加载凭据
if [[ -f "${DATA_DIR}/.env" ]]; then
  set -a
  . "${DATA_DIR}/.env"
  set +a
fi

# 默认凭据
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin}"

# 最大重试次数
MAX_RETRIES=3

log() { echo "[init-himarket-admin $(date +'%H:%M:%S')] $*"; }
err() { echo "[ERROR] $*" >&2; }

# 全局变量
ADMIN_HOST="localhost:5174"

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
# 注册管理员账号（幂等）
########################################
register_admin_account() {
  local body="{\"username\":\"${ADMIN_USERNAME}\",\"password\":\"${ADMIN_PASSWORD}\"}"
  
  local attempt=1
  while (( attempt <= MAX_RETRIES )); do
    log "注册管理员账号 (第 ${attempt}/${MAX_RETRIES} 次)..."
    
    if call_api "注册管理员" "POST" "/api/v1/admins/init" "$body"; then
      log "管理员账号注册成功！"
      log "用户名: ${ADMIN_USERNAME}"
      log "密码: ${ADMIN_PASSWORD}"
      return 0
    else
      err "注册管理员账号失败 (第 ${attempt} 次)"
      if (( attempt < MAX_RETRIES )); then
        log "等待 5 秒后重试..."
        sleep 5
      fi
    fi
    
    attempt=$((attempt+1))
  done
  
  err "注册管理员账号失败，已达最大重试次数"
  return 1
}

########################################
# 主流程
########################################
main() {
  log "========================================"
  log "开始初始化 HiMarket 管理员账号"
  log "========================================"
  
  # 注册管理员账号
  if register_admin_account; then
    log "========================================"
    log "✓ 管理员账号注册成功"
    log "  用户名: ${ADMIN_USERNAME}"
    log "  管理后台地址: http://${ADMIN_HOST}"
    log "========================================"
    exit 0
  else
    log "========================================"
    err "✗ 管理员账号注册失败"
    log "========================================"
    exit 1
  fi
}

main "$@"
