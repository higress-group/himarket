#!/usr/bin/env bash
# Portal 开发者账号注册与订阅初始化钩子 (Docker 环境)
# 由 deploy.sh 在部署就绪后自动调用
# 功能: 审批开发者账号，订阅所有已发布到 Portal 的 API Products

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="${SCRIPT_DIR}/../../data"

# 从 .env 加载环境变量
if [[ -f "${DATA_DIR}/.env" ]]; then
  set -a
  . "${DATA_DIR}/.env"
  set +a
fi

# 默认凭据
FRONT_USERNAME="${FRONT_USERNAME:-demo}"
FRONT_PASSWORD="${FRONT_PASSWORD:-demo123}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin}"

# 最大重试次数
MAX_RETRIES=3

log() { echo "[init-portal-developer $(date +'%H:%M:%S')] $*"; }
err() { echo "[ERROR] $*" >&2; }

# 检查 jq 是否安装
if ! command -v jq &> /dev/null; then
  err "jq 未安装,请先安装 jq: brew install jq (macOS) 或 apt-get install jq (Ubuntu)"
  exit 1
fi

# 全局变量（Docker 环境使用 localhost）
FRONTEND_HOST="localhost:5173"
ADMIN_HOST="localhost:5174"
ADMIN_TOKEN=""
DEVELOPER_ID=""
PORTAL_ID=""
CONSUMER_ID=""
FRONTEND_TOKEN=""

# 存储所有订阅成功的产品ID
declare -a SUBSCRIBED_PRODUCTS=()

########################################
# 调用 API 通用函数
########################################
call_api() {
  local api_name="$1"
  local method="$2"
  local host="$3"
  local path="$4"
  local body="${5:-}"
  local extra_args="${6:-}"
  
  local url="http://${host}${path}"
  
  log "调用接口 [${api_name}]: ${method} ${url}"
  
  local curl_cmd="curl -sS -w '\nHTTP_CODE:%{http_code}' -X ${method} '${url}'"
  curl_cmd="${curl_cmd} -H 'Content-Type: application/json'"
  curl_cmd="${curl_cmd} -H 'Accept: application/json, text/plain, */*'"
  
  if [[ -n "$body" ]]; then
    curl_cmd="${curl_cmd} -d '${body}'"
  fi
  
  curl_cmd="${curl_cmd} ${extra_args} --connect-timeout 10 --max-time 30"
  
  local result=$(eval "$curl_cmd" 2>&1 || echo "HTTP_CODE:000")
  
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
  
  if [[ "$http_code" =~ ^2[0-9]{2}$ ]]; then
    return 0
  elif [[ "$http_code" == "409" ]]; then
    log "资源已存在，视为成功（幂等性）"
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
  local value=""
  
  value=$(echo "$json" | sed -n "s/.*\"${field}\"[[:space:]]*:[[:space:]]*\"\([^\"]*\)\".*/\1/p" | head -1)
  
  if [[ -z "$value" ]]; then
    value=$(echo "$json" | grep -o "\"${field}\":\"[^\"]*\"" | head -1 | sed "s/\"${field}\":\"//" | sed 's/"$//')
  fi
  
  echo "$value"
}

########################################
# 步骤 1: 注册门户账号（幂等）
########################################
step_1_register_developer() {
  local body="{\"username\":\"${FRONT_USERNAME}\",\"password\":\"${FRONT_PASSWORD}\"}"
  
  local attempt=1
  while (( attempt <= MAX_RETRIES )); do
    log "执行步骤 1: 注册门户账号 (第 ${attempt}/${MAX_RETRIES} 次)..."
    
    if call_api "注册门户账号" "POST" "${FRONTEND_HOST}" "/api/v1/developers" "$body"; then
      # 如果是 409，说明已存在
      if [[ "$API_HTTP_CODE" == "409" ]]; then
        log "门户账号已存在，视为注册成功（幂等）"
        return 0
      elif [[ "$API_HTTP_CODE" == "200" ]]; then
        log "门户账号注册成功"
        return 0
      fi
    else
      err "注册门户账号失败 (第 ${attempt} 次)"
      if (( attempt < MAX_RETRIES )); then
        log "等待 5 秒后重试..."
        sleep 5
      fi
    fi
    
    attempt=$((attempt+1))
  done
  
  err "注册门户账号失败，已达最大重试次数"
  return 1
}

