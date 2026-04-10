# HiMarket Makefile — 常用开发命令入口
# 用法: make <target>

.PHONY: help build compile test lint lint-fix run clean

# 默认目标
help: ## 显示所有可用命令
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

# ==================== 构建 ====================

build: ## 完整构建（编译 + 格式检查 + 测试）
	./mvnw clean verify

compile: ## 快速编译（跳过测试和格式检查）
	./mvnw -pl himarket-bootstrap -am package -DskipTests -Dspotless.check.skip=true -q

# ==================== 测试 ====================

test: ## 运行单元测试
	./mvnw test

test-it: ## 运行集成测试
	./mvnw test -DincludedGroups=integration

# ==================== 代码质量 ====================

lint: ## 检查代码格式（Spotless）
	./mvnw spotless:check

lint-fix: ## 自动修复代码格式
	./mvnw spotless:apply

# ==================== 运行 ====================

run: ## 编译并启动后端服务
	./scripts/run.sh

# ==================== 清理 ====================

clean: ## 清理构建产物
	./mvnw clean
