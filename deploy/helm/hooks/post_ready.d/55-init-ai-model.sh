#!/usr/bin/env bash
# =============================================================================
# AI 模型自动配置脚本
# 支持两种调用方式:
#   1. 作为 install.sh post_ready hook 自动调用（hook 模式）
#   2. 独立运行: ./55-init-ai-model.sh（独立模式）
#
# hook 模式:
#   - 从 .env 和继承的环境变量获取所有配置
#   - 受 SKIP_AI_MODEL_INIT 控制
#
# 独立模式:
#   - 支持交互式选择提供商 + 环境变量传入
#   - 自动发现 Higress/HiMarket 地址（kubectl），回退到交互提示
#   - 不受 SKIP_AI_MODEL_INIT 控制
#
# 环境变量:
#   AI_MODEL_PROVIDER, AI_MODEL_API_KEY — 核心配置（必需）
#   AI_MODEL_TYPE, AI_MODEL_DOMAIN, AI_MODEL_PORT, AI_MODEL_PROTOCOL
#   AI_MODEL_NAME, AI_MODEL_DEFAULT_MODEL — 提供商配置（自动推导）
#   HIGRESS_CONSOLE_URL, HIMARKET_API_URL — 服务地址（可选，自动发现）
#   HIGRESS_PASSWORD, ADMIN_USERNAME, ADMIN_PASSWORD — 认证凭据
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HELM_DIR="${SCRIPT_DIR}/../.."

# ── 加载 .env（如存在）─────────────────────────────────────────────────────────
if [[ -f "${HELM_DIR}/.env" ]]; then
  set -a
  . "${HELM_DIR}/.env"
  set +a
fi

NS="${NAMESPACE:-himarket}"

# ── 模式检测 ───────────────────────────────────────────────────────────────────
# 当通过 install.sh run_hooks 调用时，SKIP_AI_MODEL_INIT 已由 install.sh 导出。
# 独立运行时该变量通常为空。
STANDALONE_MODE="false"
if [[ -z "${SKIP_AI_MODEL_INIT+x}" ]]; then
  # SKIP_AI_MODEL_INIT 未设置 → 独立模式
  STANDALONE_MODE="true"
else
  # hook 模式：受 SKIP_AI_MODEL_INIT 控制
  if [[ "${SKIP_AI_MODEL_INIT:-true}" == "true" ]]; then
    echo "[init-ai-model] SKIP_AI_MODEL_INIT=true，跳过 AI 模型初始化"
    exit 0
  fi
fi

# ── 凭据默认值 ─────────────────────────────────────────────────────────────────
HIGRESS_PASSWORD="${HIGRESS_PASSWORD:-admin}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin}"

