#!/usr/bin/env bash
# Nacos 管理员密码初始化钩子 (Docker 环境)
# 由 deploy.sh 在部署就绪后自动调用
# 
# Nacos 3.0.2 需要通过 POST /v3/auth/user/admin 初始化管理员密码
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="${SCRIPT_DIR}/../../data"

# 从 .env 加载环境变量
if [[ -f "${DATA_DIR}/.env" ]]; then
  set -a
  . "${DATA_DIR}/.env"
  set +a
fi

NACOS_ADMIN_PASSWORD="${NACOS_ADMIN_PASSWORD:-nacos}"

log() { echo "[init-nacos-admin $(date +'%H:%M:%S')] $*"; }
err() { echo "[ERROR] $*" >&2; }

########################################
# 初始化 Nacos 管理员密码
########################################
log "开始初始化 Nacos 管理员密码..."

NACOS_HOST="localhost"
BASE_URL="http://${NACOS_HOST}:8080"
INIT_URL="${BASE_URL}/v3/auth/user/admin"

log "初始化 URL: ${INIT_URL}"

# 重试逻辑
max_attempts=10
attempt=1

while (( attempt <= max_attempts )); do
  log "尝试初始化 Nacos 管理员密码 (第 ${attempt}/${max_attempts} 次)..."
  
  # 直接使用 curl 调用初始化 API
  init_result=$(curl -sS -w "\nHTTP_CODE:%{http_code}" -X POST "${INIT_URL}" \
    -H "Content-Type: application/x-www-form-urlencoded; charset=UTF-8" \
    -d "password=${NACOS_ADMIN_PASSWORD}" \
    --connect-timeout 5 --max-time 10 2>/dev/null || echo "HTTP_CODE:000")

  # 提取 HTTP 状态码
  if [[ "$init_result" =~ HTTP_CODE:([0-9]{3}) ]]; then
    http_code="${BASH_REMATCH[1]}"
    # 删除 HTTP_CODE 行得到响应体
    response=$(echo "$init_result" | sed '/HTTP_CODE:/d')
  else
    http_code="000"
    response="$init_result"
  fi
  
  log "初始化 API 返回: HTTP ${http_code}"
  
  # 成功 (200)
  if [[ "$http_code" == "200" ]]; then
    if [[ -n "$response" ]]; then
      log "响应内容: ${response}"
    fi
    
    # 检查是否是管理员已存在的情况 (code: 409)
    if [[ "$response" == *'"code":409'* ]] || [[ "$response" == *'have admin user'* ]]; then
      log "Nacos 管理员用户已存在，跳过初始化"
      log "请使用已有凭据登录 Nacos Console: http://${NACOS_HOST}:8848/nacos"
    else
      log "Nacos 管理员密码初始化成功！"
      log "用户名: nacos"
      log "密码: ${NACOS_ADMIN_PASSWORD}"
      log "访问地址: http://${NACOS_HOST}:8848/nacos"
    fi
    exit 0
  fi
  
  # 已存在或其他成功状态
  if [[ "$http_code" =~ ^2[0-9]{2}$ ]]; then
    log "Nacos 管理员用户已存在或已初始化"
    exit 0
  fi
  
  # 其他错误，重试
  if (( attempt < max_attempts )); then
    log "初始化失败 (HTTP ${http_code})，等待 10 秒后重试..."
    if [[ -n "$response" && "$response" != "000" ]]; then
      log "错误信息: ${response}"
    fi
    sleep 10
  fi
  
  attempt=$((attempt+1))
done

err "Nacos 管理员密码初始化失败，已达最大重试次数"
exit 1
