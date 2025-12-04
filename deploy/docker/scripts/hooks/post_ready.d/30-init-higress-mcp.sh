#!/usr/bin/env bash
# Higress MCP 统一配置初始化钩子 (Docker 环境)
# 由 deploy.sh 在部署就绪后自动调用
# 
# 功能：根据 higress-mcp.json 配置，批量初始化所有 MCP 服务

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="${SCRIPT_DIR}/../../data"
MCP_CONFIG="${DATA_DIR}/higress-mcp.json"

# 从 .env 读取 Higress 密码与其他配置
if [[ -f "${DATA_DIR}/.env" ]]; then
  set -a
  . "${DATA_DIR}/.env"
  set +a
fi

HIGRESS_PASSWORD="${HIGRESS_PASSWORD:-admin}"

# 全局变量：会话 Cookie
SESSION_COOKIE=""
HIGRESS_HOST="localhost:8001"

log() { echo "[init-higress-mcp $(date +'%H:%M:%S')] $*"; }
err() { echo "[ERROR] $*" >&2; }

########################################
# 检查依赖
########################################
check_dependencies() {
  if ! command -v jq >/dev/null 2>&1; then
    err "未找到 jq 命令，请先安装 jq"
    err "macOS: brew install jq"
    err "Linux: apt-get install jq 或 yum install jq"
    exit 1
  fi
  
  if [ ! -f "$MCP_CONFIG" ]; then
    err "MCP 配置文件不存在: $MCP_CONFIG"
    exit 1
  fi
  
  log "配置文件: $MCP_CONFIG"
}

########################################
# 登录 Higress Console 获取会话
########################################
login_higress() {
  log "登录 Higress Console..."
  
  local login_url="http://${HIGRESS_HOST}/session/login"
  local login_data='{"username":"admin","password":"'"${HIGRESS_PASSWORD}"'"}'
  local max_attempts=3
  local attempt=1
  
  while (( attempt <= max_attempts )); do
    log "尝试登录 (第 ${attempt}/${max_attempts} 次)..."
    
    local result=$(curl -sS -i -X POST "${login_url}" \
      -H "Accept: application/json, text/plain, */*" \
      -H "Content-Type: application/json" \
      --data-raw "${login_data}" \
      --connect-timeout 5 --max-time 10 2>/dev/null || echo "")
    
    local http_code=$(echo "$result" | grep -i "^HTTP/" | tail -1 | awk '{print $2}')
    local cookie_value=$(echo "$result" | grep -i "^Set-Cookie:" | grep "_hi_sess=" | sed 's/.*_hi_sess=\([^;]*\).*/\1/' | head -1)
    
    log "登录 API 返回: HTTP ${http_code}"
    
    if [[ "$http_code" =~ ^2[0-9]{2}$ ]]; then
      if [[ -n "$cookie_value" ]]; then
        SESSION_COOKIE="_hi_sess=${cookie_value}"
        log "登录成功，已获取会话 Cookie"
        return 0
      fi
    fi
    
    if [[ "$http_code" == "401" ]]; then
      err "登录失败：用户名或密码错误"
      return 1
    fi
    
    if (( attempt < max_attempts )); then
      log "登录失败 (HTTP ${http_code})，等待 5 秒后重试..."
      sleep 5
    fi
    
    attempt=$((attempt+1))
  done
  
  err "登录失败，已达最大重试次数"
  return 1
}

########################################
# 通用 API 调用函数（支持幂等性）
########################################
call_higress_api() {
  local method="$1"
  local path="$2"
  local data="$3"
  local desc="$4"
  
  local url="http://${HIGRESS_HOST}${path}"
  local max_attempts=3
  local attempt=1
  
  log "${desc}..."
  
  while (( attempt <= max_attempts )); do
    log "尝试调用 API (第 ${attempt}/${max_attempts} 次)..."
    
    local result=$(curl -sS -w "\nHTTP_CODE:%{http_code}" -X "${method}" "${url}" \
      -H "Accept: application/json, text/plain, */*" \
      -H "Content-Type: application/json" \
      -b "${SESSION_COOKIE}" \
      --data-raw "${data}" \
      --connect-timeout 5 --max-time 15 2>/dev/null || echo "HTTP_CODE:000")
    
    if [[ "$result" =~ HTTP_CODE:([0-9]{3}) ]]; then
      local http_code="${BASH_REMATCH[1]}"
      local response=$(echo "$result" | sed '/HTTP_CODE:/d')
    else
      local http_code="000"
      local response="$result"
    fi
    
    log "API 返回: HTTP ${http_code}"
    
    if [[ "$http_code" =~ ^2[0-9]{2}$ ]]; then
      log "${desc} 成功"
      return 0
    fi
    
    if [[ "$http_code" == "409" ]] || \
       [[ "$response" == *"already exists"* ]] || \
       [[ "$response" == *"已存在"* ]]; then
      log "资源已存在（幂等），视为成功"
      return 0
    fi
    
    if [[ "$http_code" =~ ^4[0-9]{2}$ ]]; then
      err "${desc} 失败: HTTP ${http_code}"
      log "响应: ${response}"
      return 1
    fi
    
    if (( attempt < max_attempts )); then
      log "请求失败 (HTTP ${http_code})，等待 5 秒后重试..."
      sleep 5
    fi
    
    attempt=$((attempt+1))
  done
  
  err "${desc} 失败，已达最大重试次数"
  return 1
}

