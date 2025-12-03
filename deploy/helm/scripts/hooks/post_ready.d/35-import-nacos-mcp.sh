#!/usr/bin/env bash
# Nacos MCP 数据导入钩子
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
LOCAL_FILE="${DATA_DIR}/nacos-mcp.json"

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
# 1. 检查 MCP 数据文件
########################################
if [ ! -f "$LOCAL_FILE" ]; then
  err "MCP 数据文件不存在: $LOCAL_FILE"
  exit 1
fi

log "检测到 MCP 数据文件: $LOCAL_FILE"

########################################
# 2. 获取 Nacos Service 地址（动态信息）
########################################
log "获取 Nacos Service 地址..."

# 获取 Nacos Service LoadBalancer IP
HOST=$(kubectl get svc nacos -n "${NS}" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")

log "Nacos Service 地址: ${HOST}"

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
  err "登录失败，未能从响应中解析 accessToken。原始响应："
  err "$LOGIN_RESP"
  exit 1
fi

log "登录成功，accessToken = $ACCESS_TOKEN"

log "读取并转义本地文件内容为表单 data 字段: $LOCAL_FILE"

# 读入文件，然后做：
# - \  -> \\
# - "  -> \"
# - 将换行转换为 \n（避免直接出现在 x-www-form-urlencoded 里）
ESCAPED_CONTENT=$(
  sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e ':a;N;$!ba;s/\n/\\n/g' "$LOCAL_FILE"
)

# 做 URL 编码（python3/python 二选一）
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
  err "当前环境没有 python3/python，无法安全做 URL 编码。"
  err "请先安装 python3 或告诉我，我可以给你一个纯 shell 简化版（对特殊字符要求不高时可用）。"
  exit 1
fi

# 其余字段做简单 URL 编码（只处理空格 -> %20，按当前场景一般够用）
ENC_NS=$(printf '%s' "$NAMESPACE_ID" | sed 's/ /%20/g')
ENC_OVERRIDE=$(printf '%s' "$OVERRIDE_EXISTING" | sed 's/ /%20/g')
ENC_VALIDATE=$(printf '%s' "$VALIDATE_ONLY" | sed 's/ /%20/g')
ENC_SKIP=$(printf '%s' "$SKIP_INVALID" | sed 's/ /%20/g')

# 拼出 x-www-form-urlencoded 的 body
FORM_BODY="namespaceId=${ENC_NS}&importType=file&data=${DATA_FIELD}&overrideExisting=${ENC_OVERRIDE}&validateOnly=${ENC_VALIDATE}&skipInvalid=${ENC_SKIP}"

BODY_FILE="$TMP_DIR/body"
echo "$FORM_BODY" > "$BODY_FILE"

log "调用 MCP 导入接口（application/x-www-form-urlencoded）..."
IMPORT_URL="http://${HOST}:8080/v3/console/ai/mcp/import/execute"

RESP=$(curl -sS -w "\nHTTP_STATUS:%{http_code}\n" -X POST "$IMPORT_URL" \
  -H "accessToken: $ACCESS_TOKEN" \
  -H "Content-Type: application/x-www-form-urlencoded; charset=UTF-8" \
  --data @"$BODY_FILE")

HTTP_STATUS=$(echo "$RESP" | sed -n 's/^HTTP_STATUS:\(.*\)$/\1/p')
BODY=$(echo "$RESP" | sed '/HTTP_STATUS:/d')

log "接口返回："
log "$BODY"
log ""
log "HTTP 状态码: $HTTP_STATUS"

if [ "$HTTP_STATUS" != "200" ]; then
  err "导入失败（HTTP $HTTP_STATUS），请检查上面的返回信息。"
  exit 1
fi

log "导入请求已发送完成。"
