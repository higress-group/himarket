#!/bin/bash
# HiMarket 沙箱镜像构建并推送脚本（单架构 linux/amd64）
#
# 兼容 Docker CE / Podman (buildah) / limactl 等容器运行时。
# 不依赖 buildx 多平台能力，使用最基础的 docker build + docker push 完成。
#
# 用法:
#   ./push-amd64.sh
#
# 环境变量 (必须):
#   HIMARKET_REPOSITORY  - 镜像仓库地址
#   HIMARKET_NAMESPACE   - 镜像命名空间
#   HIMARKET_USER        - 仓库登录用户名
#   HIMARKET_PASSWORD    - 仓库登录密码

VERSION="latest"
PLATFORM="linux/amd64"

set -e
set -o pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# --- 检查必要的环境变量 ---
check_env_var() {
    local var_name="$1"
    if [ -z "${!var_name}" ]; then
        echo "❌ Error: Required environment variable $var_name is not set."
        exit 1
    fi
}

check_env_var "HIMARKET_REPOSITORY"
check_env_var "HIMARKET_USER"
check_env_var "HIMARKET_PASSWORD"
check_env_var "HIMARKET_NAMESPACE"

REPOSITORY="$HIMARKET_REPOSITORY"
USER="$HIMARKET_USER"
PASSWORD="$HIMARKET_PASSWORD"
NAMESPACE="$HIMARKET_NAMESPACE"
SANDBOX_IMAGE_TAG="$REPOSITORY/$NAMESPACE/sandbox:$VERSION"

# --- 检测容器运行时 ---
detect_runtime() {
    if command -v docker &> /dev/null; then
        # 判断 docker 背后是 Docker CE 还是 Podman
        if docker --version 2>&1 | grep -qi podman; then
            echo "podman"
        else
            echo "docker"
        fi
    elif command -v podman &> /dev/null; then
        echo "podman"
    elif command -v nerdctl &> /dev/null; then
        echo "nerdctl"
    else
        echo ""
    fi
}

echo "=== Pre-flight Checks ==="
RUNTIME=$(detect_runtime)
if [ -z "$RUNTIME" ]; then
    echo "❌ Error: No container runtime found (docker/podman/nerdctl)."
    exit 1
fi
echo "✅ Container runtime: $RUNTIME"

# 统一使用 docker CLI（podman 通常有 docker 兼容命令）
CMD="docker"
if [ "$RUNTIME" = "podman" ] && ! command -v docker &> /dev/null; then
    CMD="podman"
elif [ "$RUNTIME" = "nerdctl" ]; then
    CMD="nerdctl"
fi

# --- 步骤 1: 登录镜像仓库 ---
echo "=== Step 1: Logging into Docker Registry: $REPOSITORY ==="
echo "$PASSWORD" | $CMD login "$REPOSITORY" --username "$USER" --password-stdin
echo "✅ Login successful."

# --- 步骤 2: 构建镜像 ---
echo "=== Step 2: Building sandbox image ($PLATFORM) ==="
echo "Image tag: $SANDBOX_IMAGE_TAG"

$CMD build \
    --platform "$PLATFORM" \
    --network=host \
    -t "$SANDBOX_IMAGE_TAG" \
    -f "${SCRIPT_DIR}/Dockerfile" \
    "${SCRIPT_DIR}"
echo "✅ Build successful."

# --- 步骤 3: 推送镜像 ---
echo "=== Step 3: Pushing sandbox image ==="
$CMD push "$SANDBOX_IMAGE_TAG"
echo "✅ Push successful."

# --- 完成 ---
echo ""
echo "========================================================"
echo "✅ Done! Sandbox image pushed successfully."
echo "  - Image: $SANDBOX_IMAGE_TAG"
echo "  - Platform: $PLATFORM"
echo "========================================================"