########################################
# 步骤 2: 管理员登录
########################################
step_2_admin_login() {
  local body="{\"username\":\"${ADMIN_USERNAME}\",\"password\":\"${ADMIN_PASSWORD}\"}"
  
  local attempt=1
  while (( attempt <= MAX_RETRIES )); do
    log "执行步骤 2: 管理员登录 (第 ${attempt}/${MAX_RETRIES} 次)..."
    
    if call_api "管理员登录" "POST" "${ADMIN_HOST}" "/api/v1/admins/login" "$body"; then
      # 提取 token
      ADMIN_TOKEN=$(extract_json_field "$API_RESPONSE" "access_token")
      
      if [[ -z "$ADMIN_TOKEN" ]]; then
        ADMIN_TOKEN=$(extract_json_field "$API_RESPONSE" "token")
      fi
      
      if [[ -z "$ADMIN_TOKEN" ]]; then
        ADMIN_TOKEN=$(extract_json_field "$API_RESPONSE" "accessToken")
      fi
      
      if [[ -z "$ADMIN_TOKEN" ]]; then
        err "无法从登录响应中提取 token"
        return 1
      fi
      
      log "管理员登录成功，获取到 Token: ${ADMIN_TOKEN:0:20}..."
      return 0
    else
      err "管理员登录失败 (第 ${attempt} 次)"
      if (( attempt < MAX_RETRIES )); then
        log "等待 5 秒后重试..."
        sleep 5
      fi
    fi
    
    attempt=$((attempt+1))
  done
  
  err "管理员登录失败，已达最大重试次数"
  return 1
}

########################################
# 步骤 3: 获取 Portal ID 和 Developer ID
########################################
step_3_get_developer_info() {
  # 先获取 Portal 列表获取 Portal ID
  local extra_args="-H 'Authorization: Bearer ${ADMIN_TOKEN}'"
  
  local attempt=1
  while (( attempt <= MAX_RETRIES )); do
    log "执行步骤 3.1: 获取 Portal ID (第 ${attempt}/${MAX_RETRIES} 次)..."
    
    if call_api "查询Portal列表" "GET" "${ADMIN_HOST}" "/api/v1/portals" "" "$extra_args"; then
      # 从列表中提取第一个 portalId
      PORTAL_ID=$(extract_json_field "$API_RESPONSE" "portalId")
      
      if [[ -z "$PORTAL_ID" ]]; then
        err "无法从响应中提取 portalId"
        if (( attempt < MAX_RETRIES )); then
          log "等待 5 秒后重试..."
          sleep 5
          attempt=$((attempt+1))
          continue
        else
          return 1
        fi
      fi
      
      log "获取到 Portal ID: ${PORTAL_ID}"
      break
    else
      err "查询 Portal 列表失败 (第 ${attempt} 次)"
      if (( attempt < MAX_RETRIES )); then
        log "等待 5 秒后重试..."
        sleep 5
      fi
    fi
    
    attempt=$((attempt+1))
  done
  
  if [[ -z "$PORTAL_ID" ]]; then
    err "无法获取 Portal ID"
    return 1
  fi
  
  # 查询该 Portal 下的开发者列表
  attempt=1
  while (( attempt <= MAX_RETRIES )); do
    log "执行步骤 3.2: 获取 Developer ID (第 ${attempt}/${MAX_RETRIES} 次)..."
    
    if call_api "查询开发者列表" "GET" "${ADMIN_HOST}" "/api/v1/developers?portalId=${PORTAL_ID}&page=1&size=10" "" "$extra_args"; then
      local temp_response="$API_RESPONSE"
      
      # 查找包含目标用户名的开发者对象
      if echo "$temp_response" | grep -q "\"username\":\"${FRONT_USERNAME}\""; then
        # 提取该用户的 developerId
        DEVELOPER_ID=$(echo "$temp_response" | sed -n "s/.*\"developerId\":\"\([^\"]*\)\"[^}]*\"username\":\"${FRONT_USERNAME}\".*/\\1/p" | head -1)
        
        if [[ -z "$DEVELOPER_ID" ]]; then
          DEVELOPER_ID=$(echo "$temp_response" | sed -n "s/.*\"username\":\"${FRONT_USERNAME}\"[^}]*\"developerId\":\"\([^\"]*\)\".*/\\1/p" | head -1)
        fi
        
        if [[ -z "$DEVELOPER_ID" ]]; then
          local user_block=$(echo "$temp_response" | grep -o "{[^}]*\"username\":\"${FRONT_USERNAME}\"[^}]*}" | head -1)
          DEVELOPER_ID=$(extract_json_field "$user_block" "developerId")
        fi
      fi
      
      if [[ -z "$DEVELOPER_ID" ]]; then
        err "未找到用户名为 ${FRONT_USERNAME} 的开发者"
        if (( attempt < MAX_RETRIES )); then
          log "等待 5 秒后重试..."
          sleep 5
          attempt=$((attempt+1))
          continue
        else
          return 1
        fi
      fi
      
      log "获取到 Developer ID: ${DEVELOPER_ID}"
      return 0
    else
      err "查询开发者列表失败 (第 ${attempt} 次)"
      if (( attempt < MAX_RETRIES )); then
        log "等待 5 秒后重试..."
        sleep 5
      fi
    fi
    
    attempt=$((attempt+1))
  done
  
  err "获取 Developer ID 失败，已达最大重试次数"
  return 1
}