########################################
# 创建服务源
########################################
create_service_source() {
  local source_config="$1"
  local mcp_name="$2"
  
  local type=$(echo "$source_config" | jq -r '.type')
  local name=$(echo "$source_config" | jq -r '.name')
  local domain=$(echo "$source_config" | jq -r '.domain')
  local port=$(echo "$source_config" | jq -r '.port')
  local protocol=$(echo "$source_config" | jq -r '.protocol')
  
  local data=$(cat <<EOF
{
  "type": "${type}",
  "name": "${name}",
  "port": "${port}",
  "domainForEdit": "${domain}",
  "protocol": "${protocol}",
  "proxyName": "",
  "domain": "${domain}"
}
EOF
)
  
  call_higress_api "POST" "/v1/service-sources" "$data" "[${mcp_name}] 创建服务源 (${name})"
}

########################################
# 创建域名
########################################
create_domain() {
  local domain="$1"
  local enable_https="$2"
  local mcp_name="$3"
  
  local https_value="off"
  if [[ "$enable_https" == "true" ]]; then
    https_value="on"
  fi
  
  local data=$(cat <<EOF
{
  "name": "${domain}",
  "enableHttps": "${https_value}"
}
EOF
)
  
  call_higress_api "POST" "/v1/domains" "$data" "[${mcp_name}] 创建域名 (${domain})"
}

########################################
# 处理 OpenAPI 类型的 MCP
########################################
process_openapi_mcp() {
  local mcp_config="$1"
  local mcp_name=$(echo "$mcp_config" | jq -r '.name')
  local description=$(echo "$mcp_config" | jq -r '.description')
  local yaml_file=$(echo "$mcp_config" | jq -r '.openApiConfig.yamlFile')
  
  log "处理 OpenAPI MCP: ${mcp_name}"
  
  # 检查 YAML 文件是否存在
  local yaml_path="${DATA_DIR}/${yaml_file}"
  if [ ! -f "$yaml_path" ]; then
    err "YAML 文件不存在: $yaml_path"
    return 1
  fi
  
  # 直接读取 YAML 文件内容
  log "读取 ${yaml_file}..."
  local yaml_content=$(cat "${yaml_path}")
  
  # 转义 YAML 为 JSON 字符串
  local escaped_config=""
  if command -v python3 >/dev/null 2>&1; then
    escaped_config=$(python3 -c "import json, sys; print(json.dumps(sys.stdin.read()))" <<< "${yaml_content}")
  elif command -v python >/dev/null 2>&1; then
    escaped_config=$(python -c "import json, sys; print json.dumps(sys.stdin.read())" <<< "${yaml_content}")
  else
    err "需要 python3 或 python 来转义 YAML 内容"
    return 1
  fi
  
  # 构建请求数据
  local domains=$(echo "$mcp_config" | jq -c '.higress.domains')
  local services=$(echo "$mcp_config" | jq -c '.higress.services')
  local consumer_auth=$(echo "$mcp_config" | jq -c '.higress.consumerAuth')
  
  local data=$(cat <<EOF
{
  "id": null,
  "name": "${mcp_name}",
  "description": "${description}",
  "domains": ${domains},
  "services": ${services},
  "type": "OPEN_API",
  "consumerAuthInfo": ${consumer_auth},
  "rawConfigurations": ${escaped_config},
  "dbConfig": null,
  "dbType": null,
  "directRouteConfig": null,
  "mcpServerName": "${mcp_name}"
}
EOF
)
  
  call_higress_api "PUT" "/v1/mcpServer" "$data" "[${mcp_name}] 创建/更新 MCP 服务器配置"
}

