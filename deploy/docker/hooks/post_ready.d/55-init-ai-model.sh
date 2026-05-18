#!/usr/bin/env bash
# =============================================================================
# AI 模型自动配置脚本 (Docker 环境)
# 支持两种调用方式:
#   1. 作为 install.sh post_ready hook 自动调用（hook 模式）
#   2. 独立运行: ./55-init-ai-model.sh（独立模式）
#
# hook 模式:
#   - 从 ~/himarket-install-docker.env 和继承的环境变量获取所有配置
#   - 受 SKIP_AI_MODEL_INIT 控制
#
# 独立模式:
#   - 支持交互式选择提供商 + 环境变量传入
#   - 不受 SKIP_AI_MODEL_INIT 控制
#
# 环境变量:
#   AI_MODEL_PROVIDER, AI_MODEL_API_KEY — 核心配置（必需）
#   AI_MODEL_TYPE, AI_MODEL_DOMAIN, AI_MODEL_PORT, AI_MODEL_PROTOCOL
#   AI_MODEL_NAME, AI_MODEL_DEFAULT_MODEL — 提供商配置（自动推导）
#   HIGRESS_PASSWORD, ADMIN_USERNAME, ADMIN_PASSWORD — 认证凭据
# =============================================================================

set -euo pipefail

# ── 模式检测（必须在 env 文件加载前执行）─────────────────────────────────────
STANDALONE_MODE="false"
if [[ -z "${SKIP_AI_MODEL_INIT+x}" ]]; then
  STANDALONE_MODE="true"
fi

# 保存从父进程继承的控制变量（优先级高于 env 文件）
_INHERITED_SKIP_AI_MODEL_INIT="${SKIP_AI_MODEL_INIT:-}"
_INHERITED_AI_MODEL_COUNT="${AI_MODEL_COUNT:-}"

# 从 ~/himarket-install-docker.env 加载环境变量
ENV_FILE="${HOME}/himarket-install-docker.env"
if [[ -f "${ENV_FILE}" ]]; then
  set -a; . "${ENV_FILE}"; set +a
fi

# 恢复继承变量（install.sh 导出值优先于 env 文件）
[[ -n "$_INHERITED_SKIP_AI_MODEL_INIT" ]] && SKIP_AI_MODEL_INIT="$_INHERITED_SKIP_AI_MODEL_INIT"
[[ -n "$_INHERITED_AI_MODEL_COUNT" ]] && AI_MODEL_COUNT="$_INHERITED_AI_MODEL_COUNT"

# ── 跳过检查 ─────────────────────────────────────────────────────────────────
if [[ "${STANDALONE_MODE}" != "true" ]]; then
  if [[ "${SKIP_AI_MODEL_INIT:-true}" == "true" ]]; then
    echo "[init-ai-model] SKIP_AI_MODEL_INIT=true，跳过 AI 模型初始化"
    exit 0
  fi
fi

# ── 凭据默认值 ─────────────────────────────────────────────────────────────────
HIGRESS_PASSWORD="${HIGRESS_PASSWORD:-admin}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin}"

# ── 全局变量（Docker 环境使用 localhost）──────────────────────────────────────
HIGRESS_SESSION_COOKIE=""
HIGRESS_HOST="http://localhost:8001"
HIMARKET_HOST="http://localhost:${HIMARKET_ADMIN_PORT:-5174}"
AUTH_TOKEN=""

MAX_RETRIES=3
RETRY_DELAY=5

log() { echo "[init-ai-model $(date +'%H:%M:%S')] $*"; }
err() { echo "[init-ai-model ERROR] $*" >&2; }

########################################
# 检查依赖
########################################
check_dependencies() {
  for cmd in curl jq; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
      err "未找到 $cmd 命令，请先安装"
      exit 1
    fi
  done
}

