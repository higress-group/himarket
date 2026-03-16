#!/bin/bash

# ==============================================================================
#                      配置项 (Configuration)
# ==============================================================================
# 以下环境变量需要在运行脚本前设置：
#
#   HIMARKET_REPOSITORY  - 镜像仓库地址
#   HIMARKET_USER        - 仓库登录用户名
#   HIMARKET_PASSWORD    - 仓库登录密码
#   HIMARKET_NAMESPACE   - 镜像命名空间

# 镜像版本
VERSION="cli-interation"

# 目标构建平台 (多架构)
PLATFORMS="linux/amd64,linux/arm64"


# ==============================================================================
#                      脚本主体 (Script Body)
# ==============================================================================

set -e
set -o pipefail

# 切换到项目根目录（脚本可从任意位置调用）
cd "$(dirname "$0")/.."

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

# --- 准备工作: 检查依赖和配置 ---
echo "=== Pre-flight Checks ==="

# 检查 Java 版本
JAVA_VERSION_OUTPUT=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2)
JAVA_MAJOR=$(echo "$JAVA_VERSION_OUTPUT" | cut -d'.' -f1)
if [ "$JAVA_MAJOR" = "1" ]; then
    JAVA_MAJOR=$(echo "$JAVA_VERSION_OUTPUT" | cut -d'.' -f2)
fi
if [ "$JAVA_MAJOR" != "17" ]; then
    echo "❌ Error: Java 17 is required, but found Java $JAVA_VERSION_OUTPUT"
    exit 1
fi
echo "✅ Java 17 detected."

for cmd in podman mvn npm; do
    if ! command -v $cmd &> /dev/null; then
        echo "❌ Error: Command not found: $cmd. Please install it and make sure it's in your PATH."
        exit 1
    fi
done
echo "✅ Dependencies (podman, mvn, npm) are present."

# --- 辅助函数: 使用 podman manifest 构建并推送多架构镜像 ---
# 用法: build_and_push_manifest <image_tag> <context_dir>
build_and_push_manifest() {
    local IMAGE_TAG="$1"
    local CONTEXT_DIR="$2"

    echo "🔨 Building multi-arch image: $IMAGE_TAG"
    echo "   Platforms: $PLATFORMS"

    # 清理可能残留的同名 manifest
    podman manifest rm "$IMAGE_TAG" 2>/dev/null || true

    # 创建 manifest list
    podman manifest create "$IMAGE_TAG"

    # 逐平台构建并添加到 manifest
    IFS=',' read -ra PLATFORM_LIST <<< "$PLATFORMS"
    for PLATFORM in "${PLATFORM_LIST[@]}"; do
        echo "   ➜ Building for $PLATFORM ..."
        podman build \
            --platform "$PLATFORM" \
            -t "${IMAGE_TAG}-$(echo $PLATFORM | tr '/' '-')" \
            "$CONTEXT_DIR"
        podman manifest add "$IMAGE_TAG" \
            "containers-storage:${IMAGE_TAG}-$(echo $PLATFORM | tr '/' '-')"
    done

    # 推送 manifest list
    echo "   ➜ Pushing manifest list ..."
    podman manifest push --all "$IMAGE_TAG" "docker://$IMAGE_TAG"
    echo "✅ $IMAGE_TAG pushed successfully."
}


# --- 步骤 1: 登录镜像仓库 ---
echo ""
echo "=== Step 1: Logging into Registry: $REPOSITORY ==="
echo "$PASSWORD" | podman login "$REPOSITORY" --username "$USER" --password-stdin
echo "✅ Login successful."


# --- 步骤 2: 构建并推送 Backend Server ---
echo ""
echo "=== Step 2: Building and pushing backend server ==="
echo "Building with Maven..."
mvn clean package -DskipTests

SERVER_IMAGE_TAG="$REPOSITORY/$NAMESPACE/himarket-server:$VERSION"
build_and_push_manifest "$SERVER_IMAGE_TAG" "himarket-bootstrap"


# --- 步骤 3: 构建并推送 Frontend ---
echo ""
echo "=== Step 3: Building and pushing frontend ==="
cd himarket-web/himarket-frontend
rm -rf ./dist
npm install --force
npm run build
cd ../..

FRONTEND_IMAGE_TAG="$REPOSITORY/$NAMESPACE/himarket-frontend:$VERSION"
build_and_push_manifest "$FRONTEND_IMAGE_TAG" "himarket-web/himarket-frontend"


# --- 步骤 4: 构建并推送 Admin ---
echo ""
echo "=== Step 4: Building and pushing admin ==="
cd himarket-web/himarket-admin
rm -rf ./dist
npm install --force
npm run build
cd ../..

ADMIN_IMAGE_TAG="$REPOSITORY/$NAMESPACE/himarket-admin:$VERSION"
build_and_push_manifest "$ADMIN_IMAGE_TAG" "himarket-web/himarket-admin"


# --- 完成 ---
echo ""
echo "========================================================"
echo "✅ All images have been built and pushed successfully!"
echo "--------------------------------------------------------"
echo "  - Server:   $SERVER_IMAGE_TAG"
echo "  - Frontend: $FRONTEND_IMAGE_TAG"
echo "  - Admin:    $ADMIN_IMAGE_TAG"
echo "========================================================"
