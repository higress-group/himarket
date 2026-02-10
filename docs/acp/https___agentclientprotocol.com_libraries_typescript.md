# https://agentclientprotocol.com/libraries/typescript

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

# TypeScript

TypeScript library for the Agent Client Protocol

The [@agentclientprotocol/sdk](https://www.npmjs.com/package/@agentclientprotocol/sdk) npm package provides implementations of both sides of the Agent Client Protocol that you can use to build your own agent server or client. To get started, add the package as a dependency to your project:

Copy

```
npm install @agentclientprotocol/sdk npm  install @agentclientprotocol/sdk
```

Depending on what kind of tool you’re building, you’ll need to use either the [AgentSideConnection](https://agentclientprotocol.github.io/typescript-sdk/classes/AgentSideConnection.html) class or the [ClientSideConnection](https://agentclientprotocol.github.io/typescript-sdk/classes/ClientSideConnection.html) class to establish communication with the ACP counterpart. You can find example implementations of both sides in the [main repository](https://github.com/agentclientprotocol/typescript-sdk/tree/main/src/examples). These can be run from your terminal or from an ACP Client like [Zed](https://zed.dev), making them great starting points for your own integration! Browse the [TypeScript library reference](https://agentclientprotocol.github.io/typescript-sdk) for detailed API documentation. For a complete, production-ready implementation of an ACP agent, check out [Gemini CLI](https://github.com/google-gemini/gemini-cli/blob/main/packages/cli/src/zed-integration/zedIntegration.ts).

Was this page helpful?

[Previous](/libraries/rust)[CommunityCommunity managed libraries for the Agent Client Protocol](/libraries/community)
