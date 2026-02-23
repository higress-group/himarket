#!/bin/bash
# 本地启动脚本：编译所有模块并启动 Spring Boot 应用
# 用法：./scripts/run.sh
# 每次修改代码后，杀掉旧进程重新执行此脚本即可

set -e

# 切换到项目根目录（脚本可从任意位置调用）
cd "$(dirname "$0")/.."

# 加载本地环境变量（数据库连接等）
# 优先使用 shell 已有的环境变量，~/.env 作为补充
if [ -f ~/.env ]; then
  source ~/.env
fi

# 杀掉占用 8080 端口的旧进程（如有，可能有多个）
OLD_PIDS=$(lsof -ti:8080 2>/dev/null || true)
if [ -n "$OLD_PIDS" ]; then
  echo "杀掉旧进程 PID=$OLD_PIDS ..."
  echo "$OLD_PIDS" | xargs kill -9 2>/dev/null || true
  sleep 1
fi

# 编译所有模块并启动
./mvnw -pl himarket-bootstrap -am install -DskipTests -q
./mvnw -pl himarket-bootstrap spring-boot:run -DskipTests
