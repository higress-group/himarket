#!/usr/bin/env bash
set -Eeuo pipefail

# 读取 data 目录下的 .env（可选）以覆盖默认变量
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
DATA_DIR="${SCRIPT_DIR}/data"

# 保存已经通过 export 设置的环境变量（优先级最高）到临时文件
EXPORTED_VARS_FILE="/tmp/himarket-exported-vars-$$.tmp"
: > "$EXPORTED_VARS_FILE"  # 清空文件

# 保存当前所有非空环境变量
while IFS='=' read -r key _; do
  if [[ -n "$key" && "$key" != "_" && "$key" != "BASH_"* && "$key" != "SHLVL" ]]; then
    eval "current_value=\${$key}"
    if [[ -n "$current_value" ]]; then
      printf '%s=%s\n' "$key" "$current_value" >> "$EXPORTED_VARS_FILE"
    fi
  fi
done < <(compgen -e)

if [[ -f "${DATA_DIR}/.env" ]]; then
  set -a
  . "${DATA_DIR}/.env"
  set +a
fi

# 恢复通过 export 设置的环境变量，确保其优先级最高
if [[ -s "$EXPORTED_VARS_FILE" ]]; then
  while IFS='=' read -r key value; do
    if [[ -n "$key" ]]; then
      export "$key"="$value"
    fi
  done < "$EXPORTED_VARS_FILE"
fi

# 清理临时文件
rm -f "$EXPORTED_VARS_FILE"

# 统一命名空间（从环境变量读取）
NS="${NAMESPACE:-himarket}"

# 商业化 Nacos 开关
USE_COMMERCIAL_NACOS="${USE_COMMERCIAL_NACOS:-false}"

# 仅部署 HiMarket 开关
HIMARKET_ONLY="${HIMARKET_ONLY:-false}"

# Chart 与值文件（相对路径）
HIMARKET_CHART_PATH="${PROJECT_ROOT}"
HIMARKET_VALUES_FILE="${PROJECT_ROOT}/values.yaml"

# HiMarket 镜像配置（可在 .env 中覆盖）
HIMARKET_HUB="${HIMARKET_HUB}"
HIMARKET_IMAGE_TAG="${HIMARKET_IMAGE_TAG}"
HIMARKET_MYSQL_IMAGE_TAG="${HIMARKET_MYSQL_IMAGE_TAG}"

# MySQL 数据库配置（可在 .env 中覆盖）
HIMARKET_MYSQL_ENABLED="${HIMARKET_MYSQL_ENABLED:-}"
EXTERNAL_DB_HOST="${EXTERNAL_DB_HOST:-}"
EXTERNAL_DB_PORT="${EXTERNAL_DB_PORT:-3306}"
EXTERNAL_DB_NAME="${EXTERNAL_DB_NAME:-}"
EXTERNAL_DB_USERNAME="${EXTERNAL_DB_USERNAME:-}"
EXTERNAL_DB_PASSWORD="${EXTERNAL_DB_PASSWORD:-}"

# Helm 仓库配置（可在 .env 中覆盖）
HIGRESS_REPO_NAME="${HIGRESS_REPO_NAME:-higress.io}"
HIGRESS_REPO_URL="${HIGRESS_REPO_URL:-https://higress.cn/helm-charts}"
HIGRESS_CHART_REF="${HIGRESS_CHART_REF:-higress.io/higress}"

NACOS_REPO_NAME="${NACOS_REPO_NAME:-ygqygq2}"
NACOS_REPO_URL="${NACOS_REPO_URL:-https://ygqygq2.github.io/charts/}"
NACOS_CHART_REF="${NACOS_CHART_REF:-ygqygq2/nacos}"

# Nacos 版本号（可在 .env 中覆盖）
NACOS_VERSION="${NACOS_VERSION}"

