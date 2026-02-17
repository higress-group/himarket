#!/bin/bash
# HiMarket 沙箱镜像构建脚本
#
# 用法:
#   ./build.sh                    # 仅构建，打 latest 标签
#   ./build.sh v1.0.0             # 构建并打指定版本标签
#   ./build.sh v1.0.0 --push      # 构建并推送到 ACR
#
# 环境变量 (推送时需要):
#   HIMARKET_REPOSITORY  - ACR 仓库地址 (如 xxx-registry.cn-hangzhou.cr.aliyuncs.com)
#   HIMARKET_NAMESPACE   - ACR 命名空间
#   HIMARKET_USER        - ACR 用户名
#   HIMARKET_PASSWORD    - ACR 密码

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOCAL_IMAGE="himarket/sandbox"
VERSION="${1:-latest}"
PUSH="${2:-}"

echo "==> 构建沙箱镜像: ${LOCAL_IMAGE}:${VERSION}"

docker build \
    --network=host \
    -t "${LOCAL_IMAGE}:${VERSION}" \
    -t "${LOCAL_IMAGE}:latest" \
    -f "${SCRIPT_DIR}/Dockerfile" \
    "${SCRIPT_DIR}"

echo "==> 构建完成: ${LOCAL_IMAGE}:${VERSION}"

if [ "${PUSH}" = "--push" ]; then
    # 校验环境变量
    : "${HIMARKET_REPOSITORY:?请设置 HIMARKET_REPOSITORY 环境变量}"
    : "${HIMARKET_NAMESPACE:?请设置 HIMARKET_NAMESPACE 环境变量}"
    : "${HIMARKET_USER:?请设置 HIMARKET_USER 环境变量}"
    : "${HIMARKET_PASSWORD:?请设置 HIMARKET_PASSWORD 环境变量}"

    REMOTE_IMAGE="${HIMARKET_REPOSITORY}/${HIMARKET_NAMESPACE}/sandbox"

    echo "==> 登录 ACR: ${HIMARKET_REPOSITORY}"
    echo "${HIMARKET_PASSWORD}" | docker login --username="${HIMARKET_USER}" --password-stdin "${HIMARKET_REPOSITORY}"

    echo "==> 打标签: ${REMOTE_IMAGE}:${VERSION}"
    docker tag "${LOCAL_IMAGE}:${VERSION}" "${REMOTE_IMAGE}:${VERSION}"
    docker tag "${LOCAL_IMAGE}:latest" "${REMOTE_IMAGE}:latest"

    echo "==> 推送镜像..."
    docker push "${REMOTE_IMAGE}:${VERSION}"
    docker push "${REMOTE_IMAGE}:latest"

    echo "==> 推送完成: ${REMOTE_IMAGE}:${VERSION}"
fi

echo ""
echo "==> 镜像大小:"
docker images "${LOCAL_IMAGE}" --format "table {{.Repository}}:{{.Tag}}\t{{.Size}}"