########################################
# 提供商预设解析
########################################
resolve_provider_preset() {
  local choice="$1"
  case "${choice}" in
    1|qwen)
      AI_MODEL_PROVIDER="qwen"
      AI_MODEL_TYPE="qwen"
      AI_MODEL_DOMAIN="dashscope.aliyuncs.com"
      AI_MODEL_PROTOCOL=""
      AI_MODEL_NAME="${AI_MODEL_NAME:-Alibaba Cloud Qwen}"
      AI_MODEL_DEFAULT_MODEL="${AI_MODEL_DEFAULT_MODEL:-qwen-max}"
      ;;
    2|bailian-codingplan)
      AI_MODEL_PROVIDER="bailian-codingplan"
      AI_MODEL_TYPE="openai"
      AI_MODEL_DOMAIN="coding.dashscope.aliyuncs.com"
      AI_MODEL_PROTOCOL="openai/v1"
      AI_MODEL_NAME="${AI_MODEL_NAME:-Bailian CodingPlan}"
      AI_MODEL_DEFAULT_MODEL="${AI_MODEL_DEFAULT_MODEL:-qwen3.5-plus}"
      ;;
    3|openai)
      AI_MODEL_PROVIDER="openai"
      AI_MODEL_TYPE="openai"
      AI_MODEL_DOMAIN="api.openai.com"
      AI_MODEL_PROTOCOL=""
      AI_MODEL_NAME="${AI_MODEL_NAME:-OpenAI}"
      AI_MODEL_DEFAULT_MODEL="${AI_MODEL_DEFAULT_MODEL:-gpt-4o}"
      ;;
    4|deepseek)
      AI_MODEL_PROVIDER="deepseek"
      AI_MODEL_TYPE="deepseek"
      AI_MODEL_DOMAIN="api.deepseek.com"
      AI_MODEL_PROTOCOL=""
      AI_MODEL_NAME="${AI_MODEL_NAME:-DeepSeek}"
      AI_MODEL_DEFAULT_MODEL="${AI_MODEL_DEFAULT_MODEL:-deepseek-chat}"
      ;;
    5|moonshot)
      AI_MODEL_PROVIDER="moonshot"
      AI_MODEL_TYPE="moonshot"
      AI_MODEL_DOMAIN="api.moonshot.cn"
      AI_MODEL_PROTOCOL=""
      AI_MODEL_NAME="${AI_MODEL_NAME:-Moonshot (Kimi)}"
      AI_MODEL_DEFAULT_MODEL="${AI_MODEL_DEFAULT_MODEL:-moonshot-v1-8k}"
      ;;
    6|zhipuai)
      AI_MODEL_PROVIDER="zhipuai"
      AI_MODEL_TYPE="zhipuai"
      AI_MODEL_DOMAIN="open.bigmodel.cn"
      AI_MODEL_PROTOCOL=""
      AI_MODEL_NAME="${AI_MODEL_NAME:-Zhipu AI}"
      AI_MODEL_DEFAULT_MODEL="${AI_MODEL_DEFAULT_MODEL:-glm-4}"
      ;;
    7|custom|custom-llm)
      AI_MODEL_PROVIDER="${AI_MODEL_PROVIDER:-custom-llm}"
      AI_MODEL_TYPE="${AI_MODEL_TYPE:-openai}"
      AI_MODEL_PROTOCOL="${AI_MODEL_PROTOCOL:-openai/v1}"
      AI_MODEL_NAME="${AI_MODEL_NAME:-Custom LLM}"
      ;;
    *)
      err "无效的提供商选择: ${choice}"
      return 1
      ;;
  esac
  AI_MODEL_PORT="${AI_MODEL_PORT:-443}"
}

########################################
# 交互式提供商选择
########################################
interactive_select_provider() {
  echo ""
  echo "可用 AI 模型提供商:"
  echo "  1) Alibaba Cloud Qwen（通义千问）"
  echo "  2) Bailian CodingPlan（百炼 CodingPlan）"
  echo "  3) OpenAI"
  echo "  4) DeepSeek"
  echo "  5) Moonshot (Kimi)"
  echo "  6) Zhipu AI（智谱）"
  echo "  7) 自定义"
  echo ""

  local choice=""
  read -r -p "选择提供商 [1]: " choice
  choice="${choice:-1}"

  if [[ "${choice}" == "7" ]]; then
    local domain="" type="" name="" model=""
    read -r -p "API 域名: " domain
    read -r -p "Provider Type（如 openai）[openai]: " type
    type="${type:-openai}"
    read -r -p "提供商展示名称 [Custom LLM]: " name
    name="${name:-Custom LLM}"
    read -r -p "默认模型 ID（可选，回车跳过）: " model

    AI_MODEL_DOMAIN="${domain}"
    AI_MODEL_TYPE="${type}"
    AI_MODEL_NAME="${name}"
    AI_MODEL_PROVIDER="custom-llm"
    AI_MODEL_PROTOCOL="openai/v1"
    AI_MODEL_DEFAULT_MODEL="${model}"
    AI_MODEL_PORT="${AI_MODEL_PORT:-443}"
  else
    resolve_provider_preset "${choice}"
  fi

  # 收集 API Key
  local api_key=""
  read -r -p "API Key: " api_key
  if [[ -z "${api_key}" ]]; then
    err "API Key 不能为空"
    exit 1
  fi
  AI_MODEL_API_KEY="${api_key}"

  # 可选覆盖默认模型
  if [[ "${choice}" != "7" ]]; then
    local model_override=""
    read -r -p "默认模型 ID [${AI_MODEL_DEFAULT_MODEL}]: " model_override
    if [[ -n "${model_override}" ]]; then
      AI_MODEL_DEFAULT_MODEL="${model_override}"
    fi
  fi

  log "已选: ${AI_MODEL_NAME}（域名: ${AI_MODEL_DOMAIN}）"
}

