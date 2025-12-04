#!/usr/bin/env bash
# HiMarket MCP 统一初始化钩子
# 由 deploy.sh 在部署就绪后自动调用
# 依赖: 40-init-himarket-register.sh (管理员账号已注册)
# 继承环境变量: NS, HIGRESS_PASSWORD, ADMIN_USERNAME, ADMIN_PASSWORD

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="${SCRIPT_DIR}/../../data"
HIGRESS_MCP_CONFIG="${DATA_DIR}/higress-mcp.json"
NACOS_MCP_CONFIG="${DATA_DIR}/nacos-mcp.json"

# 从 .env 加载环境变量
if [[ -f "${DATA_DIR}/.env" ]]; then
  set -a
  . "${DATA_DIR}/.env"
  set +a
fi

NS="${NAMESPACE:-himarket}"

# 默认登录凭据
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin}"
HIGRESS_PASSWORD="${HIGRESS_PASSWORD:-admin}"

# 最大重试次数
MAX_RETRIES=3

log() { echo "[init-himarket-mcp $(date +'%H:%M:%S')] $*"; }
err() { echo "[ERROR] $*" >&2; }

# 全局变量
AUTH_TOKEN=""
HIMARKET_HOST=""
FRONTEND_HOST=""

# 缓存 ID 映射 (使用普通变量兼容 Bash 3.2)
# 格式: PORTAL_ID_MAP_<name>=<id>
# 格式: GATEWAY_ID_MAP_<name>=<id>

########################################
# 检查依赖
########################################
check_dependencies() {
  if ! command -v jq >/dev/null 2>&1; then
    err "未找到 jq 命令，请先安装 jq"
    exit 1
  fi

  if [ ! -f "$HIGRESS_MCP_CONFIG" ]; then
    log "警告: Higress MCP 配置文件不存在: $HIGRESS_MCP_CONFIG"
  fi

  if [ ! -f "$NACOS_MCP_CONFIG" ]; then
    log "警告: Nacos MCP 配置文件不存在: $NACOS_MCP_CONFIG"
  fi
}

