#!/bin/bash
# HiMarket 沙箱镜像构建并推送脚本 (Podman)
#
# 用法:
#   ./push.sh
#
# 环境变量 (必须):
#   HIMARKET_REPOSITORY  - 镜像仓库地址
#   HIMARKET_NAMESPACE   - 镜像命名空间
#   HIMARKET_USER        - 仓库登录用户名
#   HIMARKET_PASSWORD    - 仓库登录密码

VERSION="latest"

# 目标构建平台 —— 只推单架构时改成 "linux/amd64" 即可
PLATFORMS="linux/amd64,linux/arm64"

# ==============================================================================
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

# --- Pre-flight ---
echo "=== Pre-flight Checks ==="
if ! command -v podman &> /dev/null; then
    echo "❌ Error: podman not found."
    exit 1
fi
echo "✅ podman $(podman --version | awk '{print $NF}')"

# --- 步骤 1: 登录镜像仓库 ---
echo ""
echo "=== Step 1: Logging into Registry: $REPOSITORY ==="
echo "$PASSWORD" | podman login "$REPOSITORY" --username "$USER" --password-stdin
echo "✅ Login successful."

# --- 步骤 2: 构建并推送 ---
echo ""
echo "=== Step 2: Building and pushing sandbox image ==="
echo "Image tag: $SANDBOX_IMAGE_TAG"
echo "Platforms: $PLATFORMS"

# 清理可能残留的同名 manifest
podman manifest rm "$SANDBOX_IMAGE_TAG" 2>/dev/null || true

# 创建 manifest list
podman manifest create "$SANDBOX_IMAGE_TAG"

# 逐平台构建并添加到 manifest
IFS=',' read -ra PLATFORM_LIST <<< "$PLATFORMS"
for PLATFORM in "${PLATFORM_LIST[@]}"; do
    PLATFORM_TAG="${SANDBOX_IMAGE_TAG}-$(echo "$PLATFORM" | tr '/' '-')"
    echo ""
    echo "--- Building for $PLATFORM ---"
    podman build \
        --platform "$PLATFORM" \
        -t "$PLATFORM_TAG" \
        -f "${SCRIPT_DIR}/Dockerfile" \
        "${SCRIPT_DIR}"
    podman manifest add "$SANDBOX_IMAGE_TAG" "containers-storage:$PLATFORM_TAG"
    echo "✅ Build for $PLATFORM done."
done

# 推送 manifest list
echo ""
echo "--- Pushing manifest ---"
podman manifest push --all "$SANDBOX_IMAGE_TAG" "docker://$SANDBOX_IMAGE_TAG"
echo "✅ Manifest pushed."

# --- 完成 ---
echo ""
echo "========================================================"
echo "✅ Done! Sandbox image pushed successfully."
echo "  - Image: $SANDBOX_IMAGE_TAG"
echo "  - Platforms: $PLATFORMS"
echo "========================================================"
