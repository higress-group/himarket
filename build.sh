#!/bin/bash

VERSION=latest
export DOCKER_HOST=unix://$HOME/.lima/docker/sock/docker.sock
export JAVA_HOME=/Users/terry/Library/Java/JavaVirtualMachines/corretto-17.0.14/Contents/Home
set -e

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

# 构建 server
echo "=== Building backend server ==="
echo "Building with Maven..."
mvn clean package -DskipTests

cd himarket-bootstrap
echo "Building backend Docker image..."
docker buildx build \
    --platform linux/amd64 \
    -t himarket-server:$VERSION \
    --load .
echo "Backend server build completed"
cd ..

# 构建 frontend
cd himarket-web/himarket-frontend
echo "=== Building frontend ==="
rm -rf ./dist
npm install --force
npm run build
docker buildx build \
    -t himarket-frontend:$VERSION \
    --platform linux/amd64 \
    --load .

# 构建 admin
cd ../himarket-admin
echo "=== Building admin ==="
rm -rf ./dist
npm install --force
npm run build
docker buildx build \
    -t himarket-admin:$VERSION \
    --platform linux/amd64 \
    --load .

echo "All images have been built successfully!"
