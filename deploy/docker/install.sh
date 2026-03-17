#!/usr/bin/env bash
# =============================================================================
# HiMarket Docker 统一部署脚本
# 默认交互式运行，支持 --non-interactive 模式
# =============================================================================
set -Eeuo pipefail

# ── 路径变量 ──────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/docker-compose.yml"
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
            [[ "$lang" == "zh" ]] && text="=== HiMarket Docker 部署 ===" || text="=== HiMarket Docker Deployment ===" ;;
        install.log_file)
            [[ "$lang" == "zh" ]] && text="日志文件: %s" || text="Log file: %s" ;;
        install.upgrade_detected)
            [[ "$lang" == "zh" ]] && text="检测到已有 HiMarket Docker 部署" || text="Existing HiMarket Docker deployment detected" ;;
        install.mode_prompt)
            [[ "$lang" == "zh" ]] && text="请选择操作模式:" || text="Select operation mode:" ;;
        install.mode_upgrade)
            [[ "$lang" == "zh" ]] && text="  1) 升级 — 保留现有数据，仅更新镜像" || text="  1) Upgrade — keep existing data, update images only" ;;
        install.mode_reinstall)
            [[ "$lang" == "zh" ]] && text="  2) 重新安装 — 清理所有容器和数据后全新部署" || text="  2) Reinstall — clean all containers and data, deploy fresh" ;;
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
            [[ "$lang" == "zh" ]] && text="HiMarket Docker 部署完成！" || text="HiMarket Docker deployment complete!" ;;
        install.uninstall)
            [[ "$lang" == "zh" ]] && text="开始卸载所有组件..." || text="Uninstalling all components..." ;;
        install.uninstall_done)
            [[ "$lang" == "zh" ]] && text="卸载完成" || text="Uninstall complete" ;;
        install.skip_mcp_init)
            [[ "$lang" == "zh" ]] && text="是否跳过 MCP 初始化? [y/N]" || text="Skip MCP initialization? [y/N]" ;;
        install.skip_skill_init)
            [[ "$lang" == "zh" ]] && text="是否跳过 Skill 初始化? [y/N]" || text="Skip Skill initialization? [y/N]" ;;
        install.volume_confirm)
            [[ "$lang" == "zh" ]] && text="是否同时删除数据卷？数据将不可恢复 [y/N]" || text="Also delete data volumes? Data will be unrecoverable [y/N]" ;;
        install.volume_skip)
            [[ "$lang" == "zh" ]] && text="数据卷已保留" || text="Data volumes kept" ;;
        section.image)
            [[ "$lang" == "zh" ]] && text="--- 镜像配置 ---" || text="--- Image Config ---" ;;
        section.db)
            [[ "$lang" == "zh" ]] && text="--- 数据库配置 ---" || text="--- Database Config ---" ;;
        section.credential)
            [[ "$lang" == "zh" ]] && text="--- 服务凭证 ---" || text="--- Service Credentials ---" ;;
        section.user)
            [[ "$lang" == "zh" ]] && text="--- 默认用户 ---" || text="--- Default Users ---" ;;
        section.init)
            [[ "$lang" == "zh" ]] && text="--- 初始化选项 ---" || text="--- Initialization Options ---" ;;
        section.ai_model)
            [[ "$lang" == "zh" ]] && text="--- AI 模型配置（可选）---" || text="--- AI Model Config (Optional) ---" ;;
        section.summary)
            [[ "$lang" == "zh" ]] && text="--- 配置确认 ---" || text="--- Config Summary ---" ;;
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
        prompt.required)
            [[ "$lang" == "zh" ]] && text="错误: %s 是必需的（非交互模式请通过环境变量或 ~/himarket-install.env 设置）" || text="Error: %s is required (set via env var or ~/himarket-install.env in non-interactive mode)" ;;
        prompt.required_empty)
            [[ "$lang" == "zh" ]] && text="错误: %s 不能为空" || text="Error: %s cannot be empty" ;;
        deploy.preflight)
            [[ "$lang" == "zh" ]] && text="环境预检..." || text="Preflight check..." ;;
        deploy.preflight_ok)
            [[ "$lang" == "zh" ]] && text="Docker 环境正常: %s" || text="Docker environment OK: %s" ;;
        deploy.missing_cmd)
            [[ "$lang" == "zh" ]] && text="缺少命令: %s" || text="Missing command: %s" ;;
        deploy.hooks)
            [[ "$lang" == "zh" ]] && text="执行 %s 阶段钩子..." || text="Running %s hooks..." ;;
        deploy.hook_run)
            [[ "$lang" == "zh" ]] && text="运行钩子 [%s]: %s" || text="Running hook [%s]: %s" ;;
        deploy.hook_ok)
            [[ "$lang" == "zh" ]] && text="钩子成功: %s" || text="Hook success: %s" ;;
        deploy.hook_fail)
            [[ "$lang" == "zh" ]] && text="钩子失败: %s" || text="Hook failed: %s" ;;
        deploy.wait)
            [[ "$lang" == "zh" ]] && text="等待 %s 就绪..." || text="Waiting for %s to be ready..." ;;
        deploy.wait_ok)
            [[ "$lang" == "zh" ]] && text="%s 已就绪" || text="%s is ready" ;;
        deploy.wait_timeout)
            [[ "$lang" == "zh" ]] && text="%s 启动超时" || text="Timed out waiting for %s" ;;
        lang.switch_title)
            [[ "$lang" == "zh" ]] && text="请选择语言 / Choose language:" || text="Choose language / 请选择语言:" ;;
        lang.option_zh)
            text="  1) 中文" ;;
        lang.option_en)
            text="  2) English" ;;
        lang.prompt)
            [[ "$lang" == "zh" ]] && text="请输入选项" || text="Enter choice" ;;
        *)
            text="${key}" ;;
    esac
    if [[ $# -gt 0 ]]; then
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
prompt() {
    local var_name="$1"
    local prompt_text="$2"
    local default_value="$3"

    eval "local current_value=\"\${${var_name}:-}\""
    local effective="${current_value:-${default_value}}"

    if [[ "${NON_INTERACTIVE}" == "1" ]]; then
        if [[ -n "${effective}" ]]; then
            eval "export ${var_name}='${effective}'"
            return
        fi
        error "$(msg prompt.required "${var_name}")"
    fi

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

    eval "local current_value=\"\${${var_name}:-}\""

    if [[ "${NON_INTERACTIVE}" == "1" ]]; then
        eval "export ${var_name}='${current_value}'"
        return
    fi

    local display_prompt="${prompt_text}"
    [[ -n "${current_value}" ]] && display_prompt="${prompt_text} [${current_value}]"

    local value=""
    read -r -p "${display_prompt}: " value
    value="${value:-${current_value}}"
    eval "export ${var_name}='${value}'"
}

# =============================================================================
# Docker 工具函数
# =============================================================================

# ── docker_compose — 包装 docker compose 命令 ────────────────────────────────
docker_compose() {
    docker compose -f "${COMPOSE_FILE}" "$@"
}

# ── wait_service — 等待单个容器服务就绪 ───────────────────────────────────────
wait_service() {
    local service_name="$1"
    local max_wait="${2:-300}"
    local interval=5
    local elapsed=0

    log "$(msg deploy.wait "${service_name}")"

    while (( elapsed < max_wait )); do
        local cid
        cid=$(docker_compose ps -q "${service_name}" 2>/dev/null || true)
        if [[ -n "$cid" ]]; then
            local health status
            health=$(docker inspect -f '{{ if .State.Health }}{{ .State.Health.Status }}{{ end }}' "$cid" 2>/dev/null || echo "")
            status=$(docker inspect -f '{{ .State.Status }}' "$cid" 2>/dev/null || echo "")
            if [[ "$health" == "healthy" ]]; then
                log "$(msg deploy.wait_ok "${service_name} (healthy)")"
                return 0
            fi
            if [[ -z "$health" && "$status" == "running" ]]; then
                log "$(msg deploy.wait_ok "${service_name} (running)")"
                return 0
            fi
        fi

        sleep "$interval"
        elapsed=$((elapsed + interval))

        if (( elapsed % 30 == 0 )); then
            log "等待 ${service_name} 就绪... (${elapsed}s/${max_wait}s)"
        fi
    done

    warn "$(msg deploy.wait_timeout "${service_name}")"
    docker_compose logs "${service_name}" 2>/dev/null | tail -30
    return 1
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
    for var in DEPLOY_MODE \
               HIMARKET_SERVER_IMAGE HIMARKET_ADMIN_IMAGE HIMARKET_FRONTEND_IMAGE \
               MYSQL_IMAGE NACOS_IMAGE HIGRESS_IMAGE REDIS_IMAGE SANDBOX_IMAGE \
               MYSQL_ROOT_PASSWORD MYSQL_PASSWORD MYSQL_DATABASE MYSQL_USER \
               NACOS_ADMIN_PASSWORD HIGRESS_USERNAME HIGRESS_PASSWORD \
               ADMIN_USERNAME ADMIN_PASSWORD FRONT_USERNAME FRONT_PASSWORD \
               HIMARKET_LANGUAGE \
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

        local _api_key=""
        read -r -p "$(msg install.ai_model_apikey): " _api_key
        if [[ -z "${_api_key}" ]]; then error "$(msg prompt.required_empty "API Key")"; fi

        if [[ "${_ai_choice}" != "7" ]]; then
            local _model_override=""
            read -r -p "$(msg install.ai_model_model) [${_default_model}]: " _model_override
            [[ -n "${_model_override}" ]] && _default_model="${_model_override}"
        fi

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
    local existing="false"
    if docker_compose ps -q 2>/dev/null | head -1 | grep -q .; then
        existing="true"
    fi

    if [[ "${existing}" == "true" ]]; then
        log "$(msg install.upgrade_detected)"

        if [[ "${NON_INTERACTIVE}" == "1" ]]; then
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

    # ─── 镜像配置 ───
    log ""
    log "$(msg section.image)"
    prompt HIMARKET_SERVER_IMAGE "HiMarket Server image" "opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/himarket-server:latest"
    prompt HIMARKET_ADMIN_IMAGE "HiMarket Admin image" "opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/himarket-admin:latest"
    prompt HIMARKET_FRONTEND_IMAGE "HiMarket Frontend image" "opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/himarket-frontend:latest"
    prompt MYSQL_IMAGE "MySQL image" "opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/mysql:latest"
    prompt NACOS_IMAGE "Nacos image" "nacos-registry.cn-hangzhou.cr.aliyuncs.com/nacos/nacos-server:v3.2.0-BETA"
    prompt HIGRESS_IMAGE "Higress image" "higress-registry.cn-hangzhou.cr.aliyuncs.com/higress/all-in-one:latest"

    # ─── 数据库密码 ───
    log ""
    log "$(msg section.db)"
    prompt MYSQL_ROOT_PASSWORD "MySQL root password" "himarket_root_2024"
    prompt MYSQL_PASSWORD "MySQL app password" "himarket_app_2024"
    # 内置 MySQL：DB_* 始终指向容器内 MySQL
    export DB_HOST="mysql"
    export DB_PORT="3306"
    export DB_NAME="${MYSQL_DATABASE:-portal_db}"
    export DB_USERNAME="${MYSQL_USER:-portal_user}"
    export DB_PASSWORD="${MYSQL_PASSWORD}"

    # ─── 服务凭证 ───
    log ""
    log "$(msg section.credential)"
    prompt NACOS_ADMIN_PASSWORD "Nacos admin password" "nacos"
    prompt HIGRESS_USERNAME "Higress console username" "admin"
    prompt HIGRESS_PASSWORD "Higress console password" "admin"

    # ─── 默认用户 ───
    log ""
    log "$(msg section.user)"
    prompt ADMIN_USERNAME "Admin username" "admin"
    prompt ADMIN_PASSWORD "Admin password" "admin"
    prompt FRONT_USERNAME "Developer username" "user"
    prompt FRONT_PASSWORD "Developer password" "123456"

    # ─── 初始化选项 ───
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
        # 升级模式默认跳过初始化（与 Helm 对齐），全新安装默认执行
        if [[ "${DEPLOY_MODE}" == "upgrade" ]]; then
            SKIP_MCP_INIT="${SKIP_MCP_INIT:-true}"
            SKIP_SKILL_INIT="${SKIP_SKILL_INIT:-true}"
        else
            SKIP_MCP_INIT="${SKIP_MCP_INIT:-false}"
            SKIP_SKILL_INIT="${SKIP_SKILL_INIT:-false}"
        fi
    fi
    export SKIP_MCP_INIT SKIP_SKILL_INIT

    # ─── AI 模型配置（可选，支持多个）───
    log ""
    log "$(msg section.ai_model)"
    AI_MODEL_COUNT="${AI_MODEL_COUNT:-0}"
    if [[ "${NON_INTERACTIVE}" != "1" ]]; then
        if [[ "${AI_MODEL_COUNT:-0}" -gt 0 ]]; then
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
                    SKIP_AI_MODEL_INIT="false"
                    interactive_add_models "${AI_MODEL_COUNT}"
                    ;;
                3)
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
                    SKIP_AI_MODEL_INIT="true"
                    ;;
                *)
                    SKIP_AI_MODEL_INIT="false"
                    log "$(msg install.ai_model_count "${AI_MODEL_COUNT}")"
                    ;;
            esac
        else
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
        if [[ "${AI_MODEL_COUNT:-0}" -gt 0 ]]; then
            SKIP_AI_MODEL_INIT="${SKIP_AI_MODEL_INIT:-false}"
        else
            SKIP_AI_MODEL_INIT="${SKIP_AI_MODEL_INIT:-true}"
        fi
    fi
    export SKIP_AI_MODEL_INIT AI_MODEL_COUNT
    local _ei
    for (( _ei=1; _ei<=${AI_MODEL_COUNT:-0}; _ei++ )); do
        export "AI_MODEL_${_ei}_PROVIDER" "AI_MODEL_${_ei}_TYPE" "AI_MODEL_${_ei}_DOMAIN" \
               "AI_MODEL_${_ei}_PORT" "AI_MODEL_${_ei}_PROTOCOL" "AI_MODEL_${_ei}_API_KEY" \
               "AI_MODEL_${_ei}_NAME" "AI_MODEL_${_ei}_DEFAULT_MODEL"
    done

    # ─── 配置摘要 ───
    log ""
    log "$(msg section.summary)"
    log "  DEPLOY_MODE:          ${DEPLOY_MODE}"
    log "  HIMARKET_SERVER:      ${HIMARKET_SERVER_IMAGE}"
    log "  MYSQL_IMAGE:          ${MYSQL_IMAGE}"
    log "  NACOS_IMAGE:          ${NACOS_IMAGE}"
    log "  HIGRESS_IMAGE:        ${HIGRESS_IMAGE}"
    log "  SKIP_MCP_INIT:        ${SKIP_MCP_INIT}"
    log "  SKIP_SKILL_INIT:      ${SKIP_SKILL_INIT}"
    log "  SKIP_AI_MODEL_INIT:   ${SKIP_AI_MODEL_INIT}"
    if [[ "${SKIP_AI_MODEL_INIT}" != "true" ]]; then
        log "  AI_MODEL_COUNT:       ${AI_MODEL_COUNT:-0}"
        local _si
        for (( _si=1; _si<=${AI_MODEL_COUNT:-0}; _si++ )); do
            eval "local _sn=\"\${AI_MODEL_${_si}_NAME:-}\""
            eval "local _sd=\${AI_MODEL_${_si}_DOMAIN:-}"
            log "    #${_si} ${_sn} (${_sd})"
        done
    fi
    log ""

    if [[ "${NON_INTERACTIVE}" != "1" ]]; then
        read -r -p "$(msg install.confirm_save) " SAVE_CHOICE
        SAVE_CHOICE="${SAVE_CHOICE:-Y}"
        if [[ "${SAVE_CHOICE}" =~ ^[Yy] ]]; then
            save_env
            log "$(msg install.saved "${ENV_FILE}")"
        fi

        read -r -p "$(msg install.confirm_deploy) " CONFIRM
        CONFIRM="${CONFIRM:-Y}"
        if [[ ! "${CONFIRM}" =~ ^[Yy] ]]; then
            log "$(msg install.cancelled)"
            exit 0
        fi
    else
        # 非交互模式也保存 env 文件，确保 hook 读取到最新配置
        save_env
        log "$(msg install.saved "${ENV_FILE}")"
    fi
}

