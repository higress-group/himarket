# 实现计划：Nacos-CLI 沙箱集成

## 概述

将 Skill 传输方式从"后端打包压缩 → HTTP 传输压缩包到沙箱 → 解压"改为"传递 Nacos 坐标和凭证（nacos-env.yaml）→ 沙箱内 nacos-cli 下载"。同时移除 tar.gz 压缩解压机制（Skill 文件改由 nacos-cli 下载后，剩余配置文件数量很少，逐个 writeFile 即可）。按依赖顺序实现：底层能力（ExecResult、exec 方法）→ 移除 extractArchive → 配置生成（NacosEnvGenerator、CliConfigGenerator 改造）→ 下载阶段（SkillDownloadPhase，按 nacosId 分组批量下载）→ Pipeline 注册 → 类型推断兼容。

## 任务

- [x] 1. 新增 ExecResult record 和 SandboxHttpClient.exec() 方法
  - [x] 1.1 创建 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/runtime/ExecResult.java`，定义 `record ExecResult(int exitCode, String stdout, String stderr)`
    - _需求: 2.4_
  - [x] 1.2 在 `SandboxHttpClient` 中新增 `exec(String baseUrl, String sandboxId, String command, List<String> args, Duration timeout)` 方法
    - 调用 Sidecar `POST /exec`，请求体为 `{"command": command, "args": args}`
    - HTTP 200 时解析 JSON 返回 ExecResult；非 200 时抛出 IOException（包含 sandboxId、状态码、响应体）
    - 使用调用方传入的 timeout 作为 HTTP 请求超时时间
    - _需求: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_
  - [ ]* 1.3 编写 SandboxHttpClient.exec() 属性测试
    - **Property 4: exec 非 200 响应抛出 IOException**
    - **验证: 需求 1.4**
  - [ ]* 1.4 编写 SandboxHttpClient.exec() 单元测试
    - 覆盖 200 成功、非 200 失败、中断处理等场景
    - _需求: 1.1, 1.3, 1.4, 1.6_

- [x] 2. SandboxProvider 接口新增 exec 默认方法 + RemoteSandboxProvider 覆写
  - [x] 2.1 在 `SandboxProvider` 接口中新增 `exec(SandboxInfo info, String command, List<String> args, Duration timeout)` 默认方法，默认抛出 UnsupportedOperationException
    - _需求: 2.1, 2.2_
  - [x] 2.2 在 `RemoteSandboxProvider` 中覆写 `exec()` 方法，委托给 `SandboxHttpClient.exec()`
    - _需求: 2.3_

- [x] 3. 移除 extractArchive 相关逻辑，ConfigInjectionPhase 改为逐个 writeFile
  - [x] 3.1 移除 `SandboxHttpClient` 中的 `extractArchive()` 两个重载方法和 `EXTRACT_TIMEOUT` 常量
  - [x] 3.2 移除 `SandboxProvider` 接口中的 `extractArchive()` 默认方法
  - [x] 3.3 移除 `RemoteSandboxProvider` 中的 `extractArchive()` 覆写方法
  - [x] 3.4 改造 `ConfigInjectionPhase.execute()`：移除 tar.gz 压缩逻辑（`buildTarGz()` 方法）和 extractArchive 调用，改为逐个 `provider.writeFile()` 注入配置文件
  - [x] 3.5 移除 `ConfigInjectionPhase` 中的 `commons-compress` 相关 import（TarArchiveEntry、TarArchiveOutputStream、GzipCompressorOutputStream）
  - [x] 3.6 更新 `InitPhasesTest` 中 ConfigInjectionPhase 相关测试，将 extractArchive mock 改为 writeFile mock

- [x] 4. 检查点 — 确保编译通过
  - 主代码编译通过（mvn compile）。测试编译有已有的不相关错误（NacosServiceImpl、RuntimeControllerTest）。

- [x] 5. 新增 NacosEnvGenerator 工具类
  - [x] 5.1 创建 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/NacosEnvGenerator.java`
    - 实现 `generateNacosEnvFiles(String workingDirectory, List<ResolvedSkillEntry> skills)` 方法
    - 按 nacosId 分组，为每个 Nacos 实例生成 `.nacos/nacos-env-{nacosId}.yaml`
    - 实现 `parseServerAddr(String serverAddr)` 方法，使用 `java.net.URI` 解析 host/port，无端口时默认 8848
    - 实现 `buildNacosEnvYaml(ResolvedSkillEntry skill)` 方法，根据 accessKey/secretKey 是否非空决定 authType（aliyun/nacos）
    - serverAddr 格式不合法时记录 error 日志并跳过该实例
    - _需求: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 8.1, 8.3_
  - [ ]* 5.2 编写 NacosEnvGenerator 属性测试（NacosEnvGeneratorPropertyTest）
  - [ ]* 5.3 编写 NacosEnvGenerator 属性测试
  - [ ]* 5.4 编写 NacosEnvGenerator 属性测试
  - [ ]* 5.5 编写 NacosEnvGenerator 单元测试（NacosEnvGeneratorTest）