########################################
# Higress Console: 登录
########################################
login_higress() {
  log "登录 Higress Console..."

  local login_url="${HIGRESS_HOST}/session/login"
  local login_data='{"username":"admin","password":"'"${HIGRESS_PASSWORD}"'"}'
  local attempt=1

  while (( attempt <= MAX_RETRIES )); do
    log "尝试登录 Higress (第 ${attempt}/${MAX_RETRIES} 次)..."

    local result
    result=$(curl -sS -i -X POST "${login_url}" \
      -H "Accept: application/json, text/plain, */*" \
      -H "Content-Type: application/json" \
      --data "${login_data}" \
      --connect-timeout 5 --max-time 10 2>/dev/null || echo "")

    local http_code
    http_code=$(echo "$result" | grep -i "^HTTP/" | tail -1 | awk '{print $2}')
    local cookie_value
    cookie_value=$(echo "$result" | grep -i "^Set-Cookie:" | grep "_hi_sess=" | sed 's/.*_hi_sess=\([^;]*\).*/\1/' | head -1)

    if [[ "$http_code" =~ ^2[0-9]{2}$ ]] && [[ -n "$cookie_value" ]]; then
      HIGRESS_SESSION_COOKIE="_hi_sess=${cookie_value}"
      log "Higress 登录成功"
      return 0
    fi

    if [[ "$http_code" == "401" ]]; then
      err "Higress 登录失败：用户名或密码错误"
      return 1
    fi

    if (( attempt < MAX_RETRIES )); then
      log "Higress 登录失败 (HTTP ${http_code:-000})，${RETRY_DELAY}秒后重试..."
      sleep "${RETRY_DELAY}"
    fi
    attempt=$((attempt + 1))
  done

  err "Higress 登录失败，已达最大重试次数"
  return 1
}

########################################
# Higress Console: 通用 API 调用
########################################
call_higress_api() {
  local method="$1"
  local path="$2"
  local data="$3"
  local desc="$4"

  local url="${HIGRESS_HOST}${path}"
  local attempt=1

  while (( attempt <= MAX_RETRIES )); do
    log "${desc} (第 ${attempt}/${MAX_RETRIES} 次)..."

    local result
    result=$(curl -sS -w "\nHTTP_CODE:%{http_code}" -X "${method}" "${url}" \
      -H "Accept: application/json, text/plain, */*" \
      -H "Content-Type: application/json" \
      -b "${HIGRESS_SESSION_COOKIE}" \
      --data "${data}" \
      --connect-timeout 5 --max-time 15 2>/dev/null || echo "HTTP_CODE:000")

    local http_code="" response=""
    if [[ "$result" =~ HTTP_CODE:([0-9]{3}) ]]; then
      http_code="${BASH_REMATCH[1]}"
      response=$(echo "$result" | sed '/HTTP_CODE:/d')
    else
      http_code="000"
      response="$result"
    fi

    if [[ "$http_code" =~ ^2[0-9]{2}$ ]]; then
      log "${desc} 成功"
      return 0
    fi

    if [[ "$http_code" == "409" ]] || \
       [[ "$response" == *"already exists"* ]] || \
       [[ "$response" == *"已存在"* ]]; then
      log "${desc} - 资源已存在（幂等），视为成功"
      return 0
    fi

    if [[ "$http_code" =~ ^4[0-9]{2}$ ]]; then
      err "${desc} 失败: HTTP ${http_code}"
      log "响应: ${response}"
      return 1
    fi

    if (( attempt < MAX_RETRIES )); then
      log "${desc} 失败 (HTTP ${http_code})，${RETRY_DELAY}秒后重试..."
      sleep "${RETRY_DELAY}"
    fi
    attempt=$((attempt + 1))
  done

  err "${desc} 失败，已达最大重试次数"
  return 1
}

