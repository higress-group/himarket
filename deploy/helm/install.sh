#!/usr/bin/env bash
# =============================================================================
# HiMarket Helm 统一部署脚本
# 默认交互式运行，支持 --non-interactive 模式
# =============================================================================
set -Eeuo pipefail

# ── 路径变量 ──────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HIMARKET_CHART_PATH="${SCRIPT_DIR}/himarket"
NACOS_CHART_PATH="${SCRIPT_DIR}/nacos"
HOOKS_DIR="${SCRIPT_DIR}/hooks"
export SHARED_DATA_DIR="$(cd "${SCRIPT_DIR}/../data" && pwd)"
ENV_FILE="${HOME}/himarket-install.env"

# ── 日志重定向 ────────────────────────────────────────────────────────────────
HIMARKET_LOG_FILE="${HOME}/himarket-install.log"
exec > >(tee -a "${HIMARKET_LOG_FILE}") 2>&1

# ── 全局标志 ──────────────────────────────────────────────────────────────────
NON_INTERACTIVE="${NON_INTERACTIVE:-0}"
ACTION="deploy"    # deploy | uninstall

# ── 解析命令行参数 ────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        -n|--non-interactive) NON_INTERACTIVE=1; shift ;;
        --uninstall)          ACTION="uninstall"; shift ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  -n, --non-interactive  跳过交互式提示，使用 ~/himarket-install.env / 默认值"
            echo "  --uninstall            卸载所有组件"
            echo "  -h, --help             显示帮助"
            exit 0
            ;;
        *) echo "未知参数: $1"; exit 1 ;;
    esac
done

# =============================================================================
# 工具函数
# =============================================================================

