#!/bin/bash
# HiMarket 沙箱容器入口脚本
# 启动 Node.js Sidecar Server，支持通过 WebSocket 动态启动多个 CLI 进程
#
# 环境变量:
#   SIDECAR_PORT     - WebSocket 监听端口 (默认 8080)
#   ALLOWED_COMMANDS - 允许的 CLI 命令白名单，逗号分隔 (默认 qodercli,qwen)

set -e

SIDECAR_PORT="${SIDECAR_PORT:-8080}"
ALLOWED_COMMANDS="${ALLOWED_COMMANDS:-qodercli,qwen,opencode,claude-agent-acp}"

export SIDECAR_PORT
export ALLOWED_COMMANDS

echo "[sandbox] Starting HiMarket sandbox container..."
echo "[sandbox] SIDECAR_PORT=${SIDECAR_PORT}"
echo "[sandbox] ALLOWED_COMMANDS=${ALLOWED_COMMANDS}"

echo "[sandbox] Starting Sidecar Server on port ${SIDECAR_PORT}..."

exec node /usr/local/lib/sidecar-server/index.js
