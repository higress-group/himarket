#!/bin/bash
# HiMarket 沙箱镜像构建脚本
#
# 用法:
#   ./build.sh              # 构建沙箱镜像，打 latest 标签

VERSION=latest

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== Building sandbox image ==="
docker buildx build \
    --platform linux/amd64 \
    --network=host \
    -t himarket/sandbox:$VERSION \
    -f "${SCRIPT_DIR}/Dockerfile" \
    --load \
    "${SCRIPT_DIR}"

echo "Sandbox image build completed"