# ── 中英双语消息字典 ─────────────────────────────────────────────────────────
msg() {
    local key="$1"; shift
    local lang="${HIMARKET_LANGUAGE:-zh}"
    local text=""
    case "${key}" in
        install.title)
            [[ "$lang" == "zh" ]] && text="=== HiMarket Helm 部署 ===" || text="=== HiMarket Helm Deployment ===" ;;
        install.log_file)
            [[ "$lang" == "zh" ]] && text="日志文件: %s" || text="Log file: %s" ;;
        install.upgrade_detected)
            [[ "$lang" == "zh" ]] && text="检测到已有 HiMarket 部署" || text="Existing HiMarket deployment detected" ;;
        install.upgrade_image_only)
            [[ "$lang" == "zh" ]] && text="升级模式：仅可修改镜像版本，其他配置沿用已有部署值" || text="Upgrade mode: only image versions can be changed, other settings kept from existing deployment" ;;
        install.mode_prompt)
            [[ "$lang" == "zh" ]] && text="请选择操作模式:" || text="Select operation mode:" ;;
        install.mode_upgrade)
            [[ "$lang" == "zh" ]] && text="  1) 升级 — 保留现有数据，仅更新组件" || text="  1) Upgrade — keep existing data, update components only" ;;
        install.mode_reinstall)
            [[ "$lang" == "zh" ]] && text="  2) 重新安装 — 清理所有资源后全新部署（数据将丢失）" || text="  2) Reinstall — clean all resources and deploy fresh (data will be lost)" ;;
        install.mode_choice)
            [[ "$lang" == "zh" ]] && text="请输入选项" || text="Enter choice" ;;
        install.reinstall_confirm)
            [[ "$lang" == "zh" ]] && text="确认重新安装？所有数据（数据库、配置）将被清除且不可恢复 [y/N]" || text="Confirm reinstall? All data (database, config) will be permanently deleted [y/N]" ;;
        install.reinstall_cleaning)
            [[ "$lang" == "zh" ]] && text="正在清理现有部署..." || text="Cleaning up existing deployment..." ;;
        install.confirm_deploy)
            [[ "$lang" == "zh" ]] && text="确认开始部署? [Y/n]" || text="Confirm deployment? [Y/n]" ;;
        install.confirm_save)
            [[ "$lang" == "zh" ]] && text="是否保存配置到 ~/himarket-install.env? [Y/n]" || text="Save config to ~/himarket-install.env? [Y/n]" ;;
        install.cancelled)
            [[ "$lang" == "zh" ]] && text="部署已取消" || text="Deployment cancelled" ;;
        install.saved)
            [[ "$lang" == "zh" ]] && text="配置已保存到 %s" || text="Config saved to %s" ;;
        install.complete)
            [[ "$lang" == "zh" ]] && text="HiMarket 部署完成！" || text="HiMarket deployment complete!" ;;
        install.uninstall)
            [[ "$lang" == "zh" ]] && text="开始卸载所有组件..." || text="Uninstalling all components..." ;;
        install.clean_pvc)
            [[ "$lang" == "zh" ]] && text="清理残留 PVC..." || text="Cleaning up remaining PVCs..." ;;
        install.pvc_remain)
            [[ "$lang" == "zh" ]] && text="检测到以下残留 PVC（包含持久化数据）：" || text="Found remaining PVCs (containing persistent data):" ;;
        install.pvc_confirm)
            [[ "$lang" == "zh" ]] && text="是否删除这些 PVC？数据将不可恢复" || text="Delete these PVCs? Data will be unrecoverable" ;;
        install.pvc_skip)
            [[ "$lang" == "zh" ]] && text="保留 PVC，如需手动清理: kubectl delete pvc --all -n ${NAMESPACE:-himarket}" || text="PVCs kept. To clean up manually: kubectl delete pvc --all -n ${NAMESPACE:-himarket}" ;;
        install.skip_mcp_init)
            [[ "$lang" == "zh" ]] && text="是否跳过 MCP 初始化? [y/N]" || text="Skip MCP initialization? [y/N]" ;;
        install.skip_skill_init)
            [[ "$lang" == "zh" ]] && text="是否跳过 Skill 初始化? [y/N]" || text="Skip Skill initialization? [y/N]" ;;
        install.uninstall_done)
            [[ "$lang" == "zh" ]] && text="卸载完成" || text="Uninstall complete" ;;
        prompt.preset)
            [[ "$lang" == "zh" ]] && text="  %s = （已通过环境变量预设）" || text="  %s = (pre-set via env)" ;;
        prompt.upgrade_keep)
            [[ "$lang" == "zh" ]] && text="  %s = %s（当前值，回车保留 / 输入新值覆盖）" || text="  %s = %s (current, Enter to keep / type to change)" ;;
        prompt.default)
            [[ "$lang" == "zh" ]] && text="  使用默认值: %s = %s" || text="  Using default: %s = %s" ;;
        prompt.required)
            [[ "$lang" == "zh" ]] && text="错误: %s 是必需的（非交互模式请通过环境变量或 ~/himarket-install.env 设置）" || text="Error: %s is required (set via env var or ~/himarket-install.env in non-interactive mode)" ;;
        prompt.required_empty)
            [[ "$lang" == "zh" ]] && text="错误: %s 不能为空" || text="Error: %s cannot be empty" ;;
        section.basic)
            [[ "$lang" == "zh" ]] && text="--- 基础配置 ---" || text="--- Basic Config ---" ;;
        section.image)
            [[ "$lang" == "zh" ]] && text="--- 镜像配置 ---" || text="--- Image Config ---" ;;
        section.db)
            [[ "$lang" == "zh" ]] && text="--- 数据库密码 ---" || text="--- Database Passwords ---" ;;
        section.credential)
            [[ "$lang" == "zh" ]] && text="--- 服务凭证 ---" || text="--- Service Credentials ---" ;;
        section.user)
            [[ "$lang" == "zh" ]] && text="--- 默认用户 ---" || text="--- Default Users ---" ;;
        section.storage)
            [[ "$lang" == "zh" ]] && text="--- 存储配置 ---" || text="--- Storage Config ---" ;;
        section.init)
            [[ "$lang" == "zh" ]] && text="--- 初始化选项 ---" || text="--- Initialization Options ---" ;;
        section.ai_model)
            [[ "$lang" == "zh" ]] && text="--- AI 模型配置（可选）---" || text="--- AI Model Config (Optional) ---" ;;
        install.ai_model_prompt)
            [[ "$lang" == "zh" ]] && text="是否配置 AI 模型提供商? [y/N]" || text="Configure AI model provider? [y/N]" ;;
        install.ai_model_providers_title)
            [[ "$lang" == "zh" ]] && text="可用 AI 模型提供商:" || text="Available AI model providers:" ;;
        install.ai_model_provider.1)
            [[ "$lang" == "zh" ]] && text="  1) 阿里云百炼 (Qwen)          — dashscope.aliyuncs.com" || text="  1) Alibaba Cloud Qwen          — dashscope.aliyuncs.com" ;;
        install.ai_model_provider.2)
            [[ "$lang" == "zh" ]] && text="  2) 百炼 CodingPlan             — coding.dashscope.aliyuncs.com" || text="  2) Bailian CodingPlan          — coding.dashscope.aliyuncs.com" ;;
        install.ai_model_provider.3)
            text="  3) OpenAI                      — api.openai.com" ;;
        install.ai_model_provider.4)
            text="  4) DeepSeek                    — api.deepseek.com" ;;
        install.ai_model_provider.5)
            [[ "$lang" == "zh" ]] && text="  5) Moonshot (Kimi)             — api.moonshot.cn" || text="  5) Moonshot (Kimi)             — api.moonshot.cn" ;;
        install.ai_model_provider.6)
            [[ "$lang" == "zh" ]] && text="  6) 智谱 (Zhipu)               — open.bigmodel.cn" || text="  6) Zhipu AI                    — open.bigmodel.cn" ;;
        install.ai_model_provider.7)
            [[ "$lang" == "zh" ]] && text="  7) 自定义 OpenAI 兼容 API" || text="  7) Custom OpenAI-compatible API" ;;
        install.ai_model_select)
            [[ "$lang" == "zh" ]] && text="选择提供商" || text="Select provider" ;;
        install.ai_model_apikey)
            text="API Key" ;;
        install.ai_model_domain)
            [[ "$lang" == "zh" ]] && text="API 域名" || text="API domain" ;;
        install.ai_model_type)
            [[ "$lang" == "zh" ]] && text="Provider Type（如 openai）" || text="Provider Type (e.g. openai)" ;;
        install.ai_model_model)
            [[ "$lang" == "zh" ]] && text="默认模型 ID" || text="Default model ID" ;;
        install.ai_model_name)
            [[ "$lang" == "zh" ]] && text="提供商展示名称" || text="Provider display name" ;;
        install.ai_model_selected)
            [[ "$lang" == "zh" ]] && text="已选: %s（域名: %s）" || text="Selected: %s (domain: %s)" ;;
        section.summary)
            [[ "$lang" == "zh" ]] && text="--- 配置确认 ---" || text="--- Config Summary ---" ;;
        deploy.preflight)
            [[ "$lang" == "zh" ]] && text="环境预检..." || text="Preflight check..." ;;
        deploy.preflight_ok)
            [[ "$lang" == "zh" ]] && text="集群连接正常: %s" || text="Cluster connected: %s" ;;
        deploy.missing_cmd)
            [[ "$lang" == "zh" ]] && text="缺少命令: %s" || text="Missing command: %s" ;;
        deploy.ns_create)
            [[ "$lang" == "zh" ]] && text="创建命名空间: %s" || text="Creating namespace: %s" ;;
        deploy.helm_upsert)
            [[ "$lang" == "zh" ]] && text="安装/升级 %s (第 %s/%s 次)..." || text="Installing/upgrading %s (attempt %s/%s)..." ;;
        deploy.helm_ok)
            [[ "$lang" == "zh" ]] && text="%s 安装成功" || text="%s installed successfully" ;;
        deploy.helm_fail)
            [[ "$lang" == "zh" ]] && text="%s 安装失败（第 %s 次），准备重试" || text="%s install failed (attempt %s), retrying" ;;
        deploy.helm_fatal)
            [[ "$lang" == "zh" ]] && text="%s 多次安装失败" || text="%s install failed after multiple attempts" ;;
        deploy.wait)
            [[ "$lang" == "zh" ]] && text="等待 %s/%s 就绪..." || text="Waiting for %s/%s to be ready..." ;;
        deploy.wait_timeout)
            [[ "$lang" == "zh" ]] && text="等待 %s/%s 就绪超时" || text="Timed out waiting for %s/%s" ;;
        deploy.nacos_db)
            [[ "$lang" == "zh" ]] && text="初始化 Nacos 数据库: %s" || text="Initializing Nacos database: %s" ;;
        deploy.nacos_db_ok)
            [[ "$lang" == "zh" ]] && text="Nacos 数据库初始化完成（共 %s 张表）" || text="Nacos database initialized (%s tables)" ;;
        deploy.hooks)
            [[ "$lang" == "zh" ]] && text="执行 %s 阶段钩子..." || text="Running %s hooks..." ;;
        deploy.hook_run)
            [[ "$lang" == "zh" ]] && text="运行钩子 [%s]: %s" || text="Running hook [%s]: %s" ;;
        deploy.hook_ok)
            [[ "$lang" == "zh" ]] && text="钩子成功: %s" || text="Hook success: %s" ;;
        deploy.hook_fail)
            [[ "$lang" == "zh" ]] && text="钩子失败: %s" || text="Hook failed: %s" ;;
        lang.detected)
            [[ "$lang" == "zh" ]] && text="检测到语言: 中文" || text="Detected language: English" ;;
        lang.switch_title)
            [[ "$lang" == "zh" ]] && text="请选择语言 / Choose language:" || text="Choose language / 请选择语言:" ;;
        lang.option_zh)
            text="  1) 中文" ;;
        lang.option_en)
            text="  2) English" ;;
        lang.prompt)
            [[ "$lang" == "zh" ]] && text="请输入选项" || text="Enter choice" ;;
        install.ai_model_index)
            [[ "$lang" == "zh" ]] && text="--- 模型 #%s ---" || text="--- Model #%s ---" ;;
        install.ai_model_add_more)
            [[ "$lang" == "zh" ]] && text="继续添加下一个模型? [y/N]" || text="Add another model? [y/N]" ;;
        install.ai_model_count)
            [[ "$lang" == "zh" ]] && text="共配置 %s 个模型" || text="%s model(s) configured" ;;
        install.ai_model_existing_title)
            [[ "$lang" == "zh" ]] && text="检测到已配置的 AI 模型:" || text="Existing AI models detected:" ;;
        install.ai_model_existing_item)
            [[ "$lang" == "zh" ]] && text="  #%s %s（%s）— API Key: %s" || text="  #%s %s (%s) — API Key: %s" ;;
        install.ai_model_existing_action)
            [[ "$lang" == "zh" ]] && text="请选择操作:" || text="Select action:" ;;
        install.ai_model_existing_keep)
            [[ "$lang" == "zh" ]] && text="  1) 保留现有模型配置" || text="  1) Keep existing model config" ;;
        install.ai_model_existing_add)
            [[ "$lang" == "zh" ]] && text="  2) 保留现有并继续添加新模型" || text="  2) Keep existing and add more" ;;
        install.ai_model_existing_redo)
            [[ "$lang" == "zh" ]] && text="  3) 清空并重新配置" || text="  3) Clear and reconfigure" ;;
        install.ai_model_existing_skip)
            [[ "$lang" == "zh" ]] && text="  4) 跳过（不使用 AI 模型）" || text="  4) Skip (no AI models)" ;;
        install.ai_model_existing_choice)
            [[ "$lang" == "zh" ]] && text="请输入选项" || text="Enter choice" ;;
        *)
            text="${key}" ;;
    esac
    if [[ $# -gt 0 ]]; then
        # Prefix with 'X' to prevent bash printf from treating format starting
        # with '-' as option flags (e.g. "--- title ---" triggers "invalid option")
        # shellcheck disable=SC2059
        local _fmtout
        _fmtout=$(printf "X${text}" "$@")
        printf '%s\n' "${_fmtout#X}"
    else
        echo "${text}"
    fi
}

# ── 彩色日志 ─────────────────────────────────────────────────────────────────
log()   { echo -e "\033[36m[HiMarket $(date +'%H:%M:%S')]\033[0m $*"; }
warn()  { echo -e "\033[33m[HiMarket $(date +'%H:%M:%S')]\033[0m $*"; }
error() { echo -e "\033[31m[HiMarket ERROR]\033[0m $*" >&2; exit 1; }

# ── 语言检测 ─────────────────────────────────────────────────────────────────
detect_language() {
    local tz=""
    # 尝试多种方式获取时区
    if [[ -f /etc/timezone ]]; then
        tz=$(cat /etc/timezone 2>/dev/null)
    elif [[ -L /etc/localtime ]]; then
        tz=$(readlink /etc/localtime 2>/dev/null | sed 's|.*/zoneinfo/||')
    elif command -v timedatectl >/dev/null 2>&1; then
        tz=$(timedatectl show --property=Timezone --value 2>/dev/null)
    fi
    case "${tz}" in
        Asia/Shanghai|Asia/Chongqing|Asia/Harbin|Asia/Urumqi|Asia/Taipei|Asia/Hong_Kong|Asia/Macau)
            echo "zh" ;;
        *)
            echo "en" ;;
    esac
}

