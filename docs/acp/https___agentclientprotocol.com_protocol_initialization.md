# https://agentclientprotocol.com/protocol/initialization

[Agent Client Protocol home page](/)

##### Get Started

* [ACP Registry](/get-started/registry)

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

Protocol

# Initialization

How all Agent Client Protocol connections begin

The Initialization phase allows [Clients](./overview#client) and [Agents](./overview#agent) to negotiate protocol versions, capabilities, and authentication methods.   
   
 Before a Session can be created, Clients **MUST** initialize the connection by calling the `initialize` method with:

* The latest [protocol version](#protocol-version) supported
* The [capabilities](#client-capabilities) supported

They **SHOULD** also provide a name and version to the Agent.

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 0,  "id": 0, "method": "initialize",  "method": "initialize", "params": { "params": { "protocolVersion": 1,  "protocolVersion": 1, "clientCapabilities": { "clientCapabilities": { "fs": { "fs": { "readTextFile": true,  "readTextFile": true, "writeTextFile": true  "writeTextFile": true }, }, "terminal": true  "terminal": true }, }, "clientInfo": { "clientInfo": { "name": "my-client",  "name": "my-client", "title": "My Client",  "title": "My Client", "version": "1.0.0"  "version": "1.0.0" } } } }}}
```

The Agent **MUST** respond with the chosen [protocol version](#protocol-version) and the [capabilities](#agent-capabilities) it supports. It **SHOULD** also provide a name and version to the Client as well:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 0,  "id": 0, "result": { "result": { "protocolVersion": 1,  "protocolVersion": 1, "agentCapabilities": { "agentCapabilities": { "loadSession": true,  "loadSession": true, "promptCapabilities": { "promptCapabilities": { "image": true,  "image": true, "audio": true,  "audio": true, "embeddedContext": true  "embeddedContext": true }, }, "mcp": { "mcp": { "http": true,  "http": true, "sse": true  "sse": true } } }, }, "agentInfo": { "agentInfo": { "name": "my-agent",  "name": "my-agent", "title": "My Agent",  "title": "My Agent", "version": "1.0.0"  "version": "1.0.0" }, }, "authMethods": []  "authMethods": [] } }}}
```

## [​](#protocol-version) Protocol version

The protocol versions that appear in the `initialize` requests and responses are a single integer that identifies a **MAJOR** protocol version. This version is only incremented when breaking changes are introduced. Clients and Agents **MUST** agree on a protocol version and act according to its specification. See [Capabilities](#capabilities) to learn how non-breaking features are introduced.

### [​](#version-negotiation) Version Negotiation

The `initialize` request **MUST** include the latest protocol version the Client supports. If the Agent supports the requested version, it **MUST** respond with the same version. Otherwise, the Agent **MUST** respond with the latest version it supports. If the Client does not support the version specified by the Agent in the `initialize` response, the Client **SHOULD** close the connection and inform the user about it.

## [​](#capabilities) Capabilities

Capabilities describe features supported by the Client and the Agent. All capabilities included in the `initialize` request are **OPTIONAL**. Clients and Agents **SHOULD** support all possible combinations of their peer’s capabilities. The introduction of new capabilities is not considered a breaking change. Therefore, Clients and Agents **MUST** treat all capabilities omitted in the `initialize` request as **UNSUPPORTED**. Capabilities are high-level and are not attached to a specific base protocol concept. Capabilities may specify the availability of protocol methods, notifications, or a subset of their parameters. They may also signal behaviors of the Agent or Client implementation. Implementations can also [advertise custom capabilities](./extensibility#advertising-custom-capabilities) using the `_meta` field to indicate support for protocol extensions.

### [​](#client-capabilities) Client Capabilities

The Client **SHOULD** specify whether it supports the following capabilities:

#### [​](#file-system) File System

[​](#param-read-text-file)

readTextFile

boolean

The `fs/read_text_file` method is available.

[​](#param-write-text-file)

writeTextFile

boolean

The `fs/write_text_file` method is available.

[Learn more about File System methods](./file-system)

#### [​](#terminal) Terminal

[​](#param-terminal)

terminal

boolean

All `terminal/*` methods are available, allowing the Agent to execute and manage shell commands.

[Learn more about Terminals](./terminals)

### [​](#agent-capabilities) Agent Capabilities

The Agent **SHOULD** specify whether it supports the following capabilities:

[​](#param-load-session)

loadSession

boolean

The [`session/load`](./session-setup#loading-sessions) method is available.

[​](#param-prompt-capabilities)

promptCapabilities

PromptCapabilities Object

Object indicating the different types of [content](./content) that may be included in `session/prompt` requests.

#### [​](#prompt-capabilities) Prompt capabilities

As a baseline, all Agents **MUST** support `ContentBlock::Text` and `ContentBlock::ResourceLink` in `session/prompt` requests. Optionally, they **MAY** support richer types of [content](./content) by specifying the following capabilities:

[​](#param-image)

image

boolean

The prompt may include `ContentBlock::Image`

[​](#param-audio)

audio

boolean

The prompt may include `ContentBlock::Audio`

[​](#param-embedded-context)

embeddedContext

boolean

The prompt may include `ContentBlock::Resource`

#### [​](#mcp-capabilities) MCP capabilities

[​](#param-http)

http

boolean

The Agent supports connecting to MCP servers over HTTP.

[​](#param-sse)

sse

boolean

The Agent supports connecting to MCP servers over SSE.Note: This transport has been deprecated by the MCP spec.

#### [​](#session-capabilities) Session Capabilities

As a baseline, all Agents **MUST** support `session/new`, `session/prompt`, `session/cancel`, and `session/update`. Optionally, they **MAY** support other session methods and notifications by specifying additional capabilities.

`session/load` is still handled by the top-level `load_session` capability. This will be unified in future versions of the protocol.

## [​](#implementation-information) Implementation Information

Both Clients and Agents **SHOULD** provide information about their implementation in the `clientInfo` and `agentInfo` fields respectively. Both take the following three fields:

[​](#param-name)

name

string

Intended for programmatic or logical use, but can be used as a display name fallback if title isn’t present.

[​](#param-title)

string

Intended for UI and end-user contexts — optimized to be human-readable and easily understood. If not provided, the name should be used for display.

[​](#param-version)

string

Version of the implementation. Can be displayed to the user or used for debugging or metrics purposes.

Note: in future versions of the protocol, this information will be required.

---

 Once the connection is initialized, you’re ready to [create a session](./session-setup) and begin the conversation with the Agent.

Was this page helpful?

[Previous](/protocol/overview)[Session SetupCreating and loading sessions](/protocol/session-setup)

* [Protocol version](#protocol-version)
* [Version Negotiation](#version-negotiation)
* [Capabilities](#capabilities)
* [Client Capabilities](#client-capabilities)
* [File System](#file-system)
* [Terminal](#terminal)
* [Agent Capabilities](#agent-capabilities)
* [Prompt capabilities](#prompt-capabilities)
* [MCP capabilities](#mcp-capabilities)
* [Session Capabilities](#session-capabilities)
* [Implementation Information](#implementation-information)
