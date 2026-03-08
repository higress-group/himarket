#!/bin/bash
# 本地启动脚本：编译所有模块并启动 Spring Boot 应用
# 用法：./scripts/run.sh
# 特性：
#   - 编译成 fat jar 后台启动（不阻塞终端）
#   - 优雅关闭旧进程，带超时兜底
#   - 轮询等待启动完成，返回明确的成功/失败状态
#   - 适合 AI agent 在 agent loop 中调用

set -euo pipefail

# 切换到项目根目录
cd "$(dirname "$0")/.."

# ========== 配置 ==========
APP_PORT=8080
PID_FILE=".app.pid"
LOG_FILE="$HOME/himarket.log"
BOOT_MODULE="himarket-bootstrap"
JAR_PATH="$BOOT_MODULE/target/$BOOT_MODULE-1.0-SNAPSHOT.jar"
STARTUP_TIMEOUT=120  # 启动超时（秒）
SHUTDOWN_TIMEOUT=10  # 优雅关闭超时（秒）

# ========== 加载环境变量 ==========
if [ -f ~/.env ]; then
  set -a
  source ~/.env
  set +a
fi

# ========== 函数定义 ==========

stop_old_process() {
  local pid=""

  # 优先从 PID 文件获取
  if [ -f "$PID_FILE" ]; then
    pid=$(cat "$PID_FILE" 2>/dev/null || true)
    if [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null; then
      pid=""  # 进程已不存在
    fi
  fi

  # 兜底：通过端口查找
  if [ -z "$pid" ]; then
    pid=$(lsof -ti:"$APP_PORT" 2>/dev/null || true)
  fi

  if [ -z "$pid" ]; then
    return 0
  fi

  echo "⏹ 停止旧进程 PID=$pid ..."

  # 先 SIGTERM 优雅关闭
  echo "$pid" | xargs kill 2>/dev/null || true

  # 等待进程退出
  local waited=0
  while [ $waited -lt $SHUTDOWN_TIMEOUT ]; do
    if ! echo "$pid" | xargs kill -0 2>/dev/null; then
      echo "  旧进程已退出"
      rm -f "$PID_FILE"
      return 0
    fi
    sleep 1
    waited=$((waited + 1))
  done

  # 超时，强制杀掉
  echo "  优雅关闭超时，强制终止..."
  echo "$pid" | xargs kill -9 2>/dev/null || true
  sleep 1
  rm -f "$PID_FILE"
}

compile() {
  echo "🔨 编译中..."
  # 单次 Maven 调用：编译 + 打包成 fat jar，跳过 Spotless 检查加速编译
  if ! ./mvnw -pl "$BOOT_MODULE" -am package -DskipTests -Dspotless.check.skip=true -q 2>&1; then
    echo "❌ 编译失败"
    return 1
  fi

  if [ ! -f "$JAR_PATH" ]; then
    echo "❌ 编译产物不存在: $JAR_PATH"
    return 1
  fi
  echo "✅ 编译成功"
}

start_app() {
  echo "🚀 启动应用..."

  # 清空旧日志便于判断启动状态
  : > "$LOG_FILE"

  # 后台启动 jar（日志由 logback LOCAL_FILE appender 写入 LOG_FILE，stdout 丢弃避免重复）
  nohup java \
    --add-opens java.base/java.util=ALL-UNNAMED \
    --add-opens java.base/java.lang=ALL-UNNAMED \
    --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
    -jar "$JAR_PATH" \
    > /dev/null 2>&1 &

  local app_pid=$!
  echo "$app_pid" > "$PID_FILE"
  echo "  PID=$app_pid"
}

wait_for_ready() {
  echo "⏳ 等待启动完成（最多 ${STARTUP_TIMEOUT}s）..."
  local waited=0

  while [ $waited -lt $STARTUP_TIMEOUT ]; do
    # 检查进程是否还活着
    local pid=$(cat "$PID_FILE" 2>/dev/null || true)
    if [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null; then
      echo "❌ 进程已退出，启动失败。查看日志: $LOG_FILE"
      tail -20 "$LOG_FILE"
      return 1
    fi

    # 检查日志中的启动成功标志
    if grep -q "Started HiMarketApplication" "$LOG_FILE" 2>/dev/null; then
      echo "✅ 启动成功 (${waited}s)"
      return 0
    fi

    # 检查常见启动失败标志
    if grep -q "APPLICATION FAILED TO START" "$LOG_FILE" 2>/dev/null; then
      echo "❌ 启动失败。错误信息:"
      grep -A 10 "APPLICATION FAILED TO START" "$LOG_FILE" | head -15
      return 1
    fi

    sleep 2
    waited=$((waited + 2))

    # 每 20 秒打印一次进度
    if [ $((waited % 20)) -eq 0 ]; then
      echo "  已等待 ${waited}s..."
    fi
  done

  echo "❌ 启动超时（${STARTUP_TIMEOUT}s）。最后几行日志:"
  tail -20 "$LOG_FILE"
  return 1
}

# ========== 主流程 ==========

stop_old_process
compile || exit 1
start_app
wait_for_ready || exit 1

echo "🎉 服务就绪: http://localhost:$APP_PORT"