########################################
# 获取 HiMarket Admin Service 地址
########################################
get_himarket_admin_host() {
  log "获取 HiMarket Admin Service 地址..." >&2

  local host=$(kubectl get svc himarket-admin -n "${NS}" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")

  if [[ -z "$host" ]]; then
    log "未检测到 LoadBalancer IP，使用 ClusterIP 模式" >&2
    host="himarket-admin.${NS}.svc.cluster.local"
  fi

  log "HiMarket Admin 地址: ${host}" >&2
  echo "$host"
}

########################################
# 获取 HiMarket Frontend Service 地址
########################################
get_himarket_frontend_host() {
  log "获取 HiMarket Frontend Service 地址..." >&2

  local host
  host=$(kubectl get svc himarket-frontend -n "${NS}" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")

  if [[ -z "$host" ]]; then
    log "未检测到 LoadBalancer IP，使用 ClusterIP 模式" >&2
    host="himarket-frontend.${NS}.svc.cluster.local"
  fi

  log "HiMarket Frontend 地址: ${host}" >&2
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

  local result
  result=$(eval "$curl_cmd" 2>&1 || echo "HTTP_CODE:000")

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
# 从 JSON 响应中提取字段值
########################################
extract_json_field() {
  local json="$1"
  local field="$2"

  if command -v jq >/dev/null 2>&1; then
    echo "$json" | jq -r ".${field} // empty" 2>/dev/null || echo ""
  else
    echo "$json" | sed -n "s/.*\"${field}\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" | head -1
  fi
}

########################################
# 登录获取 Token
########################################
login_admin() {
  local body="{\"username\":\"${ADMIN_USERNAME}\",\"password\":\"${ADMIN_PASSWORD}\"}"

  local attempt=1
  while (( attempt <= MAX_RETRIES )); do
    log "执行登录 (第 ${attempt}/${MAX_RETRIES} 次)..."

    if call_api "管理员登录" "POST" "/api/v1/admins/login" "$body"; then
      # 从响应体中提取 token，尝试多种可能的字段名
      AUTH_TOKEN=$(echo "$API_RESPONSE" | grep -o '"access_token":"[^"]*"' | head -1 | sed 's/"access_token":"//' | sed 's/"//' || echo "")

      if [[ -z "$AUTH_TOKEN" ]]; then
        AUTH_TOKEN=$(echo "$API_RESPONSE" | grep -o '"token":"[^"]*"' | head -1 | sed 's/"token":"//' | sed 's/"//' || echo "")
      fi

      if [[ -z "$AUTH_TOKEN" ]]; then
        AUTH_TOKEN=$(echo "$API_RESPONSE" | grep -o '"accessToken":"[^"]*"' | head -1 | sed 's/"accessToken":"//' | sed 's/"//' || echo "")
      fi

      if [[ -z "$AUTH_TOKEN" ]]; then
        AUTH_TOKEN=$(echo "$API_RESPONSE" | sed -n 's/.*"access_token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' || echo "")
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
# 获取或创建 Gateway ID
########################################
get_or_create_gateway() {
  local gateway_name="higress-demo"
  local cache_var="GATEWAY_ID_MAP_${gateway_name//-/_}"

  # 先尝试从缓存获取
  local cached_id
  cached_id=$(eval "echo \$${cache_var}" 2>/dev/null || echo "")
  if [[ -n "$cached_id" ]]; then
    echo "$cached_id"
    return 0
  fi

  # 尝试创建
  local body="{\"gatewayName\":\"${gateway_name}\",\"gatewayType\":\"HIGRESS\",\"higressConfig\":{\"address\":\"http://higress-console:8080\",\"username\":\"admin\",\"password\":\"${HIGRESS_PASSWORD}\"}}"

  call_api "插入网关" "POST" "/api/v1/gateways" "$body" >/dev/null 2>&1 || true

  # 查询获取 ID（重定向日志输出）
  call_api "查询网关列表" "GET" "/api/v1/gateways" "" >/dev/null 2>&1

  # 从响应中提取 Gateway ID
  local gw_id
  gw_id=$(echo "$API_RESPONSE" | jq -r '.data.content[]? // .[]? | select(.gatewayName=="'"${gateway_name}"'") | .gatewayId' 2>/dev/null | head -1 || echo "")

  if [[ -n "$gw_id" ]]; then
    eval "${cache_var}='$gw_id'"
    echo "$gw_id"
    return 0
  fi

  return 1
}

########################################
# 获取或创建 Nacos ID
########################################
get_or_create_nacos() {
  # 尝试创建
  local body='{"nacosName":"nacos-demo","serverUrl":"http://nacos:8848","username":"nacos","password":"nacos"}'

  call_api "插入Nacos" "POST" "/api/v1/nacos" "$body" >/dev/null 2>&1 || true

  # 查询获取 ID
  call_api "查询Nacos列表" "GET" "/api/v1/nacos" "" >/dev/null 2>&1

  # 从响应中提取 Nacos ID
  local nacos_id
  nacos_id=$(echo "$API_RESPONSE" | jq -r '.data.content[]? // .[]? | select(.nacosName=="nacos-demo") | .nacosId' 2>/dev/null | head -1 || echo "")

  if [[ -n "$nacos_id" ]]; then
    echo "$nacos_id"
    return 0
  fi

  return 1
}

########################################
# 获取或创建 Portal ID
########################################
get_or_create_portal() {
  local portal_name="$1"
  local cache_var="PORTAL_ID_MAP_${portal_name//-/_}"

  # 先尝试从缓存获取
  local cached_id
  cached_id=$(eval "echo \$${cache_var}" 2>/dev/null || echo "")
  if [[ -n "$cached_id" ]]; then
    echo "$cached_id"
    return 0
  fi

  # 尝试创建
  local body="{\"name\":\"${portal_name}\"}"

  call_api "插入Portal" "POST" "/api/v1/portals" "$body" >/dev/null 2>&1 || true

  # 查询获取 ID（重定向日志输出）
  call_api "查询Portal列表" "GET" "/api/v1/portals" "" >/dev/null 2>&1

  # 从响应中提取 Portal ID（支持新的数据结构）
  local p_id
  p_id=$(echo "$API_RESPONSE" | jq -r '.data.content[]? // .[]? | select(.name=="'"${portal_name}"'") | .portalId' 2>/dev/null | head -1 || echo "")

  if [[ -n "$p_id" ]]; then
    eval "${cache_var}='$p_id'"
    echo "$p_id"
    return 0
  fi

  return 1
}

########################################
# 获取或创建 Product ID
########################################
get_or_create_product() {
  local product_name="$1"
  local description="$2"
  local type="$3"

  # 尝试创建
  local body="{\"name\":\"${product_name}\",\"description\":\"${description}\",\"type\":\"${type}\"}"

  call_api "插入产品" "POST" "/api/v1/products" "$body" >/dev/null 2>&1 || true

  # 查询获取 ID（重定向日志输出）
  call_api "查询产品列表" "GET" "/api/v1/products" "" >/dev/null 2>&1

  # 从响应中提取 Product ID
  local prod_id
  prod_id=$(echo "$API_RESPONSE" | jq -r '.data.content[]? // .[]? | select(.name=="'"${product_name}"'") | .productId' 2>/dev/null | head -1 || echo "")

  if [[ -n "$prod_id" ]]; then
    echo "$prod_id"
    return 0
  fi

  return 1
}

########################################
# 关联产品到 Nacos MCP
########################################
link_product_to_nacos() {
  local product_id="$1"
  local nacos_id="$2"
  local mcp_name="$3"
  local namespace_id="${4:-public}"

  # 构造正确的 type 字段
  local ref_type="MCP Server (${namespace_id})"

  local body="{\"nacosId\":\"${nacos_id}\",\"sourceType\":\"NACOS\",\"productId\":\"${product_id}\",\"nacosRefConfig\":{\"mcpServerName\":\"${mcp_name}\",\"fromGatewayType\":\"NACOS\",\"type\":\"${ref_type}\",\"namespaceId\":\"${namespace_id}\"}}"

  if call_api "关联产品到Nacos" "POST" "/api/v1/products/${product_id}/ref" "$body"; then
    if [[ "$API_HTTP_CODE" =~ ^2[0-9]{2}$ ]]; then
      log "[${mcp_name}] 产品关联到 Nacos 成功"
      return 0
    elif [[ "$API_HTTP_CODE" == "409" ]]; then
      log "[${mcp_name}] 产品已关联到 Nacos（跳过）"
      return 0
    else
      err "[${mcp_name}] 产品关联到 Nacos 失败: HTTP ${API_HTTP_CODE}"
      err "响应内容: ${API_RESPONSE}"
      return 1
    fi
  else
    err "[${mcp_name}] 产品关联到 Nacos API 调用失败"
    return 1
  fi
}

########################################
# 关联产品到 Gateway
########################################
link_product_to_gateway() {
  local product_id="$1"
  local gateway_id="$2"
  local mcp_name="$3"
  local mcp_type="$4"

  local ref_type="MCP Server"

  local body="{\"gatewayId\":\"${gateway_id}\",\"sourceType\":\"GATEWAY\",\"productId\":\"${product_id}\",\"higressRefConfig\":{\"mcpServerName\":\"${mcp_name}\",\"fromGatewayType\":\"HIGRESS\",\"type\":\"${ref_type}\"}}"

  if call_api "关联产品" "POST" "/api/v1/products/${product_id}/ref" "$body"; then
    log "[${mcp_name}] 产品关联成功"
    return 0
  else
    log "[${mcp_name}] 产品关联失败（可能已关联）"
    return 0  # 允许失败，继续执行
  fi
}

########################################
# 发布产品到 Portal
########################################
publish_to_portal() {
  local product_id="$1"
  local portal_id="$2"
  local mcp_name="$3"

  if call_api "发布到门户" "POST" "/api/v1/products/${product_id}/publications/${portal_id}" ""; then
    log "[${mcp_name}] 发布到门户成功"
    return 0
  else
    log "[${mcp_name}] 发布到门户失败（可能已发布）"
    return 0  # 允许失败，继续执行
  fi
}

########################################
# 绑定域名到 Portal
########################################
bind_domain_to_portal() {
  local portal_id="$1"
  local portal_name="$2"

  local body="{\"domain\":\"${FRONTEND_HOST}\",\"type\":\"CUSTOM\",\"protocol\":\"HTTP\"}"

  log "[Portal: ${portal_name}] 开始绑定域名: ${FRONTEND_HOST}"
  log "[Portal: ${portal_name}] Portal ID: ${portal_id}"
  log "[Portal: ${portal_name}] 请求体: ${body}"

  if call_api "绑定域名" "POST" "/api/v1/portals/${portal_id}/domains" "$body"; then
    log "[Portal: ${portal_name}] API 调用成功，HTTP Code: ${API_HTTP_CODE}"

    if [[ "$API_HTTP_CODE" == "200" ]] || [[ "$API_HTTP_CODE" == "201" ]]; then
      log "[Portal: ${portal_name}] 域名绑定成功"
      return 0
    elif [[ "$API_HTTP_CODE" == "409" ]]; then
      log "[Portal: ${portal_name}] 域名已存在（跳过）"
      return 0
    else
      err "[Portal: ${portal_name}] 未预期的 HTTP 状态码: ${API_HTTP_CODE}"
      err "响应内容: ${API_RESPONSE}"
      return 1
    fi
  else
    err "[Portal: ${portal_name}] API 调用失败"
    err "HTTP 状态码: ${API_HTTP_CODE}"
    err "请求体: ${body}"
    err "响应内容: ${API_RESPONSE}"
    err "前台域名: ${FRONTEND_HOST}"
    err "Portal ID: ${portal_id}"
    return 1
  fi
}

########################################
# 处理单个 Higress MCP 的 HiMarket 配置
########################################
process_single_higress_mcp() {
  local mcp_config="$1"

  local mcp_name=$(echo "$mcp_config" | jq -r '.name')
  local mcp_type=$(echo "$mcp_config" | jq -r '.type')

  # 检查是否需要在 HiMarket 中配置
  local himarket_config=$(echo "$mcp_config" | jq -r '.himarket // empty')
  if [[ -z "$himarket_config" ]] || [[ "$himarket_config" == "null" ]]; then
    log "[${mcp_name}] 跳过 HiMarket 配置（未定义）"
    return 0
  fi

  log "========================================"
  log "处理 Higress MCP: ${mcp_name}"
  log "========================================"

  # 提取 HiMarket 配置
  local product_name=$(echo "$mcp_config" | jq -r '.himarket.product.name')
  local product_desc=$(echo "$mcp_config" | jq -r '.himarket.product.description')
  local product_type=$(echo "$mcp_config" | jq -r '.himarket.product.type')
  local publish_to_portal=$(echo "$mcp_config" | jq -r '.himarket.publishToPortal')
  local portal_name=$(echo "$mcp_config" | jq -r '.himarket.portalName // "demo"')

  # 1. 创建产品
  log "[${mcp_name}] 创建产品..."
  local product_id=$(get_or_create_product "$product_name" "$product_desc" "$product_type")

  if [[ -z "$product_id" ]]; then
    err "[${mcp_name}] 无法获取产品 ID"
    return 1
  fi

  log "[${mcp_name}] Product ID: ${product_id}"

  # 2. 获取 Gateway ID
  log "[${mcp_name}] 获取网关 ID..."
  local gateway_id=$(get_or_create_gateway)

  if [[ -z "$gateway_id" ]]; then
    err "[${mcp_name}] 无法获取网关 ID"
    return 1
  fi

  log "[${mcp_name}] Gateway ID: ${gateway_id}"

  # 3. 关联产品到网关
  log "[${mcp_name}] 关联产品到网关..."
  link_product_to_gateway "$product_id" "$gateway_id" "$mcp_name" "$mcp_type"

  # 4. 如果需要发布到 Portal
  if [[ "$publish_to_portal" == "true" ]]; then
    log "[${mcp_name}] 获取或创建 Portal..."
    local portal_id=$(get_or_create_portal "$portal_name")

    if [[ -z "$portal_id" ]]; then
      err "[${mcp_name}] 无法获取 Portal ID"
      return 1
    fi

    log "[${mcp_name}] Portal ID: ${portal_id}"

    # 发布产品到 Portal
    log "[${mcp_name}] 发布到 Portal..."
    publish_to_portal "$product_id" "$portal_id" "$mcp_name"

    # 绑定域名（每个 Portal 只绑定一次）
    local domain_bound_var="PORTAL_ID_MAP_${portal_name//-/_}_domain_bound"
    local is_bound=$(eval "echo \$${domain_bound_var}" 2>/dev/null || echo "")
    if [[ -z "$is_bound" ]]; then
      log "[Portal: ${portal_name}] 绑定前台域名..."
      if bind_domain_to_portal "$portal_id" "$portal_name"; then
        eval "${domain_bound_var}='true'"
      else
        err "[Portal: ${portal_name}] 域名绑定失败，但继续执行"
      fi
    fi
  fi

  log "[${mcp_name}] Higress MCP 配置完成"
  return 0
}

########################################
# 处理单个 Nacos MCP 的 HiMarket 配置
########################################
process_single_nacos_mcp() {
  local mcp_config="$1"

  # 从 serverSpecification.name 提取 MCP 名称
  local mcp_name=$(echo "$mcp_config" | jq -r '.serverSpecification.name // .name')

  # 检查是否需要在 HiMarket 中配置
  local himarket_config=$(echo "$mcp_config" | jq -r '.himarket // empty')
  if [[ -z "$himarket_config" ]] || [[ "$himarket_config" == "null" ]]; then
    log "[${mcp_name}] 跳过 HiMarket 配置（未定义）"
    return 0
  fi

  log "=========================================="
  log "处理 Nacos MCP: ${mcp_name}"
  log "=========================================="

  # 提取 HiMarket 配置
  local product_name=$(echo "$mcp_config" | jq -r '.himarket.product.name')
  local product_desc=$(echo "$mcp_config" | jq -r '.himarket.product.description')
  local product_type=$(echo "$mcp_config" | jq -r '.himarket.product.type')
  local publish_to_portal=$(echo "$mcp_config" | jq -r '.himarket.publishToPortal')
  local portal_name=$(echo "$mcp_config" | jq -r '.himarket.portalName // "demo"')
  local namespace_id=$(echo "$mcp_config" | jq -r '.himarket.namespaceId // "public"')

  # 1. 创建产品
  log "[${mcp_name}] 创建产品..."
  local product_id=$(get_or_create_product "$product_name" "$product_desc" "$product_type")

  if [[ -z "$product_id" ]]; then
    err "[${mcp_name}] 无法获取产品 ID"
    return 1
  fi

  log "[${mcp_name}] Product ID: ${product_id}"

  # 2. 获取 Nacos ID
  log "[${mcp_name}] 获取 Nacos ID..."
  local nacos_id=$(get_or_create_nacos)

  if [[ -z "$nacos_id" ]]; then
    err "[${mcp_name}] 无法获取 Nacos ID"
    return 1
  fi

  log "[${mcp_name}] Nacos ID: ${nacos_id}"

  # 3. 关联产品到 Nacos（必须成功才能继续）
  log "[${mcp_name}] 关联产品到 Nacos..."
  if ! link_product_to_nacos "$product_id" "$nacos_id" "$mcp_name" "$namespace_id"; then
    err "[${mcp_name}] 产品关联失败，无法继续发布"
    return 1
  fi

  # 4. 如果需要发布到 Portal
  if [[ "$publish_to_portal" == "true" ]]; then
    log "[${mcp_name}] 获取或创建 Portal..."
    local portal_id=$(get_or_create_portal "$portal_name")

    if [[ -z "$portal_id" ]]; then
      err "[${mcp_name}] 无法获取 Portal ID"
      return 1
    fi

    log "[${mcp_name}] Portal ID: ${portal_id}"

    # 发布产品到 Portal
    log "[${mcp_name}] 发布到 Portal..."
    publish_to_portal "$product_id" "$portal_id" "$mcp_name"

    # 绑定域名（每个 Portal 只绑定一次）
    local domain_bound_var="PORTAL_ID_MAP_${portal_name//-/_}_domain_bound"
    local is_bound=$(eval "echo \$${domain_bound_var}" 2>/dev/null || echo "")
    if [[ -z "$is_bound" ]]; then
      log "[Portal: ${portal_name}] 绑定前台域名..."
      if bind_domain_to_portal "$portal_id" "$portal_name"; then
        eval "${domain_bound_var}='true'"
      else
        err "[Portal: ${portal_name}] 域名绑定失败，但继续执行"
      fi
    fi
  fi

  log "[${mcp_name}] Nacos MCP 配置完成"
  return 0
}

########################################
# 主流程
########################################
main() {
  log "开始初始化 HiMarket MCP 配置..."

  # 检查依赖
  check_dependencies

  # 获取服务地址
  HIMARKET_HOST=$(get_himarket_admin_host)
  FRONTEND_HOST=$(get_himarket_frontend_host)

  # 登录
  if ! login_admin; then
    err "登录失败，无法继续"
    exit 1
  fi

  # 确保 Gateway 和 Nacos 存在
  log "初始化基础设施..."

  # 根据配置决定是否注册开源 Nacos
  local use_commercial_nacos="${USE_COMMERCIAL_NACOS:-false}"
  if [[ "$use_commercial_nacos" != "true" ]]; then
    log "使用开源 Nacos，注册到 HiMarket..."
    get_or_create_nacos >/dev/null 2>&1 || true
  else
    log "使用商业化 Nacos，跳过开源 Nacos 注册"
  fi

  get_or_create_gateway >/dev/null 2>&1 || true

  # 读取 Higress MCP 配置列表
  local higress_mcp_count=0
  if [ -f "$HIGRESS_MCP_CONFIG" ]; then
    higress_mcp_count=$(jq '. | length' "$HIGRESS_MCP_CONFIG")
    log "检测到 ${higress_mcp_count} 个 Higress MCP 配置"
  fi

  # 读取 Nacos MCP 配置列表
  local nacos_mcp_count=0
  if [ -f "$NACOS_MCP_CONFIG" ]; then
    nacos_mcp_count=$(jq '. | length' "$NACOS_MCP_CONFIG")
    log "检测到 ${nacos_mcp_count} 个 Nacos MCP 配置"
  fi

  local success_count=0
  local failed_count=0
  local failed_list=""

  # 遍历处理每个 Higress MCP
  if [ "$higress_mcp_count" -gt 0 ]; then
    for i in $(seq 0 $((higress_mcp_count - 1))); do
      local mcp_config=$(jq ".[$i]" "$HIGRESS_MCP_CONFIG")
      local mcp_name=$(echo "$mcp_config" | jq -r '.name')

      if process_single_higress_mcp "$mcp_config"; then
        success_count=$((success_count + 1))
      else
        failed_count=$((failed_count + 1))
        failed_list="${failed_list}  - ${mcp_name} (Higress)\n"
      fi
    done
  fi

  # 遍历处理每个 Nacos MCP
  if [ "$nacos_mcp_count" -gt 0 ]; then
    for i in $(seq 0 $((nacos_mcp_count - 1))); do
      local mcp_config=$(jq ".[$i]" "$NACOS_MCP_CONFIG")
      local mcp_name=$(echo "$mcp_config" | jq -r '.serverSpecification.name // .name')

      if process_single_nacos_mcp "$mcp_config"; then
        success_count=$((success_count + 1))
      else
        failed_count=$((failed_count + 1))
        failed_list="${failed_list}  - ${mcp_name} (Nacos)\n"
      fi
    done
  fi

  log "========================================"
  log "HiMarket MCP 初始化完成报告"
  log "========================================"
  log "总计: $((higress_mcp_count + nacos_mcp_count)) 个 MCP (Higress: ${higress_mcp_count}, Nacos: ${nacos_mcp_count})"
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
  log "HiMarket Admin: http://${HIMARKET_HOST}"
  log "HiMarket Frontend: http://${FRONTEND_HOST}"
  log "========================================"
}

main "$@"