# Nacos 镜像配置（可在 .env 中覆盖）
NACOS_IMAGE_REGISTRY="${NACOS_IMAGE_REGISTRY:-}"
NACOS_IMAGE_REPOSITORY="${NACOS_IMAGE_REPOSITORY:-}"
NACOS_PLUGIN_IMAGE_REGISTRY="${NACOS_PLUGIN_IMAGE_REGISTRY:-}"
NACOS_PLUGIN_IMAGE_REPOSITORY="${NACOS_PLUGIN_IMAGE_REPOSITORY:-}"
NACOS_PLUGIN_IMAGE_TAG="${NACOS_PLUGIN_IMAGE_TAG:-}"
NACOS_INITDB_IMAGE_REGISTRY="${NACOS_INITDB_IMAGE_REGISTRY:-}"
NACOS_INITDB_IMAGE_REPOSITORY="${NACOS_INITDB_IMAGE_REPOSITORY:-}"
NACOS_INITDB_IMAGE_TAG="${NACOS_INITDB_IMAGE_TAG:-}"

# Nacos MySQL 镜像配置（可在 .env 中覆盖）
NACOS_MYSQL_IMAGE_REGISTRY="${NACOS_MYSQL_IMAGE_REGISTRY:-}"
NACOS_MYSQL_IMAGE_REPOSITORY="${NACOS_MYSQL_IMAGE_REPOSITORY:-}"
NACOS_MYSQL_IMAGE_TAG="${NACOS_MYSQL_IMAGE_TAG:-}"

# Higress Console 用户名密码（可在 .env 中覆盖）
HIGRESS_USERNAME="${HIGRESS_USERNAME:-admin}"
HIGRESS_PASSWORD="${HIGRESS_PASSWORD:-admin}"

log() { echo "[deploy $(date +'%H:%M:%S')] $*"; }
err() { echo "[ERROR] $*" >&2; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { err "缺少命令: $1"; exit 1; }
}

create_ns() {
  local ns="$1"
  kubectl get ns "$ns" >/dev/null 2>&1 || kubectl create ns "$ns"
}

helm_upsert() {
  # args: release ns chart_ref extra_args...
  local release="$1"; shift
  local ns="$1"; shift
  local chart="$1"; shift
  local max_attempts=3
  local attempt=1
  while (( attempt <= max_attempts )); do
    log "安装/升级 ${release} (第 ${attempt}/${max_attempts} 次)..."
    if helm upgrade --install "$release" "$chart" -n "$ns" \
         --create-namespace --wait --atomic --timeout 20m "$@"; then
      log "${release} 安装成功"
      return 0
    else
      err "${release} 安装失败（第 ${attempt} 次），准备重试"
      helm uninstall "$release" -n "$ns" >/dev/null 2>&1 || true
      sleep 8
      attempt=$((attempt+1))
    fi
  done
  err "${release} 多次安装失败"
  return 1
}

wait_rollout() {
  # args: ns kind name timeoutSeconds
  local ns="$1"; shift
  local kind="$1"; shift
  local name="$1"; shift
  local timeout="${1:-900}"
  log "等待 ${ns} 中 ${kind}/${name} 就绪..."
  if ! kubectl rollout status -n "$ns" "${kind}/${name}" --timeout="${timeout}s"; then
    err "等待 ${kind}/${name} 就绪超时"
    kubectl describe "$kind" "$name" -n "$ns" || true
    kubectl get pods -n "$ns" -o wide || true
    return 1
  fi
}

