#!/bin/bash
# HiMarket 沙箱容器入口脚本
# 使用 websocat 将 WebSocket 连接与 CLI 进程的 stdio 双向桥接
#
# 环境变量:
#   CLI_COMMAND  - CLI 工具命令 (如 qodercli)
#   CLI_ARGS     - CLI 工具参数 (如 --acp)
#   SIDECAR_PORT - WebSocket 监听端口 (默认 8080)

set -e

SIDECAR_PORT="${SIDECAR_PORT:-8080}"

echo "[sandbox] Starting HiMarket sandbox container..."
echo "[sandbox] CLI_COMMAND=${CLI_COMMAND:-<not set>}"
echo "[sandbox] CLI_ARGS=${CLI_ARGS:-<not set>}"
echo "[sandbox] SIDECAR_PORT=${SIDECAR_PORT}"

# 校验必要的环境变量
if [ -z "${CLI_COMMAND}" ]; then
    echo "[sandbox] ERROR: CLI_COMMAND is not set"
    exit 1
fi

# 校验 websocat 是否可用
if ! command -v websocat &> /dev/null; then
    echo "[sandbox] ERROR: websocat not found, cannot start WebSocket bridge"
    exit 1
fi

# 构建 CLI 启动命令
CLI_CMD="${CLI_COMMAND}"
if [ -n "${CLI_ARGS}" ]; then
    CLI_CMD="${CLI_CMD} ${CLI_ARGS}"
fi

echo "[sandbox] Starting websocat bridge: ws://0.0.0.0:${SIDECAR_PORT} <-> ${CLI_CMD}"

# websocat 参数说明:
#   ws-l:0.0.0.0:PORT  — 监听 WebSocket 连接
#   sh-c:CMD           — 每个连接启动一个 CLI 子进程
#   --text             — 文本帧模式（匹配 JSON-RPC newline-delimited 协议）
#   -E                 — 任一端关闭时退出
exec websocat \
    --text \
    -E \
    "ws-l:0.0.0.0:${SIDECAR_PORT}" \
    "sh-c:${CLI_CMD}"
