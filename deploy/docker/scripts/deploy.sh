#!/usr/bin/env bash
# Himarket Docker All-in-One 一键部署脚本
# 功能：一行命令拉起 Himarket/Higress/Nacos，并完成数据初始化
#
# 使用方法：
#   ./deploy.sh install        - 部署全栈服务并初始化
#   ./deploy.sh himarket-only  - 仅部署 Himarket 服务（不含 Nacos/Higress）
#   ./deploy.sh uninstall      - 卸载所有服务

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="${SCRIPT_DIR}/data"
HOOKS_DIR="${SCRIPT_DIR}/hooks"

# 导出部署模式环境变量供钩子脚本识别
export DEPLOYMENT_MODE="docker"

# 加载环境变量（优先 docker/data/.env）
if [[ -f "${DATA_DIR}/.env" ]]; then
  set -a
  source "${DATA_DIR}/.env"
  set +a
fi

# 商业化 Nacos 开关
USE_COMMERCIAL_NACOS="${USE_COMMERCIAL_NACOS:-false}"

# 内置 MySQL 开关
USE_BUILTIN_MYSQL="${USE_BUILTIN_MYSQL:-true}"

log() { echo "[deploy $(date +'%H:%M:%S')] $*"; }
err() { echo "[ERROR] $*" >&2; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || { err "缺少命令: $1"; exit 1; }
}

########################################
# 检查依赖
########################################
check_dependencies() {
  log "检查依赖..."
  require_cmd docker
  require_cmd docker-compose
  require_cmd curl
  require_cmd jq
  
  # 检查 Docker 是否正在运行
  if ! docker info >/dev/null 2>&1; then
    err "Docker 未运行，请先启动 Docker"
    exit 1
  fi
  
  log "依赖检查完成"
}

########################################
# 等待服务就绪
########################################
wait_service() {
  local service_name="$1"
  local max_wait="${2:-300}"  # 默认最多等待 5 分钟
  local interval=5
  local elapsed=0
  
  log "等待服务就绪: ${service_name}..."
  
  while (( elapsed < max_wait )); do
    # 获取容器ID
    local cid
    cid=$(docker-compose ps -q "${service_name}" 2>/dev/null || true)
    if [[ -n "$cid" ]]; then
      # 读取容器健康与状态
      local health status
      health=$(docker inspect -f '{{ if .State.Health }}{{ .State.Health.Status }}{{ end }}' "$cid" 2>/dev/null || echo "")
      status=$(docker inspect -f '{{ .State.Status }}' "$cid" 2>/dev/null || echo "")
      # 有健康检查时，必须 healthy
      if [[ "$health" == "healthy" ]]; then
        log "✓ ${service_name} 已就绪(healthy)"
        return 0
      fi
      # 无健康检查时，以 running 作为就绪标准
      if [[ -z "$health" && "$status" == "running" ]]; then
        log "✓ ${service_name} 已就绪(running)"
        return 0
      fi
    fi
    
    sleep "$interval"
    elapsed=$((elapsed + interval))
    
    if (( elapsed % 30 == 0 )); then
      local psline
      psline=$(docker-compose ps "${service_name}" 2>/dev/null | sed -n '2p' || true)
      log "等待 ${service_name} 就绪... (${elapsed}s/${max_wait}s) 状态: ${psline}"
    fi
  done
  
  err "${service_name} 启动超时"
  docker-compose logs "${service_name}" | tail -50
  return 1
}

########################################
# 执行指定阶段的钩子脚本（按序号执行）
########################################
run_hooks() {
  local phase="${1:-post_ready}"  # 默认 post_ready
  local hooks_dir="${HOOKS_DIR}/${phase}.d"

  if [[ ! -d "$hooks_dir" ]]; then
    log "钩子目录不存在，跳过: ${hooks_dir}"
    return 0
  fi

  log "执行 ${phase} 阶段钩子..."
  local hook_count=0
  # 按文件名顺序（建议使用两位数字前缀）执行
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
      log "运行钩子 [${hook_count}]: $(basename "$hook")"
      if bash "$hook"; then
        log "钩子成功: $(basename "$hook")"
      else
        err "钩子失败: $(basename "$hook")"
        return 1
      fi
    fi
  done

  if [[ $hook_count -eq 0 ]]; then
    log "${phase} 阶段无可执行钩子"
  else
    log "${phase} 阶段共执行 ${hook_count} 个钩子"
  fi
}