# 安全更新 Higress ConfigMap 的 mcpServer 配置
update_higress_mcp_redis() {
  local ns="$1"
  log "开始配置 Higress mcpServer.redis..."

  # 检查 ConfigMap 是否存在
  if ! kubectl get configmap higress-config -n "$ns" >/dev/null 2>&1; then
    err "ConfigMap higress-config 不存在于命名空间 ${ns}"
    return 1
  fi

  # 定义临时文件路径
  local tmp_higress="/tmp/higress-data-${RANDOM}.yaml"
  local tmp_patch="/tmp/higress-patch-${RANDOM}.json"
  local tmp_python_script="/tmp/json_escape_script-${RANDOM}.py"

  # 1. 提取 data.higress 内容 (原始JSON字符串，包含转义的\n)
  local current_higress_content
  current_higress_content=$(kubectl get configmap higress-config -n "$ns" -o jsonpath='{.data.higress}')

  # 2. 反转义并保存到临时文件
  printf "%b" "$current_higress_content" > "$tmp_higress"

  # 定义新的 mcpServer 配置块
  local mcp_config='mcpServer:
  enable: true
  redis:
    address: "redis-stack-server:6379"
    password: ""
    db: 0
    username: "redis-stack-server"
  servers: []
  sse_path_suffix: "/sse"'

  # 3. 修改 data.higress 内容
  # 检查是否已有 mcpServer 配置(可能有缩进)
  if grep -q '^mcpServer:' "$tmp_higress" || grep -q '^  mcpServer:' "$tmp_higress"; then
    log "检测到已有 mcpServer 配置，准备替换..."
    # 使用 sed 删除从 mcpServer: 开始到下一个顶级键之前的所有行
    sed -i.bak '/^mcpServer:/,/^[a-zA-Z]/{ /^[a-zA-Z]/!d; /^mcpServer:/d; }' "$tmp_higress"
    # 再次清理，以防有缩进的 mcpServer
    sed -i.bak '/^  mcpServer:/,/^[a-zA-Z]/{ /^[a-zA-Z]/!d; /^  mcpServer:/d; }' "$tmp_higress"
  else
    log "未检测到 mcpServer 配置，将新增该配置块"
  fi

  # 在文件末尾追加新的 mcpServer 配置
  echo "" >> "$tmp_higress"
  echo "$mcp_config" >> "$tmp_higress"

  # 4. 使用 Python 脚本将修改后的内容进行完整的 JSON 字符串转义
  cat <<'EOF' > "$tmp_python_script"
import json
import sys

# 读取文件内容
with open(sys.argv[1], 'r') as f:
    content = f.read()
escaped_content = json.dumps(content)[1:-1]
print(escaped_content)
EOF

  local updated_higress_content
  updated_higress_content=$(python3 "$tmp_python_script" "$tmp_higress")

  # 5. 构造 JSON Patch 文件
  cat <<EOF > "$tmp_patch"
[
  {
    "op": "replace",
    "path": "/data/higress",
    "value": "$updated_higress_content"
  }
]
EOF

  # 6. 使用 kubectl patch 应用修改
  log "应用 mcpServer 配置到 ConfigMap..."
  if kubectl patch configmap higress-config -n "$ns" --type='json' --patch-file "$tmp_patch"; then
    log "mcpServer 配置已成功应用"
    # 重启 higress-gateway 以应用新配置
    log "重启 higress-gateway 以应用配置..."
    kubectl rollout restart deployment higress-gateway -n "$ns" >/dev/null 2>&1 || true
    local result=0
  else
    err "应用 mcpServer 配置失败"
    local result=1
  fi

  # 清理临时文件
  rm -f "$tmp_higress" "${tmp_higress}.bak" "$tmp_patch" "$tmp_python_script"

  return $result
}


