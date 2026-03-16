# Nacos-CLI 沙箱环境使用指南

本指南介绍如何在沙箱环境中使用 nacos-cli 和 nacos-env 配置文件实现 skill 下载到指定目录。

## 环境配置

### 0. 安装 nacos-cli

Linux / macOS：

```
curl -fsSL https://nacos.io/nacos-installer.sh | sudo bash -s -- --cli -v 0.0.9
```

Windows（PowerShell）：

```
iwr -UseBasicParsing https://nacos.io/nacos-installer.ps1 -OutFile $env:TEMP\nacos-installer.ps1; & $env:TEMP\nacos-installer.ps1 -cli; Remove-Item $env:TEMP\nacos-installer.ps1
```

### 1. 创建 nacos-env 配置文件

在沙箱环境中创建配置文件（推荐命名为 `nacos-env.yaml`）：

```yaml
# Nacos 服务器地址
host: 127.0.0.1

# Nacos 服务器端口
port: 8848

# 认证类型：nacos（默认）或 aliyun
authType: nacos

# Nacos 用户名
username: nacos

# Nacos 密码
password: nacos

# 命名空间 ID（可选，留空表示 public 命名空间）
namespace: ""

# 如果使用阿里云 Nacos，需要配置以下字段：
# accessKey: your-access-key
# secretKey: your-secret-key
```

### 2. 配置文件路径

配置文件支持以下路径格式：
- 绝对路径：`/path/to/nacos-env.yaml`
- 相对路径：`./nacos-env.yaml`
- 波浪号路径：`~/nacos-env.yaml` 或 `~/.config/nacos-env.yaml`

## Skill 下载命令

### 1. 下载 Skill

#### 使用配置文件 + 默认目录（~/.skills）

```bash
nacos-cli skill-get skill-creator --config ./nacos-env.yaml
```

```bash
nacos-cli skill-get skill1 skill2 skill3 --config ./nacos-env.yaml
```

#### 使用配置文件 + 指定目录

```bash
# 下载到绝对路径
nacos-cli skill-get skill-creator --config ./nacos-env.yaml -o /sandbox/skills

# 下载到相对路径
nacos-cli skill-get skill-creator --config ./nacos-env.yaml -o ./skills

# 下载到用户目录
nacos-cli skill-get skill-creator --config ./nacos-env.yaml -o ~/my-skills
```

#### 不使用配置文件（命令行参数）

```bash
nacos-cli skill-get skill-creator \
  --host 127.0.0.1 \
  --port 8848 \
  -u nacos \
  -p nacos \
  -o /sandbox/skills
```

## 沙箱环境典型使用场景

### 场景 1：初始化沙箱环境

```bash
# 1. 创建配置文件
cat > /sandbox/nacos-env.yaml << EOF
host: 127.0.0.1
port: 8848
authType: nacos
username: nacos
password: nacos
namespace: ""
EOF

# 2. 创建 skill 目录
mkdir -p /sandbox/skills

# 3. 下载所需的 skill
nacos-cli skill-get skill-creator --config /sandbox/nacos-env.yaml -o /sandbox/skills
nacos-cli skill-get skill-analyzer --config /sandbox/nacos-env.yaml -o /sandbox/skills
```

### 场景 2：持续同步开发环境

```bash
# 启动后台同步进程，监听所有 skill 变化
nacos-cli skill-sync --all \
  --config /sandbox/nacos-env.yaml \
  -d /sandbox/skills
```

### 场景 3：使用不同命名空间

```bash
# 开发环境配置
cat > /sandbox/dev-env.yaml << EOF
host: 127.0.0.1
port: 8848
username: nacos
password: nacos
namespace: dev
EOF

# 生产环境配置
cat > /sandbox/prod-env.yaml << EOF
host: 127.0.0.1
port: 8848
username: nacos
password: nacos
namespace: prod
EOF

# 从开发环境下载
nacos-cli skill-get my-skill --config /sandbox/dev-env.yaml -o /sandbox/dev-skills

# 从生产环境下载
nacos-cli skill-get my-skill --config /sandbox/prod-env.yaml -o /sandbox/prod-skills
```

## 配置优先级

配置参数的优先级从高到低：

1. **命令行参数**（最高优先级）
2. **配置文件**
3. **默认值**（最低优先级）

示例：

```bash
# host 使用命令行的 10.0.0.1，其他参数使用配置文件
nacos-cli skill-get my-skill --config ./nacos-env.yaml --host 10.0.0.1 -o /sandbox/skills
```

## 常见问题

### 1. 权限问题

如果遇到权限错误，确保目标目录有写入权限：

```bash
chmod 755 /sandbox/skills
```

### 2. 连接失败

检查 Nacos 服务器是否可访问：

```bash
curl http://127.0.0.1:8848/nacos/
```

### 3. 认证失败

确认配置文件中的用户名和密码正确，或使用命令行参数覆盖：

```bash
nacos-cli skill-get my-skill --config ./nacos-env.yaml -u admin -p admin123
```

### 4. 命名空间问题

如果找不到 skill，检查是否使用了正确的命名空间：

```bash
# 查看当前命名空间的 skill
nacos-cli skill-list --config ./nacos-env.yaml

# 切换到其他命名空间
nacos-cli skill-list --config ./nacos-env.yaml -n your-namespace-id
```

## 其他有用命令

### 上传 Skill

```bash
# 上传单个 skill
nacos-cli skill-upload /sandbox/skills/my-skill --config ./nacos-env.yaml

# 批量上传目录下所有 skill
nacos-cli skill-upload --all /sandbox/skills --config ./nacos-env.yaml
```

### 配置管理

```bash
# 列出所有配置
nacos-cli config-list --config ./nacos-env.yaml

# 获取特定配置
nacos-cli config-get myconfig DEFAULT_GROUP --config ./nacos-env.yaml
```

## 总结

在沙箱环境中使用 nacos-cli 的最佳实践：

1. 创建 nacos-env.yaml` 配置文件
2. 使用 `--config` 参数引用配置文件
3. 使用 `-o` 或 `-d` 参数指定 skill 下载目录
4. 对于开发环境，使用 `skill-sync --all` 保持实时同步
5. 对于生产环境，使用 `skill-get` 下载特定版本的 skill