########################################
# 步骤 4: 审批开发者（幂等）
########################################
step_4_approve_developer() {
  if [[ -z "$DEVELOPER_ID" ]] || [[ -z "$PORTAL_ID" ]]; then
    err "缺少 Developer ID 或 Portal ID，无法执行审批"
    return 1
  fi
  
  local body="{\"portalId\":\"${PORTAL_ID}\",\"status\":\"APPROVED\"}"
  local extra_args="-H 'Authorization: Bearer ${ADMIN_TOKEN}'"
  
  local attempt=1
  while (( attempt <= MAX_RETRIES )); do
    log "执行步骤 4: 审批开发者 (第 ${attempt}/${MAX_RETRIES} 次)..."
    
    if call_api "审批开发者" "PATCH" "${ADMIN_HOST}" "/api/v1/developers/${DEVELOPER_ID}/status" "$body" "$extra_args"; then
      log "开发者审批成功"
      return 0
    else
      # 如果已经是 APPROVED 状态，也视为成功（幂等）
      if echo "$API_RESPONSE" | grep -q "APPROVED"; then
        log "开发者已处于审批通过状态（幂等）"
        return 0
      fi
      
      err "审批开发者失败 (第 ${attempt} 次)"
      if (( attempt < MAX_RETRIES )); then
        log "等待 5 秒后重试..."
        sleep 5
      fi
    fi
    
    attempt=$((attempt+1))
  done
  
  err "审批开发者失败，已达最大重试次数"
  return 1
}

########################################
# 步骤 5: 前台登录
########################################
step_5_frontend_login() {
  local body="{\"username\":\"${FRONT_USERNAME}\",\"password\":\"${FRONT_PASSWORD}\"}"
  
  local attempt=1
  while (( attempt <= MAX_RETRIES )); do
    log "执行步骤 5: 前台登录 (第 ${attempt}/${MAX_RETRIES} 次)..."
    
    if call_api "前台登录" "POST" "${FRONTEND_HOST}" "/api/v1/developers/login" "$body"; then
      # 提取 token
      FRONTEND_TOKEN=$(extract_json_field "$API_RESPONSE" "access_token")
      
      if [[ -z "$FRONTEND_TOKEN" ]]; then
        FRONTEND_TOKEN=$(extract_json_field "$API_RESPONSE" "token")
      fi
      
      if [[ -z "$FRONTEND_TOKEN" ]]; then
        FRONTEND_TOKEN=$(extract_json_field "$API_RESPONSE" "accessToken")
      fi
      
      if [[ -z "$FRONTEND_TOKEN" ]]; then
        err "无法从登录响应中提取 token"
        return 1
      fi
      
      # 提取 consumerId（可能在登录响应中）
      CONSUMER_ID=$(extract_json_field "$API_RESPONSE" "consumerId")
      
      log "前台登录成功，获取到 Token: ${FRONTEND_TOKEN:0:20}..."
      if [[ -n "$CONSUMER_ID" ]]; then
        log "获取到 Consumer ID: ${CONSUMER_ID}"
      fi
      
      return 0
    else
      err "前台登录失败 (第 ${attempt} 次)"
      if (( attempt < MAX_RETRIES )); then
        log "等待 5 秒后重试..."
        sleep 5
      fi
    fi
    
    attempt=$((attempt+1))
  done
  
  err "前台登录失败，已达最大重试次数"
  return 1
}