########################################
# Higress: 创建 DNS 服务源
########################################
create_service_source() {
  local name="ai-provider-${AI_MODEL_RESOURCE_ID}"
  local data
  data=$(jq -n \
    --arg type "dns" \
    --arg name "$name" \
    --arg port "${AI_MODEL_PORT}" \
    --arg domain "${AI_MODEL_DOMAIN}" \
    --arg protocol "https" \
    '{
      type: $type,
      name: $name,
      port: $port,
      domainForEdit: $domain,
      protocol: $protocol,
      proxyName: "",
      domain: $domain
    }')

  call_higress_api "POST" "/v1/service-sources" "$data" "创建 DNS 服务源 (${name})"
}

########################################
# Higress: 创建 AI Provider
########################################
create_ai_provider() {
  local data
  local provider_name="${AI_MODEL_RESOURCE_ID}"

  case "${AI_MODEL_TYPE}" in
    qwen)
      data=$(jq -n \
        --arg name "${provider_name}" \
        --arg type "qwen" \
        --arg apiKey "${AI_MODEL_API_KEY}" \
        '{
          name: $name,
          type: $type,
          tokens: [$apiKey],
          rawConfigs: {
            qwenEnableSearch: false,
            qwenEnableCompatible: true
          }
        }')
      ;;
    openai)
      local custom_url=""
      if [[ "${AI_MODEL_DOMAIN}" != "api.openai.com" ]]; then
        local proto_path=""
        if [[ -n "${AI_MODEL_PROTOCOL}" ]]; then
          proto_path="/${AI_MODEL_PROTOCOL#*/}"
        fi
        custom_url="https://${AI_MODEL_DOMAIN}${proto_path}"
      fi

      if [[ -n "${custom_url}" ]]; then
        data=$(jq -n \
          --arg name "${provider_name}" \
          --arg type "openai" \
          --arg apiKey "${AI_MODEL_API_KEY}" \
          --arg customUrl "${custom_url}" \
          --argjson port "${AI_MODEL_PORT:-443}" \
          --arg protocol "${AI_MODEL_PROTOCOL:-}" \
          'if $protocol != "" then {
              name: $name, type: $type, tokens: [$apiKey], protocol: $protocol,
              rawConfigs: { openaiCustomUrl: $customUrl, openaiCustomServicePort: $port }
            } else {
              name: $name, type: $type, tokens: [$apiKey],
              rawConfigs: { openaiCustomUrl: $customUrl, openaiCustomServicePort: $port }
            } end')
      else
        data=$(jq -n \
          --arg name "${provider_name}" \
          --arg type "openai" \
          --arg apiKey "${AI_MODEL_API_KEY}" \
          '{name: $name, type: $type, tokens: [$apiKey]}')
      fi
      ;;
    *)
      if [[ -n "${AI_MODEL_PROTOCOL}" ]]; then
        data=$(jq -n \
          --arg name "${provider_name}" \
          --arg type "${AI_MODEL_TYPE}" \
          --arg protocol "${AI_MODEL_PROTOCOL}" \
          --arg apiKey "${AI_MODEL_API_KEY}" \
          '{name: $name, type: $type, tokens: [$apiKey], protocol: $protocol}')
      else
        data=$(jq -n \
          --arg name "${provider_name}" \
          --arg type "${AI_MODEL_TYPE}" \
          --arg apiKey "${AI_MODEL_API_KEY}" \
          '{name: $name, type: $type, tokens: [$apiKey]}')
      fi
      ;;
  esac

  call_higress_api "POST" "/v1/ai/providers" "$data" "创建 AI Provider (${provider_name})"
}

