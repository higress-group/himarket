#!/usr/bin/env bash
# Himarket MCP 统一初始化钩子
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

# 商业化 Nacos 配置
USE_COMMERCIAL_NACOS="${USE_COMMERCIAL_NACOS:-false}"
COMMERCIAL_NACOS_NAME="${COMMERCIAL_NACOS_NAME:-}"
COMMERCIAL_NACOS_SERVER_URL="${COMMERCIAL_NACOS_SERVER_URL:-}"
COMMERCIAL_NACOS_USERNAME="${COMMERCIAL_NACOS_USERNAME:-}"
COMMERCIAL_NACOS_PASSWORD="${COMMERCIAL_NACOS_PASSWORD:-}"
COMMERCIAL_NACOS_ACCESS_KEY="${COMMERCIAL_NACOS_ACCESS_KEY:-}"
COMMERCIAL_NACOS_SECRET_KEY="${COMMERCIAL_NACOS_SECRET_KEY:-}"

# 最大重试次数
MAX_RETRIES=5
RETRY_DELAY=10

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
# 获取 Himarket Frontend Service 地址
########################################
get_himarket_frontend_host() {
  log "获取 Himarket Frontend Service 地址..." >&2

  local host
  host=$(kubectl get svc himarket-frontend -n "${NS}" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")

  if [[ -z "$host" ]]; then
    log "未检测到 LoadBalancer IP，使用 ClusterIP 模式" >&2
    host="himarket-frontend.${NS}.svc.cluster.local"
  fi

  log "Himarket Frontend 地址: ${host}" >&2
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
  local max_attempts="${5:-$MAX_RETRIES}"  # 允许自定义重试次数

  local url="http://${HIMARKET_HOST}${path}"

  local attempt=1
  while (( attempt <= max_attempts )); do
    if (( max_attempts > 1 )); then
      log "调用接口 [${api_name}]: ${method} ${url} (第 ${attempt}/${max_attempts} 次)"
    else
      log "调用接口 [${api_name}]: ${method} ${url}"
    fi

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
      # 检查是否是连接错误
      if echo "$result" | grep -q "Failed to connect\|Couldn't connect\|Connection refused"; then
        http_code="000"
        response="$result"
      else
        http_code="000"
        response="$result"
      fi
    fi

    if (( max_attempts > 1 )); then
      log "接口 [${api_name}] 返回: HTTP ${http_code}"
    fi

    if [[ -n "$response" ]] && [[ "$response" != "000" ]] && [[ ${#response} -lt 500 ]]; then
      log "响应内容: ${response}"
    fi

    export API_RESPONSE="$response"
    export API_HTTP_CODE="$http_code"

    # 成功的状态码
    if [[ "$http_code" =~ ^2[0-9]{2}$ ]] || [[ "$http_code" == "409" ]]; then
      return 0
    fi

    # 如果是连接错误且还有重试次数
    if [[ "$http_code" == "000" ]] && (( attempt < max_attempts )); then
      log "连接失败，${RETRY_DELAY}秒后重试..."
      sleep $RETRY_DELAY
      attempt=$((attempt + 1))
      continue
    fi

    # 其他错误，直接返回失败
    if (( attempt >= max_attempts )); then
      return 1
    fi

    sleep $RETRY_DELAY
    attempt=$((attempt + 1))
  done

  return 1
}

########################################
# JSON 转义函数
########################################
json_escape() {
  local input="$1"
  # 使用 jq 进行 JSON 转义
  if command -v jq >/dev/null 2>&1; then
    echo -n "$input" | jq -R -s '.'
  else
    # 简单的转义处理（备用方案）
    echo -n "$input" | sed 's/\\/\\\\/g; s/"/\\"/g; s/\n/\\n/g; s/\r/\\r/g; s/\t/\\t/g'
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
# 登录获取 Token（增加重试次数）
########################################
login_admin() {
  local body="{\"username\":\"${ADMIN_USERNAME}\",\"password\":\"${ADMIN_PASSWORD}\"}"

  local attempt=1
  while (( attempt <= MAX_RETRIES )); do
    log "执行登录 (第 ${attempt}/${MAX_RETRIES} 次)..."

    if call_api "管理员登录" "POST" "/api/v1/admins/login" "$body" 1; then
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
        
        if (( attempt < MAX_RETRIES )); then
          sleep $RETRY_DELAY
          attempt=$((attempt + 1))
          continue
        fi
        return 1
      fi

      log "登录成功，获取到 Token: ${AUTH_TOKEN:0:20}..."
      return 0
    fi

    if (( attempt < MAX_RETRIES )); then
      log "登录失败，${RETRY_DELAY}秒后重试..."
      sleep $RETRY_DELAY
    fi
    attempt=$((attempt + 1))
  done

  err "登录失败（已重试 ${MAX_RETRIES} 次）"
  return 1
}

########################################
# 获取 Higress Gateway LoadBalancer IP
########################################
get_higress_gateway_address() {
  log "获取 Higress Gateway LoadBalancer IP..." >&2

  local gateway_ip
  gateway_ip=$(kubectl get svc higress-gateway -n "${NS}" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")

  if [[ -z "$gateway_ip" ]]; then
    log "未检测到 LoadBalancer IP，尝试获取 ClusterIP" >&2
    gateway_ip=$(kubectl get svc higress-gateway -n "${NS}" -o jsonpath='{.spec.clusterIP}' 2>/dev/null || echo "")
  fi

  if [[ -z "$gateway_ip" ]]; then
    err "无法获取 Higress Gateway 地址"
    return 1
  fi

  log "Higress Gateway 地址: ${gateway_ip}" >&2
  echo "$gateway_ip"
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

  # 获取 Higress Gateway LoadBalancer IP
  local gateway_address
  gateway_address=$(get_higress_gateway_address)
  if [[ -z "$gateway_address" ]]; then
    err "无法获取 Higress Gateway 地址，无法创建网关"
    return 1
  fi

  # 使用 jq 构建 JSON 请求体
  local body=$(jq -n \
    --arg gatewayName "$gateway_name" \
    --arg gatewayType "HIGRESS" \
    --arg address "http://higress-console:8080" \
    --arg username "admin" \
    --arg password "$HIGRESS_PASSWORD" \
    --arg gatewayAddress "http://$gateway_address:80" \
    '{
      gatewayName: $gatewayName,
      gatewayType: $gatewayType,
      higressConfig: {
        address: $address,
        username: $username,
        password: $password,
        gatewayAddress: $gatewayAddress
      }
    }')

  call_api "插入网关" "POST" "/api/v1/gateways" "$body" >/dev/null 2>&1 || true

  # 查询获取 ID（带重试）
  local attempt=1
  local gw_id=""
  while (( attempt <= 3 )); do
    call_api "查询网关列表" "GET" "/api/v1/gateways" "" >/dev/null 2>&1
    
    # 从响应中提取 Gateway ID
    gw_id=$(echo "$API_RESPONSE" | jq -r '.data.content[]? // .[]? | select(.gatewayName=="'"${gateway_name}"'") | .gatewayId' 2>/dev/null | head -1 || echo "")
    
    if [[ -n "$gw_id" ]]; then
      eval "${cache_var}='$gw_id'"
      echo "$gw_id"
      return 0
    fi
    
    if (( attempt < 3 )); then
      sleep 3
    fi
    attempt=$((attempt + 1))
  done

  return 1
}

########################################
# 获取或创建 Nacos ID
########################################
get_or_create_nacos() {
  local nacos_name="nacos-demo"
  local server_url="http://nacos:8848"
  local username="nacos"
  local password="nacos"
  local access_key=""
  local secret_key=""
  local nacos_id=""
  
  # 如果使用商业化 Nacos，使用商业化配置查询
  if [[ "$USE_COMMERCIAL_NACOS" == "true" ]]; then
    log "查询商业化 Nacos 实例" >&2
    
    if [[ -z "$COMMERCIAL_NACOS_NAME" ]]; then
      err "商业化 Nacos 配置不完整（缺少 COMMERCIAL_NACOS_NAME）"
      return 1
    fi
    
    nacos_name="$COMMERCIAL_NACOS_NAME"
  else
    # 开源 Nacos，先尝试创建
    log "创建开源 Nacos 实例" >&2
    
    # 使用 jq 构建 JSON 请求体
    local body=$(jq -n \
      --arg nacosName "$nacos_name" \
      --arg serverUrl "$server_url" \
      --arg username "$username" \
      --arg password "$password" \
      '{
        nacosName: $nacosName,
        serverUrl: $serverUrl,
        username: $username,
        password: $password
      }')

    # 尝试创建
    call_api "插入Nacos" "POST" "/api/v1/nacos" "$body" >/dev/null 2>&1 || true
  fi

  # 查询获取 ID（带重试）
  local attempt=1
  local result_nacos_id=""
  while (( attempt <= 3 )); do
    call_api "查询Nacos列表" "GET" "/api/v1/nacos" "" >/dev/null 2>&1
    
    # 从响应中提取 Nacos ID
    result_nacos_id=$(echo "$API_RESPONSE" | jq -r '.data.content[]? // .[]? | select(.nacosName=="'"${nacos_name}"'") | .nacosId' 2>/dev/null | head -1 || echo "")
    
    if [[ -n "$result_nacos_id" ]]; then
      log "获取到 Nacos ID: ${result_nacos_id} (名称: ${nacos_name})" >&2
      echo "$result_nacos_id"
      return 0
    fi
    
    if (( attempt < 3 )); then
      log "未获取到 Nacos ID，3秒后重试..." >&2
      sleep 3
    fi
    attempt=$((attempt + 1))
  done

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

  # 查询获取 ID（带重试）
  local attempt=1
  local p_id=""
  while (( attempt <= 3 )); do
    call_api "查询Portal列表" "GET" "/api/v1/portals" "" >/dev/null 2>&1
    
    # 从响应中提取 Portal ID
    p_id=$(echo "$API_RESPONSE" | jq -r '.data.content[]? // .[]? | select(.name=="'"${portal_name}"'") | .portalId' 2>/dev/null | head -1 || echo "")
    
    if [[ -n "$p_id" ]]; then
      eval "${cache_var}='$p_id'"
      echo "$p_id"
      return 0
    fi
    
    if (( attempt < 3 )); then
      sleep 3
    fi
    attempt=$((attempt + 1))
  done

  return 1
}

########################################
# 获取或创建 Product ID
########################################
get_or_create_product() {
  local product_name="$1"
  local description="$2"
  local type="$3"

  # 使用 jq 构建 JSON 以避免转义问题
  local body=$(jq -n \
    --arg name "$product_name" \
    --arg description "$description" \
    --arg type "$type" \
    '{
      name: $name,
      description: $description,
      type: $type
    }')

  call_api "插入产品" "POST" "/api/v1/products" "$body" >/dev/null 2>&1 || true

  # 查询获取 ID（带重试）
  local attempt=1
  local prod_id=""
  while (( attempt <= 3 )); do
    call_api "查询产品列表" "GET" "/api/v1/products" "" >/dev/null 2>&1
    
    # 从响应中提取 Product ID
    prod_id=$(echo "$API_RESPONSE" | jq -r '.data.content[]? // .[]? | select(.name=="'"${product_name}"'") | .productId' 2>/dev/null | head -1 || echo "")
    
    if [[ -n "$prod_id" ]]; then
      echo "$prod_id"
      return 0
    fi
    
    if (( attempt < 3 )); then
      sleep 3
    fi
    attempt=$((attempt + 1))
  done

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

  # 使用 jq 构建 JSON 以避免转义问题
  local body=$(jq -n \
    --arg nacosId "$nacos_id" \
    --arg sourceType "NACOS" \
    --arg productId "$product_id" \
    --arg mcpServerName "$mcp_name" \
    --arg fromGatewayType "NACOS" \
    --arg type "$ref_type" \
    --arg namespaceId "$namespace_id" \
    '{
      nacosId: $nacosId,
      sourceType: $sourceType,
      productId: $productId,
      nacosRefConfig: {
        mcpServerName: $mcpServerName,
        fromGatewayType: $fromGatewayType,
        type: $type,
        namespaceId: $namespaceId
      }
    }')

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

  # 使用 jq 构建 JSON 以避免转义问题
  local body=$(jq -n \
    --arg gatewayId "$gateway_id" \
    --arg sourceType "GATEWAY" \
    --arg productId "$product_id" \
    --arg mcpServerName "$mcp_name" \
    --arg fromGatewayType "HIGRESS" \
    --arg type "$ref_type" \
    '{
      gatewayId: $gatewayId,
      sourceType: $sourceType,
      productId: $productId,
      higressRefConfig: {
        mcpServerName: $mcpServerName,
        fromGatewayType: $fromGatewayType,
        type: $type
      }
    }')

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

  # 构建请求体
  local body="{\"portalId\":\"${portal_id}\"}"

  if call_api "发布到门户" "POST" "/api/v1/products/${product_id}/publications" "$body"; then
    if [[ "$API_HTTP_CODE" =~ ^2[0-9]{2}$ ]]; then
      log "[${mcp_name}] 发布到门户成功"
      return 0
    elif [[ "$API_HTTP_CODE" == "409" ]]; then
      log "[${mcp_name}] 产品已发布到门户（跳过）"
      return 0
    else
      log "[${mcp_name}] 发布到门户失败（HTTP ${API_HTTP_CODE}）"
      return 0  # 允许失败，继续执行
    fi
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
# 处理单个 Higress MCP 的 Himarket 配置
########################################
process_single_higress_mcp() {
  local mcp_config="$1"

  local mcp_name=$(echo "$mcp_config" | jq -r '.name')
  local mcp_type=$(echo "$mcp_config" | jq -r '.type')

  # 检查是否需要在 Himarket 中配置
  local himarket_config=$(echo "$mcp_config" | jq -r '.himarket // empty')
  if [[ -z "$himarket_config" ]] || [[ "$himarket_config" == "null" ]]; then
    log "[${mcp_name}] 跳过 Himarket 配置（未定义）"
    return 0
  fi

  log "========================================"
  log "处理 Higress MCP: ${mcp_name}"
  log "========================================"

  # 提取 Himarket 配置
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
# 处理单个 Nacos MCP 的 Himarket 配置
########################################
process_single_nacos_mcp() {
  local mcp_config="$1"

  # 从 serverSpecification.name 提取 MCP 名称
  local mcp_name=$(echo "$mcp_config" | jq -r '.serverSpecification.name // .name')

  # 检查是否需要在 Himarket 中配置
  local himarket_config=$(echo "$mcp_config" | jq -r '.himarket // empty')
  if [[ -z "$himarket_config" ]] || [[ "$himarket_config" == "null" ]]; then
    log "[${mcp_name}] 跳过 Himarket 配置（未定义）"
    return 0
  fi

  log "=========================================="
  log "处理 Nacos MCP: ${mcp_name}"
  log "=========================================="

  # 提取 Himarket 配置
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
  log "开始初始化 Himarket MCP 配置..."

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

  # 注册 Nacos 到 Himarket（开源或商业化）
  log "注册 Nacos 到 Himarket..."
  get_or_create_nacos >/dev/null 2>&1 || true

  # 根据配置决定是否注册 Higress Gateway
  local use_ai_gateway="${USE_AI_GATEWAY:-false}"
  if [[ "$use_ai_gateway" != "true" ]]; then
    log "使用 Higress 网关，注册到 Himarket..."
    get_or_create_gateway >/dev/null 2>&1 || true
  else
    log "使用 AI 网关，跳过 Higress Gateway 注册"
  fi

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

  # 遍历处理每个 Higress MCP（仅在未启用 AI 网关时处理）
  if [[ "$use_ai_gateway" != "true" ]] && [ "$higress_mcp_count" -gt 0 ]; then
    log "处理 Higress MCP 配置..."
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
  elif [[ "$use_ai_gateway" == "true" ]] && [ "$higress_mcp_count" -gt 0 ]; then
    log "已启用 AI 网关，跳过 ${higress_mcp_count} 个 Higress MCP 配置"
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
  log "Himarket MCP 初始化完成报告"
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
  log "Himarket Admin: http://${HIMARKET_HOST}"
  log "Himarket Frontend: http://${FRONTEND_HOST}"
  log "========================================"
}

main "$@"
