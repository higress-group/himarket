#!/usr/bin/env bash
# Higress 管理员用户初始化钩子 (Docker 环境)
# 由 deploy.sh 在部署就绪后自动调用
# 功能：初始化 Higress Console 的 admin 用户
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="${SCRIPT_DIR}/../../data"

# 从 .env 加载环境变量
if [[ -f "${DATA_DIR}/.env" ]]; then
  set -a
  . "${DATA_DIR}/.env"
  set +a
fi

# 从环境变量读取密码，默认值为 admin
HIGRESS_PASSWORD="${HIGRESS_PASSWORD:-admin}"
HIGRESS_ADMIN_USER="${HIGRESS_ADMIN_USER:-admin}"
HIGRESS_ADMIN_DISPLAY_NAME="${HIGRESS_ADMIN_USER:-admin}"

log() { echo "[init-higress-admin $(date +'%H:%M:%S')] $*"; }
err() { echo "[ERROR] $*" >&2; }

########################################
# 初始化 Higress 管理员用户
########################################
log "开始初始化 Higress 管理员用户..."

# Docker 环境下使用 localhost:8001 (映射到容器的 8080 端口)
HIGRESS_HOST="localhost:8001"
INIT_URL="http://${HIGRESS_HOST}/system/init"

log "初始化 URL: ${INIT_URL}"

# 构建请求体
REQUEST_BODY=$(cat <<EOF
{
  "adminUser": {
    "name": "${HIGRESS_ADMIN_USER}",
    "displayName": "${HIGRESS_ADMIN_DISPLAY_NAME}",
    "password": "${HIGRESS_PASSWORD}"
  }
}
EOF
)

# 重试逻辑
max_attempts=10
attempt=1

while (( attempt <= max_attempts )); do
  log "尝试初始化 Higress 管理员用户 (第 ${attempt}/${max_attempts} 次)..."
  
  # 调用初始化接口
  init_result=$(curl -sS -w "\nHTTP_CODE:%{http_code}" -X POST "${INIT_URL}" \
    -H "Accept: application/json, text/plain, */*" \
    -H "Content-Type: application/json" \
    --data-raw "${REQUEST_BODY}" \
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
  
  # 成功 (200 或 2xx)
  if [[ "$http_code" =~ ^2[0-9]{2}$ ]]; then
    if [[ -n "$response" ]]; then
      log "响应内容: ${response}"
    fi
    
    log "Higress 管理员用户初始化成功！"
    log "用户名: ${HIGRESS_ADMIN_USER}"
    log "密码: ${HIGRESS_PASSWORD}"
    log "访问地址: http://${HIGRESS_HOST}"
    exit 0
  fi
  
  # 已存在或已初始化（可能返回 400 或特定错误）
  if [[ "$response" == *"already"* ]] || [[ "$response" == *"已初始化"* ]] || [[ "$response" == *"已存在"* ]]; then
    log "Higress 管理员用户已存在或已初始化"
    log "用户名: ${HIGRESS_ADMIN_USER}"
    log "访问地址: http://${HIGRESS_HOST}"
    exit 0
  fi
  
  # 其他错误，重试
  if (( attempt < max_attempts )); then
    log "初始化失败 (HTTP ${http_code})，等待 5 秒后重试..."
    if [[ -n "$response" && "$response" != "000" ]]; then
      log "错误信息: ${response}"
    fi
    sleep 5
  fi
  
  attempt=$((attempt+1))
done

err "Higress 管理员用户初始化失败，已达最大重试次数"
exit 1