########################################
# Higress: 创建 AI Route
########################################
create_ai_route() {
  local route_name="${AI_MODEL_ROUTE_NAME:-ai-route-${AI_MODEL_RESOURCE_ID}}"
  local path_prefix="/${route_name}/v1/chat/completions"
  local data
  data=$(jq -n \
    --arg name "$route_name" \
    --arg provider "${AI_MODEL_RESOURCE_ID}" \
    --arg pathValue "$path_prefix" \
    '{
      name: $name,
      domains: [],
      pathPredicate: {
        matchType: "PRE",
        matchValue: $pathValue,
        caseSensitive: false
      },
      upstreams: [{
        provider: $provider,
        weight: 100,
        modelMapping: {}
      }]
    }')

  call_higress_api "POST" "/v1/ai/routes" "$data" "创建 AI Route (${route_name})"
}

########################################
# HiMarket: 通用 API 调用
########################################
call_himarket_api() {
  local api_name="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local max_attempts="${5:-$MAX_RETRIES}"

  local url="${HIMARKET_HOST}${path}"
  local attempt=1

  while (( attempt <= max_attempts )); do
    if (( max_attempts > 1 )); then
      log "调用 [${api_name}]: ${method} ${url} (第 ${attempt}/${max_attempts} 次)"
    else
      log "调用 [${api_name}]: ${method} ${url}"
    fi

    local curl_args=(-sS -w "\nHTTP_CODE:%{http_code}" -X "${method}" "${url}"
      -H "Content-Type: application/json"
      -H "Accept: application/json, text/plain, */*"
      --connect-timeout 10 --max-time 30)

    if [[ -n "${AUTH_TOKEN}" ]]; then
      curl_args+=(-H "Authorization: Bearer ${AUTH_TOKEN}")
    fi

    if [[ -n "${body}" ]]; then
      curl_args+=(--data "${body}")
    fi

    local result
    result=$(curl "${curl_args[@]}" 2>&1 || echo "HTTP_CODE:000")

    local http_code="" response=""
    if [[ "$result" =~ HTTP_CODE:([0-9]{3}) ]]; then
      http_code="${BASH_REMATCH[1]}"
      response=$(echo "$result" | sed '/HTTP_CODE:/d')
    else
      http_code="000"
      response="$result"
    fi

    export API_RESPONSE="$response"
    export API_HTTP_CODE="$http_code"

    if [[ "$http_code" =~ ^2[0-9]{2}$ ]] || [[ "$http_code" == "409" ]]; then
      return 0
    fi

    if [[ "$http_code" == "000" ]] && (( attempt < max_attempts )); then
      log "连接失败，${RETRY_DELAY}秒后重试..."
      sleep "${RETRY_DELAY}"
      attempt=$((attempt + 1))
      continue
    fi

    if (( attempt >= max_attempts )); then
      return 1
    fi

    sleep "${RETRY_DELAY}"
    attempt=$((attempt + 1))
  done
  return 1
}

########################################
# HiMarket: 登录
########################################
login_himarket() {
  log "登录 HiMarket Admin..."

  local body
  body=$(jq -n \
    --arg username "${ADMIN_USERNAME}" \
    --arg password "${ADMIN_PASSWORD}" \
    '{username: $username, password: $password}')

  local attempt=1
  while (( attempt <= MAX_RETRIES )); do
    if call_himarket_api "管理员登录" "POST" "/api/v1/admins/login" "$body" 1; then
      AUTH_TOKEN=$(echo "$API_RESPONSE" | jq -r '.data.access_token // empty' 2>/dev/null || echo "")
      if [[ -z "$AUTH_TOKEN" ]]; then
        AUTH_TOKEN=$(echo "$API_RESPONSE" | jq -r '.data.token // .data.accessToken // empty' 2>/dev/null || echo "")
      fi

      if [[ -n "$AUTH_TOKEN" ]]; then
        log "HiMarket 登录成功"
        return 0
      fi

      err "无法从登录响应中提取 token"
    fi

    if (( attempt < MAX_RETRIES )); then
      log "HiMarket 登录失败，${RETRY_DELAY}秒后重试..."
      sleep "${RETRY_DELAY}"
    fi
    attempt=$((attempt + 1))
  done

  err "HiMarket 登录失败"
  return 1
}

