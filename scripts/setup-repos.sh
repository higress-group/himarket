#!/usr/bin/env bash
# 初始化 reference-projects/ 下的外部仓库（浅克隆）。
# 已存在的仓库会跳过，不会覆盖。
#
# 用法:
#   ./scripts/setup-repos.sh                  # 克隆所有外部仓库（可编辑项目会提示输入 fork 地址）
#   ./scripts/setup-repos.sh nacos            # 只克隆 nacos（只读）
#   ./scripts/setup-repos.sh higress-doc      # 只克隆 higress 文档站（需输入 fork 地址）
#   ./scripts/setup-repos.sh higress-doc git@github.com:yourname/higress-group.github.io.git
#                                             # 直接指定 fork 地址，跳过交互提示

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPOS_DIR="$ROOT_DIR/reference-projects"

# ── 仓库定义 ──────────────────────────────────────────────
# 只读仓库：直接克隆上游，禁止修改
# 格式: name|url|branch|local_dir
READONLY_REPOS=(
  "nacos|https://github.com/alibaba/nacos.git|develop|nacos"
)

# 可编辑仓库：需要用户提供自己的 fork 地址，脚本自动配置 upstream
# 格式: name|upstream_url|branch|local_dir
EDITABLE_REPOS=(
  "higress-doc|git@github.com:higress-group/higress-group.github.io.git|ai|higress-group.github.io"
)

# ── 函数 ──────────────────────────────────────────────────
clone_readonly() {
  local name="$1" url="$2" branch="$3" dir="$4"
  local target="$REPOS_DIR/$dir"

  if [ -d "$target/.git" ]; then
    echo "[$name] 已存在，跳过: $target"
    return 0
  fi

  echo "[$name] 克隆只读仓库: $url (分支: $branch) → $target"
  mkdir -p "$REPOS_DIR"
  git clone --depth 1 --branch "$branch" "$url" "$target"
  echo "[$name] 完成（只读参考，请勿修改）"
}

clone_editable() {
  local name="$1" upstream_url="$2" branch="$3" dir="$4" fork_url="${5:-}"
  local target="$REPOS_DIR/$dir"

  if [ -d "$target/.git" ]; then
    echo "[$name] 已存在，跳过: $target"
    return 0
  fi

  # 如果没有通过参数传入 fork 地址，交互式提示用户输入
  if [ -z "$fork_url" ]; then
    echo ""
    echo "[$name] 这是一个可编辑项目，需要通过你自己的 fork 提交改动。"
    echo "  上游仓库: $upstream_url"
    echo "  请先 fork 上游仓库到你的 GitHub 账号，然后输入你的 fork 地址。"
    echo ""
    read -rp "  你的 fork 地址（留空跳过）: " fork_url

    if [ -z "$fork_url" ]; then
      echo "[$name] 已跳过。你可以稍后运行以下命令单独克隆："
      echo "  ./scripts/setup-repos.sh $name <your-fork-url>"
      return 0
    fi
  fi

  echo "[$name] 克隆 fork: $fork_url (分支: $branch) → $target"
  mkdir -p "$REPOS_DIR"
  git clone --branch "$branch" "$fork_url" "$target"

  # 配置 upstream remote
  echo "[$name] 配置 upstream: $upstream_url"
  git -C "$target" remote add upstream "$upstream_url"
  git -C "$target" fetch upstream "$branch" --depth 1

  echo "[$name] 完成"
  echo "  origin   → $fork_url（你的 fork，可推送）"
  echo "  upstream → $upstream_url（上游仓库，用于同步）"
}

print_available() {
  echo "可用项目:"
  for entry in "${READONLY_REPOS[@]}"; do
    IFS='|' read -r name _ _ _ <<< "$entry"
    echo "  $name (只读)"
  done
  for entry in "${EDITABLE_REPOS[@]}"; do
    IFS='|' read -r name _ _ _ <<< "$entry"
    echo "  $name (可编辑，需提供 fork 地址)"
  done
}

# ── 主流程 ─────────────────────────────────────────────────
filter="${1:-}"
fork_url_arg="${2:-}"

matched=false

# 处理只读仓库
for entry in "${READONLY_REPOS[@]}"; do
  IFS='|' read -r name url branch dir <<< "$entry"
  if [ -z "$filter" ] || [ "$filter" = "$name" ]; then
    clone_readonly "$name" "$url" "$branch" "$dir"
    matched=true
  fi
done

# 处理可编辑仓库
for entry in "${EDITABLE_REPOS[@]}"; do
  IFS='|' read -r name upstream_url branch dir <<< "$entry"
  if [ -z "$filter" ] || [ "$filter" = "$name" ]; then
    clone_editable "$name" "$upstream_url" "$branch" "$dir" "$fork_url_arg"
    matched=true
  fi
done

if [ -n "$filter" ] && [ "$matched" = false ]; then
  echo "未知项目: $filter"
  print_available
  exit 1
fi

echo ""
echo "全部完成。"
