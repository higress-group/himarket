#!/usr/bin/env bash
# Nacos MCP 数据导入钩子 (Docker 环境)
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

# 待导入的 MCP 数据文件列表
MCP_FILES=(
  "${DATA_DIR}/nacos-mcp.json"
)

# Nacos 默认用户名密码
NACOS_USERNAME="${NACOS_USERNAME:-nacos}"
NACOS_PASSWORD="${NACOS_PASSWORD:-nacos}"

# 固定配置
NAMESPACE_ID="public"
OVERRIDE_EXISTING="true"
VALIDATE_ONLY="false"
SKIP_INVALID="true"

log() { echo "[import-mcp $(date +'%H:%M:%S')] $*"; }
err() { echo "[ERROR] $*" >&2; }

########################################
# 1. 检查是否有文件需要导入
########################################
FILES_EXIST=false
for file in "${MCP_FILES[@]}"; do
  if [ -f "$file" ]; then
    FILES_EXIST=true
    log "检测到 MCP 数据文件: $file"
  fi
done

if [ "$FILES_EXIST" = false ]; then
  log "警告: 未找到任何 MCP 数据文件"
  log "跳过 Nacos MCP 数据导入"
  exit 0
fi

########################################
# 2. Docker 环境使用 localhost
########################################
HOST="localhost"
log "Nacos Service 地址: ${HOST}:8848"

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

log "登录成功,accessToken = $ACCESS_TOKEN"

########################################
# 3. 定义导入单个文件的函数
########################################
import_mcp_file() {
  local LOCAL_FILE="$1"
  
  if [ ! -f "$LOCAL_FILE" ]; then
    log "跳过不存在的文件: $LOCAL_FILE"
    return 0
  fi
  
  log "==========================================="
  log "开始导入文件: $LOCAL_FILE"
  log "==========================================="
  
  log "读取并转义本地文件内容为表单 data 字段..."
  
  # 读入文件,然后做:
  # - \  -> \\
  # - "  -> \"
  # - 将换行转换为 \n(避免直接出现在 x-www-form-urlencoded 里)
  ESCAPED_CONTENT=$(
    sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e ':a;N;$!ba;s/\n/\\n/g' "$LOCAL_FILE"
  )
  
  # 做 URL 编码(python3/python 二选一)
  if command -v python3 >/dev/null 2>&1; then
    DATA_FIELD=$(python3 - <<EOF
import urllib.parse
s = """$ESCAPED_CONTENT"""
print(urllib.parse.quote_plus(s))
EOF
)
  elif command -v python >/dev/null 2>&1; then
    DATA_FIELD=$(python - <<EOF
import urllib
s = """$ESCAPED_CONTENT"""
print(urllib.quote_plus(s))
EOF
)
  else
    err "当前环境没有 python3/python,无法安全做 URL 编码。"
    err "请先安装 python3 或告诉我,我可以给你一个纯 shell 简化版(对特殊字符要求不高时可用)。"
    exit 1
  fi
  
  # 其余字段做简单 URL 编码(只处理空格 -> %20,按当前场景一般够用)
  ENC_NS=$(printf '%s' "$NAMESPACE_ID" | sed 's/ /%20/g')
  ENC_OVERRIDE=$(printf '%s' "$OVERRIDE_EXISTING" | sed 's/ /%20/g')
  ENC_VALIDATE=$(printf '%s' "$VALIDATE_ONLY" | sed 's/ /%20/g')
  ENC_SKIP=$(printf '%s' "$SKIP_INVALID" | sed 's/ /%20/g')
  
  # 拼出 x-www-form-urlencoded 的 body
  FORM_BODY="namespaceId=${ENC_NS}&importType=file&data=${DATA_FIELD}&overrideExisting=${ENC_OVERRIDE}&validateOnly=${ENC_VALIDATE}&skipInvalid=${ENC_SKIP}"
  
  BODY_FILE="$TMP_DIR/body"
  echo "$FORM_BODY" > "$BODY_FILE"
  
  log "调用 MCP 导入接口(application/x-www-form-urlencoded)..."
  IMPORT_URL="http://${HOST}:8080/v3/console/ai/mcp/import/execute"
  
  RESP=$(curl -sS -w "\nHTTP_STATUS:%{http_code}\n" -X POST "$IMPORT_URL" \
    -H "accessToken: $ACCESS_TOKEN" \
    -H "Content-Type: application/x-www-form-urlencoded; charset=UTF-8" \
    --data @"$BODY_FILE")
  
  HTTP_STATUS=$(echo "$RESP" | sed -n 's/^HTTP_STATUS:\(.*\)$/\1/p')
  BODY=$(echo "$RESP" | sed '/HTTP_STATUS:/d')
  
  log "接口返回:"
  log "$BODY"
  log ""
  log "HTTP 状态码: $HTTP_STATUS"
  
  if [ "$HTTP_STATUS" != "200" ]; then
    err "导入文件失败(HTTP $HTTP_STATUS): $LOCAL_FILE"
    err "请检查上面的返回信息。"
    return 1
  fi
  
  # 检查响应体中的业务状态
  # 如果 success=false 但错误信息包含 "has existed"，视为成功（幂等性）
  if echo "$BODY" | grep -q '"success"[[:space:]]*:[[:space:]]*false'; then
    if echo "$BODY" | grep -q 'has existed'; then
      log "⚠ MCP server 已存在，视为成功（幂等性）"
      log "✓ 文件导入成功: $LOCAL_FILE"
      return 0
    else
      err "导入文件失败: $LOCAL_FILE"
      err "业务返回 success=false 且非 'has existed' 错误"
      return 1
    fi
  fi
  
  log "✓ 文件导入成功: $LOCAL_FILE"
  return 0
}

########################################
# 4. 循环导入所有 MCP 文件
########################################
SUCCESS_COUNT=0
FAIL_COUNT=0

for file in "${MCP_FILES[@]}"; do
  if import_mcp_file "$file"; then
    SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
  else
    FAIL_COUNT=$((FAIL_COUNT + 1))
  fi
done

log "==========================================="
log "导入完成: 成功 $SUCCESS_COUNT 个, 失败 $FAIL_COUNT 个"
log "==========================================="

if [ $FAIL_COUNT -gt 0 ]; then
  err "部分文件导入失败,请检查日志"
  exit 1
fi

log "所有 MCP 数据文件导入完成。"
