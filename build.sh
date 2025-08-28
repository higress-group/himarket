#!/bin/bash

VERSION=1.0.0

set -e

# 构建 server
echo "=== Building backend server ==="
echo "Building with Maven..."
mvn clean package -DskipTests

cd portal-bootstrap
echo "Building backend Docker image..."
docker buildx build \
    --platform linux/amd64 \
    -t api-portal-server:$VERSION \
    --load .
echo "Backend server build completed"
cd ..

# 构建 frontend
cd portal-web/api-portal-frontend
echo "=== Building frontend ==="
rm -rf ./dist
npm run build
docker buildx build \
    -t api-portal-frontend:$VERSION \
    --platform linux/amd64 \
    --load .

# 构建 admin
cd ../api-portal-admin
echo "=== Building admin ==="
rm -rf ./dist
npm run build
docker buildx build \
    -t api-portal-admin:$VERSION \
    --platform linux/amd64 \
    --load .

echo "All images have been built successfully!"
