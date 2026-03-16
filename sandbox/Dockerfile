# HiMarket 统一沙箱镜像 (精简版)
# 预装 Node.js + Python + qodercli + qwen-code + opencode
# 适用于 HiCoding / HiWork / HiCli 全场景
# 单容器方案：Node.js Sidecar Server 桥接 WebSocket ↔ CLI Agent stdio
#
# 基础镜像：Rocky Linux 9 (RHEL 9 兼容，支持 x86_64 + aarch64 双架构)

FROM opensource-registry.cn-hangzhou.cr.aliyuncs.com/higress-group/rockylinux:9

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
# 配置阿里云镜像源（解决 mirrors.rockylinux.org 不可达问题）
RUN sed -e 's|^mirrorlist=|#mirrorlist=|g' \
    -e 's|^#baseurl=http://dl.rockylinux.org/$contentdir|baseurl=https://mirrors.aliyun.com/rockylinux|g' \
    -i.bak /etc/yum.repos.d/rocky*.repo

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
RUN curl -fsSL https://qoder.com/install | bash && \
    QODERCLI_BIN=$(find /root/.qoder/bin -name 'qodercli-*' -type f -executable | head -1) && \
    if [ -n "$QODERCLI_BIN" ]; then ln -sf "$QODERCLI_BIN" /usr/local/bin/qodercli; fi && \
    qodercli --version || echo "[sandbox] WARNING: qodercli not found after install"

# --- qwen-code (npm package) ---
RUN npm install -g @qwen-code/qwen-code@latest

# --- claude-agent-acp (Claude Code ACP 协议适配层) ---
RUN npm install -g @zed-industries/claude-agent-acp

# --- codex (npm package) ---
# TODO: 待适配
# RUN npm install -g @openai/codex

# --- opencode (npm package) ---
RUN npm install -g opencode-ai

# --- nacos-cli (Nacos 命令行工具，用于沙箱内下载 Skill) ---
RUN curl -fsSL https://nacos.io/nacos-installer.sh | bash -s -- --cli -v 0.0.9 && \
    nacos-cli help

# ============================================================
# 5. Sidecar Server — Node.js WebSocket 服务，支持多 CLI 进程
# ============================================================
COPY sidecar-server/ /usr/local/lib/sidecar-server/
RUN cd /usr/local/lib/sidecar-server && npm install --production
# node-pty 需要编译原生模块，上面已安装 gcc/gcc-c++/make/python3

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
