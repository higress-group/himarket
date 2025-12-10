# React + TypeScript + Vite



## 部署方式

1. 本地构建
```bash
npm run build
```

2. 构建本地镜像
```bash
docker buildx build --platform linux/amd64 -t api-portal-frontend:latest .
```

3. 推送镜像到ACR
```bash
docker push api-portal-frontend:latest
```

4. ACK 对应的无状态重新部署

