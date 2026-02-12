## TL;DR

**一句话：我们要把桌面端 AI IDE 的 Agent 架构，搬到 Web 上，做一个多租户、全场景的 Agent 平台。**

核心洞察——Coding Agent 是当前落地最成熟的智能体形态，但剥掉"编码"这层皮，底层的 AgentLoop（接收指令 → 推理 → 调用工具 → 返回结果）是**领域无关**的。给它装上 python-pptx 就是 PPT Agent，装上浏览器自动化就是信息图 Agent，装上代码运行时就是全栈开发 Agent。变的是 Skill/MCP Server 组合，不变的是 AgentLoop + 协议栈。

基于这个洞察，HiMarket 2.0 要做的事：

- **双产品形态**：HiWork（AI 办公助手，对标扣子）+ HiCoding（AI 应用开发平台，对标扣子编程），共享同一套后端基础设施
- **协议桥接**：ACP Proxy（Java 后端，会话路由）+ ACP Bridge（沙箱内 sidecar，stdio↔WebSocket 转换），解决"浏览器无法直连远端 CLI"的核心矛盾
- **物理隔离交给 AgentRun**：每个会话一个独立沙箱 Pod，Agent Runtime 跑 AgentLoop，Sandbox 跑 MCP Server / 浏览器 / 编译器等辅助进程，互不干扰
- **多租户治理**：三层隔离（物理隔离 → 逻辑隔离 → 数据隔离）+ 弹性伸缩 + 预热池 + 租户级配额
- **全链路流式**：从 LLM 到 CLI stdout 到 Bridge 到 Proxy 到 WebSocket 到前端渲染，每一跳都是流式的，这是非谈判项
- **面向未来**：Transport Adapter 可插拔，ACP 协议未来支持 HTTP 传输时可直接去掉 Bridge 层

简单说，这不是做一个"Web 版 VS Code"，而是**从已验证的 Coding Agent 架构中提取可复用的架构模式，构建一个能跑任意 Agent 的 Web 平台**。把云原生的产品全部可以串联进来：HiMarket、AI 网关、AgentRun 是核心。不要等待用户开发他们的 Agent 部署到云上，我们来帮用户创建 Agent 并提供开箱即用的 Agent 产品。

## 1 引言

AI 产品迭代实在太快了，以至于 HiMarket 1.0 还没有正式 release，本文已经开始尝试探索 HiMarket 2.0 的架构设计。HiMarket 2.0 的核心目标是：在 Web 环境下，将应用开发、Agent 开发、AI 办公场景（PPT 制作、写作、信息图生成）等多元 Agent 能力，以多租户、会话隔离、弹性伸缩的方式交付给用户。

## 1.1 HiMarket 2.0 POC 预览

本文很长，我知道很多人可能没有耐心读下去，或者不知道我要搞一个什么东西，所以先看 POC 效果再讲架构。

#### HiWork POC 效果预览

基于下边的架构思路，我已完成 HiWork 的 POC 版本，初步验证了"统一 AgentLoop + 按场景挂载 Skill/MCP Server"的可行性。以下是两个典型场景的实际效果：

**场景一：AI 生成 PPT**

用户通过自然语言描述 PPT 需求，Agent 调用 pptx Skill 自动生成完整的演示文稿。

