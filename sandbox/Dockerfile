# HiMarket 统一沙箱镜像 (精简版)
# 预装 Node.js + Python + qodercli + qwen-code
# 适用于 HiCoding / HiWork / HiCli 全场景
# 单容器方案：websocat 桥接 WebSocket ↔ CLI Agent stdio
#
# 基础镜像：Rocky Linux 9 (RHEL 9 兼容，支持 x86_64 + aarch64 双架构)

FROM rockylinux:9

LABEL maintainer="himarket-team"
LABEL description="HiMarket unified sandbox image for HiCoding/HiWork/HiCli"

# ============================================================
# 0. 基础环境：时区 + locale
# ============================================================
ENV LANG=en_US.UTF-8 \
    LC_ALL=en_US.UTF-8 \
    TZ=Asia/Shanghai

RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

# ============================================================
# 1. 系统基础工具
# ============================================================
RUN yum install -y --allowerasing \
    ca-certificates \
    curl \
    wget \
    git \
    openssh-clients \
    gcc \
    gcc-c++ \
    make \
    jq \
    less \
    unzip \
    zip \
    tar \
    gzip \
    xz \
    procps-ng \
    openssl-devel \
    zlib-devel \
    libffi-devel \
    sudo \
    which \
    findutils \
    glibc-langpack-en \
    && yum clean all \
    && rm -rf /var/cache/yum

# ============================================================
# 2. Python 3
# ============================================================
RUN yum install -y \
    python3 \
    python3-devel \
    python3-pip \
    && yum clean all \
    && rm -rf /var/cache/yum

RUN pip3 config set global.index-url https://mirrors.aliyun.com/pypi/simple/ && \
    pip3 config set global.trusted-host mirrors.aliyun.com && \
    pip3 install --no-cache-dir --upgrade pip setuptools wheel

# 常用 Python 包（按需精简）
RUN pip3 install --no-cache-dir \
    requests \
    httpx \
    pydantic

# ============================================================
# 3. Node.js 20 LTS (双架构支持)
# ============================================================
RUN ARCH=$(uname -m) && \
    if [ "$ARCH" = "x86_64" ]; then NODE_ARCH="x64"; \
    elif [ "$ARCH" = "aarch64" ]; then NODE_ARCH="arm64"; \
    else echo "Unsupported arch: $ARCH" && exit 1; fi && \
    curl -fsSL "https://mirrors.aliyun.com/nodejs-release/v20.18.1/node-v20.18.1-linux-${NODE_ARCH}.tar.xz" \
    -o /tmp/node.tar.xz && \
    tar -xJf /tmp/node.tar.xz -C /usr/local --strip-components=1 && \
    rm -f /tmp/node.tar.xz

RUN npm config set registry https://registry.npmmirror.com

# ============================================================
# 4. CLI Agent 工具安装
# ============================================================

# --- qodercli (native binary) ---
RUN curl -fsSL https://qoder.com/install | bash

# --- qwen-code (npm package) ---
RUN npm install -g @qwen-code/qwen-code@latest

# --- kiro-cli (native binary) ---
# TODO: 待适配
# RUN curl -fsSL https://example.com/kiro-cli/install | bash

# --- claude-code (npm package) ---
# TODO: 待适配
# RUN npm install -g @anthropic-ai/claude-code

# --- codex (npm package) ---
# TODO: 待适配
# RUN npm install -g @openai/codex

# --- opencode (native binary) ---
# TODO: 待适配
# RUN curl -fsSL https://example.com/opencode/install | bash

# ============================================================
# 5. websocat — WebSocket ↔ stdio 桥接器 (替代自研 ACP Sidecar)
# ============================================================
# websocat 将 WebSocket 连接与 CLI 进程的 stdin/stdout 双向桥接，
# 天然适配 ACP 协议（newline-delimited JSON-RPC）。
# GitHub: https://github.com/vi/websocat
COPY websocat.x86_64-unknown-linux-musl /usr/local/bin/websocat
RUN chmod +x /usr/local/bin/websocat

# ============================================================
# 6. 启动脚本
# ============================================================
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

# ============================================================
# 7. 工作空间 & 用户配置
# ============================================================
RUN mkdir -p /workspace && chmod 777 /workspace

WORKDIR /workspace

RUN git config --global init.defaultBranch main && \
    git config --global core.autocrlf input && \
    git config --global --add safe.directory /workspace

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