########################################
# 处理 DIRECT_ROUTE 类型的 MCP
########################################
process_direct_route_mcp() {
  local mcp_config="$1"
  local mcp_name=$(echo "$mcp_config" | jq -r '.name')
  local description=$(echo "$mcp_config" | jq -r '.description')
  local route_path=$(echo "$mcp_config" | jq -r '.directRouteConfig.path')
  local transport_type=$(echo "$mcp_config" | jq -r '.directRouteConfig.transportType')
  
  log "处理 DIRECT_ROUTE MCP: ${mcp_name}"
  
  # 构建请求数据
  local domains=$(echo "$mcp_config" | jq -c '.higress.domains')
  local services=$(echo "$mcp_config" | jq -c '.higress.services')
  local consumer_auth=$(echo "$mcp_config" | jq -c '.higress.consumerAuth')
  
  local data=$(cat <<EOF
{
  "id": null,
  "name": "${mcp_name}",
  "description": "${description}",
  "domains": ${domains},
  "services": ${services},
  "type": "DIRECT_ROUTE",
  "consumerAuthInfo": ${consumer_auth},
  "rawConfigurations": null,
  "dbConfig": null,
  "dbType": null,
  "directRouteConfig": {
    "path": "${route_path}",
    "transportType": "${transport_type}"
  },
  "mcpServerName": "${mcp_name}"
}
EOF
)
  
  call_higress_api "PUT" "/v1/mcpServer" "$data" "[${mcp_name}] 创建/更新 MCP 服务器配置"
}

########################################
# 处理单个 MCP 配置
########################################
process_single_mcp() {
  local mcp_config="$1"
  
  local mcp_name=$(echo "$mcp_config" | jq -r '.name')
  local mcp_type=$(echo "$mcp_config" | jq -r '.type')
  
  log "========================================"
  log "开始处理 MCP: ${mcp_name} (类型: ${mcp_type})"
  log "========================================"
  
  # 1. 创建所有服务源
  local sources=$(echo "$mcp_config" | jq -c '.higress.serviceSources[]')
  while IFS= read -r source; do
    if ! create_service_source "$source" "$mcp_name"; then
      err "[${mcp_name}] 创建服务源失败"
      return 1
    fi
  done <<< "$sources"
  
  # 2. 创建所有域名
  local enable_https=$(echo "$mcp_config" | jq -r '.higress.enableHttps')
  local domains=$(echo "$mcp_config" | jq -r '.higress.domains[]')
  while IFS= read -r domain; do
    if ! create_domain "$domain" "$enable_https" "$mcp_name"; then
      err "[${mcp_name}] 创建域名失败"
      return 1
    fi
  done <<< "$domains"
  
  # 3. 根据类型创建 MCP 服务器配置
  if [[ "$mcp_type" == "OPEN_API" ]]; then
    if ! process_openapi_mcp "$mcp_config"; then
      err "[${mcp_name}] 创建 OpenAPI MCP 失败"
      return 1
    fi
  elif [[ "$mcp_type" == "DIRECT_ROUTE" ]]; then
    if ! process_direct_route_mcp "$mcp_config"; then
      err "[${mcp_name}] 创建 DIRECT_ROUTE MCP 失败"
      return 1
    fi
  else
    err "[${mcp_name}] 不支持的 MCP 类型: ${mcp_type}"
    return 1
  fi
  
  log "[${mcp_name}] MCP 配置完成"
  return 0
}

########################################
# 主流程
########################################
main() {
  log "开始初始化 Higress MCP 配置..."
  
  # 检查依赖
  check_dependencies
  
  # 登录
  if ! login_higress; then
    err "登录 Higress Console 失败，无法继续执行"
    exit 1
  fi
  
  # 读取 MCP 配置列表
  local mcp_count=$(jq '. | length' "$MCP_CONFIG")
  log "检测到 ${mcp_count} 个 MCP 配置"
  
  local success_count=0
  local failed_count=0
  local failed_list=""
  
  # 遍历处理每个 MCP
  for i in $(seq 0 $((mcp_count - 1))); do
    local mcp_config=$(jq ".[$i]" "$MCP_CONFIG")
    local mcp_name=$(echo "$mcp_config" | jq -r '.name')
    
    if process_single_mcp "$mcp_config"; then
      success_count=$((success_count + 1))
    else
      failed_count=$((failed_count + 1))
      failed_list="${failed_list}  - ${mcp_name}\n"
    fi
  done
  
  log "========================================"
  log "Higress MCP 初始化完成报告"
  log "========================================"
  log "总计: ${mcp_count} 个 MCP"
  log "成功: ${success_count} 个"
  log "失败: ${failed_count} 个"
  
  if [[ $failed_count -gt 0 ]]; then
    log ""
    log "失败的 MCP:"
    echo -e "$failed_list" | while IFS= read -r line; do
      [[ -n "$line" ]] && log "$line"
    done
    exit 1
  fi
  
  log "========================================"
  log "Higress Console 地址: http://${HIGRESS_HOST}"
  log "========================================"
}

main "$@"
