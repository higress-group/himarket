# https://agentclientprotocol.com/libraries/rust

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

# Rust

Rust library for the Agent Client Protocol

The [agent-client-protocol](https://crates.io/crates/agent-client-protocol) Rust crate provides implementations of both sides of the Agent Client Protocol that you can use to build your own agent server or client. To get started, add the crate as a dependency to your project’s `Cargo.toml`:

Copy

```
cargo add agent-client-protocol cargo  add agent-client-protocol
```

Depending on what kind of tool you’re building, you’ll need to implement either the [Agent](https://docs.rs/agent-client-protocol/latest/agent_client_protocol/trait.Agent.html) trait or the [Client](https://docs.rs/agent-client-protocol/latest/agent_client_protocol/trait.Client.html) trait to define the interaction with the ACP counterpart. The [agent](https://github.com/agentclientprotocol/rust-sdk/blob/main/examples/agent.rs) and [client](https://github.com/agentclientprotocol/rust-sdk/blob/main/examples/client.rs) example binaries provide runnable examples of how to do this, which you can use as a starting point. You can read the full documentation for the `agent-client-protocol` crate on [docs.rs](https://docs.rs/agent-client-protocol/latest/agent_client_protocol/).

## [​](#users) Users

The `agent-client-protocol` crate powers the integration with external agents in the [Zed](https://zed.dev) editor.

Was this page helpful?

[Previous](/libraries/python)[TypeScriptTypeScript library for the Agent Client Protocol](/libraries/typescript)

* [Users](#users)