########################################
# 获取 Higress Gateway 公网 IP
########################################
get_higress_gateway_address() {
  log "获取 Higress Gateway 公网 IP 地址..." >&2

  local gateway_ip=""

  for service in "ifconfig.me" "icanhazip.com" "ipecho.net/plain" "api.ipify.org"; do
    gateway_ip=$(curl -s --connect-timeout 3 --max-time 5 "http://${service}" 2>/dev/null | tr -d '[:space:]')

    if [[ "$gateway_ip" =~ ^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$ ]]; then
      if [[ ! "$gateway_ip" =~ ^10\. ]] && \
         [[ ! "$gateway_ip" =~ ^172\.(1[6-9]|2[0-9]|3[0-1])\. ]] && \
         [[ ! "$gateway_ip" =~ ^192\.168\. ]] && \
         [[ ! "$gateway_ip" =~ ^127\. ]]; then
        log "检测到公网 IP: ${gateway_ip}" >&2
        echo "${gateway_ip}"
        return 0
      fi
    fi
  done

  err "无法获取公网 IP 地址"
  return 1
}

########################################
# HiMarket: 获取或创建 Gateway
########################################
get_or_create_gateway() {
  local gateway_name="higress-demo"

  # Docker 环境：获取公网 IP 作为 gatewayAddress
  local gateway_ip
  gateway_ip=$(get_higress_gateway_address) || gateway_ip=""

  local gateway_address="http://localhost:8082"
  if [[ -n "$gateway_ip" ]]; then
    gateway_address="http://${gateway_ip}:8082"
  fi

  local body
  body=$(jq -n \
    --arg gatewayName "$gateway_name" \
    --arg address "http://higress:8001" \
    --arg username "admin" \
    --arg password "$HIGRESS_PASSWORD" \
    --arg gatewayAddress "$gateway_address" \
    '{
      gatewayName: $gatewayName,
      gatewayType: "HIGRESS",
      higressConfig: {
        address: $address,
        username: $username,
        password: $password,
        gatewayAddress: $gatewayAddress
      }
    }')

  call_himarket_api "创建网关" "POST" "/api/v1/gateways" "$body" 1 >/dev/null 2>&1 || true

  # 查询获取 ID
  local attempt=1 gw_id=""
  while (( attempt <= 3 )); do
    call_himarket_api "查询网关" "GET" "/api/v1/gateways" "" 1 >/dev/null 2>&1 || true
    gw_id=$(echo "$API_RESPONSE" | jq -r '.data.content[]? // .[]? | select(.gatewayName=="'"${gateway_name}"'") | .gatewayId' 2>/dev/null | head -1 || echo "")

    if [[ -n "$gw_id" ]]; then
      echo "$gw_id"
      return 0
    fi
    sleep 3
    attempt=$((attempt + 1))
  done

  return 1
}

########################################
# HiMarket: 获取或创建 Portal
########################################
get_or_create_portal() {
  local portal_name="${1:-demo}"

  local body="{\"name\":\"${portal_name}\"}"
  call_himarket_api "创建Portal" "POST" "/api/v1/portals" "$body" 1 >/dev/null 2>&1 || true

  local attempt=1 p_id=""
  while (( attempt <= 3 )); do
    call_himarket_api "查询Portal" "GET" "/api/v1/portals" "" 1 >/dev/null 2>&1 || true
    p_id=$(echo "$API_RESPONSE" | jq -r '.data.content[]? // .[]? | select(.name=="'"${portal_name}"'") | .portalId' 2>/dev/null | head -1 || echo "")

    if [[ -n "$p_id" ]]; then
      echo "$p_id"
      return 0
    fi
    sleep 3
    attempt=$((attempt + 1))
  done

  return 1
}

########################################
# HiMarket: 创建 MODEL_API 产品
########################################
create_model_product() {
  local model="${AI_MODEL_DEFAULT_MODEL:-}"
  local product_name="${AI_MODEL_NAME}"
  if [[ -n "${model}" ]]; then
    product_name="${AI_MODEL_NAME} - ${model}"
  fi

  local body
  body=$(jq -n \
    --arg name "$product_name" \
    --arg description "AI Model API - ${product_name}" \
    --arg model "$model" \
    '{
      name: $name,
      description: $description,
      type: "MODEL_API",
      autoApprove: true,
      feature: {
        modelFeature: {
          model: $model,
          streaming: true
        }
      }
    }')

  call_himarket_api "创建产品" "POST" "/api/v1/products" "$body" 1 >/dev/null 2>&1 || true

  local attempt=1 prod_id=""
  while (( attempt <= 3 )); do
    call_himarket_api "查询产品" "GET" "/api/v1/products" "" 1 >/dev/null 2>&1 || true
    prod_id=$(echo "$API_RESPONSE" | jq -r '.data.content[]? // .[]? | select(.name=="'"${product_name}"'") | .productId' 2>/dev/null | head -1 || echo "")

    if [[ -n "$prod_id" ]]; then
      echo "$prod_id"
      return 0
    fi
    sleep 3
    attempt=$((attempt + 1))
  done

  return 1
}

