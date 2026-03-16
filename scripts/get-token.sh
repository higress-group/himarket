#!/bin/bash
# 获取本地开发环境的 JWT Token
# 用法: source scripts/get-token.sh [admin|developer]
# 会设置环境变量 HIMARKET_TOKEN 供后续 curl 使用

ROLE=${1:-admin}
BASE_URL=${HIMARKET_BASE_URL:-http://localhost:8080}

# 从 ~/.env 读取认证信息（如果存在）
if [ -f ~/.env ]; then
  source ~/.env
fi

# 默认账号密码，可通过环境变量覆盖
ADMIN_USERNAME=${HIMARKET_ADMIN_USERNAME:-admin}
ADMIN_PASSWORD=${HIMARKET_ADMIN_PASSWORD:-admin}
DEV_USERNAME=${HIMARKET_DEV_USERNAME:-user}
DEV_PASSWORD=${HIMARKET_DEV_PASSWORD:-123456}

if [ "$ROLE" = "admin" ]; then
  ENDPOINT="$BASE_URL/admins/login"
  USERNAME=$ADMIN_USERNAME
  PASSWORD=$ADMIN_PASSWORD
elif [ "$ROLE" = "developer" ]; then
  ENDPOINT="$BASE_URL/developers/login"
  USERNAME=$DEV_USERNAME
  PASSWORD=$DEV_PASSWORD
else
  echo "用法: source scripts/get-token.sh [admin|developer]"
  return 1 2>/dev/null || exit 1
fi

RESPONSE=$(curl -s -X POST "$ENDPOINT" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")

TOKEN=$(echo "$RESPONSE" | jq -r '.data.access_token')

if [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ]; then
  export HIMARKET_TOKEN="$TOKEN"
  echo "✅ 已获取 ${ROLE} Token，已设置到 HIMARKET_TOKEN 环境变量"
  echo "示例: curl -H \"Authorization: Bearer \$HIMARKET_TOKEN\" $BASE_URL/..."
else
  echo "❌ 获取 Token 失败，响应: $RESPONSE"
  return 1 2>/dev/null || exit 1
fi
