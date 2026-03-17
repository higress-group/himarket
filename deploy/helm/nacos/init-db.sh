#!/usr/bin/env bash
#
# Nacos 数据库初始化脚本
# 用法: ./init-db.sh -h <host> -P <port> -u <user> -p <password> -d <database>
# 依赖: mysql client
#
# 功能:
#   1. 创建数据库（如不存在）
#   2. 执行 mysql-schema.sql 建表（幂等，CREATE TABLE IF NOT EXISTS）
#   3. 执行 sql/upgrade/*.sql 升级脚本（如有，按文件名排序）

set -euo pipefail

# ============================================================================
# 默认值（可通过命令行参数或环境变量覆盖）
# ============================================================================

DB_HOST="${DB_HOST:-}"
DB_PORT="${DB_PORT:-3306}"
DB_USERNAME="${DB_USERNAME:-}"
DB_PASSWORD="${DB_PASSWORD:-}"
DB_NAME="${DB_NAME:-nacos}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_DIR="${SCRIPT_DIR}/sql"
SCHEMA_FILE="${SQL_DIR}/mysql-schema.sql"
UPGRADE_DIR="${SQL_DIR}/upgrade"

MAX_WAIT=60
INTERVAL=3

# ============================================================================
# 颜色输出
# ============================================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

# ============================================================================
# 参数解析
# ============================================================================

usage() {
    cat <<EOF
用法: $(basename "$0") [选项]

Nacos 数据库初始化脚本（建库 + 建表，幂等可重复执行）

选项:
  -h, --host       MySQL 地址          (或环境变量 DB_HOST)
  -P, --port       MySQL 端口          (或环境变量 DB_PORT，默认 3306)
  -u, --user       MySQL 用户名        (或环境变量 DB_USERNAME)
  -p, --password   MySQL 密码          (或环境变量 DB_PASSWORD)
  -d, --database   数据库名            (或环境变量 DB_NAME，默认 nacos)
      --help       显示此帮助信息

示例:
  $(basename "$0") -h 127.0.0.1 -u root -p mypass -d nacos
  DB_HOST=127.0.0.1 DB_USERNAME=root DB_PASSWORD=mypass $(basename "$0")
EOF
    exit 0
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -h|--host)      DB_HOST="$2";     shift 2 ;;
            -P|--port)      DB_PORT="$2";     shift 2 ;;
            -u|--user)      DB_USERNAME="$2"; shift 2 ;;
            -p|--password)  DB_PASSWORD="$2"; shift 2 ;;
            -d|--database)  DB_NAME="$2";     shift 2 ;;
            --help)         usage ;;
            *)              error "未知参数: $1"; usage ;;
        esac
    done
}

validate_args() {
    local missing=()
    [[ -z "$DB_HOST" ]]     && missing+=("DB_HOST (-h)")
    [[ -z "$DB_USERNAME" ]] && missing+=("DB_USERNAME (-u)")
    [[ -z "$DB_PASSWORD" ]] && missing+=("DB_PASSWORD (-p)")

    if [[ ${#missing[@]} -gt 0 ]]; then
        error "缺少必要参数: ${missing[*]}"
        echo ""
        usage
    fi
}

# ============================================================================
# MySQL 连接工具
# ============================================================================

mysql_cmd() {
    mysql -h "$DB_HOST" -P "$DB_PORT" -u "$DB_USERNAME" -p"$DB_PASSWORD" \
        --default-character-set=utf8mb4 "$@" 2>&1
}

wait_for_mysql() {
    info "等待 MySQL 连接 ${DB_HOST}:${DB_PORT} ..."
    local elapsed=0

    while [[ $elapsed -lt $MAX_WAIT ]]; do
        if mysql_cmd -e "SELECT 1" >/dev/null 2>&1; then
            info "MySQL 连接成功"
            return 0
        fi
        sleep "$INTERVAL"
        elapsed=$((elapsed + INTERVAL))
        echo -n "."
    done

    echo ""
    error "MySQL 连接超时（${MAX_WAIT}s）: ${DB_HOST}:${DB_PORT}"
    exit 1
}

# ============================================================================
# 主逻辑
# ============================================================================

create_database() {
    info "创建数据库（如不存在）: ${DB_NAME}"
    mysql_cmd -e "CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;"
    info "数据库就绪: ${DB_NAME}"
}

execute_schema() {
    if [[ ! -f "$SCHEMA_FILE" ]]; then
        error "Schema 文件不存在: ${SCHEMA_FILE}"
        exit 1
    fi

    info "执行 Schema: $(basename "$SCHEMA_FILE")"
    mysql_cmd "$DB_NAME" < "$SCHEMA_FILE"
    info "Schema 执行完成"
}

execute_upgrades() {
    if [[ ! -d "$UPGRADE_DIR" ]]; then
        return
    fi

    local sql_files
    sql_files=$(find "$UPGRADE_DIR" -maxdepth 1 -name '*.sql' -type f 2>/dev/null | sort)

    if [[ -z "$sql_files" ]]; then
        info "无升级 SQL 需要执行"
        return
    fi

    info "执行升级 SQL ..."
    while IFS= read -r sql_file; do
        info "  -> $(basename "$sql_file")"
        mysql_cmd "$DB_NAME" < "$sql_file"
    done <<< "$sql_files"
    info "升级 SQL 执行完成"
}

show_summary() {
    local table_count
    table_count=$(mysql_cmd "$DB_NAME" -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${DB_NAME}';")

    echo ""
    info "============================================"
    info "Nacos 数据库初始化完成"
    info "============================================"
    info "  地址: ${DB_HOST}:${DB_PORT}"
    info "  库名: ${DB_NAME}"
    info "  表数: ${table_count}"
    info "============================================"
    echo ""
}

main() {
    parse_args "$@"
    validate_args

    # 检查 mysql client
    if ! command -v mysql &>/dev/null; then
        error "未找到 mysql 命令行客户端，请先安装"
        error "  macOS:  brew install mysql-client"
        error "  Ubuntu: sudo apt-get install mysql-client"
        exit 1
    fi

    echo ""
    info "Nacos 数据库初始化"
    info "  目标: ${DB_HOST}:${DB_PORT}/${DB_NAME}"
    echo ""

    wait_for_mysql
    create_database
    execute_schema
    execute_upgrades
    show_summary
}

main "$@"