HIMARKET_LANGUAGE="${HIMARKET_LANGUAGE:-$(detect_language)}"

# ── prompt() — 交互式配置项提示 ──────────────────────────────────────────────
# Usage: prompt VAR_NAME "提示文本" "默认值"
prompt() {
    local var_name="$1"
    local prompt_text="$2"
    local default_value="$3"

    # 读取变量当前值
    eval "local current_value=\"\${${var_name}:-}\""

    # 有效值：当前值优先，其次默认值
    local effective="${current_value:-${default_value}}"

    # 非交互模式 → 直接使用有效值或报错
    if [[ "${NON_INTERACTIVE}" == "1" ]]; then
        if [[ -n "${effective}" ]]; then
            eval "export ${var_name}='${effective}'"
            return
        fi
        error "$(msg prompt.required "${var_name}")"
    fi

    # 交互模式 → 展示提示 + 当前/默认值，让用户确认或修改
    local display_prompt="${prompt_text}"
    [[ -n "${effective}" ]] && display_prompt="${prompt_text} [${effective}]"

    local value=""
    read -r -p "${display_prompt}: " value
    value="${value:-${effective}}"
    if [[ -z "${value}" ]]; then
        error "$(msg prompt.required_empty "${var_name}")"
    fi
    eval "export ${var_name}='${value}'"
}

# ── prompt_optional() — 可选配置项 ───────────────────────────────────────────
prompt_optional() {
    local var_name="$1"
    local prompt_text="$2"

    # 读取变量当前值
    eval "local current_value=\"\${${var_name}:-}\""

    # 非交互模式：保持当前值
    if [[ "${NON_INTERACTIVE}" == "1" ]]; then
        eval "export ${var_name}='${current_value}'"
        return
    fi

    # 交互模式 → 展示当前值，让用户确认或修改
    local display_prompt="${prompt_text}"
    [[ -n "${current_value}" ]] && display_prompt="${prompt_text} [${current_value}]"

    local value=""
    read -r -p "${display_prompt}: " value
    value="${value:-${current_value}}"
    eval "export ${var_name}='${value}'"
}

# =============================================================================
# Kubernetes / Helm 工具函数
# =============================================================================

# ── helm_upsert — helm upgrade --install with retries ────────────────────────
helm_upsert() {
    local release="$1"; shift
    local ns="$1"; shift
    local chart="$1"; shift
    local max_attempts=3
    local attempt=1

    while (( attempt <= max_attempts )); do
        log "$(msg deploy.helm_upsert "${release}" "${attempt}" "${max_attempts}")"
        if helm upgrade --install "${release}" "${chart}" -n "${ns}" \
             --create-namespace --wait --atomic --timeout 20m "$@"; then
            log "$(msg deploy.helm_ok "${release}")"
            return 0
        else
            warn "$(msg deploy.helm_fail "${release}" "${attempt}")"
            helm uninstall "${release}" -n "${ns}" >/dev/null 2>&1 || true
            sleep 8
            attempt=$((attempt + 1))
        fi
    done
    error "$(msg deploy.helm_fatal "${release}")"
}

# ── wait_rollout — kubectl rollout status ─────────────────────────────────────
wait_rollout() {
    local ns="$1"
    local kind="$2"
    local name="$3"
    local timeout="${4:-900}"

    log "$(msg deploy.wait "${kind}" "${name}")"
    if ! kubectl rollout status -n "${ns}" "${kind}/${name}" --timeout="${timeout}s"; then
        warn "$(msg deploy.wait_timeout "${kind}" "${name}")"
        kubectl describe "${kind}" "${name}" -n "${ns}" || true
        kubectl get pods -n "${ns}" -o wide || true
        return 1
    fi
}

