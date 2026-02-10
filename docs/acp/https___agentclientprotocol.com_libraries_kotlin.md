# https://agentclientprotocol.com/libraries/kotlin

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

# Kotlin

Kotlin library for the Agent Client Protocol

The [kotlin-sdk](https://github.com/agentclientprotocol/kotlin-sdk) provides implementations of both sides of the Agent Client Protocol that you can use to build your own agent server or client. **It currently supports JVM, other targets are in progress.** To get started, add the repository to your build file:

Copy

```
repositories {repositories { mavenCentral()  mavenCentral()}}
```

Add the dependency:

Copy

```
dependencies {dependencies { implementation("com.agentclientprotocol:acp:0.1.0-SNAPSHOT")  implementation("com.agentclientprotocol:acp:0.1.0-SNAPSHOT")}}
```

The [sample](https://github.com/agentclientprotocol/kotlin-sdk/tree/master/samples/kotlin-acp-client-sample) demonstrates how to implement both sides of the protocol.

Was this page helpful?

[Previous](/protocol/schema)[PythonPython library for the Agent Client Protocol](/libraries/python)