- [x] 6. CliConfigGenerator 接口改造与各实现类更新
  - [x] 6.1 在 `CliConfigGenerator` 接口中新增 `skillsDirectory()` 默认方法（返回 `"skills/"`）
    - _需求: 7.2_
  - [x] 6.2 改造 `CliConfigGenerator` 接口的 `generateSkillConfig()` 默认实现，调用 `NacosEnvGenerator.generateNacosEnvFiles()` 生成 nacos-env.yaml
    - _需求: 7.1, 7.3, 7.4_
  - [x] 6.3 改造 `QoderCliConfigGenerator`：删除旧 `generateSkillConfig()` 覆写，新增 `skillsDirectory()` 返回 `.qoder/skills/`
    - _需求: 6.1, 7.2_
  - [x] 6.4 改造 `ClaudeCodeConfigGenerator`：删除旧 `generateSkillConfig()` 覆写，新增 `skillsDirectory()` 返回 `.claude/skills/`
    - _需求: 6.2, 7.2_
  - [x] 6.5 改造 `QwenCodeConfigGenerator`：删除旧 `generateSkillConfig()` 覆写，新增 `skillsDirectory()` 返回 `.qwen/skills/`
    - _需求: 6.4, 7.2_
  - [x] 6.6 改造 `OpenCodeConfigGenerator`：删除旧 `generateSkillConfig()` 覆写，新增 `skillsDirectory()` 返回 `.opencode/skills/`
    - _需求: 6.3, 7.2_
  - [ ]* 6.7 编写 CliConfigGenerator 属性测试（CliConfigGeneratorPropertyTest）

- [x] 7. 检查点 — 确保编译通过
  - 主代码编译通过。更新了 QwenCodeConfigGeneratorSkillTest 适配新行为。

- [x] 8. 新增 SkillDownloadPhase
  - [x] 8.1 创建 `himarket-server/src/main/java/com/alibaba/himarket/service/acp/runtime/SkillDownloadPhase.java`
    - 实现 InitPhase 接口，name=`skill-download`，order=350
    - `shouldExecute()` 检查 resolvedSessionConfig 中 skills 列表是否非空
    - `execute()` 将用户勾选的 skills 按 nacosId 分组，每组将 skillName 列表拼接为参数，通过 `provider.exec()` 执行 `nacos-cli skill-get`
    - 根据 providerKey 映射 skills 输出目录
    - exec 超时设置为 60 秒
    - 单个 nacosId 分组下载失败记录警告日志，继续后续分组
    - `retryPolicy()` 返回 `RetryPolicy.none()`，`verify()` 返回 true
    - _需求: 4.1-4.9, 8.2-8.4_
  - [ ]* 8.2-8.5 编写 SkillDownloadPhase 属性测试和单元测试

- [x] 9. ConfigFileBuilder.inferConfigType() 兼容更新
  - [x] 9.1 修改 `ConfigFileBuilder.inferConfigType()` 方法，新增 `.nacos/*.yaml` 路径识别为 `SKILL_CONFIG`
    - _需求: 9.1, 9.2_
  - [ ]* 9.2-9.3 编写 ConfigFileBuilder 属性测试和单元测试

- [x] 10. 注册 SkillDownloadPhase 到 Pipeline
  - [x] 10.1 修改 `AcpSessionInitializer`，在构建 SandboxInitPipeline 时将 `SkillDownloadPhase` 加入 phases 列表
    - _需求: 5.1, 5.2, 5.3_

- [x] 11. 最终检查点 — 全量验证
  - 主代码编译通过（mvn compile -pl himarket-server -am）。Spotless 格式化已应用。
  - 测试编译有已有的不相关错误（NacosServiceImpl 缺少方法、RuntimeControllerTest 引用不存在的类），非本次改动引起。

- [x] 12. 沙箱侧改造 — Dockerfile 安装 nacos-cli + Sidecar 新增 /exec 端点
  - [x] 12.1 在 `sandbox/Dockerfile` 中新增 nacos-cli 安装步骤（`curl -fsSL https://nacos.io/nacos-installer.sh | bash -s -- --cli -v 0.0.9`）
    - 放在 CLI Agent 工具安装之后、Sidecar Server 之前
  - [x] 12.2 在 `sandbox/sidecar-server/index.js` 中新增 `POST /exec` HTTP 端点
    - 请求体: `{"command": "nacos-cli", "args": [...], "cwd": "/workspace", "timeout": 120000}`
    - 响应体: `{"exitCode": 0, "stdout": "...", "stderr": "..."}`
    - 使用 `child_process.execFile()` 执行命令，支持超时（默认 120s）
    - 命令不存在返回 exitCode=127，超时返回 exitCode=124
    - _需求: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6_

## 说明

- 标记 `*` 的子任务为可选测试任务，可跳过以加速 MVP 交付
- 每个任务引用了对应的需求编号，确保需求全覆盖
- 属性测试使用 jqwik 库，每个属性对应设计文档中的正确性属性
- 检查点任务用于增量验证，确保每个阶段的代码正确性