# ── 保存当前配置到 ~/himarket-install.env ─────────────────────────────────────
save_env() {
    cat > "${ENV_FILE}" <<ENVEOF
# HiMarket Docker 部署配置（由 install.sh 自动生成）

# ========== 部署模式 ==========
DEPLOY_MODE="${DEPLOY_MODE}"

# ========== 镜像配置 ==========
HIMARKET_SERVER_IMAGE="${HIMARKET_SERVER_IMAGE}"
HIMARKET_ADMIN_IMAGE="${HIMARKET_ADMIN_IMAGE}"
HIMARKET_FRONTEND_IMAGE="${HIMARKET_FRONTEND_IMAGE}"
MYSQL_IMAGE="${MYSQL_IMAGE}"
NACOS_IMAGE="${NACOS_IMAGE}"
HIGRESS_IMAGE="${HIGRESS_IMAGE}"

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

# ========== 初始化选项 ==========
SKIP_MCP_INIT="${SKIP_MCP_INIT}"
SKIP_SKILL_INIT="${SKIP_SKILL_INIT}"

# ========== AI 模型配置 ==========
SKIP_AI_MODEL_INIT="${SKIP_AI_MODEL_INIT:-true}"
AI_MODEL_COUNT="${AI_MODEL_COUNT:-0}"
ENVEOF

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

docker_preflight() {
    log "$(msg deploy.preflight)"
    command -v docker >/dev/null 2>&1 || error "$(msg deploy.missing_cmd "docker")"
    command -v curl >/dev/null 2>&1 || error "$(msg deploy.missing_cmd "curl")"
    command -v jq >/dev/null 2>&1 || error "$(msg deploy.missing_cmd "jq")"

    if ! docker compose version >/dev/null 2>&1; then
        error "$(msg deploy.missing_cmd "docker compose")"
    fi

    if ! docker info >/dev/null 2>&1; then
        error "Docker 未运行，请先启动 Docker"
    fi

    local docker_ver
    docker_ver=$(docker version --format '{{.Server.Version}}' 2>/dev/null || echo "unknown")
    log "$(msg deploy.preflight_ok "Docker ${docker_ver}")"
}

deploy_all() {
    # 1. 预检查
    docker_preflight

    # 2. 加载配置
    load_config

    # 3. 交互式配置
    interactive_config

    # 4. 重新安装模式：先清理现有资源
    if [[ "${DEPLOY_MODE}" == "reinstall" ]]; then
        log "$(msg install.reinstall_cleaning)"
        docker_compose down -v 2>/dev/null || true
        rm -rf "${HOME}/himarket-data/data/mysql" "${HOME}/himarket-data/standalone-logs" "${HOME}/himarket-data/data/higress" "${HOME}/himarket-data/data/sandbox-workspace"
    fi

    # 5. 构建 profiles（MySQL 和 Server 无 profile，始终启动）
    local profiles="opensource-nacos,higress-gateway"

    export COMPOSE_PROFILES="${profiles}"
    log "Docker Compose profiles: ${profiles}"

    # 6. 启动所有服务
    log "启动 Docker Compose 服务..."
    docker_compose up -d

    # 7. 等待核心服务就绪
    log "等待核心服务启动..."

    wait_service "mysql" 120

    wait_service "nacos" 300
    wait_service "redis-stack-server" 60
    wait_service "higress" 180
    wait_service "himarket-server" 180
    wait_service "himarket-admin" 120
    wait_service "himarket-frontend" 120

    # 8. 执行 post_ready 钩子
    log "所有容器已就绪，开始执行数据初始化..."
    export SKIP_HOOK_ERRORS=true
    run_hooks "post_ready" || warn "部分钩子执行失败，请检查日志"

    # 9. 展示结果面板
    show_result_panel
}

# ── 展示部署结果面板 ─────────────────────────────────────────────────────────
show_result_panel() {
    log ""
    log "========================================================"
    log "  $(msg install.complete)"
    log "========================================================"
    log ""
    log "  HiMarket Admin:       http://localhost:5174"
    log "  HiMarket Frontend:    http://localhost:5173"
    log "  Nacos Console:        http://localhost:8848/nacos"
    log "  Higress Console:      http://localhost:8001"
    log "  HiMarket Server API:  http://localhost:8081"
    log ""
    log "  Admin login:          ${ADMIN_USERNAME} / ${ADMIN_PASSWORD}"
    log "  Developer login:      ${FRONT_USERNAME} / ${FRONT_PASSWORD}"
    log "  Nacos login:          nacos / ${NACOS_ADMIN_PASSWORD:-nacos}"
    log "  Higress login:        ${HIGRESS_USERNAME} / ${HIGRESS_PASSWORD}"
    log ""
    if [[ "${SKIP_AI_MODEL_INIT:-true}" != "true" ]]; then
        local _ri
        for (( _ri=1; _ri<=${AI_MODEL_COUNT:-0}; _ri++ )); do
            eval "local _rn=\"\${AI_MODEL_${_ri}_NAME:-}\""
            eval "local _rd=\${AI_MODEL_${_ri}_DOMAIN:-}"
            log "  AI Model #${_ri}:         ${_rn} (${_rd})"
        done
        log ""
    fi
    log "  Log:  ${HIMARKET_LOG_FILE}"
    log "  Env:  ${ENV_FILE}"
    log ""
    log "  docker compose -f ${COMPOSE_FILE} ps       # 查看状态"
    log "  docker compose -f ${COMPOSE_FILE} logs -f   # 查看日志"
    log "  $0 --uninstall                              # 卸载"
    log "========================================================"
    log ""
}

# =============================================================================
# 卸载
# =============================================================================

uninstall_all() {
    log "$(msg install.uninstall)"

    docker_compose down 2>/dev/null || true

    if [[ "${NON_INTERACTIVE}" != "1" ]]; then
        local answer=""
        read -r -p "$(msg install.volume_confirm) " answer
        if [[ "${answer}" =~ ^[Yy]$ ]]; then
            docker_compose down -v 2>/dev/null || true
            rm -rf "${HOME}/himarket-data/data/mysql" "${HOME}/himarket-data/standalone-logs" "${HOME}/himarket-data/data/higress" "${HOME}/himarket-data/data/sandbox-workspace"
            log "数据卷和本地数据已清理"
        else
            log "$(msg install.volume_skip)"
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
