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
VERSION="latest"

# 目标构建平台 (多架构)
PLATFORMS="linux/amd64,linux/arm64"


# ==============================================================================
#                      脚本主体 (Script Body)
# ==============================================================================

# 脚本出错时立即退出
set -e
# 管道命令中任何一个失败则整个管道失败
set -o pipefail

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

# 从环境变量读取配置
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

# 检查必要的命令是否存在
for cmd in docker mvn npm; do
    if ! command -v $cmd &> /dev/null; then
        echo "❌ Error: Command not found: $cmd. Please install it and make sure it's in your PATH."
        exit 1
    fi
done
echo "✅ Dependencies (docker, mvn, npm) are present."


# 确保 Docker buildx 构建器已准备就绪
BUILDER_NAME="mybuilder"
if ! docker buildx inspect "$BUILDER_NAME" > /dev/null 2>&1; then
    echo "Creating a new docker buildx builder instance named '$BUILDER_NAME'..."
    docker buildx create --name "$BUILDER_NAME" --use
else
    echo "Using existing docker buildx builder instance '$BUILDER_NAME'."
    docker buildx use "$BUILDER_NAME"
fi


# --- 步骤 1: 登录镜像仓库 (修正-1: 安全且正确地登录) ---
echo "=== Step 1: Logging into Docker Registry: $REPOSITORY ==="
# 使用 --password-stdin 从管道读取密码，避免密码暴露在命令行历史中
echo "$PASSWORD" | docker login "$REPOSITORY" --username "$USER" --password-stdin
echo "✅ Login successful."


# --- 步骤 2: 构建并推送 Backend Server ---
echo "\n=== Step 2: Building and pushing backend server ==="
echo "Building with Maven..."
mvn clean package -DskipTests

# 定义完整的镜像标签
SERVER_IMAGE_TAG="$REPOSITORY/$NAMESPACE/himarket-server:$VERSION"

cd himarket-bootstrap
echo "Building and pushing backend Docker image ($SERVER_IMAGE_TAG) for platforms: $PLATFORMS"
docker buildx build \
    --platform "$PLATFORMS" \
    -t "$SERVER_IMAGE_TAG" \
    --push . # --push 会在构建成功后直接推送多架构镜像
echo "✅ Backend server image pushed successfully."
cd ..


# --- 步骤 3: 构建并推送 Frontend ---
cd himarket-web/himarket-frontend
echo "\n=== Step 3: Building and pushing frontend ==="

# 定义完整的镜像标签
FRONTEND_IMAGE_TAG="$REPOSITORY/$NAMESPACE/himarket-frontend:$VERSION"

echo "Preparing frontend assets..."
rm -rf ./dist
npm install --force
npm run build

echo "Building and pushing frontend Docker image ($FRONTEND_IMAGE_TAG) for platforms: $PLATFORMS"
docker buildx build \
    -t "$FRONTEND_IMAGE_TAG" \
    --platform "$PLATFORMS" \
    --push .
echo "✅ Frontend image pushed successfully."
cd ../..


# --- 步骤 4: 构建并推送 Admin ---
# (修正-2: 修正目录切换逻辑)
cd himarket-web/himarket-admin
echo  "\n=== Step 4: Building and pushing admin ==="

# 定义完整的镜像标签
ADMIN_IMAGE_TAG="$REPOSITORY/$NAMESPACE/himarket-admin:$VERSION"

echo "Preparing admin assets..."
rm -rf ./dist
npm install --force
npm run build

echo "Building and pushing admin Docker image ($ADMIN_IMAGE_TAG) for platforms: $PLATFORMS"
docker buildx build \
    -t "$ADMIN_IMAGE_TAG" \
    --platform "$PLATFORMS" \
    --push .
echo "✅ Admin image pushed successfully."
cd ../.. # 从 himarket-admin 返回项目根目录


# --- 完成 ---
echo "\n========================================================"
echo "✅ All images have been built and pushed successfully!"
echo "--------------------------------------------------------"
echo "  - Server:   $SERVER_IMAGE_TAG"
echo "  - Frontend: $FRONTEND_IMAGE_TAG"
echo "  - Admin:    $ADMIN_IMAGE_TAG"
echo "================================"