########################################
# HiMarket: 关联产品到网关
########################################
link_product_to_gateway_model() {
  local product_id="$1"
  local gateway_id="$2"
  local route_name="${AI_MODEL_ROUTE_NAME:-ai-route-${AI_MODEL_RESOURCE_ID}}"

  local body
  body=$(jq -n \
    --arg gatewayId "$gateway_id" \
    --arg productId "$product_id" \
    --arg modelRouteName "$route_name" \
    '{
      gatewayId: $gatewayId,
      sourceType: "GATEWAY",
      productId: $productId,
      higressRefConfig: {
        modelRouteName: $modelRouteName
      }
    }')

  if call_himarket_api "关联产品到网关" "POST" "/api/v1/products/${product_id}/ref" "$body"; then
    log "产品关联到网关成功 (modelRouteName: ${route_name})"
    return 0
  else
    err "产品关联到网关失败"
    return 1
  fi
}

########################################
# HiMarket: 发布产品到 Portal
########################################
publish_product_to_portal() {
  local product_id="$1"
  local portal_id="$2"

  local body="{\"portalId\":\"${portal_id}\"}"

  if call_himarket_api "发布到门户" "POST" "/api/v1/products/${product_id}/publications" "$body"; then
    log "产品发布到门户成功"
    return 0
  else
    log "产品发布到门户失败（可能已发布）"
    return 0
  fi
}

########################################
# 从索引变量加载单个模型到全局变量
########################################
load_model_vars() {
  local idx="$1"
  AI_MODEL_IDX="${idx}"
  eval "AI_MODEL_PROVIDER=\${AI_MODEL_${idx}_PROVIDER:-}"
  eval "AI_MODEL_TYPE=\${AI_MODEL_${idx}_TYPE:-}"
  eval "AI_MODEL_DOMAIN=\${AI_MODEL_${idx}_DOMAIN:-}"
  eval "AI_MODEL_PORT=\${AI_MODEL_${idx}_PORT:-443}"
  eval "AI_MODEL_PROTOCOL=\${AI_MODEL_${idx}_PROTOCOL:-}"
  eval "AI_MODEL_API_KEY=\${AI_MODEL_${idx}_API_KEY:-}"
  eval "AI_MODEL_NAME=\"\${AI_MODEL_${idx}_NAME:-}\""
  eval "AI_MODEL_DEFAULT_MODEL=\${AI_MODEL_${idx}_DEFAULT_MODEL:-}"
  AI_MODEL_RESOURCE_ID="${AI_MODEL_PROVIDER}-${idx}"
}

########################################
# 处理单个模型
########################################
process_one_model() {
  local model_idx="$1"
  local gateway_id="$2"
  local portal_id="$3"

  log ""
  log "---------- 配置模型 #${model_idx}: ${AI_MODEL_NAME} (${AI_MODEL_DOMAIN}) ----------"

  local higress_ok="true"

  if ! create_service_source; then
    err "创建 DNS 服务源失败 (${AI_MODEL_RESOURCE_ID})"
    higress_ok="false"
  fi

  if [[ "${higress_ok}" == "true" ]]; then
    if ! create_ai_provider; then
      err "创建 AI Provider 失败 (${AI_MODEL_RESOURCE_ID})"
      higress_ok="false"
    fi
  fi

  if [[ "${higress_ok}" == "true" ]]; then
    if ! create_ai_route; then
      err "创建 AI Route 失败 (${AI_MODEL_RESOURCE_ID})"
      higress_ok="false"
    fi
  fi

  if [[ "${higress_ok}" != "true" ]]; then
    err "模型 #${model_idx} Higress 配置未完成，跳过 HiMarket 产品发布"
    return 1
  fi

  log "创建 MODEL_API 产品 (${AI_MODEL_NAME} - ${AI_MODEL_DEFAULT_MODEL})..."
  local product_id
  product_id=$(create_model_product)
  if [[ -z "${product_id}" ]]; then
    err "无法创建/获取产品 ID (${AI_MODEL_NAME} - ${AI_MODEL_DEFAULT_MODEL})"
    return 1
  fi
  log "Product ID: ${product_id}"

  log "关联产品到网关..."
  link_product_to_gateway_model "${product_id}" "${gateway_id}" || true

  if [[ -n "${portal_id}" ]]; then
    log "发布产品到 Portal..."
    publish_product_to_portal "${product_id}" "${portal_id}" || true
  fi

  log "模型 #${model_idx} (${AI_MODEL_NAME} - ${AI_MODEL_DEFAULT_MODEL}) 配置完成"
  return 0
}

