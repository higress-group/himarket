#!/bin/bash

VERSION=latest

set -e

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
