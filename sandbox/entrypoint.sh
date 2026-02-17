#!/bin/bash
# HiMarket 沙箱容器入口脚本
# 单容器方案：同时启动 ACP Sidecar 和保持容器运行
#
# 环境变量:
#   CLI_COMMAND  - CLI 工具命令 (如 qodercli)
#   CLI_ARGS     - CLI 工具参数 (如 --acp)
#   SIDECAR_PORT - Sidecar WebSocket 端口 (默认 8080)

set -e

SIDECAR_PORT="${SIDECAR_PORT:-8080}"

echo "[sandbox] Starting HiMarket sandbox container..."
echo "[sandbox] CLI_COMMAND=${CLI_COMMAND:-<not set>}"
echo "[sandbox] CLI_ARGS=${CLI_ARGS:-<not set>}"
echo "[sandbox] SIDECAR_PORT=${SIDECAR_PORT}"

# 启动 ACP Sidecar（后台运行）
# Sidecar 负责：
#   1. 监听 WebSocket 连接 (ws://0.0.0.0:${SIDECAR_PORT}/ws)
#   2. 收到后端消息后，启动/转发给 CLI 进程的 stdin
#   3. 读取 CLI 进程的 stdout，通过 WebSocket 返回给后端
if command -v acp-sidecar &> /dev/null; then
    echo "[sandbox] Starting ACP Sidecar on port ${SIDECAR_PORT}..."
    acp-sidecar \
        --port "${SIDECAR_PORT}" \
        --cli-command "${CLI_COMMAND}" \
        --cli-args "${CLI_ARGS}" \
        &
    SIDECAR_PID=$!
    echo "[sandbox] ACP Sidecar started (PID: ${SIDECAR_PID})"
else
    echo "[sandbox] WARNING: acp-sidecar not found, running in standby mode"
fi

# 保持容器运行
# 如果 sidecar 退出，容器也退出
if [ -n "${SIDECAR_PID:-}" ]; then
    wait ${SIDECAR_PID}
    EXIT_CODE=$?
    echo "[sandbox] ACP Sidecar exited with code ${EXIT_CODE}"
    exit ${EXIT_CODE}
else
    # 没有 sidecar 时保持容器运行（开发调试用）
    exec sleep infinity
fi