# 执行指定阶段的钩子脚本
run_hooks() {
  local phase="$1"  # 如 post_ready
  local hooks_dir="${SCRIPT_DIR}/hooks/${phase}.d"

  if [[ ! -d "$hooks_dir" ]]; then
    log "钩子目录不存在，跳过: ${hooks_dir}"
    return 0
  fi

  log "执行 ${phase} 阶段钩子..."

  # 按文件名排序执行所有 .sh 脚本
  local hook_count=0
  for hook in "${hooks_dir}"/*.sh; do
    if [[ -f "$hook" && -x "$hook" ]]; then
      local hook_name=$(basename "$hook")
      
      # 根据商业化 Nacos 开关跳过特定脚本
      if [[ "${USE_COMMERCIAL_NACOS}" == "true" ]]; then
        # 使用商业化 Nacos 时，跳过开源 Nacos 相关脚本
        if [[ "$hook_name" == "10-init-nacos-admin.sh" || "$hook_name" == "35-import-nacos-mcp.sh" ]]; then
          log "跳过钩子: ${hook_name} (已启用商业化 Nacos)"
          continue
        fi
      else
        # 使用开源 Nacos 时，跳过商业化 Nacos 脚本
        if [[ "$hook_name" == "25-init-commercial-nacos.sh" ]]; then
          log "跳过钩子: ${hook_name} (未启用商业化 Nacos)"
          continue
        fi
      fi
      
      hook_count=$((hook_count+1))
      log "运行钩子 [${hook_count}]: ${hook_name}"

      # 继承当前环境变量（NS、DB_*等）并执行
      if bash "$hook"; then
        log "钩子成功: ${hook_name}"
      else
        err "钩子失败: ${hook_name}"
        if [[ "${SKIP_HOOK_ERRORS:-false}" != "true" ]]; then
          return 1
        fi
        log "警告：跳过钩子错误（SKIP_HOOK_ERRORS=true）"
      fi
    fi
  done

  if [[ $hook_count -eq 0 ]]; then
    log "${phase} 阶段无可执行钩子"
  else
    log "${phase} 阶段共执行 ${hook_count} 个钩子"
  fi
}

cluster_preflight() {
  require_cmd kubectl
  require_cmd helm
  kubectl cluster-info >/dev/null
  log "当前上下文: $(kubectl config current-context || echo 'unknown')"
}

add_repos() {
  helm repo add "$HIGRESS_REPO_NAME" "$HIGRESS_REPO_URL"
  helm repo add "$NACOS_REPO_NAME" "$NACOS_REPO_URL"
  helm repo update "$HIGRESS_REPO_NAME"
  helm repo update "$NACOS_REPO_NAME"
}

deploy_all() {
  cluster_preflight
  create_ns "$NS"

  # 如果是仅部署 HiMarket 模式
  if [[ "${HIMARKET_ONLY}" == "true" ]]; then
    log "仅部署 HiMarket 模式（跳过 Nacos、Higress 及所有钩子）"
    
    # 只部署 HiMarket
    helm_upsert "himarket" "$NS" "$HIMARKET_CHART_PATH" -f "$HIMARKET_VALUES_FILE" \
      --set "hub=${HIMARKET_HUB}" \
      --set "frontend.image.tag=${HIMARKET_IMAGE_TAG}" \
      --set "admin.image.tag=${HIMARKET_IMAGE_TAG}" \
      --set "server.image.tag=${HIMARKET_IMAGE_TAG}" \
      --set "mysql.image.tag=${HIMARKET_MYSQL_IMAGE_TAG}" \
      --set "mysql.enabled=${HIMARKET_MYSQL_ENABLED}" \
      --set "database.host=${EXTERNAL_DB_HOST}" \
      --set "database.port=${EXTERNAL_DB_PORT}" \
      --set "database.name=${EXTERNAL_DB_NAME}" \
      --set "database.username=${EXTERNAL_DB_USERNAME}" \
      --set "database.password=${EXTERNAL_DB_PASSWORD}"
    
    log "HiMarket 部署完成。所有资源安装在命名空间: ${NS}"
    return 0
  fi

  # 完整部署模式
  add_repos

  # 1) 部署 HiMarket
  log "准备部署 HiMarket..."
  helm_upsert "himarket" "$NS" "$HIMARKET_CHART_PATH" -f "$HIMARKET_VALUES_FILE" \
    --set "hub=${HIMARKET_HUB}" \
    --set "frontend.image.tag=${HIMARKET_IMAGE_TAG}" \
    --set "admin.image.tag=${HIMARKET_IMAGE_TAG}" \
    --set "server.image.tag=${HIMARKET_IMAGE_TAG}" \
    --set "mysql.image.tag=${HIMARKET_MYSQL_IMAGE_TAG}" \
    --set "mysql.enabled=${HIMARKET_MYSQL_ENABLED}" \
    --set "database.host=${EXTERNAL_DB_HOST}" \
    --set "database.port=${EXTERNAL_DB_PORT}" \
    --set "database.name=${EXTERNAL_DB_NAME}" \
    --set "database.username=${EXTERNAL_DB_USERNAME}" \
    --set "database.password=${EXTERNAL_DB_PASSWORD}"

  # 2) 部署 Nacos（根据开关决定是否部署开源版本）
  if [[ "${USE_COMMERCIAL_NACOS}" == "true" ]]; then
    log "使用商业化 Nacos 实例，跳过开源 Nacos 部署"
    log "商业化 Nacos 将在 post_ready 阶段进行初始化"
  else
    log "部署开源 Nacos..."
    local nacos_args=()
    nacos_args+=("--set" "service.type=LoadBalancer")
    nacos_args+=("--set" "mysql.enabled=true")
    
    [[ -n "${NACOS_MYSQL_IMAGE_REGISTRY}" ]] && nacos_args+=("--set" "mysql.image.registry=${NACOS_MYSQL_IMAGE_REGISTRY}")
    [[ -n "${NACOS_MYSQL_IMAGE_REPOSITORY}" ]] && nacos_args+=("--set" "mysql.image.repository=${NACOS_MYSQL_IMAGE_REPOSITORY}")
    [[ -n "${NACOS_MYSQL_IMAGE_TAG}" ]] && nacos_args+=("--set" "mysql.image.tag=${NACOS_MYSQL_IMAGE_TAG}")
    [[ -n "${NACOS_IMAGE_REGISTRY}" ]] && nacos_args+=("--set" "image.registry=${NACOS_IMAGE_REGISTRY}")
    [[ -n "${NACOS_IMAGE_REPOSITORY}" ]] && nacos_args+=("--set" "image.repository=${NACOS_IMAGE_REPOSITORY}")
    [[ -n "${NACOS_VERSION}" ]] && nacos_args+=("--set" "image.tag=${NACOS_VERSION}")
    [[ -n "${NACOS_PLUGIN_IMAGE_REGISTRY}" ]] && nacos_args+=("--set" "plugin.image.registry=${NACOS_PLUGIN_IMAGE_REGISTRY}")
    [[ -n "${NACOS_PLUGIN_IMAGE_REPOSITORY}" ]] && nacos_args+=("--set" "plugin.image.repository=${NACOS_PLUGIN_IMAGE_REPOSITORY}")
    [[ -n "${NACOS_PLUGIN_IMAGE_TAG}" ]] && nacos_args+=("--set" "plugin.image.tag=${NACOS_PLUGIN_IMAGE_TAG}")
    [[ -n "${NACOS_INITDB_IMAGE_REGISTRY}" ]] && nacos_args+=("--set" "initDB.image.registry=${NACOS_INITDB_IMAGE_REGISTRY}")
    [[ -n "${NACOS_INITDB_IMAGE_REPOSITORY}" ]] && nacos_args+=("--set" "initDB.image.repository=${NACOS_INITDB_IMAGE_REPOSITORY}")
    [[ -n "${NACOS_INITDB_IMAGE_TAG}" ]] && nacos_args+=("--set" "initDB.image.tag=${NACOS_INITDB_IMAGE_TAG}")
    
    helm_upsert "nacos" "$NS" "$NACOS_CHART_REF" "${nacos_args[@]}"
    wait_rollout "$NS" "statefulset" "nacos" 900
  fi

  # 3) 部署 Higress（官方 chart），直接设置参数
  helm_upsert "higress" "$NS" "$HIGRESS_CHART_REF" \
    --set "higress-core.global.enableRedis=true" \
    --set "higress-console.service.type=LoadBalancer" \
    --set "higress-console.admin.username=${HIGRESS_USERNAME}" \
    --set "higress-console.admin.password=${HIGRESS_PASSWORD}"
  wait_rollout "$NS" "deployment" "higress-gateway" 900
  wait_rollout "$NS" "deployment" "higress-controller" 600

  # 配置 Higress MCP Redis 到 ConfigMap（安全合并，不覆盖其它字段）
  update_higress_mcp_redis "$NS" || log "警告：mcpServer 配置更新失败，请手动检查"

  # 4) 执行 post_ready 阶段钩子（数据初始化等）
  log "所有组件部署就绪，开始执行数据初始化钩子..."
  run_hooks "post_ready" || log "警告：部分钩子执行失败，请检查日志"

  log "全部部署完成。所有资源安装在命名空间: ${NS}"
  log "服务暴露方式固定为 LoadBalancer；请通过 EXTERNAL-IP 访问前端/管理与网关。"
}

uninstall_all() {
  log "开始卸载所有组件（命名空间保留）..."
  helm uninstall higress -n "$NS" || true
  helm uninstall nacos -n "$NS" || true
  helm uninstall himarket -n "$NS" || true
  log "卸载完成。"
}

main() {
  local action="${1:-install}"
  case "$action" in
    install) deploy_all ;;
    himarket-only) 
      HIMARKET_ONLY="true"
      deploy_all 
      ;;
    uninstall) uninstall_all ;;
    *) err "未知操作：${action}（支持：install|himarket-only|uninstall）" ; exit 1 ;;
  esac
}

main "$@"
