# https://agentclientprotocol.com/libraries/python

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

Libraries

# Python

Python library for the Agent Client Protocol

The [agentclientprotocol/python-sdk](https://github.com/agentclientprotocol/python-sdk) repository packages Pydantic models, async base classes, and JSON-RPC plumbing so you can build ACP-compatible agents and clients in Python. It mirrors the official ACP schema and ships helper utilities for both sides of the protocol. To get started, add the SDK to your project:

Copy

```
pip install agent-client-protocol pip  install agent-client-protocol
```

(Using [uv](https://github.com/astral-sh/uv)? Run `uv add agent-client-protocol`.) The repository includes runnable examples for agents, clients, Gemini CLI bridges, and dual-agent/client demos under [`examples/`](https://github.com/agentclientprotocol/python-sdk/tree/main/examples). Browse the full documentation—including the quickstart, contrib helpers, and API reference—at [agentclientprotocol.github.io/python-sdk](https://agentclientprotocol.github.io/python-sdk/).

Was this page helpful?

[Previous](/libraries/kotlin)[RustRust library for the Agent Client Protocol](/libraries/rust)
