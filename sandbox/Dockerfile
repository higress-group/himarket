# HiMarket 统一沙箱镜像
# 预装所有 CLI Agent 工具 + 常用编程语言运行时 + 开发工具 + ACP Sidecar
# 适用于 HiCoding / HiWork / HiCli 全场景
# 单容器方案：CLI Agent + Sidecar 合并在同一个容器中
#
# 基础镜像：Alibaba Cloud Linux 3 (兼容 CentOS/RHEL 生态，glibc)

FROM alibaba-cloud-linux-3-registry.cn-hangzhou.cr.aliyuncs.com/alinux3/alinux3

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
RUN yum install -y \
    # 网络 & 下载
    ca-certificates \
    curl \
    wget \
    # 版本控制
    git \
    openssh-clients \
    # 构建工具链
    gcc \
    gcc-c++ \
    make \
    cmake \
    pkgconfig \
    # 文本处理 & 实用工具
    jq \
    vim \
    less \
    tree \
    unzip \
    zip \
    tar \
    gzip \
    xz \
    # 进程管理
    procps-ng \
    htop \
    # DNS & 网络调试
    bind-utils \
    iputils \
    net-tools \
    # 开发库
    openssl-devel \
    zlib-devel \
    bzip2-devel \
    libffi-devel \
    readline-devel \
    sqlite-devel \
    # 其他
    sudo \
    which \
    findutils \
    glibc-langpack-en \
    && yum clean all \
    && rm -rf /var/cache/yum

# ============================================================
# 2. Python 3.11 (AI/ML 生态核心语言)
# ============================================================
RUN yum install -y \
    python3 \
    python3-devel \
    python3-pip \
    && yum clean all \
    && rm -rf /var/cache/yum

# pip 换源
RUN pip3 config set global.index-url https://mirrors.aliyun.com/pypi/simple/ && \
    pip3 config set global.trusted-host mirrors.aliyun.com && \
    pip3 install --no-cache-dir --upgrade pip setuptools wheel

# ============================================================
# 3. Node.js 20 LTS (claude-code / codex 依赖 npx)
# ============================================================
RUN curl -fsSL https://mirrors.aliyun.com/nodejs-release/v20.18.1/node-v20.18.1-linux-x64.tar.xz \
    -o /tmp/node.tar.xz && \
    tar -xJf /tmp/node.tar.xz -C /usr/local --strip-components=1 && \
    rm -f /tmp/node.tar.xz

# npm 换源
RUN npm config set registry https://registry.npmmirror.com

# ============================================================
# 4. Go 1.22
# ============================================================
RUN curl -fsSL https://mirrors.aliyun.com/golang/go1.22.8.linux-amd64.tar.gz \
    -o /tmp/go.tar.gz && \
    tar -C /usr/local -xzf /tmp/go.tar.gz && \
    rm -f /tmp/go.tar.gz

ENV GOPATH=/root/go \
    PATH="/usr/local/go/bin:/root/go/bin:${PATH}" \
    GOPROXY=https://goproxy.cn,direct

# ============================================================
# 5. Rust (minimal)
# ============================================================
ENV RUSTUP_DIST_SERVER=https://rsproxy.cn \
    RUSTUP_UPDATE_ROOT=https://rsproxy.cn/rustup

RUN curl --proto '=https' --tlsv1.2 -sSf https://rsproxy.cn/rustup-init.sh | \
    sh -s -- -y --default-toolchain stable --profile minimal && \
    echo '[source.crates-io]' > /root/.cargo/config.toml && \
    echo 'replace-with = "rsproxy-sparse"' >> /root/.cargo/config.toml && \
    echo '[source.rsproxy-sparse]' >> /root/.cargo/config.toml && \
    echo 'registry = "sparse+https://rsproxy.cn/index/"' >> /root/.cargo/config.toml

ENV PATH="/root/.cargo/bin:${PATH}"

# ============================================================
# 6. Java 17 (Dragonwell，与 himarket-server 保持一致)
# ============================================================
RUN curl -fsSL https://dragonwell.oss-cn-shanghai.aliyuncs.com/17.0.12.0.12%2B10/Alibaba_Dragonwell_Standard_17.0.12.0.12.10_x64_linux.tar.gz \
    -o /tmp/jdk.tar.gz && \
    mkdir -p /usr/local/java && \
    tar -xzf /tmp/jdk.tar.gz -C /usr/local/java --strip-components=1 && \
    rm -f /tmp/jdk.tar.gz

ENV JAVA_HOME=/usr/local/java \
    PATH="/usr/local/java/bin:${PATH}"

# ============================================================
# 7. 常用 Python 包 (AI 开发常用)
# ============================================================
RUN pip3 install --no-cache-dir \
    requests \
    httpx \
    flask \
    fastapi \
    uvicorn \
    pydantic \
    pytest \
    black \
    ruff \
    mypy \
    jupyter \
    numpy \
    pandas

# ============================================================
# 8. 常用 Node.js 全局工具
# ============================================================
RUN npm install -g \
    typescript \
    ts-node \
    tsx \
    eslint \
    prettier \
    vitest

# ============================================================
# 9. CLI Agent 工具安装
# ============================================================

# --- qodercli (native binary, 已适配) ---
RUN curl -fsSL https://qoder.com/install | bash

# --- qwen-code (npm package, 已适配) ---
RUN npm install -g @qwen-code/qwen-code@latest

# --- kiro-cli (native binary) ---
# TODO: 待适配，替换为实际的安装命令
# RUN curl -fsSL https://example.com/kiro-cli/install | bash

# --- claude-code (npm package) ---
# TODO: 待适配，确认包名后取消注释
# RUN npm install -g @anthropic-ai/claude-code

# --- codex (npm package) ---
# TODO: 待适配，确认包名后取消注释
# RUN npm install -g @openai/codex

# --- opencode (native binary) ---
# TODO: 待适配，替换为实际的安装命令
# RUN curl -fsSL https://example.com/opencode/install | bash

# ============================================================
# 10. ACP Sidecar (内置到统一镜像)
# ============================================================
# Sidecar 负责在容器内提供 WebSocket 端点 (:8080/ws)
# 将后端的 JSON-RPC 消息桥接到 CLI 进程的 stdin/stdout
#
# TODO: 替换为实际的 sidecar 二进制构建或下载
# COPY acp-sidecar /usr/local/bin/acp-sidecar
# RUN chmod +x /usr/local/bin/acp-sidecar

# ============================================================
# 11. 启动脚本
# ============================================================
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

# ============================================================
# 12. 工作空间 & 用户配置
# ============================================================
RUN mkdir -p /workspace && chmod 777 /workspace

WORKDIR /workspace

# Git 全局配置（沙箱内默认值）
RUN git config --global init.defaultBranch main && \
    git config --global core.autocrlf input && \
    git config --global --add safe.directory /workspace

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