########################################
# 仅部署 Himarket 服务
########################################
deploy_himarket_only() {
  log "========================================"
  log "开始部署 Himarket Only..."
  log "========================================"
  
  # 检查依赖
  check_dependencies
  
  # 启动 Himarket 服务
  log "启动 Himarket 服务..."
  cd "${SCRIPT_DIR}"
  
  # 根据 USE_BUILTIN_MYSQL 开关决定是否启用内置 MySQL
  if [[ "${USE_BUILTIN_MYSQL}" == "true" ]]; then
    log "使用内置 MySQL"
    export COMPOSE_PROFILES=builtin-mysql
    docker-compose up -d mysql himarket-server himarket-admin himarket-frontend
  else
    log "使用外置 MySQL (DB_HOST=${DB_HOST})"
    # 验证外置 MySQL 配置
    if [[ -z "${DB_HOST}" || -z "${DB_PORT}" || -z "${DB_NAME}" || -z "${DB_USERNAME}" || -z "${DB_PASSWORD}" ]]; then
      err "使用外置 MySQL 时，必须配置 DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD"
      exit 1
    fi
    docker-compose up -d himarket-server himarket-admin himarket-frontend
  fi
  
  # 等待服务就绪
  log "等待 Himarket 服务启动..."
  
  # 只有使用内置 MySQL 时才等待 MySQL 服务
  if [[ "${USE_BUILTIN_MYSQL}" == "true" ]]; then
    wait_service "mysql" 120
  else
    log "跳过内置 MySQL 等待（使用外置 MySQL）"
  fi
  
  wait_service "himarket-server" 180
  wait_service "himarket-admin" 120
  wait_service "himarket-frontend" 120
  
  log "========================================"
  log "✓ Himarket Only 部署完成！"
  log "========================================"
  log ""
  log "服务访问地址："
  log "  - Himarket 管理后台: http://localhost:5174"
  log ""
  log "  - Himarket 开发者门户: http://localhost:5173"
  log ""
  log "  - Himarket Server: http://localhost:8081"
  log "========================================"
  log ""
  log "查看服务状态: docker-compose ps"
  log "查看服务日志: docker-compose logs -f [service-name]"
  log "停止所有服务: docker-compose stop"
  log "卸载所有服务: ./deploy.sh uninstall"
}

########################################
# 部署所有服务
########################################
deploy_all() {
  log "========================================"
  log "开始部署 Himarket All-in-One（全栈模式）..."
  log "========================================"
  
  # 检查依赖
  check_dependencies
  
  # 启动所有服务
  log "启动 Docker Compose 服务..."
  cd "${SCRIPT_DIR}"
  
  # 根据 USE_BUILTIN_MYSQL 开关决定是否启用内置 MySQL
  local profiles="full-stack"
  if [[ "${USE_BUILTIN_MYSQL}" == "true" ]]; then
    log "使用内置 MySQL"
    profiles="full-stack,builtin-mysql"
    export COMPOSE_PROFILES="${profiles}"
    docker-compose up -d
  else
    log "使用外置 MySQL (DB_HOST=${DB_HOST})"
    # 验证外置 MySQL 配置
    if [[ -z "${DB_HOST}" || -z "${DB_PORT}" || -z "${DB_NAME}" || -z "${DB_USERNAME}" || -z "${DB_PASSWORD}" ]]; then
      err "使用外置 MySQL 时，必须配置 DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD"
      exit 1
    fi
    export COMPOSE_PROFILES="${profiles}"
    docker-compose up -d
  fi
  
  # 等待核心服务就绪
  log "等待核心服务启动..."
  
  # 只有使用内置 MySQL 时才等待 MySQL 服务
  if [[ "${USE_BUILTIN_MYSQL}" == "true" ]]; then
    wait_service "mysql" 120
  else
    log "跳过内置 MySQL 等待（使用外置 MySQL）"
  fi
  
  wait_service "nacos" 180
  wait_service "redis-stack-server" 60
  wait_service "himarket-server" 180
  wait_service "himarket-admin" 120
  wait_service "himarket-frontend" 120
  wait_service "higress" 180
  
  # 部署阶段完成后执行 post_ready 钩子（与 Helm 一致）
  run_hooks "post_ready" || log "警告：post_ready 钩子执行失败"
  
  log "======================================="
  log "✓ 全栈部署完成！"
  log "========================================"
  log ""
  log "服务访问地址："
  log "  - Himarket 管理后台: http://localhost:5174"
  log ""
  log "  - Himarket 开发者门户: http://localhost:5173"
  log ""
  log "  - Nacos Console: http://localhost:8080"
  log ""
  log "  - Higress Console: http://localhost:8001"
  log ""
  log "查看服务状态: docker-compose ps"
  log "查看服务日志: docker-compose logs -f [service-name]"
  log "停止所有服务: docker-compose stop"
  log "卸载所有服务: ./deploy.sh uninstall"
}

########################################
# 卸载所有服务
########################################
uninstall_all() {
  log "========================================"
  log "开始卸载 Himarket All-in-One..."
  log "========================================"
  
  cd "${SCRIPT_DIR}"
  
  log "停止并删除所有容器..."
  docker-compose down
  
  log "清理数据卷（可选）..."
  read -p "是否删除数据卷？这将清除所有数据 (y/N): " -n 1 -r
  echo
  if [[ $REPLY =~ ^[Yy]$ ]]; then
    log "删除数据卷..."
    docker-compose down -v
    rm -rf "${SCRIPT_DIR}/data/mysql"
    rm -rf "${SCRIPT_DIR}/data/nacos-mysql"
    log "数据卷已删除"
  else
    log "数据卷已保留"
  fi
  
  log "========================================"
  log "✓ 卸载完成"
  log "========================================"
}

########################################
# 主函数
########################################
main() {
  local action="${1:-install}"
  
  case "$action" in
    install)
      deploy_all
      ;;
    himarket-only)
      deploy_himarket_only
      ;;
    uninstall)
      uninstall_all
      ;;
    *)
      err "未知操作：${action}"
      echo "用法: $0 {install|himarket-only|uninstall}"
      exit 1
      ;;
  esac
}

main "$@"