# ── init_nacos_db_in_cluster — 集群内 MySQL 初始化 Nacos DB ──────────────────
init_nacos_db_in_cluster() {
    local ns="$1"
    local db_pass="$2"
    local db_name="$3"

    local mysql_pod="mysql-0"
    log "$(msg deploy.nacos_db "${db_name}")"

    # 等待 MySQL Pod 就绪
    if ! kubectl wait --for=condition=ready pod/"${mysql_pod}" -n "${ns}" --timeout=300s; then
        error "等待 MySQL Pod 就绪超时"
    fi

    # 创建数据库（幂等）
    kubectl exec -n "${ns}" "${mysql_pod}" -- \
        mysql -uroot -p"${db_pass}" --default-character-set=utf8mb4 \
        -e "CREATE DATABASE IF NOT EXISTS \`${db_name}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;"

    # 执行 Schema SQL
    local schema_file="${NACOS_CHART_PATH}/sql/mysql-schema.sql"
    if [[ ! -f "${schema_file}" ]]; then
        error "Schema 文件不存在: ${schema_file}"
    fi
    kubectl exec -i -n "${ns}" "${mysql_pod}" -- \
        mysql -uroot -p"${db_pass}" --default-character-set=utf8mb4 "${db_name}" \
        < "${schema_file}"

    # 执行升级 SQL（如有）
    local upgrade_dir="${NACOS_CHART_PATH}/sql/upgrade"
    if [[ -d "${upgrade_dir}" ]]; then
        local sql_files
        sql_files=$(find "${upgrade_dir}" -maxdepth 1 -name '*.sql' -type f 2>/dev/null | sort)
        if [[ -n "${sql_files}" ]]; then
            while IFS= read -r sql_file; do
                kubectl exec -i -n "${ns}" "${mysql_pod}" -- \
                    mysql -uroot -p"${db_pass}" --default-character-set=utf8mb4 "${db_name}" \
                    < "${sql_file}"
            done <<< "${sql_files}"
        fi
    fi

    # 验证
    local table_count
    table_count=$(kubectl exec -n "${ns}" "${mysql_pod}" -- \
        mysql -uroot -p"${db_pass}" --default-character-set=utf8mb4 -N \
        -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${db_name}';" 2>/dev/null | tr -d '[:space:]')
    log "$(msg deploy.nacos_db_ok "${table_count}")"
}

# ── update_higress_mcp_redis — 安全更新 Higress ConfigMap ─────────────────────
update_higress_mcp_redis() {
    local ns="$1"

    if ! kubectl get configmap higress-config -n "${ns}" >/dev/null 2>&1; then
        warn "ConfigMap higress-config 不存在，跳过 MCP Redis 配置"
        return 1
    fi

    local tmp_higress="/tmp/higress-data-${RANDOM}.yaml"
    local tmp_patch="/tmp/higress-patch-${RANDOM}.json"
    local tmp_python_script="/tmp/json_escape-${RANDOM}.py"

    # 提取 data.higress 内容
    local current_higress_content
    current_higress_content=$(kubectl get configmap higress-config -n "${ns}" -o jsonpath='{.data.higress}')
    printf "%b" "${current_higress_content}" > "${tmp_higress}"

    # 新 mcpServer 配置块
    local mcp_config='mcpServer:
  enable: true
  redis:
    address: "redis-stack-server:6379"
    password: ""
    db: 0
    username: "redis-stack-server"
  servers: []
  sse_path_suffix: "/sse"'

    # 删除旧 mcpServer 配置（如存在）
    if grep -q '^mcpServer:' "${tmp_higress}" || grep -q '^  mcpServer:' "${tmp_higress}"; then
        sed -i.bak '/^mcpServer:/,/^[a-zA-Z]/{ /^[a-zA-Z]/!d; /^mcpServer:/d; }' "${tmp_higress}"
        sed -i.bak '/^  mcpServer:/,/^[a-zA-Z]/{ /^[a-zA-Z]/!d; /^  mcpServer:/d; }' "${tmp_higress}"
    fi

    # 追加新配置
    echo "" >> "${tmp_higress}"
    echo "${mcp_config}" >> "${tmp_higress}"

    # JSON 转义
    cat <<'PYEOF' > "${tmp_python_script}"
import json, sys
with open(sys.argv[1], 'r') as f:
    content = f.read()
print(json.dumps(content)[1:-1])
PYEOF

    local updated_higress_content
    updated_higress_content=$(python3 "${tmp_python_script}" "${tmp_higress}")

    cat <<EOF > "${tmp_patch}"
[{"op":"replace","path":"/data/higress","value":"${updated_higress_content}"}]
EOF

    local result=0
    if kubectl patch configmap higress-config -n "${ns}" --type='json' --patch-file "${tmp_patch}"; then
        kubectl rollout restart deployment higress-gateway -n "${ns}" >/dev/null 2>&1 || true
    else
        warn "mcpServer 配置更新失败"
        result=1
    fi

    rm -f "${tmp_higress}" "${tmp_higress}.bak" "${tmp_patch}" "${tmp_python_script}"
    return ${result}
}

# ── run_hooks — 按序号执行钩子脚本 ───────────────────────────────────────────
run_hooks() {
    local phase="$1"
    local hooks_dir="${HOOKS_DIR}/${phase}.d"

    if [[ ! -d "${hooks_dir}" ]]; then
        return 0
    fi

    log "$(msg deploy.hooks "${phase}")"
    local hook_count=0
    local hook_failures=0
    for hook in "${hooks_dir}"/*.sh; do
        if [[ -f "${hook}" && -x "${hook}" ]]; then
            hook_count=$((hook_count + 1))
            local hook_name
            hook_name=$(basename "${hook}")
            log "$(msg deploy.hook_run "${hook_count}" "${hook_name}")"
            if bash "${hook}"; then
                log "$(msg deploy.hook_ok "${hook_name}")"
            else
                warn "$(msg deploy.hook_fail "${hook_name}")"
                hook_failures=$((hook_failures + 1))
                if [[ "${SKIP_HOOK_ERRORS:-false}" != "true" ]]; then
                    return 1
                fi
            fi
        fi
    done
    return $( (( hook_failures > 0 )) && echo 1 || echo 0 )
}

# =============================================================================
# 配置加载
# =============================================================================

load_config() {
    # 1. 保存当前 export 的环境变量（最高优先级）
    local saved_vars=""
    for var in DEPLOY_MODE NAMESPACE HIMARKET_HUB HIMARKET_IMAGE_TAG HIMARKET_MYSQL_IMAGE_TAG \
               NACOS_VERSION NACOS_IMAGE_REGISTRY NACOS_IMAGE_REPOSITORY \
               HIGRESS_REPO_NAME HIGRESS_REPO_URL HIGRESS_CHART_REF \
               MYSQL_ROOT_PASSWORD MYSQL_PASSWORD \
               NACOS_ADMIN_PASSWORD HIGRESS_USERNAME HIGRESS_PASSWORD \
               ADMIN_USERNAME ADMIN_PASSWORD FRONT_USERNAME FRONT_PASSWORD \
               MYSQL_STORAGE_CLASS MYSQL_STORAGE_SIZE SANDBOX_STORAGE_CLASS SANDBOX_STORAGE_SIZE \
               HIGRESS_INGRESS_CLASS HIMARKET_LANGUAGE \
               SKIP_MCP_INIT SKIP_SKILL_INIT SKIP_HOOK_ERRORS \
               SKIP_AI_MODEL_INIT AI_MODEL_COUNT; do
        eval "local _val=\"\${${var}:-}\""
        if [[ -n "${_val}" ]]; then
            saved_vars="${saved_vars} ${var}='${_val}'"
        fi
    done

    # 保存索引 AI 模型环境变量（最多 10 个）
    local _mi _field _varname _mval
    for (( _mi=1; _mi<=10; _mi++ )); do
        for _field in PROVIDER TYPE DOMAIN PORT PROTOCOL API_KEY NAME DEFAULT_MODEL; do
            _varname="AI_MODEL_${_mi}_${_field}"
            eval "_mval=\"\${${_varname}:-}\""
            if [[ -n "${_mval}" ]]; then
                saved_vars="${saved_vars} ${_varname}='${_mval}'"
            fi
        done
    done

    # 2. 加载配置文件（如存在）
    if [[ -f "${ENV_FILE}" ]]; then
        log "加载配置: ${ENV_FILE}"
        set -a
        # shellcheck source=/dev/null
        source "${ENV_FILE}"
        set +a
    fi

    # 3. 恢复 export 变量（覆盖配置文件中的同名变量）
    if [[ -n "${saved_vars}" ]]; then
        eval "export ${saved_vars}"
    fi
}

# ── interactive_add_models — 交互式添加 AI 模型 ──────────────────────────────
# Usage: interactive_add_models [START_INDEX]
# 从 START_INDEX 开始交互式添加模型，结果更新全局 AI_MODEL_COUNT
interactive_add_models() {
    local _model_idx="${1:-0}"
    local _add_more="y"

    while [[ "${_add_more}" =~ ^[Yy]$ ]]; do
        _model_idx=$((_model_idx + 1))
        log "$(msg install.ai_model_index "${_model_idx}")"
        echo ""
        echo "$(msg install.ai_model_providers_title)"
        echo "$(msg install.ai_model_provider.1)"
        echo "$(msg install.ai_model_provider.2)"
        echo "$(msg install.ai_model_provider.3)"
        echo "$(msg install.ai_model_provider.4)"
        echo "$(msg install.ai_model_provider.5)"
        echo "$(msg install.ai_model_provider.6)"
        echo "$(msg install.ai_model_provider.7)"
        echo ""
        local _ai_choice=""
        read -r -p "$(msg install.ai_model_select) [1]: " _ai_choice
        _ai_choice="${_ai_choice:-1}"

        local _provider="" _type="" _domain="" _protocol="" _name="" _default_model="" _port="443"
        local _skip_this="false"

        case "${_ai_choice}" in
            1)
                _provider="qwen"; _type="qwen"; _domain="dashscope.aliyuncs.com"
                _protocol=""; _name="Alibaba Cloud Qwen"; _default_model="qwen3.5-plus" ;;
            2)
                _provider="bailian-codingplan"; _type="openai"; _domain="coding.dashscope.aliyuncs.com"
                _protocol="openai/v1"; _name="Bailian CodingPlan"; _default_model="qwen3.5-plus" ;;
            3)
                _provider="openai"; _type="openai"; _domain="api.openai.com"
                _protocol=""; _name="OpenAI"; _default_model="gpt-4o" ;;
            4)
                _provider="deepseek"; _type="deepseek"; _domain="api.deepseek.com"
                _protocol=""; _name="DeepSeek"; _default_model="deepseek-chat" ;;
            5)
                _provider="moonshot"; _type="moonshot"; _domain="api.moonshot.cn"
                _protocol=""; _name="Moonshot (Kimi)"; _default_model="moonshot-v1-8k" ;;
            6)
                _provider="zhipuai"; _type="zhipuai"; _domain="open.bigmodel.cn"
                _protocol=""; _name="Zhipu AI"; _default_model="glm-4" ;;
            7)
                read -r -p "$(msg install.ai_model_domain): " _domain
                if [[ -z "${_domain}" ]]; then error "$(msg prompt.required_empty "AI_MODEL_DOMAIN")"; fi
                local _tmp_type=""
                read -r -p "$(msg install.ai_model_type) [openai]: " _tmp_type
                _type="${_tmp_type:-openai}"
                local _tmp_name=""
                read -r -p "$(msg install.ai_model_name) [Custom LLM]: " _tmp_name
                _name="${_tmp_name:-Custom LLM}"
                _provider="custom-llm"
                _protocol="openai/v1"
                local _tmp_model=""
                read -r -p "$(msg install.ai_model_model): " _tmp_model
                _default_model="${_tmp_model}" ;;
            *)
                warn "无效选项: ${_ai_choice}，请重新选择"
                _model_idx=$((_model_idx - 1))
                _skip_this="true" ;;
        esac

        if [[ "${_skip_this}" == "true" ]]; then
            _add_more="y"
            continue
        fi

        # 收集 API Key
        local _api_key=""
        read -r -p "$(msg install.ai_model_apikey): " _api_key
        if [[ -z "${_api_key}" ]]; then error "$(msg prompt.required_empty "API Key")"; fi

        # 可选覆盖默认模型
        if [[ "${_ai_choice}" != "7" ]]; then
            local _model_override=""
            read -r -p "$(msg install.ai_model_model) [${_default_model}]: " _model_override
            [[ -n "${_model_override}" ]] && _default_model="${_model_override}"
        fi

        # 存储到索引变量
        export "AI_MODEL_${_model_idx}_PROVIDER=${_provider}"
        export "AI_MODEL_${_model_idx}_TYPE=${_type}"
        export "AI_MODEL_${_model_idx}_DOMAIN=${_domain}"
        export "AI_MODEL_${_model_idx}_PORT=${_port}"
        export "AI_MODEL_${_model_idx}_PROTOCOL=${_protocol}"
        export "AI_MODEL_${_model_idx}_API_KEY=${_api_key}"
        export "AI_MODEL_${_model_idx}_NAME=${_name}"
        export "AI_MODEL_${_model_idx}_DEFAULT_MODEL=${_default_model}"

        log "$(msg install.ai_model_selected "${_name}" "${_domain}")"

        echo ""
        _add_more=""
        read -r -p "$(msg install.ai_model_add_more) " _add_more
        _add_more="${_add_more:-N}"
    done

    AI_MODEL_COUNT="${_model_idx}"
    log "$(msg install.ai_model_count "${AI_MODEL_COUNT}")"
}

# =============================================================================
# 交互式配置
# =============================================================================

interactive_config() {
    log ""
    log "$(msg install.title)"
    log "$(msg install.log_file "${HIMARKET_LOG_FILE}")"
    log ""

    # 语言选择（仅交互模式）
    if [[ "${NON_INTERACTIVE}" != "1" ]]; then
        local lang_default="1"
        [[ "${HIMARKET_LANGUAGE}" == "en" ]] && lang_default="2"

        log "$(msg lang.switch_title)"
        echo "$(msg lang.option_zh)"
        echo "$(msg lang.option_en)"
        echo ""
        read -r -p "$(msg lang.prompt) [${lang_default}]: " LANG_CHOICE
        LANG_CHOICE="${LANG_CHOICE:-${lang_default}}"
        case "${LANG_CHOICE}" in
            1) HIMARKET_LANGUAGE="zh" ;;
            2) HIMARKET_LANGUAGE="en" ;;
        esac
        export HIMARKET_LANGUAGE
        echo ""
    fi

    # 自动检测部署模式
    local ns="${NAMESPACE:-himarket}"
    local existing="false"
    if helm ls -n "${ns}" --filter '^himarket$' -q 2>/dev/null | grep -q 'himarket'; then
        existing="true"
    fi

    if [[ "${existing}" == "true" ]]; then
        log "$(msg install.upgrade_detected)"

        if [[ "${NON_INTERACTIVE}" == "1" ]]; then
            # 非交互模式：按 DEPLOY_MODE 决定，默认 upgrade
            DEPLOY_MODE="${DEPLOY_MODE:-upgrade}"
        else
            log "$(msg install.mode_prompt)"
            echo "$(msg install.mode_upgrade)"
            echo "$(msg install.mode_reinstall)"
            echo ""
            read -r -p "$(msg install.mode_choice) [1]: " MODE_CHOICE
            MODE_CHOICE="${MODE_CHOICE:-1}"
            case "${MODE_CHOICE}" in
                2)
                    # 二次确认
                    local confirm=""
                    read -r -p "$(msg install.reinstall_confirm) " confirm
                    if [[ "${confirm}" =~ ^[Yy]$ ]]; then
                        DEPLOY_MODE="reinstall"
                    else
                        log "$(msg install.cancelled)"
                        exit 0
                    fi
                    ;;
                *)
                    DEPLOY_MODE="upgrade"
                    ;;
            esac
        fi
    else
        DEPLOY_MODE="install"
    fi

    if [[ "${DEPLOY_MODE}" == "upgrade" ]]; then
        # ─── 升级模式：仅允许修改镜像 Tag ───
        log ""
        log "$(msg install.upgrade_image_only)"
        log ""
        log "$(msg section.image)"
        prompt HIMARKET_IMAGE_TAG "HiMarket image tag" "${HIMARKET_IMAGE_TAG:-latest}"
        prompt HIMARKET_MYSQL_IMAGE_TAG "MySQL image tag" "${HIMARKET_MYSQL_IMAGE_TAG:-latest}"
        prompt NACOS_VERSION "Nacos version" "${NACOS_VERSION:-v3.2.0-BETA}"

        # 其他配置沿用已有值（从配置文件加载或回退默认值）
        NAMESPACE="${NAMESPACE:-himarket}"
        HIMARKET_HUB="${HIMARKET_HUB:-opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group}"
        NACOS_IMAGE_REGISTRY="${NACOS_IMAGE_REGISTRY:-nacos-registry.cn-hangzhou.cr.aliyuncs.com}"
        NACOS_IMAGE_REPOSITORY="${NACOS_IMAGE_REPOSITORY:-nacos/nacos-server}"
        MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-himarket_root_2024}"
        MYSQL_PASSWORD="${MYSQL_PASSWORD:-himarket_app_2024}"
        NACOS_ADMIN_PASSWORD="${NACOS_ADMIN_PASSWORD:-nacos}"
        HIGRESS_USERNAME="${HIGRESS_USERNAME:-admin}"
        HIGRESS_PASSWORD="${HIGRESS_PASSWORD:-admin}"
        ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
        ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin}"
        FRONT_USERNAME="${FRONT_USERNAME:-user}"
        FRONT_PASSWORD="${FRONT_PASSWORD:-123456}"
        MYSQL_STORAGE_CLASS="${MYSQL_STORAGE_CLASS:-alicloud-disk-essd}"
        MYSQL_STORAGE_SIZE="${MYSQL_STORAGE_SIZE:-50Gi}"
        SANDBOX_STORAGE_CLASS="${SANDBOX_STORAGE_CLASS:-alicloud-disk-essd}"
        SANDBOX_STORAGE_SIZE="${SANDBOX_STORAGE_SIZE:-50Gi}"
        HIGRESS_INGRESS_CLASS="${HIGRESS_INGRESS_CLASS:-himarket}"
        if [[ "${NON_INTERACTIVE}" != "1" ]]; then
            log ""
            log "$(msg section.init)"
            local skip_mcp_answer=""
            read -r -p "$(msg install.skip_mcp_init) " skip_mcp_answer
            [[ "${skip_mcp_answer}" =~ ^[Yy]$ ]] && SKIP_MCP_INIT="true" || SKIP_MCP_INIT="false"

            local skip_skill_answer=""
            read -r -p "$(msg install.skip_skill_init) " skip_skill_answer
            [[ "${skip_skill_answer}" =~ ^[Yy]$ ]] && SKIP_SKILL_INIT="true" || SKIP_SKILL_INIT="false"
        else
            SKIP_MCP_INIT="${SKIP_MCP_INIT:-true}"
            SKIP_SKILL_INIT="${SKIP_SKILL_INIT:-true}"
        fi
        export SKIP_MCP_INIT SKIP_SKILL_INIT
        SKIP_AI_MODEL_INIT="${SKIP_AI_MODEL_INIT:-true}"
        export SKIP_AI_MODEL_INIT AI_MODEL_COUNT
        local _ei
        for (( _ei=1; _ei<=${AI_MODEL_COUNT:-0}; _ei++ )); do
            export "AI_MODEL_${_ei}_PROVIDER" "AI_MODEL_${_ei}_TYPE" "AI_MODEL_${_ei}_DOMAIN" \
                   "AI_MODEL_${_ei}_PORT" "AI_MODEL_${_ei}_PROTOCOL" "AI_MODEL_${_ei}_API_KEY" \
                   "AI_MODEL_${_ei}_NAME" "AI_MODEL_${_ei}_DEFAULT_MODEL"
        done
    else
    # ─── 分组交互式提示（全新安装 / 重新安装）───
    log ""
    log "$(msg section.basic)"
    prompt NAMESPACE "Kubernetes namespace" "himarket"

    log ""
    log "$(msg section.image)"
    prompt HIMARKET_HUB "HiMarket image hub" "opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group"
    prompt HIMARKET_IMAGE_TAG "HiMarket image tag" "latest"
    prompt HIMARKET_MYSQL_IMAGE_TAG "MySQL image tag" "latest"
    prompt NACOS_VERSION "Nacos version" "v3.2.0-BETA"
    prompt NACOS_IMAGE_REGISTRY "Nacos image registry" "nacos-registry.cn-hangzhou.cr.aliyuncs.com"
    prompt NACOS_IMAGE_REPOSITORY "Nacos image repository" "nacos/nacos-server"

    log ""
    log "$(msg section.db)"
    prompt MYSQL_ROOT_PASSWORD "MySQL root password" "himarket_root_2024"
    prompt MYSQL_PASSWORD "MySQL app password" "himarket_app_2024"

    log ""
    log "$(msg section.credential)"
    prompt NACOS_ADMIN_PASSWORD "Nacos admin password" "nacos"
    prompt HIGRESS_USERNAME "Higress console username" "admin"
    prompt HIGRESS_PASSWORD "Higress console password" "admin"

    log ""
    log "$(msg section.user)"
    prompt ADMIN_USERNAME "Admin username" "admin"
    prompt ADMIN_PASSWORD "Admin password" "admin"
    prompt FRONT_USERNAME "Developer username" "user"
    prompt FRONT_PASSWORD "Developer password" "123456"

    log ""
    log "$(msg section.storage)"
    prompt MYSQL_STORAGE_CLASS "MySQL StorageClass" "alicloud-disk-essd"
    prompt MYSQL_STORAGE_SIZE "MySQL storage size" "50Gi"
    prompt SANDBOX_STORAGE_CLASS "Sandbox StorageClass" "alicloud-disk-essd"
    prompt SANDBOX_STORAGE_SIZE "Sandbox storage size" "50Gi"
    prompt HIGRESS_INGRESS_CLASS "Higress IngressClass" "himarket"

    log ""
    log "$(msg section.init)"
    if [[ "${NON_INTERACTIVE}" != "1" ]]; then
        local skip_mcp_answer=""
        read -r -p "$(msg install.skip_mcp_init) " skip_mcp_answer
        [[ "${skip_mcp_answer}" =~ ^[Yy]$ ]] && SKIP_MCP_INIT="true" || SKIP_MCP_INIT="false"

        local skip_skill_answer=""
        read -r -p "$(msg install.skip_skill_init) " skip_skill_answer
        [[ "${skip_skill_answer}" =~ ^[Yy]$ ]] && SKIP_SKILL_INIT="true" || SKIP_SKILL_INIT="false"
    else
        SKIP_MCP_INIT="${SKIP_MCP_INIT:-false}"
        SKIP_SKILL_INIT="${SKIP_SKILL_INIT:-false}"
    fi
    export SKIP_MCP_INIT SKIP_SKILL_INIT

    # ─── AI 模型配置（可选，支持多个）───
    log ""
    log "$(msg section.ai_model)"
    AI_MODEL_COUNT="${AI_MODEL_COUNT:-0}"
    if [[ "${NON_INTERACTIVE}" != "1" ]]; then
        if [[ "${AI_MODEL_COUNT:-0}" -gt 0 ]]; then
            # 展示已有的模型配置
            log ""
            log "$(msg install.ai_model_existing_title)"
            local _di
            for (( _di=1; _di<=${AI_MODEL_COUNT}; _di++ )); do
                eval "local _dn=\"\${AI_MODEL_${_di}_NAME:-}\""
                eval "local _dd=\${AI_MODEL_${_di}_DOMAIN:-}"
                eval "local _dk=\${AI_MODEL_${_di}_API_KEY:-}"
                local _dk_masked="****"
                [[ ${#_dk} -ge 4 ]] && _dk_masked="...${_dk: -4}"
                log "$(msg install.ai_model_existing_item "${_di}" "${_dn}" "${_dd}" "${_dk_masked}")"
            done
            log ""
            echo "$(msg install.ai_model_existing_action)"
            echo "$(msg install.ai_model_existing_keep)"
            echo "$(msg install.ai_model_existing_add)"
            echo "$(msg install.ai_model_existing_redo)"
            echo "$(msg install.ai_model_existing_skip)"
            echo ""
            local _existing_choice=""
            read -r -p "$(msg install.ai_model_existing_choice) [1]: " _existing_choice
            _existing_choice="${_existing_choice:-1}"

            case "${_existing_choice}" in
                2)
                    # 保留现有并继续添加新模型
                    SKIP_AI_MODEL_INIT="false"
                    interactive_add_models "${AI_MODEL_COUNT}"
                    ;;
                3)
                    # 清空并重新配置
                    local _ci
                    for (( _ci=1; _ci<=${AI_MODEL_COUNT}; _ci++ )); do
                        unset "AI_MODEL_${_ci}_PROVIDER" "AI_MODEL_${_ci}_TYPE" "AI_MODEL_${_ci}_DOMAIN" \
                              "AI_MODEL_${_ci}_PORT" "AI_MODEL_${_ci}_PROTOCOL" "AI_MODEL_${_ci}_API_KEY" \
                              "AI_MODEL_${_ci}_NAME" "AI_MODEL_${_ci}_DEFAULT_MODEL"
                    done
                    AI_MODEL_COUNT=0
                    SKIP_AI_MODEL_INIT="false"
                    interactive_add_models 0
                    ;;
                4)
                    # 跳过本次初始化（保留配置供下次使用）
                    SKIP_AI_MODEL_INIT="true"
                    ;;
                *)
                    # 1 或其他 → 保留现有模型配置
                    SKIP_AI_MODEL_INIT="false"
                    log "$(msg install.ai_model_count "${AI_MODEL_COUNT}")"
                    ;;
            esac
        else
            # 无已有配置 — 询问是否新增
            local ai_answer=""
            read -r -p "$(msg install.ai_model_prompt) " ai_answer
            if [[ "${ai_answer}" =~ ^[Yy]$ ]]; then
                SKIP_AI_MODEL_INIT="false"
                interactive_add_models 0
            else
                SKIP_AI_MODEL_INIT="true"
            fi
        fi
    else
        # 非交互模式：检测已有配置格式
        if [[ "${AI_MODEL_COUNT:-0}" -gt 0 ]]; then
            SKIP_AI_MODEL_INIT="${SKIP_AI_MODEL_INIT:-false}"
        else
            SKIP_AI_MODEL_INIT="${SKIP_AI_MODEL_INIT:-true}"
        fi
    fi
    export SKIP_AI_MODEL_INIT AI_MODEL_COUNT
    # 导出所有索引变量
    local _ei
    for (( _ei=1; _ei<=${AI_MODEL_COUNT:-0}; _ei++ )); do
        export "AI_MODEL_${_ei}_PROVIDER" "AI_MODEL_${_ei}_TYPE" "AI_MODEL_${_ei}_DOMAIN" \
               "AI_MODEL_${_ei}_PORT" "AI_MODEL_${_ei}_PROTOCOL" "AI_MODEL_${_ei}_API_KEY" \
               "AI_MODEL_${_ei}_NAME" "AI_MODEL_${_ei}_DEFAULT_MODEL"
    done
    fi

    # Helm 仓库配置（使用默认值，通常无需交互）
    HIGRESS_REPO_NAME="${HIGRESS_REPO_NAME:-higress.io}"
    HIGRESS_REPO_URL="${HIGRESS_REPO_URL:-https://higress.cn/helm-charts}"
    HIGRESS_CHART_REF="${HIGRESS_CHART_REF:-higress.io/higress}"

    # ─── 配置摘要 ───
    log ""
    log "$(msg section.summary)"
    log "  DEPLOY_MODE:       ${DEPLOY_MODE}"
    log "  NAMESPACE:         ${NAMESPACE}"
    log "  HIMARKET_HUB:      ${HIMARKET_HUB}"
    log "  HIMARKET_IMAGE_TAG:${HIMARKET_IMAGE_TAG}"
    log "  MYSQL_STORAGE:     ${MYSQL_STORAGE_CLASS} / ${MYSQL_STORAGE_SIZE}"
    log "  SANDBOX_STORAGE:   ${SANDBOX_STORAGE_CLASS} / ${SANDBOX_STORAGE_SIZE}"
    log "  NACOS_VERSION:     ${NACOS_VERSION}"
    log "  HIGRESS_INGRESS:   ${HIGRESS_INGRESS_CLASS}"
    log "  SKIP_MCP_INIT:     ${SKIP_MCP_INIT}"
    log "  SKIP_SKILL_INIT:   ${SKIP_SKILL_INIT}"
    log "  SKIP_AI_MODEL_INIT:${SKIP_AI_MODEL_INIT}"
    if [[ "${SKIP_AI_MODEL_INIT}" != "true" ]]; then
        log "  AI_MODEL_COUNT:    ${AI_MODEL_COUNT:-0}"
        local _si
        for (( _si=1; _si<=${AI_MODEL_COUNT:-0}; _si++ )); do
            eval "local _sn=\"\${AI_MODEL_${_si}_NAME:-}\""
            eval "local _sd=\${AI_MODEL_${_si}_DOMAIN:-}"
            log "    #${_si} ${_sn} (${_sd})"
        done
    fi
    log ""

    if [[ "${NON_INTERACTIVE}" != "1" ]]; then
        # 可选保存到 ~/himarket-install.env
        read -r -p "$(msg install.confirm_save) " SAVE_CHOICE
        SAVE_CHOICE="${SAVE_CHOICE:-Y}"
        if [[ "${SAVE_CHOICE}" =~ ^[Yy] ]]; then
            save_env
            log "$(msg install.saved "${ENV_FILE}")"
        fi

        # 确认部署
        read -r -p "$(msg install.confirm_deploy) " CONFIRM
        CONFIRM="${CONFIRM:-Y}"
        if [[ ! "${CONFIRM}" =~ ^[Yy] ]]; then
            log "$(msg install.cancelled)"
            exit 0
        fi
    fi
}

# ── 保存当前配置到 ~/himarket-install.env ─────────────────────────────────────
save_env() {
    cat > "${ENV_FILE}" <<ENVEOF
# HiMarket Helm 部署配置（由 install.sh 自动生成）

# ========== 部署模式 ==========
DEPLOY_MODE="${DEPLOY_MODE}"

# ========== 基础配置 ==========
NAMESPACE="${NAMESPACE}"

# ========== 镜像配置 ==========
HIMARKET_HUB="${HIMARKET_HUB}"
HIMARKET_IMAGE_TAG="${HIMARKET_IMAGE_TAG}"
HIMARKET_MYSQL_IMAGE_TAG="${HIMARKET_MYSQL_IMAGE_TAG}"
NACOS_VERSION="${NACOS_VERSION}"
NACOS_IMAGE_REGISTRY="${NACOS_IMAGE_REGISTRY}"
NACOS_IMAGE_REPOSITORY="${NACOS_IMAGE_REPOSITORY}"

# ========== Helm 仓库 ==========
HIGRESS_REPO_NAME="${HIGRESS_REPO_NAME}"
HIGRESS_REPO_URL="${HIGRESS_REPO_URL}"
HIGRESS_CHART_REF="${HIGRESS_CHART_REF}"

# ========== 数据库密码 ==========
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD}"
MYSQL_PASSWORD="${MYSQL_PASSWORD}"

# ========== 服务凭证 ==========
NACOS_ADMIN_PASSWORD="${NACOS_ADMIN_PASSWORD}"
HIGRESS_USERNAME="${HIGRESS_USERNAME}"
HIGRESS_PASSWORD="${HIGRESS_PASSWORD}"

# ========== 默认用户 ==========
ADMIN_USERNAME="${ADMIN_USERNAME}"
ADMIN_PASSWORD="${ADMIN_PASSWORD}"
FRONT_USERNAME="${FRONT_USERNAME}"
FRONT_PASSWORD="${FRONT_PASSWORD}"

# ========== 存储配置 ==========
MYSQL_STORAGE_CLASS="${MYSQL_STORAGE_CLASS}"
MYSQL_STORAGE_SIZE="${MYSQL_STORAGE_SIZE}"
SANDBOX_STORAGE_CLASS="${SANDBOX_STORAGE_CLASS}"
SANDBOX_STORAGE_SIZE="${SANDBOX_STORAGE_SIZE}"

# ========== Higress IngressClass ==========
HIGRESS_INGRESS_CLASS="${HIGRESS_INGRESS_CLASS}"

# ========== 初始化选项 ==========
SKIP_MCP_INIT="${SKIP_MCP_INIT}"
SKIP_SKILL_INIT="${SKIP_SKILL_INIT}"

# ========== AI 模型配置 ==========
SKIP_AI_MODEL_INIT="${SKIP_AI_MODEL_INIT:-true}"
AI_MODEL_COUNT="${AI_MODEL_COUNT:-0}"
ENVEOF

    # 追加多模型配置
    local _si
    for (( _si=1; _si<=${AI_MODEL_COUNT:-0}; _si++ )); do
        eval "local _sp=\${AI_MODEL_${_si}_PROVIDER:-}"
        eval "local _st=\${AI_MODEL_${_si}_TYPE:-}"
        eval "local _sd=\${AI_MODEL_${_si}_DOMAIN:-}"
        eval "local _spt=\${AI_MODEL_${_si}_PORT:-443}"
        eval "local _spr=\${AI_MODEL_${_si}_PROTOCOL:-}"
        eval "local _sk=\${AI_MODEL_${_si}_API_KEY:-}"
        eval "local _sn=\"\${AI_MODEL_${_si}_NAME:-}\""
        eval "local _sm=\${AI_MODEL_${_si}_DEFAULT_MODEL:-}"
        cat >> "${ENV_FILE}" <<MODEL_ENVEOF
AI_MODEL_${_si}_PROVIDER="${_sp}"
AI_MODEL_${_si}_TYPE="${_st}"
AI_MODEL_${_si}_DOMAIN="${_sd}"
AI_MODEL_${_si}_PORT="${_spt}"
AI_MODEL_${_si}_PROTOCOL="${_spr}"
AI_MODEL_${_si}_API_KEY="${_sk}"
AI_MODEL_${_si}_NAME="${_sn}"
AI_MODEL_${_si}_DEFAULT_MODEL="${_sm}"
MODEL_ENVEOF
    done
}

# =============================================================================
# 部署流程
# =============================================================================

cluster_preflight() {
    log "$(msg deploy.preflight)"
    command -v kubectl >/dev/null 2>&1 || error "$(msg deploy.missing_cmd "kubectl")"
    command -v helm >/dev/null 2>&1 || error "$(msg deploy.missing_cmd "helm")"
    kubectl cluster-info >/dev/null 2>&1 || error "$(msg deploy.missing_cmd "kubectl cluster-info")"
    local ctx
    ctx=$(kubectl config current-context 2>/dev/null || echo "unknown")
    log "$(msg deploy.preflight_ok "${ctx}")"
}

create_ns() {
    local ns="$1"
    if ! kubectl get ns "${ns}" >/dev/null 2>&1; then
        log "$(msg deploy.ns_create "${ns}")"
        kubectl create ns "${ns}"
    fi
}

add_repos() {
    helm repo add "${HIGRESS_REPO_NAME}" "${HIGRESS_REPO_URL}" --force-update
    helm repo update "${HIGRESS_REPO_NAME}"
}

deploy_all() {
    # 1. 预检查
    cluster_preflight

    # 2. 加载配置
    load_config

    # 3. 交互式配置
    interactive_config

    local NS="${NAMESPACE}"
    local NACOS_DB_NAME="nacos"

    # 4. 重新安装模式：先清理现有资源
    if [[ "${DEPLOY_MODE}" == "reinstall" ]]; then
        log "$(msg install.reinstall_cleaning)"
        helm uninstall higress -n "${NS}" 2>/dev/null || true
        helm uninstall nacos -n "${NS}" 2>/dev/null || true
        helm uninstall himarket -n "${NS}" 2>/dev/null || true
        kubectl delete pvc --all -n "${NS}" --wait=true --timeout=120s 2>/dev/null || true
        log "$(msg install.uninstall_done)"
    fi

    # 5. 创建命名空间
    create_ns "${NS}"

    # 5. 添加 Helm 仓库
    add_repos

    # 6. 部署 HiMarket（含 MySQL + Server + Admin + Frontend + Sandbox）
    helm_upsert "himarket" "${NS}" "${HIMARKET_CHART_PATH}" \
        --set "hub=${HIMARKET_HUB}" \
        --set "frontend.image.tag=${HIMARKET_IMAGE_TAG}" \
        --set "admin.image.tag=${HIMARKET_IMAGE_TAG}" \
        --set "server.image.tag=${HIMARKET_IMAGE_TAG}" \
        --set "mysql.image.tag=${HIMARKET_MYSQL_IMAGE_TAG}" \
        --set "mysql.auth.rootPassword=${MYSQL_ROOT_PASSWORD}" \
        --set "mysql.auth.password=${MYSQL_PASSWORD}" \
        --set "mysql.persistence.storageClass=${MYSQL_STORAGE_CLASS}" \
        --set "mysql.persistence.size=${MYSQL_STORAGE_SIZE}" \
        --set "sandbox.persistence.storageClass=${SANDBOX_STORAGE_CLASS}" \
        --set "sandbox.persistence.size=${SANDBOX_STORAGE_SIZE}"

    # 7. 等待 MySQL Pod 就绪 + 初始化 Nacos 数据库
    init_nacos_db_in_cluster "${NS}" "${MYSQL_ROOT_PASSWORD}" "${NACOS_DB_NAME}"

    # 8. 部署 Nacos
    local nacos_db_pass
    nacos_db_pass=$(kubectl get secret mysql-secret -n "${NS}" -o jsonpath='{.data.MYSQL_ROOT_PASSWORD}' 2>/dev/null | base64 -d 2>/dev/null || echo "${MYSQL_ROOT_PASSWORD}")

    helm_upsert "nacos" "${NS}" "${NACOS_CHART_PATH}" \
        --set "database.host=mysql-headless-svc" \
        --set "database.port=3306" \
        --set "database.name=${NACOS_DB_NAME}" \
        --set "database.username=root" \
        --set "database.password=${nacos_db_pass}" \
        --set "image.registry=${NACOS_IMAGE_REGISTRY}" \
        --set "image.repository=${NACOS_IMAGE_REPOSITORY}" \
        --set "image.tag=${NACOS_VERSION}"

    wait_rollout "${NS}" "deployment" "nacos" 900

    # 9. 部署 Higress
    helm_upsert "higress" "${NS}" "${HIGRESS_CHART_REF}" \
        --set "higress-core.global.enableRedis=true" \
        --set "higress-core.global.ingressClass=${HIGRESS_INGRESS_CLASS}" \
        --set "higress-console.global.ingressClass=${HIGRESS_INGRESS_CLASS}" \
        --set "higress-console.service.type=LoadBalancer" \
        --set "higress-console.admin.username=${HIGRESS_USERNAME}" \
        --set "higress-console.admin.password=${HIGRESS_PASSWORD}"

    wait_rollout "${NS}" "deployment" "higress-gateway" 900
    wait_rollout "${NS}" "deployment" "higress-controller" 600

    # 10. 配置 Higress MCP Redis
    update_higress_mcp_redis "${NS}" || warn "mcpServer 配置更新失败，请手动检查"

    # 11. 执行 post_ready 钩子（允许单个 hook 失败后继续执行后续 hook）
    log "所有组件部署就绪，开始执行数据初始化..."
    export SKIP_HOOK_ERRORS=true
    run_hooks "post_ready" || warn "部分钩子执行失败，请检查日志"

    # 12. 展示结果面板
    show_result_panel "${NS}"
}

# ── 展示部署结果面板 ─────────────────────────────────────────────────────────
show_result_panel() {
    local ns="$1"

    # 获取各组件 External-IP
    local frontend_ip admin_ip nacos_ip higress_ip
    frontend_ip=$(kubectl get svc himarket-frontend -n "${ns}" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "<pending>")
    admin_ip=$(kubectl get svc himarket-admin -n "${ns}" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "<pending>")
    nacos_ip=$(kubectl get svc nacos -n "${ns}" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "<pending>")
    higress_ip=$(kubectl get svc higress-console -n "${ns}" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "<pending>")
    local higress_port
    higress_port=$(kubectl get svc higress-console -n "${ns}" -o jsonpath='{.spec.ports[0].port}' 2>/dev/null || echo "8080")

    log ""
    log "╔══════════════════════════════════════════════════════╗"
    log "║            $(msg install.complete)                   ║"
    log "╠══════════════════════════════════════════════════════╣"
    log "║  Namespace:        ${ns}"
    log "║"
    log "║  Frontend:         http://${frontend_ip}"
    log "║  Admin:            http://${admin_ip}"
    log "║  Nacos:            http://${nacos_ip}:8848/nacos"
    log "║  Higress Console:  http://${higress_ip}:${higress_port}"
    log "║"
    log "║  Admin login:      ${ADMIN_USERNAME} / ${ADMIN_PASSWORD}"
    log "║  Developer login:  ${FRONT_USERNAME} / ${FRONT_PASSWORD}"
    log "║  Nacos login:      ${NACOS_USERNAME:-nacos} / ${NACOS_ADMIN_PASSWORD}"
    log "║  Higress login:    ${HIGRESS_USERNAME} / ${HIGRESS_PASSWORD}"
    log "║"
    if [[ "${SKIP_AI_MODEL_INIT:-true}" != "true" ]]; then
        local _ri
        for (( _ri=1; _ri<=${AI_MODEL_COUNT:-0}; _ri++ )); do
            eval "local _rn=\"\${AI_MODEL_${_ri}_NAME:-}\""
            eval "local _rd=\${AI_MODEL_${_ri}_DOMAIN:-}"
            log "║  AI Model #${_ri}:      ${_rn} (${_rd})"
        done
    fi
    log "║  Log:  ${HIMARKET_LOG_FILE}"
    log "║  Env:  ${ENV_FILE}"
    log "╚══════════════════════════════════════════════════════╝"
    log ""
}

# =============================================================================
# 卸载
# =============================================================================

uninstall_all() {
    local ns="${NAMESPACE:-himarket}"
    log "$(msg install.uninstall)"
    helm uninstall higress -n "${ns}" 2>/dev/null || true
    helm uninstall nacos -n "${ns}" 2>/dev/null || true
    helm uninstall himarket -n "${ns}" 2>/dev/null || true

    # 检查残留 PVC，交互确认是否清理
    local pvcs
    pvcs=$(kubectl get pvc -n "${ns}" -o name 2>/dev/null || true)
    if [[ -n "${pvcs}" ]]; then
        warn "$(msg install.pvc_remain)"
        kubectl get pvc -n "${ns}" --no-headers 2>/dev/null || true
        echo ""
        if [[ "${NON_INTERACTIVE}" == "true" ]]; then
            warn "$(msg install.pvc_skip)"
        else
            local answer=""
            read -r -p "$(msg install.pvc_confirm) [y/N]: " answer
            if [[ "${answer}" =~ ^[Yy]$ ]]; then
                log "$(msg install.clean_pvc)"
                kubectl delete pvc --all -n "${ns}" --wait=false 2>/dev/null || true
            else
                warn "$(msg install.pvc_skip)"
            fi
        fi
    fi

    log "$(msg install.uninstall_done)"
}

# =============================================================================
# 入口
# =============================================================================

main() {
    case "${ACTION}" in
        deploy)    deploy_all ;;
        uninstall)
            load_config
            uninstall_all
            ;;
        *) error "Unknown action: ${ACTION}" ;;
    esac
}

main
