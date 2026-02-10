# https://agentclientprotocol.com/get-started/introduction

[Agent Client Protocol home page](/)

##### Get Started

* [Architecture](/get-started/architecture)
* [Agents](/get-started/agents)
* [Clients](/get-started/clients)
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
* [Session Config Options](/protocol/session-config-options)
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

# Introduction

Get started with the Agent Client Protocol.

The Agent Client Protocol (ACP) standardizes communication between code editors/IDEs and coding agents and is suitable for both local and remote scenarios.

## [​](#why-acp) Why ACP?

AI coding agents and editors are tightly coupled but interoperability isn’t the default. Each editor must build custom integrations for every agent they want to support, and agents must implement editor-specific APIs to reach users. This creates several problems:

* Integration overhead: Every new agent-editor combination requires custom work
* Limited compatibility: Agents work with only a subset of available editors
* Developer lock-in: Choosing an agent often means accepting their available interfaces

ACP solves this by providing a standardized protocol for agent-editor communication, similar to how the [Language Server Protocol (LSP)](https://microsoft.github.io/language-server-protocol/) standardized language server integration. Agents that implement ACP work with any compatible editor. Editors that support ACP gain access to the entire ecosystem of ACP-compatible agents. This decoupling allows both sides to innovate independently while giving developers the freedom to choose the best tools for their workflow.

## [​](#overview) Overview

ACP assumes that the user is primarily in their editor, and wants to reach out and use agents to assist them with specific tasks. ACP is suitable for both local and remote scenarios:

* **Local agents** run as sub-processes of the code editor, communicating via JSON-RPC over stdio.
* **Remote agents** can be hosted in the cloud or on separate infrastructure, communicating over HTTP or WebSocket

Full support for remote agents is a work in progress. We are actively collaborating with agentic platforms to ensure the protocol addresses the specific requirements of cloud-hosted and remote deployment scenarios.

The protocol re-uses the JSON representations used in MCP where possible, but includes custom types for useful agentic coding UX elements, like displaying diffs. The default format for user-readable text is Markdown, which allows enough flexibility to represent rich formatting without requiring that the code editor is capable of rendering HTML.

Was this page helpful?

[ArchitectureOverview of the Agent Client Protocol architecture.](/get-started/architecture)

* [Why ACP?](#why-acp)
* [Overview](#overview)
