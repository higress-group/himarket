# https://agentclientprotocol.com/get-started/registry

[Agent Client Protocol home page](/)

##### Get Started

* [ACP Registry](/get-started/registry)

##### Protocol

* [Overview](/protocol/overview)
* [Initialization](/protocol/initialization)
* [Session Setup](/protocol/session-setup)
* [Prompt Turn](/protocol/prompt-turn)
* [Content](/protocol/content)
* [Tool Calls](/protocol/tool-calls)
* [File System](/protocol/file-system)
* [Terminals](/protocol/terminals)
* [Agent Plan](/protocol/agent-plan)
* [Session Modes](/protocol/session-modes)
* [Slash Commands](/protocol/slash-commands)
* [Extensibility](/protocol/extensibility)
* [Transports](/protocol/transports)
* [Schema](/protocol/schema)

##### Libraries

* [Kotlin](/libraries/kotlin)
* [Python](/libraries/python)
* [Rust](/libraries/rust)
* [TypeScript](/libraries/typescript)
* [Community](/libraries/community)

* [GitHub](https://github.com/agentclientprotocol/agent-client-protocol)
* [Zed Industries](https://zed.dev)
* [JetBrains](https://jetbrains.com)

[Agent Client Protocol home page](/)

[Protocol](/get-started/introduction)[RFDs](/rfds/about)[Community](/community/communication)[Updates](/updates)[Brand](/brand)

[Protocol](/get-started/introduction)[RFDs](/rfds/about)[Community](/community/communication)[Updates](/updates)[Brand](/brand)

Get Started

# ACP Registry

The easiest way to find and install ACP-compatible agents.

## [​](#overview) Overview

The ACP Registry is an easy way for developers to distribute their ACP-compatible agents to any client that speaks the protocol. At the moment, this is a curated set of agents, including only the ones that [support authentication](/rfds/auth-methods). Visit [the registry repository on GitHub](https://github.com/agentclientprotocol/registry) to learn more about it.

The registry is under active development, so expect its format and contents to change.

## [​](#available-agents) Available Agents

[## Auggie CLI

`0.15.0`](https://github.com/augmentcode/auggie-zed-extension)[## Claude Code

`0.13.2`](https://github.com/zed-industries/claude-code-acp)[## Codex CLI

`0.9.0`](https://github.com/zed-industries/codex-acp)

## Factory Droid

`0.56.3`

[## Gemini CLI

`0.26.0`](https://github.com/google-gemini/gemini-cli)[## GitHub Copilot

`1.418.0`](https://github.com/github/copilot-language-server-release)[## Mistral Vibe

`2.0.2`](https://github.com/mistralai/mistral-vibe)[## OpenCode

`1.1.48`](https://github.com/sst/opencode)[## Qwen Code

`0.8.2`](https://github.com/QwenLM/qwen-code)

## [​](#using-the-registry) Using the Registry

Clients can fetch the registry programmatically:

```
curl https://cdn.agentclientprotocol.com/registry/v1/latest/registry.json curl https://cdn.agentclientprotocol.com/registry/v1/latest/registry.json
```

The registry JSON contains all agent metadata including distribution information for automatic installation.

## [​](#submit-your-agent) Submit your Agent

To add your agent to the registry:

1. Fork the [registry repository on GitHub](https://github.com/agentclientprotocol/registry)
2. Create a folder with your agent’s ID (lowercase, hyphens allowed)
3. Add an `agent.json` file following [the schema](https://github.com/agentclientprotocol/registry/blob/main/agent.schema.json)
4. Optionally add an `icon.svg` (16x16 recommended)
5. Submit a pull request

See the [contributing guide](https://github.com/agentclientprotocol/registry/blob/main/CONTRIBUTING.md) for details.

Was this page helpful?

[Previous](/get-started/clients)[OverviewHow the Agent Client Protocol works](/protocol/overview)

* [Overview](#overview)
* [Available Agents](#available-agents)
* [Using the Registry](#using-the-registry)
* [Submit your Agent](#submit-your-agent)
