#!/bin/bash
# HiMarket 沙箱镜像构建并推送脚本
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
PLATFORMS="linux/amd64,linux/arm64"

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

# --- 检查依赖 ---
echo "=== Pre-flight Checks ==="
if ! command -v docker &> /dev/null; then
    echo "❌ Error: Command not found: docker."
    exit 1
fi
echo "✅ Dependencies (docker) are present."

# 确保 Docker buildx 构建器已准备就绪
BUILDER_NAME="mybuilder"
if ! docker buildx inspect "$BUILDER_NAME" > /dev/null 2>&1; then
    echo "Creating a new docker buildx builder instance named '$BUILDER_NAME'..."
    docker buildx create --name "$BUILDER_NAME" --use
else
    echo "Using existing docker buildx builder instance '$BUILDER_NAME'."
    docker buildx use "$BUILDER_NAME"
fi

# --- 步骤 1: 登录镜像仓库 ---
echo "=== Step 1: Logging into Docker Registry: $REPOSITORY ==="
echo "$PASSWORD" | docker login "$REPOSITORY" --username "$USER" --password-stdin
echo "✅ Login successful."

# --- 步骤 2: 构建并推送 Sandbox ---
SANDBOX_IMAGE_TAG="$REPOSITORY/$NAMESPACE/sandbox:$VERSION"

echo "=== Step 2: Building and pushing sandbox image ==="
echo "Building and pushing sandbox Docker image ($SANDBOX_IMAGE_TAG) for platforms: $PLATFORMS"
docker buildx build \
    --platform "$PLATFORMS" \
    --network=host \
    -t "$SANDBOX_IMAGE_TAG" \
    -f "${SCRIPT_DIR}/Dockerfile" \
    --push \
    "${SCRIPT_DIR}"
echo "✅ Sandbox image pushed successfully."

# --- 完成 ---
echo ""
echo "========================================================"
echo "✅ Sandbox image has been built and pushed successfully!"
echo "--------------------------------------------------------"
echo "  - Sandbox: $SANDBOX_IMAGE_TAG"
echo "========================================================"