# ── 全局变量 ───────────────────────────────────────────────────────────────────
HIGRESS_SESSION_COOKIE=""
HIGRESS_HOST=""           # Higress Console base URL (e.g. http://1.2.3.4:8080)
HIMARKET_HOST=""          # HiMarket Admin base URL  (e.g. http://1.2.3.4)
AUTH_TOKEN=""              # HiMarket JWT

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
      # 自定义模式：需要 AI_MODEL_DOMAIN, AI_MODEL_TYPE 已设置
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
# 服务地址三级回退解析
########################################
resolve_service_urls() {
  # --- Higress Console ---
  if [[ -n "${HIGRESS_CONSOLE_URL:-}" ]]; then
    HIGRESS_HOST="${HIGRESS_CONSOLE_URL}"
    log "Higress Console 地址 (环境变量): ${HIGRESS_HOST}"
  else
    local higress_ip=""
    higress_ip=$(kubectl get svc higress-console -n "${NS}" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
    if [[ -n "${higress_ip}" ]]; then
      local higress_port=""
      higress_port=$(kubectl get svc higress-console -n "${NS}" -o jsonpath='{.spec.ports[0].port}' 2>/dev/null || echo "8080")
      HIGRESS_HOST="http://${higress_ip}:${higress_port}"
      log "Higress Console 地址 (kubectl): ${HIGRESS_HOST}"
    elif [[ "${STANDALONE_MODE}" == "true" ]]; then
      read -r -p "请输入 Higress Console 地址 (如 http://1.2.3.4:8080): " HIGRESS_HOST
      if [[ -z "${HIGRESS_HOST}" ]]; then
        err "Higress Console 地址不能为空"
        exit 1
      fi
    else
      err "无法获取 Higress Console 地址"
      exit 1
    fi
  fi

  # --- HiMarket Admin ---
  if [[ -n "${HIMARKET_API_URL:-}" ]]; then
    HIMARKET_HOST="${HIMARKET_API_URL}"
    log "HiMarket Admin 地址 (环境变量): ${HIMARKET_HOST}"
  else
    local himarket_ip=""
    himarket_ip=$(kubectl get svc himarket-admin -n "${NS}" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
    if [[ -n "${himarket_ip}" ]]; then
      HIMARKET_HOST="http://${himarket_ip}"
      log "HiMarket Admin 地址 (kubectl): ${HIMARKET_HOST}"
    elif [[ "${STANDALONE_MODE}" == "true" ]]; then
      read -r -p "请输入 HiMarket Admin 地址 (如 http://1.2.3.4): " HIMARKET_HOST
      if [[ -z "${HIMARKET_HOST}" ]]; then
        err "HiMarket Admin 地址不能为空"
        exit 1
      fi
    else
      err "无法获取 HiMarket Admin 地址"
      exit 1
    fi
  fi
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

    # 成功
    if [[ "$http_code" =~ ^2[0-9]{2}$ ]]; then
      log "${desc} 成功"
      return 0
    fi

    # 幂等成功
    if [[ "$http_code" == "409" ]] || \
       [[ "$response" == *"already exists"* ]] || \
       [[ "$response" == *"已存在"* ]]; then
      log "${desc} - 资源已存在（幂等），视为成功"
      return 0
    fi

    # 客户端错误（非 409）不重试
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
      # qwen 类型需要 qwen 特有配置，启用 compatible 模式走 OpenAI 兼容端点
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
      # openai 类型：自定义域名需要 openaiCustomUrl
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
      # deepseek / moonshot / zhipuai 等：Higress 内置支持，无需 customUrl
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

    # 成功或幂等
    if [[ "$http_code" =~ ^2[0-9]{2}$ ]] || [[ "$http_code" == "409" ]]; then
      return 0
    fi

    # 连接失败时重试
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
# HiMarket: 获取或创建 Gateway
########################################
get_or_create_gateway() {
  local gateway_name="higress-demo"

  # 获取 Higress Gateway LoadBalancer IP
  local gateway_ip
  gateway_ip=$(kubectl get svc higress-gateway -n "${NS}" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")
  if [[ -z "$gateway_ip" ]]; then
    gateway_ip=$(kubectl get svc higress-gateway -n "${NS}" -o jsonpath='{.spec.clusterIP}' 2>/dev/null || echo "")
  fi

  if [[ -n "$gateway_ip" ]]; then
    local body
    body=$(jq -n \
      --arg gatewayName "$gateway_name" \
      --arg address "http://higress-console:8080" \
      --arg username "admin" \
      --arg password "$HIGRESS_PASSWORD" \
      --arg gatewayAddress "http://$gateway_ip:80" \
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
  fi

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

  # 查询获取 ID
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
  # 产品名称包含供应商和模型名，避免同供应商配置多个模型时名称重复
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

  # 查询获取 ID
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
# HiMarket: 关联产品到网关 (modelRouteName)
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
    return 0  # 允许失败
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
  # 资源唯一 ID：provider-index（防止同 provider 配置多个模型时资源名冲突）
  AI_MODEL_RESOURCE_ID="${AI_MODEL_PROVIDER}-${idx}"
}

########################################
# 处理单个模型的 Higress + HiMarket 配置
########################################
process_one_model() {
  local model_idx="$1"
  local gateway_id="$2"
  local portal_id="$3"

  log ""
  log "---------- 配置模型 #${model_idx}: ${AI_MODEL_NAME} (${AI_MODEL_DOMAIN}) ----------"

  # --- Higress 配置 ---
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

  # --- HiMarket 产品 ---
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
  log "开始 AI 模型自动配置..."
  log "运行模式: $(if [[ "${STANDALONE_MODE}" == "true" ]]; then echo "独立模式"; else echo "hook 模式"; fi)"

  # 1. 检查依赖
  check_dependencies

  # 2. 检测模型配置格式
  local model_count="${AI_MODEL_COUNT:-0}"

  if [[ "${model_count}" -gt 0 ]]; then
    log "检测到 ${model_count} 个模型配置"
  elif [[ "${STANDALONE_MODE}" == "true" ]]; then
    # 独立模式：交互式选择
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

  # 3. 解析服务地址
  resolve_service_urls

  # 4. 登录 Higress Console
  log ""
  log "========== 登录服务 =========="

  if ! login_higress; then
    err "Higress Console 登录失败"
    err "请检查 HIGRESS_CONSOLE_URL 和 HIGRESS_PASSWORD"
    exit 1
  fi

  # 5. 登录 HiMarket
  if ! login_himarket; then
    err "HiMarket Admin 登录失败"
    err "请检查 HIMARKET_API_URL / ADMIN_USERNAME / ADMIN_PASSWORD"
    exit 1
  fi

  # 6. 获取共享资源
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
    # Portal 失败不阻断
  fi
  log "Portal ID: ${portal_id:-<未获取>}"

  # 7. 逐个处理模型
  local i success_count=0 fail_count=0
  for (( i=1; i<=model_count; i++ )); do
    load_model_vars "${i}"

    # 补全预设字段
    resolve_provider_preset "${AI_MODEL_PROVIDER}" 2>/dev/null || true

    if process_one_model "${i}" "${gateway_id}" "${portal_id}"; then
      success_count=$((success_count + 1))
    else
      fail_count=$((fail_count + 1))
    fi
  done

  # 8. 汇总
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