########################################
# 步骤 6: 获取 Consumer ID 和所有可订阅的产品列表
########################################
step_6_get_subscription_info() {
  # 如果步骤 5 已经获取到 Consumer ID，跳过此步骤
  if [[ -n "$CONSUMER_ID" ]]; then
    log "已有 Consumer ID: ${CONSUMER_ID}，跳过查询"
  else
    # 调用 consumers 列表接口获取 consumerId
    local extra_args="-H 'Authorization: Bearer ${FRONTEND_TOKEN}'"
    
    local attempt=1
    while (( attempt <= MAX_RETRIES )); do
      log "执行步骤 6.1: 获取 Consumer ID (第 ${attempt}/${MAX_RETRIES} 次)..."
      
      if call_api "查询Consumers列表" "GET" "${FRONTEND_HOST}" "/api/v1/consumers?page=1&size=100" "" "$extra_args"; then
        # 从列表中提取第一个 consumerId
        CONSUMER_ID=$(extract_json_field "$API_RESPONSE" "consumerId")
        
        if [[ -z "$CONSUMER_ID" ]]; then
          err "无法从响应中提取 consumerId"
          if (( attempt < MAX_RETRIES )); then
            log "等待 5 秒后重试..."
            sleep 5
            attempt=$((attempt+1))
            continue
          else
            return 1
          fi
        fi
        
        log "获取到 Consumer ID: ${CONSUMER_ID}"
        break
      else
        err "查询Consumers列表失败 (第 ${attempt} 次)"
        if (( attempt < MAX_RETRIES )); then
          log "等待 5 秒后重试..."
          sleep 5
        fi
      fi
      
      attempt=$((attempt+1))
    done
    
    if [[ -z "$CONSUMER_ID" ]]; then
      err "无法获取 Consumer ID"
      return 1
    fi
  fi
  
  # 获取产品列表，查找所有可订阅的产品
  local extra_args="-H 'Authorization: Bearer ${FRONTEND_TOKEN}'"
  
  local attempt=1
  while (( attempt <= MAX_RETRIES )); do
    log "执行步骤 6.2: 获取可订阅产品列表 (第 ${attempt}/${MAX_RETRIES} 次)..."
    
    if call_api "查询产品列表" "GET" "${FRONTEND_HOST}" "/api/v1/products" "" "$extra_args"; then
      # 从列表中提取所有产品ID（使用 jq 处理）
      local product_ids=$(echo "$API_RESPONSE" | jq -r '.[] | .productId // empty' 2>/dev/null || echo "")
      
      if [[ -z "$product_ids" ]]; then
        # 如果 jq 失败，尝试手动提取
        product_ids=$(echo "$API_RESPONSE" | grep -o '"productId":"[^"]*"' | sed 's/"productId":"//' | sed 's/"//')
      fi
      
      if [[ -z "$product_ids" ]]; then
        log "警告: 未找到任何可订阅的产品"
        return 0
      fi
      
      local product_count=$(echo "$product_ids" | wc -l | tr -d ' ')
      log "找到 ${product_count} 个可订阅产品"
      
      # 将产品ID保存到全局变量（用于后续订阅）
      export PRODUCT_IDS="$product_ids"
      
      return 0
    else
      err "查询产品列表失败 (第 ${attempt} 次)"
      if (( attempt < MAX_RETRIES )); then
        log "等待 5 秒后重试..."
        sleep 5
      fi
    fi
    
    attempt=$((attempt+1))
  done
  
  err "获取产品列表失败，已达最大重试次数"
  return 1
}