此处为语雀视频卡片，点击链接查看：[钉钉录屏_2026-02-10 155450.mp4](https://aliyuque.antfin.com/jingfeng.xjf/qot9ec/kphdgg22fvhdk0wl#AQRdB)

**场景二：AI 生成 PDF**

用户描述文档需求，Agent 调用 PDF Skill 生成排版完整的 PDF 文档。

![img](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/156306/1770814556275-9316492e-1872-400a-8d98-6789d176e2b9.png)

HiCoding 场景还没搞，POC 进行中。

### 1.2 为什么从 AI IDE 架构切入

在介绍 HiMarket 2.0 架构之前，先需要了解桌面端 AI IDE 的架构，以此作为起点，并非偶然，而是基于以下推导链条：

1. **Coding Agent 是当前落地最广泛的智能体形态。** 从 Cursor、Claude Code 到 Qoder，编码场景已经率先跑通了"人类意图 → Agent 规划 → 工具调用 → 产物交付"的完整闭环，积累了最成熟的工程实践。其中 Qoder 尤为典型——其桌面端 IDE、桌面端办公助手 QoderWork、CLI 三种形态共享同一个 AgentLoop，验证了"一套 Agent 内核驱动多种上层应用"的可行性。
2. **Coding Agent 的架构可以泛化为通用 Agent 架构。** 剥离"编码"这个具体领域，其底层模型——一个 AgentLoop 接收指令、调用 Skill/MCP 工具、流式返回结果——本身就是领域无关的。给同一个 AgentLoop 装上 python-pptx 的 MCP Server，它就是 PPT Agent；装上浏览器自动化工具，它就是信息图 Agent；装上文档处理工具，它就是写作 Agent。**变的是 Skill 和 MCP Server 的组合，不变的是 AgentLoop + 协议栈 + 运行时管理的架构骨架。**
3. **AI IDE 已经解决了 Agent 架构中最难的工程问题。** 包括：Agent 与宿主的双向流式通信（ACP/stdio）、Agent 对外部工具的标准化调用（MCP）、多步推理与工具调用的编排（AgentLoop）、用户对 Agent 行为的审批与干预（Human-in-the-loop）。这些问题和解法对 Web 端的 Agent 平台同样适用。

因此，剖析 AI IDE 架构的真正目的，不是为了做一个"Web 版 VS Code"，而是**从已经验证的 Coding Agent 架构中提取可复用的架构模式，指导 HiMarket 这样一个多能力、多租户的 Web 端 Agent 平台的设计**。

### 1.3 本文结构

本文首先剖析主流桌面端 AI IDE 的架构范式与协议栈，然后逐层推导 Web 端的适配方案，接着明确 HiMarket 的双产品形态规划（HiWork — AI 办公助手 + HiCoding — AI 应用开发平台），最终给出一套从协议桥接、进程管理、产物渲染到运营治理的完整架构蓝图。

## 2 桌面端 AI IDE 架构剖析

### 2.1 两种架构哲学

当前 AI IDE 市场呈现出两种截然不同的架构路线：

| 路线                        | 代表产品               | 核心思路                                                     |
| --------------------------- | ---------------------- | ------------------------------------------------------------ |
| **GUI 优先 / IDE 深度定制** | Cursor, Windsurf       | Fork VS Code 内核，重构上下文管理和 Agent 编排，将 AI 深度嵌入编辑器的每一个环节 |
| **CLI 优先 / 终端自治**     | Claude Code, Qoder CLI | Agent 以独立 CLI 进程存在，为上层多种应用形态提供统一的 AgentLoop，强调可组合性和自主性 |

**Cursor** 的架构核心是"双代理"模型：云端的管理代理（Manager Agent）负责高层规划与任务拆解，本地的执行代理（Worker Agent）负责文件读写和终端命令。两者通过 gRPC/SSE 流式通信。此外，Cursor 在本地构建了类 RAG 的代码索引系统，动态检索最相关的代码片段推入 prompt，以应对代码库规模超出上下文窗口的挑战。

**Claude Code** 选择了终端作为主战场，架构分为三层：

- **核心层（Core）**：处理主对话流，消耗共享上下文窗口（200K~1M token）
- **委托层（Delegation）**：主代理孵化具有独立上下文的子代理（Sub-agents），任务完成后仅返回摘要，避免主对话上下文膨胀
- **扩展层（Extension）**：通过 MCP 连接外部服务，通过 Hook 系统实现 shell 自动化

**Qoder** 是这一路线的典型代表，它提供三种产品形态——Qoder IDE（基于 VS Code Fork 的桌面端 IDE）、QoderWork（专注办公助手场景的桌面端应用）和 Qoder CLI（终端工具），但三者共享同一个底层：**Qoder CLI 提供的统一 AgentLoop**。Qoder IDE 中的 Quest 模式和 QoderWork 完全基于 Qoder CLI 构建，上层应用只负责 UI 交互和产物渲染，核心的推理、工具调用、任务编排全部下沉到 CLI 层。这种"一个 AgentLoop，多种桌面端交付形态"的架构，恰好印证了前文的判断——Agent 的核心是领域无关的 Loop，变的只是上层 UI 和挂载的 Skill/MCP 组合。Qoder CLI 本身资源开销低（比同类工具减少 70% 闲置内存占用），支持快速启动和异步处理，核心特色是基于规范驱动的"任务模式（Quest Mode）"。值得注意的是，Qoder 目前所有形态均为桌面端应用，尚未提供 Web 端——这也正是 HiMarket 探索 Web 端 Agent 架构的价值所在。

### 2.2 共性架构模型

尽管路线不同，所有 AI IDE 都遵循同一个底层模型。在展开架构图之前，先对几个关键术语做简要说明——如果你用过 Cursor 或 VS Code + AI 插件，这些概念你已经在日常使用中接触过了：

**术语速查**

| 术语               | 全称                                     | 你在 AI IDE 中见过它的地方                                   |
| ------------------ | ---------------------------------------- | ------------------------------------------------------------ |
| **Extension Host** | 扩展宿主进程                             | 你在 VS Code/Cursor 里装的每一个插件（Git、ESLint、AI 助手...），它们的代码都不是跑在编辑器 UI 里的，而是跑在一个独立的后台进程中，这个进程就叫 Extension Host。这样设计的好处是：某个插件卡死了，编辑器界面不会跟着卡。 |
| **IPC**            | Inter-Process Communication，进程间通信  | Extension Host 和编辑器 UI 是两个进程，它们之间需要"打电话"传递消息（比如插件告诉 UI"在第 10 行画一条红线"），这个通信机制就是 IPC。在桌面端通常走 stdio（标准输入输出）或 named pipe。 |
| **LSP**            | Language Server Protocol，语言服务器协议 | 你在编辑器里写代码时看到的自动补全、跳转定义、错误下划线，背后都是一个"语言服务器"在分析代码。LSP 是微软定义的标准协议，让任何编辑器都能对接任何语言的分析服务。 |
| **ACP**            | Agent Client Protocol，代理客户端协议    | 类似 LSP 的思路，但解决的问题是"编辑器如何与 AI Agent 通信"。你在 AI IDE 里跟 Agent 对话、Agent 请求你批准文件修改、Agent 流式输出代码，背后走的就是 ACP。目前主流的 CLI 工具（Claude Code、Goose、Aider、Qoder CLI 等）都已支持 ACP 协议。 |
| **MCP**            | Model Context Protocol，模型上下文协议   | 你在 AI IDE 里让 Agent 去读数据库、调浏览器、访问 API，Agent 不是自己直接做的，而是通过 MCP 协议调用一个个外部"工具服务器"。可以理解为 AI 世界的 USB-C 接口——标准化的工具插拔协议。在本文中，MCP Server 和 Skill（内置能力）处于同等地位，共同构成 Agent 的能力集。 |

有了这些概念，我们来看完整的架构模型：

```plain
┌─────────────────────────────────────────────────────┐
│                  IDE / Editor                        │
│  ┌──────────┐  ┌────────────────┐  ┌─────────────┐  │
│  │  UI 层   │  │ Extension Host │  │ 语言服务(LSP)│  │
│  └────┬─────┘  └────┬───────────┘  └─────────────┘  │
│       │              │                                │
│       └──────┬───────┘                                │
│              │ IPC (stdio / named pipe)                │
│       ┌──────┴───────┐                                │
│       │ Agent Client  │ ← 编辑器侧的协议适配层         │
│       └──────┬───────┘                                │
└──────────────┼────────────────────────────────────────┘
               │ ACP (Agent Client Protocol)
               │ 传输层: stdio + JSON-RPC 2.0
               │
        ┌──────┴───────┐
        │  Agent CLI   │ ← 独立进程, 即 AgentLoop
        │  (qodercli   │
        │  / claude)   │
        ├──────────────┤
        │  Skills      │ ← 内置能力 (文件编辑/搜索...)
        │  MCP Client  │ ← 连接外部工具服务
        └──────────────┘
               │ MCP (Model Context Protocol)
               │ 传输层: stdio / SSE / Streamable HTTP
               │
        ┌──────┴───────┐
        │  MCP Server  │ ← 外部工具 (浏览器/DB/API...)
        └──────────────┘
```

用你日常使用 AI IDE 的体验来对照这张图：

- **你在对话框里输入一段需求** → UI 层捕获输入，通过 IPC 发给 Extension Host 中的 AI 扩展
- **AI 扩展启动 Agent CLI 子进程** → Extension Host spawn 出一个 qodercli/claude 进程，通过 ACP（stdio）与它通信
- **Agent 说"我需要读一下你的数据库"** → Agent CLI 内部的 MCP Client 通过 MCP 协议调用你配置好的数据库 MCP Server
- **Agent 开始逐字输出代码** → Agent CLI 通过 stdout 流式返回结果 → Extension Host 通过 IPC 转发给 UI 层 → 你看到对话框里的文字逐字出现
- **Agent 请求你批准修改文件** → Agent CLI 通过 ACP 向编辑器发起反向请求 → UI 弹出确认对话框 → 你点"Accept" → 文件被修改

关键洞察：**Agent CLI 本质上就是一个通用的 AgentLoop**——接收指令、推理、调用工具（Skill 和 MCP Server）、返回结果。给它装上不同的 Skill 和 MCP Server 组合，它就变成了垂直领域的专业 Agent（编码 Agent、PPT Agent、写作 Agent 等）。IDE 扮演的是 Agent Client 角色，负责 UI 渲染和用户交互。

## 3 协议栈分析：IPC 与 ACP

在 AI IDE 的架构中，有两层关键协议值得 HiMarket 重点关注。IPC 提供了"传输无关"的架构思想参考，而 ACP 则有可能在 Web 场景中被直接复用。

注：Agent 调用外部工具的 MCP 协议和内置的 Skill 能力在本文中视为同等地位，它们共同构成 AgentLoop 的能力集，不再单独展开分析。

### 3.1 IPC：传输无关的架构启示

VS Code 的 IPC 机制不直接用于 AI Agent 通信，但它的设计思想对 HiMarket 有重要的架构参考价值。

VS Code 通过 `IMessagePassingProtocol` 接口对底层物理传输进行了抽象：

- 在 Electron 桌面应用中 → 走原生 IPC（基于 Electron 的 `ipcMain`/`ipcRenderer`）
- 在 VS Code Server（远程开发）中 → 走 WebSocket 连接远程 Node.js 进程
- 在 web 版 vscode.dev 中 → 同样走 WebSocket

**上层的通道模型（Channel）和 RPCProtocol 完全不需要感知底层走的是什么传输。** 这种"协议驱动、传输无关"的分层设计，正是 HiMarket 做 Web 适配时应该借鉴的核心思想——同一套 Agent 通信协议，底层传输可以从 stdio 切换到 WebSocket，而上层的会话管理、消息路由、业务逻辑不需要改动。

### 3.2 ACP：编辑器与 Agent 的标准化协议

ACP（Agent Client Protocol）由 [Zed](https://zed.dev) 公司提出，是一个开放标准（官网：[agentclientprotocol.com](https://agentclientprotocol.com/get-started/introduction)），旨在标准化代码编辑器与编码 Agent 之间的通信，其设计灵感直接来自 LSP 的成功经验。

目前 ACP 生态已初具规模：

- **Agent 侧**：Claude Code、Qoder CLI、Qwen Code、Gemini CLI、Codex CLI、Kiro CLI、Kimi CLI、OpenCode、Augment 等主流 CLI 工具均已支持
- **Client 侧**：Zed、VS Code（通过扩展）、AionUI (https://github.com/iOfficeAI/AionUi，这个项目值得关注，本质就是一个 cli 聚合器) 等编辑器已接入
- **SDK 支持**：提供 TypeScript、Python、Rust、Kotlin 四种官方 SDK

#### ACP 的核心通信模型

```plain
┌───────────────┐                       ┌───────────────┐
│  Editor/IDE   │                       │  Agent (CLI)  │
│   (Client)    │                       │   (Server)    │
│               │                       │               │
│  ┌─────────┐  │   JSON-RPC 2.0       │  ┌─────────┐  │
│  │ Agent   │  │◄─────────────────────►│  │ Agent   │  │
│  │ Panel   │  │   传输层: stdio       │  │ Loop    │  │
│  └─────────┘  │                       │  └─────────┘  │
│               │   双向通信：           │               │
│               │   ← Agent 流式输出     │  ┌─────────┐  │
│               │   ← Agent 请求审批     │  │ Skills  │  │
│               │   → 用户发送指令       │  │ + MCP   │  │
│               │   → 用户批准/拒绝      │  └─────────┘  │
└───────────────┘                       └───────────────┘
```

ACP 不是简单的"客户端发请求、服务端回响应"的单向模式，而是**真正的双向协议**——Agent 可以主动向编辑器发起请求。具体来说，ACP 定义了以下关键交互：

| 交互方向           | 场景             | 示例                                                         |
| ------------------ | ---------------- | ------------------------------------------------------------ |
| **Client → Agent** | 用户发送指令     | 用户在对话框输入"帮我重构这个函数"，编辑器将指令发送给 Agent |
| **Client → Agent** | 用户提供上下文   | 编辑器将当前打开的文件列表、选中的代码片段传递给 Agent       |
| **Agent → Client** | 流式输出         | Agent 逐 token 输出推理过程和代码，编辑器实时渲染            |
| **Agent → Client** | 请求工具调用审批 | Agent 想执行 `rm -rf` 命令，先向编辑器请求用户确认           |
| **Agent → Client** | 请求文件访问     | Agent 需要读取某个文件的内容，向编辑器请求                   |
| **Agent → Client** | 报告进度/状态    | Agent 通知编辑器当前正在"分析项目结构..."或"运行测试..."     |
| **Client → Agent** | 用户中断         | 用户点击"停止"，编辑器发送取消信号给 Agent                   |

#### ACP 的信任模型

ACP 建立了一套三方信任关系：

```plain
          用户 (最高信任)
         ╱              ╲
    信任编辑器         信任 Agent
       ╱                  ╲
  Editor ─── 有限信任 ─── Agent
  (可控制文件/工具)   (需请求审批)
```

- **用户信任编辑器**：用户授权编辑器访问本地文件系统
- **用户信任 Agent**（有条件）：用户可以配置 Agent 的权限级别（自动执行 / 逐步审批）
- **编辑器有限信任 Agent**：Agent 请求的文件访问和工具调用，编辑器可以拦截并要求用户确认

这个信任模型对 HiMarket 的 Web 场景同样关键——在多租户环境下，平台（对应编辑器角色）需要对 AgentRun 的行为进行审批和控制，不能让 Agent 不受约束地执行任意操作。

#### ACP 对 HiMarket Web 化的直接价值

ACP 之所以是 HiMarket 重点参考的协议，核心原因在于：

1. **消息格式可直接复用**：ACP 使用 JSON-RPC 2.0，这是一种纯文本、传输无关的消息格式。无论底层走 stdio 还是 WebSocket，上层的消息结构完全一致。HiMarket 的 Proxy 层只需要做传输转换，不需要做消息格式转换。
2. **双向通信模型天然适配 WebSocket**：ACP 的双向请求模式（Agent 可以主动向 Client 发请求）与 WebSocket 的全双工特性高度匹配。桌面端通过 stdio 实现的双向通信，在 Web 端可以无损地映射到 WebSocket。
3. **会话模型可复用**：ACP 支持单进程内的并发会话（concurrent sessions），这意味着一个 Agent CLI 进程可以同时服务多个会话。这个特性在 Web 场景下可以用于优化资源利用——多个轻量级会话可以复用同一个 AgentRun 进程。
4. **信任模型可映射**：ACP 中编辑器对 Agent 行为的审批机制，可以直接映射为 HiMarket 平台对 AgentRun 的权限控制。用户审批工具调用、平台拦截危险操作，这些在桌面端已经验证过的模式，可以在 Web 端沿用。
5. **协议分层带来研发分工的解耦**：借鉴 ACP 的分层设计，HiMarket 的研发团队可以按协议边界进行专业化分工——对 AgentLoop 理解深的同学，专注于 Agent CLI 层的优化（推理编排、上下文管理、多步规划）；对业务场景理解深的同学，专注于构建 Skill 和 MCP Server，打造 PPT 制作、信息图生成、文档写作等垂类应用场景；前端同学则基于 ACP 协议的标准化消息格式做 UI 渲染，专注于提升交互体验（流式输出、产物预览、审批弹窗等）。**各层通过协议契约解耦，可以独立迭代，互不阻塞。**

### 3.3 ACP 的 stdio 瓶颈与 Web 适配

ACP 当前主要支持 stdio 传输——编辑器 fork 一个 Agent CLI 子进程，通过 stdin/stdout 双向通信，简洁高效。在 stdio 传输层上采用 **NDJSON（Newline Delimited JSON）** 格式——每行一个独立的 JSON 对象，以换行符分隔：

- 可以增量解析，不需要等整个响应完成
- 天然适配流式传输场景
- 解析逻辑极其简单：按行切割 → JSON.parse

但在 Web 端，浏览器无法直接发起 stdio 调用，这是整个架构需要解决的核心矛盾。参考 IPC 的"传输无关"思想，我们需要一个 **Proxy 层**做传输桥接（WebSocket ↔ stdio），而 ACP 的 JSON-RPC 消息本身可以透传，不需要翻译。

随着 ACP 生态的演进，大概率会像 MCP 一样增加 HTTP/SSE 传输支持。届时 Proxy 层可以进一步简化甚至去除。

## 4 Web 端 HiMarket 核心挑战

将桌面端的成功经验搬到 Web 端，需要跨越以下几个核心挑战：

| 维度               | 桌面端 AI IDE                | Web 端 HiMarket              |
| ------------------ | ---------------------------- | ---------------------------- |
| **租户模型**       | 天然单租户（用户自己的机器） | 多租户共享基础设施           |
| **会话管理**       | 通常单会话，手动切换         | 大量并发独立会话             |
| **Agent 运行单元** | 一个 CLI 进程                | 每会话一个隔离的 AgentRun    |
| **通信协议**       | stdio（同步、本地）          | WebSocket（异步、网络）      |
| **安全边界**       | 用户操作系统级别             | 需要应用层沙箱隔离           |
| **产物呈现**       | IDE 内置渲染能力             | 需要 Web 端多元产物渲染      |
| **资源管控**       | 用户自行管理                 | 平台需要计量、配额、弹性伸缩 |

## 5 整体架构设计

### 5.1 HiMarket 的双产品形态

在展开架构细节之前，先回答一个更根本的问题：**HiMarket 应该以什么产品形态面向用户？**

参考字节的扣子平台，其已经验证了"双产品形态"的可行性：

| 产品                                                 | 定位                  | 目标用户                        | 核心能力                                                     |
| ---------------------------------------------------- | --------------------- | ------------------------------- | ------------------------------------------------------------ |
| **扣子**（[space.coze.cn](https://space.coze.cn)）   | AI 办公助手           | 所有有学习和工作需求的 C 端用户 | 写作、PPT、视频、设计、Excel、网页、播客、图表等多元 Agent 办公技能；技能商店生态；MCP 扩展集成；多源信息整合与自主任务执行 |
| **扣子编程**（[code.coze.cn](https://code.coze.cn)） | AI 驱动的应用开发平台 | 从零基础到专业的开发者          | Vibe Coding（自然语言驱动全栈开发）；智能体 / 工作流 / 技能 / 网页应用的全代码与低代码开发；云端运行环境与一站式部署；生产级基建（数据库、存储、集成、安全隔离） |

扣子（对应 HiWork）：

![img](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/156306/1770820432651-1e420046-5d94-4af1-9989-fe045c4ae3f8.png)

扣子编程（对应 HiCoding）：

![img](https://intranetproxy.alipay.com/skylark/lark/0/2026/png/156306/1770820461531-f99d0921-d16b-4c2a-8b8a-42b360761a03.png)

这两种形态的底层逻辑高度一致：**用户通过自然语言描述意图 → AI Agent 理解并拆解任务 → 调用 Skill/MCP 工具执行 → 交付可用产物**。区别仅在于产物类型和上层 UI——办公助手交付文档/PPT/图表等办公产物，开发平台交付可运行的应用/智能体/工作流。

#### HiMarket 的对应规划

HiMarket 应同步发展两种产品形态：

**形态一：HiWork — AI Agent 协同办公平台**

对标扣子，面向全量用户提供 AI 办公助手能力：

- **多元 Agent 办公技能**：写作、PPT 制作、信息图生成、数据分析（Excel）、视频制作、网页设计等。每种能力对应一组 Skill + MCP Server 组合，由统一的 AgentLoop 驱动
- **技能生态**：支持技能的创建、分享与商店化运营，用户可以将领域经验封装为可复用的技能包，供他人使用
- **Agent 主动执行**：不仅回答问题，更能主动拆解复杂任务，调用浏览器、代码运行时、文件工具等，自主完成端到端的工作流并输出完整产物
- **MCP 扩展集成**：对接企业内部系统、第三方 API、知识库等，拓展 Agent 的能力边界

**形态二：HiCoding — AI 驱动的应用开发平台**

对标扣子编程，面向开发者提供 Vibe Coding 体验：

- **自然语言驱动开发**：通过对话描述需求，AI 自动生成、调试全栈代码，零编程基础用户也能构建可用应用
- **多开发范式兼容**：支持 AI 编程（Vibe Coding）和低代码/零代码可视化编排两种开发模式
- **多种开发目标**：智能体（即问即答 Agent / 多步骤 Long-running Agent）、工作流、可复用技能、全栈网页应用
- **云端开发环境**：浏览器即 IDE，每个项目自动分配云端运行环境（即 AgentRun 实例），内置编译/运行/预览能力
- **一站式部署**：从代码到生产服务的全流程自动化——打包、构建、部署、域名绑定、版本管理、日志监控
- **生产级基建**：开箱即用的数据库与存储、模型集成（通义/方舟等）、环境隔离与加密凭据管理

#### 双形态与架构的一致性

这一双产品形态规划与本文的核心架构论点完全一致：**AgentLoop 是领域无关的，变的只是 Skill/MCP Server 组合和上层 UI**。

```plain
┌─────────────────────────────────────────────────────┐
│               HiMarket 双产品形态                      │
│                                                       │
│  ┌───────────────────┐    ┌───────────────────────┐  │
│  │  HiWork           │    │  HiCoding              │  │
│  │  (AI 办公助手)      │    │  (AI 应用开发平台)      │  │
│  │                    │    │                        │  │
│  │  写作/PPT/信息图    │    │  Vibe Coding IDE       │  │
│  │  Excel/视频/设计    │    │  智能体/工作流编排       │  │
│  │  技能商店/Agent市场 │    │  一站式部署/版本管理     │  │
│  └────────┬──────────┘    └──────────┬─────────────┘  │
│           │                          │                 │
│           └──────────┬───────────────┘                 │
│                      │                                 │
│           ┌──────────┴───────────┐                     │
│           │ 共享基础设施层         │                     │
│           │                      │                     │
│           │  ACP Proxy           │                     │
│           │  Session Manager     │                     │
│           │  AgentRun Manager    │                     │
│           │  认证鉴权 / 计量计费   │                     │
│           └──────────┬───────────┘                     │
│                      │                                 │
│           ┌──────────┴───────────┐                     │
│           │ AgentRun 实例         │                     │
│           │                      │                     │
│           │  AgentLoop (统一)     │                     │
│           │  + 按场景挂载的       │                     │
│           │    Skill / MCP Server │                     │
│           └──────────────────────┘                     │
└─────────────────────────────────────────────────────────┘
```

两种产品形态共享同一套 HiMarket 后端（Session Manager、ACP Proxy、AgentRun Manager）和 AgentRun 基础设施。差异点仅体现在：

| 差异维度          | HiWork                                                      | HiCoding                                                     |
| ----------------- | ----------------------------------------------------------- | ------------------------------------------------------------ |
| **前端 UI**       | 对话式办公界面 + 产物渲染面板                               | Web IDE（代码编辑器 + 终端 + 预览）                          |
| **AgentRun 配置** | 挂载办公类 Skill/MCP Server（pptx、写作、信息图、Excel 等） | 挂载开发类 Skill/MCP Server（代码运行时、包管理器、部署工具等） |
| **产物类型**      | 文档、PPT、图表、视频等办公产物                             | 可运行的应用、智能体、工作流代码                             |
| **会话模式**      | 任务驱动的短会话为主                                        | 项目驱动的长会话为主，需持久化工作区                         |
| **部署需求**      | 产物导出/下载                                               | 应用部署到生产环境 + 域名绑定 + 版本管理                     |

这意味着后续各节的架构设计（ACP Proxy、ACP Bridge、多租户隔离、弹性伸缩、产物渲染等）对两种产品形态均适用，无需为每种形态单独设计一套基础设施。

### 5.2 关键产品的定位与架构总览

在展开架构之前，先明确几个关键产品的定位：

- **HiMarket**：阿里云 AI 开放平台，前端基于 TypeScript，后端基于 Java，提供 AI 能力的管理和交付。HiMarket 提供两种产品形态：HiWork（AI 办公助手）和 HiCoding（AI 应用开发平台）。
- **AgentRun**：阿里云独立云产品，提供 Agent 的运行时环境，内部由两个核心对象组成：

- - **Agent Runtime**：运行 Agent CLI / AgentLoop 的主容器
  - **Sandbox**：运行 MCP Server、浏览器自动化（Playwright/Puppeteer）、代码编译器/解释器、文件预处理工具、自定义 Sidecar 等辅助进程的安全隔离沙箱。Sandbox 与 Agent Runtime 分离，确保 Agent 调用的外部工具不会影响主 AgentLoop 的稳定性，同时提供更细粒度的安全控制（如限制 MCP Server 的网络访问范围）。

- **Agent CLI**：支持 ACP 协议的 AgentLoop 程序（如 qodercli、Qwen Code、OpenCode 等），运行在 AgentRun 沙箱内部。

三者的关系是：HiMarket 管业务和用户交互，AgentRun 管运行时隔离和资源（Agent Runtime 跑 AgentLoop，Sandbox 跑辅助工具），Agent CLI 管推理和工具调用。

```plain
┌─────────────────────────────────────────────────────┐
│          浏览器 (前端 / TypeScript)                    │
│  ┌──────────┐  ┌─────────────────┐                   │
│  │ Chat UI  │  │ 产物渲染面板     │                   │
│  │          │  │ (Monaco/xterm/   │                   │
│  │          │  │  PPT/SVG/iframe) │                   │
│  └────┬─────┘  └────────┬────────┘                   │
│       │    WebSocket     │                            │
└───────┼──────────────────┼────────────────────────────┘
        │                  │
════════╪══════════════════╪══════════════════════════════
        │   WSS / HTTPS    │
┌───────┴──────────────────┴────────────────────────────┐
│         HiMarket Backend (Java/Spring)                 │
│                                                        │
│  ┌────────────────────────────────────────────────┐    │
│  │  Session Manager                               │    │
│  │  (认证鉴权 / 会话生命周期 / 租户上下文注入)       │    │
│  └──────────────────┬─────────────────────────────┘    │
│                     │                                   │
│  ┌──────────────────┴─────────────────────────────┐    │
│  │  ACP Proxy                                     │    │
│  │  (WebSocket↔ACP 协议桥接 / 会话路由)             │    │
│  └──────────────────┬─────────────────────────────┘    │
│                     │                                   │
│  ┌──────────────────┴─────────────────────────────┐    │
│  │  AgentRun Manager                              │    │
│  │  (调用 AgentRun API: 创建/销毁/查询实例)          │    │
│  └──────────────────┬─────────────────────────────┘    │
└─────────────────────┼──────────────────────────────────┘
                      │
                      │ AgentRun API + 网络通信
                      │
┌─────────────────────┼──────────────────────────────────────────┐
│   AgentRun 实例                                                 │
│                                                                  │
│  ┌──────────────────────────────┐  ┌──────────────────────────┐ │
│  │  Agent Runtime               │  │  Sandbox                 │ │
│  │  (运行 AgentLoop 的主容器)     │  │  (运行辅助进程的隔离沙箱)  │ │
│  │                              │  │                          │ │
│  │  ┌────────────────────────┐  │  │  ┌────────────────────┐  │ │
│  │  │ ACP Bridge             │  │  │  │ MCP Server(s)      │  │ │
│  │  │ (网络↔stdio 适配)      │  │  │  │ (DB/API/文件工具)   │  │ │
│  │  └──────────┬─────────────┘  │  │  ├────────────────────┤  │ │
│  │             │ stdio          │  │  │ 浏览器引擎          │  │ │
│  │  ┌──────────┴─────────────┐  │  │  │ (Playwright等)     │  │ │
│  │  │ Agent CLI              │  │  │  ├────────────────────┤  │ │
│  │  │ (qodercli/qwen code/   │◄─┼──┼─►│ 代码编译/运行时     │  │ │
│  │  │  opencode)             │  │  │  │ (Node/Python/Go)   │  │ │
│  │  │                        │  │  │  ├────────────────────┤  │ │
│  │  │ + Skills               │  │  │  │ 文件预处理工具      │  │ │
│  │  └────────────────────────┘  │  │  │ (pdf解析/图片处理)  │  │ │
│  └──────────────────────────────┘  │  ├────────────────────┤  │ │
│                                     │  │ 自定义 Sidecar     │  │ │
│                                     │  │ (File Watcher等)   │  │ │
│                                     │  └────────────────────┘  │ │
│                                     └──────────────────────────┘ │
│                                                                  │
│  ┌───────────────────────────────────────────────────────────┐   │
│  │  共享存储 (Workspace): Agent Runtime 与 Sandbox 共享的      │   │
│  │  临时文件系统，Agent 写入的产物可被 Sandbox 中的工具访问      │   │
│  └───────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────┘
```

### 5.3 分层职责

**第 1 层 — 前端 UI 层（HiMarket 前端）**

- 通过 WebSocket 与 HiMarket Java 后端建立持久连接
- 根据 Agent 返回的 ACP 消息，实时渲染对话流和产物
- 处理用户交互（发送指令、中断生成、审批工具调用等）

**第 2 层 — HiMarket Backend（Java/Spring）**

- **Session Manager**：认证鉴权、会话生命周期管理、租户上下文注入（从 JWT 中提取 tenantId/userId）
- **ACP Proxy**：核心桥接组件，将前端 WebSocket 消息转换为 ACP 协议消息，路由到对应的 AgentRun 实例（详见第 6 节）
- **AgentRun Manager**：通过 AgentRun 的云产品 API 管理沙箱实例的创建、销毁、查询，负责资源编排和弹性伸缩（详见第 8 节）

**第 3 层 — AgentRun 实例**

- 由阿里云 AgentRun 产品提供，天然具备物理隔离能力
- **Agent Runtime**（主容器）：运行 ACP Bridge 和 Agent CLI 进程。ACP Bridge 将 CLI 的 stdio 暴露为 WebSocket 端口，供 HiMarket 后端远程连接（详见 6.1 节）
- **Sandbox**（隔离沙箱）：运行 Agent 所需的各类辅助进程，与 Agent Runtime 分离以实现安全隔离。典型的 Sandbox 进程包括：

- - **MCP Server**：数据库连接、API 代理、文件工具等外部能力服务
  - **浏览器引擎**：Playwright / Puppeteer，用于网页抓取、截图、自动化测试
  - **代码编译/运行时**：Node.js、Python、Go 等，用于执行 Agent 生成的代码
  - **文件预处理工具**：PDF 解析、图片处理、文档格式转换等
  - **自定义 Sidecar**：File Watcher（文件变更监控）、日志收集等

- **共享 Workspace**：Agent Runtime 和 Sandbox 共享的临时文件系统，Agent 写入的产物可被 Sandbox 中的工具访问（如 Agent 生成 Python 脚本 → Sandbox 中的 Python 运行时执行它）

## 6 ACP Proxy 与 ACP Bridge：协议桥接的核心设计

桌面端 AI IDE 中，编辑器通过本地 stdio 直接与 Agent CLI 通信。在 HiMarket 的架构中，HiMarket Java 后端和 Agent CLI 分别运行在不同的网络环境（Java 后端在 HiMarket 集群，CLI 在远端的 AgentRun 沙箱），无法直接走 stdio。因此需要两级桥接：

```plain
浏览器        HiMarket Backend       AgentRun 沙箱
  │               │                      │
  │    WSS        │     ACP over WS      │
  │◄─────────────►│◄────────────────────►│
  │               │                      │
  │           ACP Proxy              ACP Bridge
  │         (Java 后端内)          (沙箱内 sidecar)
  │               │                      │
  │               │                 ┌────┴────┐
  │               │                 │CLI 进程  │
  │               │                 │(stdio)  │
  │               │                 └─────────┘
```

- **ACP Proxy**（运行在 HiMarket Java 后端）：面向前端的 WebSocket 端点，负责会话路由，将消息转发到对应 AgentRun 实例
- **ACP Bridge**（运行在 AgentRun 沙箱内部）：轻量级 sidecar 进程，启动并管理 Agent CLI，将 CLI 的 stdio 暴露为 WebSocket 端口，供 ACP Proxy 远程连接

### 6.1 ACP Bridge：沙箱内的 stdio 网络化

ACP Bridge 是运行在 AgentRun 的 **Agent Runtime** 容器内的一个薄适配层，解决的核心问题是：**让远端的 HiMarket 后端能够通过网络与 Agent Runtime 中的 CLI 进程进行 ACP 协议通信。**

```plain
┌──────── AgentRun 实例 ────────────────────────────────────┐
│                                                            │
│  ┌── Agent Runtime ──────────┐  ┌── Sandbox ────────────┐ │
│  │                           │  │                       │ │
│  │  ┌──────────────────┐     │  │  ┌─────────────────┐  │ │
│  │  │  ACP Bridge      │     │  │  │ MCP Server(s)   │  │ │
│  │  │  - 启动 CLI      │     │  │  ├─────────────────┤  │ │
│  │  │  - stdio 读写    │     │  │  │ 浏览器引擎       │  │ │
│  │  │  - WS 端口暴露   │     │  │  ├─────────────────┤  │ │
│  │  └────────┬─────────┘     │  │  │ 代码运行时       │  │ │
│  │           │ stdio         │  │  ├─────────────────┤  │ │
│  │  ┌────────┴─────────┐     │  │  │ File Watcher    │  │ │
│  │  │  Agent CLI       │◄────┼──┼─►│ ...             │  │ │
│  │  │  (AgentLoop)     │ MCP │  │  └─────────────────┘  │ │
│  │  │  + Skills        │     │  │                       │ │
│  │  └──────────────────┘     │  └───────────────────────┘ │
│  │       WebSocket :8080     │                             │
│  └───────────┬───────────────┘     共享 Workspace           │
│              │                     (临时文件系统)            │
└──────────────┼─────────────────────────────────────────────┘
               │
        HiMarket 后端
        通过网络连接
```

ACP Bridge 的职责极其简单：

1. 启动 Agent CLI 子进程，捕获其 stdin/stdout/stderr
2. 监听一个 WebSocket 端口（如 8080）
3. 收到 WebSocket 消息 → 写入 CLI 的 stdin
4. 从 CLI 的 stdout 读到数据 → 通过 WebSocket 发出
5. CLI 进程退出时，通知连接方并清理资源

Bridge 本身不做任何业务逻辑，不解析 ACP 消息内容，只做纯粹的传输层转换。这使得它可以对接任何支持 ACP 协议的 CLI 工具，无需针对不同 CLI 做适配。

如果未来 ACP 协议原生支持 HTTP/WebSocket 传输，Agent CLI 自身就能直接暴露网络端口，ACP Bridge 可以被移除。

### 6.2 ACP Proxy：HiMarket 后端的会话路由

ACP Proxy 运行在 HiMarket Java 后端，是前端与 AgentRun 之间的中枢：

```plain
前端 WS 连接 ──► ACP Proxy ──► AgentRun 实例 A 的 ACP Bridge
前端 WS 连接 ──► ACP Proxy ──► AgentRun 实例 B 的 ACP Bridge
前端 WS 连接 ──► ACP Proxy ──► AgentRun 实例 C 的 ACP Bridge
```

Proxy 维护一个会话映射表：

```plain
ConcurrentHashMap<String, HiMarketSession>

HiMarketSession {
    String sessionId;
    String tenantId;
    WebSocketSession frontendWs;      // 前端 WebSocket 连接
    WebSocketSession agentRunWs;      // 到 AgentRun Bridge 的 WebSocket 连接
    String agentRunInstanceId;         // AgentRun 实例 ID
    String agentRunEndpoint;           // AgentRun 实例的网络地址
    Instant createdAt;
    Instant lastActiveAt;
    SessionState state;
}
```

核心消息流：

**下行（用户 → Agent）**：

1. 前端通过 WebSocket 发送 ACP 消息到 HiMarket 后端
2. ACP Proxy 根据 sessionId 找到对应的 HiMarketSession
3. 将消息通过 agentRunWs 转发到 AgentRun 实例的 ACP Bridge
4. Bridge 写入 CLI 的 stdin

**上行（Agent → 用户）**：

1. CLI 通过 stdout 输出 ACP 消息
2. ACP Bridge 通过 WebSocket 发送到 HiMarket 后端
3. ACP Proxy 根据会话映射，通过 frontendWs 转发到前端
4. 前端实时渲染

**ACP 消息透传**：ACP Proxy 默认对消息内容做透传，不解析、不修改。但在以下场景需要拦截：

- **审批拦截**：Agent 请求执行危险操作时，Proxy 可以根据平台策略自动拒绝，无需转发到前端
- **计量埋点**：Proxy 可以在转发时旁路提取 token 用量、工具调用等计量信息
- **限流控制**：当租户超出配额时，Proxy 直接返回错误，不再转发到 AgentRun

### 6.3 会话生命周期

```plain
用户点击"开始"          对话进行中                  用户关闭/超时
     │                      │                          │
     ▼                      ▼                          ▼
┌────────┐  创建沙箱  ┌──────────┐  ACP消息流  ┌──────────┐  销毁沙箱  ┌────────────┐
│ INIT   │──────────►│ STARTING │───────────►│ ACTIVE   │──────────►│ STOPPING   │
└────────┘           └──────────┘            └──────────┘           └─────┬──────┘
     │                                                                     │
  调用 AgentRun API                                              调用 AgentRun API
  创建沙箱实例                                                   销毁沙箱实例
  等待 Bridge 就绪                                               清理会话映射表
  建立 WS 连接                                                         │
                                                                       ▼
                                                                ┌────────────┐
                                                                │TERMINATED  │
                                                                └────────────┘
```

关键操作：

- **创建会话**：调用 AgentRun API 创建沙箱实例 → 等待实例就绪 → 建立到 Bridge 的 WebSocket 连接 → 绑定前端 WebSocket → 会话进入 ACTIVE
- **消息转发**：前端消息 → Proxy → Bridge → CLI stdin；CLI stdout → Bridge → Proxy → 前端
- **关闭会话**：关闭两端 WebSocket → 调用 AgentRun API 销毁沙箱实例 → 从映射表移除
- **异常处理**：Bridge WebSocket 断开 → 通知前端"Agent 会话异常" → 清理资源

### 6.4 信号控制与中断

用户在 Web 端点击"停止生成"时，前端发送一个控制帧：

```json
{"jsonrpc": "2.0", "method": "$/cancel", "params": {"id": "req-123"}}
```

ACP Proxy 将该消息透传到 Bridge，Bridge 有两种处理策略：

1. **协议级取消**：将 cancel 消息写入 CLI 的 stdin，由 CLI 自行处理（优雅）
2. **信号级中断**：向 CLI 进程发送 `SIGINT`（粗暴但有效），适用于 CLI 不响应协议级取消的情况

## 7 多租户与会话隔离

### 7.1 隔离层次模型

HiMarket 的隔离设计充分利用 AgentRun 作为独立云产品的天然优势——物理隔离由 AgentRun 直接提供，HiMarket 只需关注逻辑隔离和数据隔离。

```plain
┌─────────────────────────────────────────────────┐
│ 第 3 层: 数据隔离 (HiMarket 负责)                │
│   - 会话数据/对话历史按 tenantId 分区             │
│   - 向量库/知识索引按租户隔离                     │
│   - 产物存储按 tenantId 隔离 (如 OSS prefix)      │
├─────────────────────────────────────────────────┤
│ 第 2 层: 逻辑隔离 (HiMarket 负责)                │
│   - JWT 携带 tenantId / userId / scope           │
│   - Java 后端做认证鉴权                          │
│   - AgentRun 实例启动时注入 scoped 凭据           │
│   - Skill/MCP Server 连接的资源按租户限定         │
├─────────────────────────────────────────────────┤
│ 第 1 层: 物理隔离 (AgentRun 天然提供)             │
│   - 每个 AgentRun 实例是独立的沙箱 Pod            │
│   - 独立的文件系统、网络命名空间                  │
│   - CPU / 内存 / 磁盘 IO / 网络 配额限制         │
│   - 沙箱间完全隔离，无法互相访问                  │
└─────────────────────────────────────────────────┘
```

### 7.2 物理隔离（AgentRun 提供）

AgentRun 通过 Agent Runtime + Sandbox 双容器架构提供了两层物理隔离：

| 隔离层级       | 隔离对象                      | 机制                                           | 安全意义                                                     |
| -------------- | ----------------------------- | ---------------------------------------------- | ------------------------------------------------------------ |
| **实例间隔离** | 不同租户/会话的 AgentRun 实例 | 每个实例独立的 Pod，进程/文件系统/网络完全隔离 | 租户 A 的 Agent 无法访问租户 B 的数据                        |
| **实例内隔离** | Agent Runtime 与 Sandbox      | AgentLoop 和辅助工具分别运行在不同容器中       | MCP Server 的安全漏洞或浏览器引擎的崩溃不会影响 AgentLoop 的稳定性；可以对 Sandbox 施加更严格的网络/权限限制 |

Agent Runtime 与 Sandbox 的隔离设计意味着：

- **稳定性**：Sandbox 中的进程（如浏览器引擎）崩溃不会导致 AgentLoop 中断
- **安全性**：MCP Server 访问外部 API 时，可以对 Sandbox 单独做网络白名单控制，不影响 Agent Runtime 的通信链路
- **资源管控**：可以分别为 Agent Runtime 和 Sandbox 设置独立的 CPU/内存配额

### 7.3 逻辑隔离与身份传递（HiMarket 负责）

```plain
用户登录 → JWT (tenantId, userId, plan, scopes)
    │
    ├─► Java 后端验证 JWT → 注入租户上下文
    │
    ├─► Session Manager 提取租户上下文 → 决定资源配额
    │
    └─► AgentRun 实例创建时：
        ├─ 通过 API 传入租户专属的环境变量
        ├─ 注入 scoped 凭据（限制 Agent 只能访问该租户的资源）
        └─ 工作区初始化（加载租户的项目文件/模板等）
```

### 7.4 会话上下文隔离

AI Agent 的对话历史和工作记忆也是敏感数据，需要确保：

- **内存隔离**：每个 AgentRun 实例是独立沙箱，进程级内存天然隔离
- **持久化隔离**：对话历史存储时以 (tenantId, sessionId) 为主键
- **会话清理**：会话结束或超时后，AgentRun 实例销毁，沙箱内所有数据随之消失；HiMarket 侧清理会话映射表

## 8 AgentRun 实例管理与弹性伸缩

HiMarket 通过 AgentRun 的云产品 API 管理沙箱实例的生命周期，核心挑战是在用户体验（快速响应）和资源效率（成本控制）之间取得平衡。

### 8.1 实例生命周期

```plain
用户发起会话                          会话进行中            用户关闭/超时
     │                                    │                    │
     ▼                                    ▼                    ▼
┌──────────────┐                    ┌──────────┐         ┌──────────┐
│ 调用 AgentRun │   实例就绪         │ 实例活跃  │         │ 调用 API  │
│ API 创建实例  │──────────────────►│ 会话通信  │────────►│ 销毁实例  │
│              │                    │          │         │          │
│ 延迟: 冷启动  │                    │          │         │ 释放资源  │
│ 3~10s       │                    │          │         │          │
└──────────────┘                    └──────────┘         └──────────┘
       │
       │ 可通过预热池降低
       ▼
┌──────────────┐
│ 从预热池分配  │
│ 延迟: <1s    │
└──────────────┘
```

### 8.2 伸缩策略

| 策略                    | 适用场景       | 实现方式                                                     |
| ----------------------- | -------------- | ------------------------------------------------------------ |
| **预热池（Warm Pool）** | 降低冷启动延迟 | 通过 AgentRun API 预先创建 N 个空闲实例，新会话直接从池中分配 |
| **按需创建 + 排队**     | 资源紧张时     | 池耗尽时新请求进入等待队列，异步创建实例                     |
| **自动伸缩**            | 流量波动场景   | 基于活跃会话数动态调整预热池大小                             |
| **租户级配额**          | 公平性保障     | 每个租户限制最大并发 AgentRun 实例数，超限排队或拒绝         |

### 8.3 资源回收

- **空闲超时**：会话无活动超过阈值（如 10min），通知前端后销毁 AgentRun 实例
- **会话超时**：单次会话最大存活时间（如 30min），到期强制终止
- **异常检测**：ACP Bridge WebSocket 断开 → 判定实例异常 → 通知前端 → 销毁实例
- **定时巡检**：后台任务定期扫描会话映射表，清理已失联但未被回收的实例

## 9 产物渲染与前端体验

HiMarket 的 Agent 不仅产出文本和代码，还包括 PPT、信息图、Web 应用等多元产物。如何在浏览器中高保真渲染这些产物，是用户体验的关键。

### 9.1 产物类型与渲染策略

| 产物类型        | Agent 端生成方式           | Web 端渲染技术          | 交互能力                     |
| --------------- | -------------------------- | ----------------------- | ---------------------------- |
| 源代码          | 直接写入文件系统           | Monaco Editor           | 语法高亮、差异比对、行内编辑 |
| 终端输出        | CLI stdout/stderr          | xterm.js                | 实时流式显示、ANSI 颜色      |
| SVG / 信息图    | LLM 生成 SVG/HTML          | 原生 SVG 渲染 / iframe  | 矢量缩放、交互动画           |
| PPT             | python-pptx 或 HTML 幻灯片 | PptxGenJS / iframe 预览 | 幻灯片翻页、离线导出         |
| Web 应用        | Vite/React 运行时          | 沙箱 iframe + 端口代理  | HMR 热重载、完整浏览器预览   |
| Markdown / 文档 | Agent 直接生成             | React-Markdown / PDF.js | 富文本呈现、TOC 导航         |

### 9.2 实时文件同步（Sidecar 模式）

```plain
┌────────────────────────── AgentRun 沙箱 ─────────────────────┐
│  ┌──────────┐   ┌─────────────────┐                          │
│  │ CLI 进程  │   │ File Watcher    │                          │
│  │ (Agent)  │   │ (fs.watch/      │                          │
│  │          │──►│  inotify)       │                          │
│  │ 写入文件  │   │                 │                          │
│  └──────────┘   └────────┬────────┘                          │
└──────────────────────────┼───────────────────────────────────┘
                           │ 文件变更事件
                           ▼
               通过 ACP Bridge / 独立通道
                    推送到 HiMarket 后端
                           │
                    ┌──────┴───────┐
                    │ 前端渲染面板  │
                    │ 按文件类型    │
                    │ 挂载渲染组件  │
                    └──────────────┘
```

在 AgentRun 沙箱中，可以额外部署一个轻量级的文件监控进程（Sidecar 或集成在 ACP Bridge 中），监听工作目录的变更。当 Agent 生成或修改文件时，监控进程捕获事件，将变更内容或文件路径推送给 HiMarket 后端，再转发到前端。前端根据文件类型动态挂载对应的渲染组件。

### 9.3 生成式 UI

更高级的模式是 Agent 不只生成文件，而是直接发送结构化的 UI 指令：

```json
{
  "type": "render",
  "component": "chart",
  "props": {
    "type": "bar",
    "data": [...],
    "title": "代码复杂度分布"
  }
}
```

前端根据 `component` 字段从预定义的组件库中动态挂载对应组件，实现 Agent 驱动的动态 UI。这种模式适合信息图、数据可视化等场景，比生成静态文件再渲染的链路更短、体验更好。

## 10 ACP 协议演进与 HiMarket 的长期适配

当前 ACP 仅支持 stdio 传输，因此 HiMarket 需要在 AgentRun 沙箱内部署 ACP Bridge 做 stdio→WebSocket 的转换。但参考 MCP 的演进路径（stdio → SSE → Streamable HTTP），ACP 大概率会增加 HTTP/WebSocket 传输支持。届时架构可以逐步简化：

```plain
当前 (stdio only):
  浏览器 ──WSS──► HiMarket ──WS──► ACP Bridge ──stdio──► CLI

阶段二 (ACP over HTTP):
  浏览器 ──WSS──► HiMarket ──HTTP/WS──► CLI (直接暴露网络端口)
  (ACP Bridge 被移除)

远期 (ACP over HTTP + 直连):
  浏览器 ──────── 直接 HTTP/WSS ──────► CLI (通过 AgentRun 网络暴露)
  (HiMarket 仅做认证和计量旁路)
```

**架构建议**：HiMarket 的 ACP Proxy 应设计为可插拔的 Transport Adapter。当 ACP 支持 HTTP 传输时，只需替换适配器，上层的会话管理和路由逻辑不需要改动。

```plain
ACP Proxy
├── SessionRouter          (会话路由, 不变)
├── AgentRunManager         (实例管理, 不变)
└── TransportAdapter        (可插拔)
    ├── WsBridgeAdapter        ← 当前: 连接 ACP Bridge 的 WS 端口
    └── HttpDirectAdapter      ← 未来: 直连 CLI 的 HTTP 端口
```

## 11 总结

随着 Kimi k2.5/GLM5 的发布，包括春节期间即将发布的 deepseek v4，越来越多的国产模型能够支撑的起这样通用 AgentLoop 的实现，在专有云、公有云场景用户均可以有希望直接用上这个模型，这是这套架构得以落地的基本盘。

HiMarket 的架构本质是在 **HiMarket（Web 平台）** 与 **AgentRun（沙箱运行时）** 两个阿里云产品之间，建立一条安全、高效、可伸缩的 ACP 通信链路，使支持 ACP 协议的各种 Agent CLI（qodercli、Qwen Code、OpenCode 等）能够在 Web 端为用户服务。

关键架构决策：

1. **双产品形态，共享基础设施**——HiWork（AI 办公助手）和 HiCoding（AI 应用开发平台）共享同一套 HiMarket 后端和 AgentRun 基础设施，差异仅体现在前端 UI 和挂载的 Skill/MCP Server 组合
2. **ACP Proxy + ACP Bridge 双层桥接**——HiMarket 后端的 ACP Proxy 做会话路由和前端对接，AgentRun 沙箱内的 ACP Bridge 做 stdio→WebSocket 转换，两者配合解决"浏览器无法直连远端 CLI stdio"的核心矛盾
3. **物理隔离交给 AgentRun**——沙箱隔离是 AgentRun 作为云产品的天然能力，HiMarket 聚焦逻辑隔离和数据隔离
4. **ACP 消息透传**——Proxy 默认对 ACP 消息做透传，仅在审批拦截、计量埋点、限流控制等平台级场景做旁路处理
5. **Transport Adapter 可插拔**——为 ACP 协议未来支持 HTTP 传输预留演进空间，届时可移除 ACP Bridge
6. **产物渲染是体验差异化的关键**——PPT/信息图/Web 应用的实时预览能力决定了 HiMarket 与纯 CLI 工具的区别
7. **全链路流式是非谈判项**——从 LLM 到 CLI stdout 到 ACP Bridge 到 ACP Proxy 到 WebSocket 到前端渲染，每一跳都必须支持流式
