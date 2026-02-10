# https://agentclientprotocol.com/protocol/extensibility

[Agent Client Protocol home page](/)

##### Get Started

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

Protocol

# Extensibility

Adding custom data and capabilities

The Agent Client Protocol provides built-in extension mechanisms that allow implementations to add custom functionality while maintaining compatibility with the core protocol. These mechanisms ensure that Agents and Clients can innovate without breaking interoperability.

## [​](#the-meta-field) The `_meta` Field

All types in the protocol include a `_meta` field with type `{ [key: string]: unknown }` that implementations can use to attach custom information. This includes requests, responses, notifications, and even nested types like content blocks, tool calls, plan entries, and capability objects.

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 1,  "id": 1, "method": "session/prompt",  "method": "session/prompt", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "prompt": [ "prompt": [ { { "type": "text",  "type": "text", "text": "Hello, world!"  "text": "Hello, world!" } } ], ], "_meta": { "_meta": { "traceparent": "00-80e1afed08e019fc1110464cfa66635c-7a085853722dc6d2-01",  "traceparent": "00-80e1afed08e019fc1110464cfa66635c-7a085853722dc6d2-01", "zed.dev/debugMode": true "zed.dev/debugMode": true } } } }}}
```

Clients may propagate fields to the agent for correlation purposes, such as `requestId`. The following root-level keys in `_meta` **SHOULD** be reserved for [W3C trace context](https://www.w3.org/TR/trace-context/) to guarantee interop with existing MCP implementations and OpenTelemetry tooling:

* `traceparent`
* `tracestate`
* `baggage`

Implementations **MUST NOT** add any custom fields at the root of a type that’s part of the specification. All possible names are reserved for future protocol versions.

## [​](#extension-methods) Extension Methods

The protocol reserves any method name starting with an underscore (`_`) for custom extensions. This allows implementations to add new functionality without the risk of conflicting with future protocol versions. Extension methods follow standard [JSON-RPC 2.0](https://www.jsonrpc.org/specification) semantics:

* **[Requests](https://www.jsonrpc.org/specification#request_object)** - Include an `id` field and expect a response
* **[Notifications](https://www.jsonrpc.org/specification#notification)** - Omit the `id` field and are one-way

### [​](#custom-requests) Custom Requests

In addition to the requests specified by the protocol, implementations **MAY** expose and call custom JSON-RPC requests as long as their name starts with an underscore (`_`).

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 1,  "id": 1, "method": "_zed.dev/workspace/buffers",  "method": "_zed.dev/workspace/buffers", "params": { "params": { "language": "rust"  "language": "rust" } }}}
```

Upon receiving a custom request, implementations **MUST** respond accordingly with the provided `id`:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 1,  "id": 1, "result": { "result": { "buffers": [ "buffers": [ { "id": 0, "path": "/home/user/project/src/main.rs" }, { "id": 0, "path": "/home/user/project/src/main.rs" }, { "id": 1, "path": "/home/user/project/src/editor.rs" } { "id": 1, "path": "/home/user/project/src/editor.rs" } ] ] } }}}
```

If the receiving end doesn’t recognize the custom method name, it should respond with the standard “Method not found” error:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 1,  "id": 1, "error": { "error": { "code": -32601,  "code": -32601, "message": "Method not found"  "message": "Method not found" } }}}
```

To avoid such cases, extensions **SHOULD** advertise their [custom capabilities](#advertising-custom-capabilities) so that callers can check their availability first and adapt their behavior or interface accordingly.

### [​](#custom-notifications) Custom Notifications

Custom notifications are regular JSON-RPC notifications that start with an underscore (`_`). Like all notifications, they omit the `id` field:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "method": "_zed.dev/file_opened",  "method": "_zed.dev/file_opened", "params": { "params": { "path": "/home/user/project/src/editor.rs"  "path": "/home/user/project/src/editor.rs" } }}}
```

Unlike with custom requests, implementations **SHOULD** ignore unrecognized notifications.

## [​](#advertising-custom-capabilities) Advertising Custom Capabilities

Implementations **SHOULD** use the `_meta` field in capability objects to advertise support for extensions and their methods:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 0,  "id": 0, "result": { "result": { "protocolVersion": 1,  "protocolVersion": 1, "agentCapabilities": { "agentCapabilities": { "loadSession": true,  "loadSession": true, "_meta": { "_meta": { "zed.dev": { "zed.dev": { "workspace": true,  "workspace": true, "fileNotifications": true  "fileNotifications": true } } } } } } } }}}
```

This allows implementations to negotiate custom features during initialization without breaking compatibility with standard Clients and Agents.

Was this page helpful?

[Previous](/protocol/slash-commands)[TransportsMechanisms for agents and clients to communicate with each other](/protocol/transports)

* [The \_meta Field](#the-meta-field)
* [Extension Methods](#extension-methods)
* [Custom Requests](#custom-requests)
* [Custom Notifications](#custom-notifications)
* [Advertising Custom Capabilities](#advertising-custom-capabilities)