########################################
# 步骤 7: 订阅所有产品（幂等）
########################################
step_7_subscribe_products() {
  if [[ -z "$CONSUMER_ID" ]]; then
    err "缺少 Consumer ID，无法执行订阅"
    return 1
  fi
  
  if [[ -z "$PRODUCT_IDS" ]]; then
    log "没有可订阅的产品，跳过订阅步骤"
    return 0
  fi
  
  local success_count=0
  local failed_count=0
  local skipped_count=0
  local failed_products=()
  local skipped_products=()
  
  # 遍历所有产品ID并订阅
  while IFS= read -r product_id; do
    [[ -z "$product_id" ]] && continue
    
    log "订阅产品: ${product_id}..."
    
    local body="{\"productId\":\"${product_id}\"}"
    local extra_args="-H 'Authorization: Bearer ${FRONTEND_TOKEN}'"
    
    local attempt=1
    local subscribed=false

    while (( attempt <= MAX_RETRIES )); do
      if call_api "订阅产品(${product_id})" "POST" "${FRONTEND_HOST}" "/api/v1/consumers/${CONSUMER_ID}/subscriptions" "$body" "$extra_args"; then
        # 如果是 409，说明已订阅
        if [[ "$API_HTTP_CODE" == "409" ]]; then
          log "产品 ${product_id} 已订阅（幂等）"
          subscribed=true
          break
        elif [[ "$API_HTTP_CODE" == "200" ]]; then
          log "产品 ${product_id} 订阅成功"
          subscribed=true
          break
        fi
      else
        # 检查是否是不支持订阅的产品（HTTP 400 且包含特定错误消息）
        if [[ "$API_HTTP_CODE" == "400" ]] && echo "$API_RESPONSE" | grep -q "不支持订阅"; then
          log "产品 ${product_id} 不支持订阅，跳过"
          skipped_count=$((skipped_count + 1))
          skipped_products+=("$product_id")
          break
        fi

        # 检查是否是重复订阅（HTTP 400 且包含"重复订阅"），视为成功（幂等性）
        if [[ "$API_HTTP_CODE" == "400" ]] && echo "$API_RESPONSE" | grep -q "重复订阅"; then
          log "产品 ${product_id} 已订阅（重复订阅，幂等）"
          subscribed=true
          break
        fi

        err "订阅产品 ${product_id} 失败 (第 ${attempt} 次)"
        if (( attempt < MAX_RETRIES )); then
          log "等待 5 秒后重试..."
          sleep 5
        fi
      fi

      attempt=$((attempt+1))
    done
    
    if [[ "$subscribed" == "true" ]]; then
      success_count=$((success_count + 1))
      SUBSCRIBED_PRODUCTS+=("$product_id")
    elif [[ $skipped_count -eq 0 ]] || ! [[ " ${skipped_products[@]} " =~ " ${product_id} " ]]; then
      # 只有在不是被跳过的产品时才计入失败
      failed_count=$((failed_count + 1))
      failed_products+=("$product_id")
    fi
    
  done <<< "$PRODUCT_IDS"
  
  log "========================================"
  log "订阅结果汇总:"
  log "成功: ${success_count} 个"
  log "跳过（不支持订阅）: ${skipped_count} 个"
  log "失败: ${failed_count} 个"
  
  if [[ ${#skipped_products[@]} -gt 0 ]]; then
    log ""
    log "跳过的产品列表（不支持订阅）:"
    for prod in "${skipped_products[@]}"; do
      log "  ⊘ ${prod}"
    done
  fi
  
  if [[ ${#failed_products[@]} -gt 0 ]]; then
    log ""
    log "失败的产品列表:"
    for prod in "${failed_products[@]}"; do
      log "  ✗ ${prod}"
    done
  fi
  
  log "========================================"
  
  # 只要有至少一个订阅成功，或者全部被跳过（不支持订阅），就认为整体成功
  if [[ $success_count -gt 0 ]] || [[ $failed_count -eq 0 ]]; then
    return 0
  else
    return 1
  fi
}

########################################
# 主流程
########################################
main() {
  log "开始初始化 Portal 开发者账号与订阅..."
  
  # 步骤 1: 注册门户账号
  if ! step_1_register_developer; then
    err "步骤 1 失败，终止执行"
    exit 1
  fi
  
  # 步骤 2: 管理员登录
  if ! step_2_admin_login; then
    err "步骤 2 失败，终止执行"
    exit 1
  fi
  
  # 步骤 3: 获取 Portal ID 和 Developer ID
  if ! step_3_get_developer_info; then
    err "步骤 3 失败，终止执行"
    exit 1
  fi
  
  # 步骤 4: 审批开发者
  if ! step_4_approve_developer; then
    err "步骤 4 失败，终止执行"
    exit 1
  fi
  
  # 步骤 5: 前台登录
  if ! step_5_frontend_login; then
    err "步骤 5 失败，终止执行"
    exit 1
  fi
  
  # 步骤 6: 获取 Consumer ID 和 Product ID
  if ! step_6_get_subscription_info; then
    err "步骤 6 失败，终止执行"
    exit 1
  fi
  
  # 步骤 7: 订阅所有产品
  if ! step_7_subscribe_products; then
    err "步骤 7 失败，终止执行"
    exit 1
  fi
  
  log "========================================"
  log "Portal 开发者账号与订阅初始化完成！"
  log "========================================"
  log "前台用户名: ${FRONT_USERNAME}"
  log "前台密码: ${FRONT_PASSWORD}"
  log "Portal ID: ${PORTAL_ID}"
  log "Developer ID: ${DEVELOPER_ID}"
  log "Consumer ID: ${CONSUMER_ID}"
  log "订阅产品数量: ${#SUBSCRIBED_PRODUCTS[@]}"
  
  if [[ ${#SUBSCRIBED_PRODUCTS[@]} -gt 0 ]]; then
    log ""
    log "已订阅的产品列表:"
    for product_id in "${SUBSCRIBED_PRODUCTS[@]}"; do
      log "  ✓ ${product_id}"
    done
  fi
  
  log "前台访问地址: http://${FRONTEND_HOST}"
  log "========================================"
}

main "$@"