########################################
# 主流程
########################################
main() {
  log "开始 AI 模型自动配置 (Docker 环境)..."
  log "运行模式: $(if [[ "${STANDALONE_MODE}" == "true" ]]; then echo "独立模式"; else echo "hook 模式"; fi)"

  check_dependencies

  local model_count="${AI_MODEL_COUNT:-0}"

  if [[ "${model_count}" -gt 0 ]]; then
    log "检测到 ${model_count} 个模型配置"
  elif [[ "${STANDALONE_MODE}" == "true" ]]; then
    interactive_select_provider
    model_count=1
    export "AI_MODEL_1_PROVIDER=${AI_MODEL_PROVIDER}"
    export "AI_MODEL_1_TYPE=${AI_MODEL_TYPE:-}"
    export "AI_MODEL_1_DOMAIN=${AI_MODEL_DOMAIN:-}"
    export "AI_MODEL_1_PORT=${AI_MODEL_PORT:-443}"
    export "AI_MODEL_1_PROTOCOL=${AI_MODEL_PROTOCOL:-}"
    export "AI_MODEL_1_API_KEY=${AI_MODEL_API_KEY}"
    export "AI_MODEL_1_NAME=${AI_MODEL_NAME:-}"
    export "AI_MODEL_1_DEFAULT_MODEL=${AI_MODEL_DEFAULT_MODEL:-}"
  else
    err "hook 模式下 AI_MODEL_COUNT 必须由 install.sh 设置"
    exit 1
  fi

  log ""
  log "========== 登录服务 =========="

  if ! login_higress; then
    err "Higress Console 登录失败"
    exit 1
  fi

  if ! login_himarket; then
    err "HiMarket Admin 登录失败"
    exit 1
  fi

  log "获取网关 ID..."
  local gateway_id
  gateway_id=$(get_or_create_gateway)
  if [[ -z "${gateway_id}" ]]; then
    err "无法获取 Gateway ID"
    exit 1
  fi
  log "Gateway ID: ${gateway_id}"

  log "获取 Portal ID..."
  local portal_id
  portal_id=$(get_or_create_portal "demo")
  if [[ -z "${portal_id}" ]]; then
    err "无法获取 Portal ID"
  fi
  log "Portal ID: ${portal_id:-<未获取>}"

  local i success_count=0 fail_count=0
  for (( i=1; i<=model_count; i++ )); do
    load_model_vars "${i}"
    resolve_provider_preset "${AI_MODEL_PROVIDER}" 2>/dev/null || true

    if process_one_model "${i}" "${gateway_id}" "${portal_id}"; then
      success_count=$((success_count + 1))
    else
      fail_count=$((fail_count + 1))
    fi
  done

  log ""
  log "========================================"
  log "AI 模型自动配置完成"
  log "========================================"
  log "  总计: ${model_count} 个模型"
  log "  成功: ${success_count}"
  if [[ "${fail_count}" -gt 0 ]]; then
    log "  失败: ${fail_count}"
  fi
  for (( i=1; i<=model_count; i++ )); do
    load_model_vars "${i}"
    log "  #${i} ${AI_MODEL_NAME} - ${AI_MODEL_DEFAULT_MODEL} (${AI_MODEL_DOMAIN}) - Route: ai-route-${AI_MODEL_RESOURCE_ID}"
  done
  log "========================================"
}

main "$@"
