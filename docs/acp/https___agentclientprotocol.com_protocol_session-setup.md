# https://agentclientprotocol.com/protocol/session-setup

[Agent Client Protocol home page](/)

##### Get Started

* [ACP Registry](/get-started/registry)

* [Session Setup](/protocol/session-setup)
* [Prompt Turn](/protocol/prompt-turn)
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

# Session Setup

Creating and loading sessions

Sessions represent a specific conversation or thread between the [Client](./overview#client) and [Agent](./overview#agent). Each session maintains its own context, conversation history, and state, allowing multiple independent interactions with the same Agent. Before creating a session, Clients **MUST** first complete the [initialization](./initialization) phase to establish protocol compatibility and capabilities.   
   

## [​](#creating-a-session) Creating a Session

Clients create a new session by calling the `session/new` method with:

* The [working directory](#working-directory) for the session
* A list of [MCP servers](#mcp-servers) the Agent should connect to

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 1,  "id": 1, "method": "session/new",  "method": "session/new", "params": { "params": { "cwd": "/home/user/project",  "cwd": "/home/user/project", "mcpServers": [ "mcpServers": [ { { "name": "filesystem",  "name": "filesystem", "command": "/path/to/mcp-server",  "command": "/path/to/mcp-server", "args": ["--stdio"],  "args": ["--stdio"], "env": []  "env": [] } } ] ] } }}}
```

The Agent **MUST** respond with a unique [Session ID](#session-id) that identifies this conversation:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 1,  "id": 1, "result": { "result": { "sessionId": "sess_abc123def456"  "sessionId": "sess_abc123def456" } }}}
```

## [​](#loading-sessions) Loading Sessions

Agents that support the `loadSession` capability allow Clients to resume previous conversations. This feature enables persistence across restarts and sharing sessions between different Client instances.

### [​](#checking-support) Checking Support

Before attempting to load a session, Clients **MUST** verify that the Agent supports this capability by checking the `loadSession` field in the `initialize` response:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 0,  "id": 0, "result": { "result": { "protocolVersion": 1,  "protocolVersion": 1, "agentCapabilities": { "agentCapabilities": { "loadSession": true  "loadSession": true } } } }}}
```

If `loadSession` is `false` or not present, the Agent does not support loading sessions and Clients **MUST NOT** attempt to call `session/load`.

### [​](#loading-a-session) Loading a Session

To load an existing session, Clients **MUST** call the `session/load` method with:

* The [Session ID](#session-id) to resume
* [MCP servers](#mcp-servers) to connect to
* The [working directory](#working-directory)

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 1,  "id": 1, "method": "session/load",  "method": "session/load", "params": { "params": { "sessionId": "sess_789xyz",  "sessionId": "sess_789xyz", "cwd": "/home/user/project",  "cwd": "/home/user/project", "mcpServers": [ "mcpServers": [ { { "name": "filesystem",  "name": "filesystem", "command": "/path/to/mcp-server",  "command": "/path/to/mcp-server", "args": ["--mode", "filesystem"],  "args": ["--mode", "filesystem"], "env": []  "env": [] } } ] ] } }}}
```

The Agent **MUST** replay the entire conversation to the Client in the form of `session/update` notifications (like `session/prompt`). For example, a user message from the conversation history:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "method": "session/update",  "method": "session/update", "params": { "params": { "sessionId": "sess_789xyz",  "sessionId": "sess_789xyz", "update": { "update": { "sessionUpdate": "user_message_chunk",  "sessionUpdate": "user_message_chunk", "content": { "content": { "type": "text",  "type": "text", "text": "What's the capital of France?"  "text": "What's the capital of France?" } } } } } }}}
```

Followed by the agent’s response:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "method": "session/update",  "method": "session/update", "params": { "params": { "sessionId": "sess_789xyz",  "sessionId": "sess_789xyz", "update": { "update": { "sessionUpdate": "agent_message_chunk",  "sessionUpdate": "agent_message_chunk", "content": { "content": { "type": "text",  "type": "text", "text": "The capital of France is Paris."  "text": "The capital of France is Paris." } } } } } }}}
```

When **all** the conversation entries have been streamed to the Client, the Agent **MUST** respond to the original `session/load` request.

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 1,  "id": 1, "result": null  "result": null}}
```

The Client can then continue sending prompts as if the session was never interrupted.

## [​](#session-id) Session ID

The session ID returned by `session/new` is a unique identifier for the conversation context. Clients use this ID to:

* Send prompt requests via `session/prompt`
* Cancel ongoing operations via `session/cancel`
* Load previous sessions via `session/load` (if the Agent supports the `loadSession` capability)

## [​](#working-directory) Working Directory

The `cwd` (current working directory) parameter establishes the file system context for the session. This directory:

* **MUST** be an absolute path
* **MUST** be used for the session regardless of where the Agent subprocess was spawned
* **SHOULD** serve as a boundary for tool operations on the file system

## [​](#mcp-servers) MCP Servers

The [Model Context Protocol (MCP)](https://modelcontextprotocol.io) allows Agents to access external tools and data sources. When creating a session, Clients **MAY** include connection details for MCP servers that the Agent should connect to. MCP servers can be connected to using different transports. All Agents **MUST** support the stdio transport, while HTTP and SSE transports are optional capabilities that can be checked during initialization. While they are not required to by the spec, new Agents **SHOULD** support the HTTP transport to ensure compatibility with modern MCP servers.

### [​](#transport-types) Transport Types

#### [​](#stdio-transport) Stdio Transport

All Agents **MUST** support connecting to MCP servers via stdio (standard input/output). This is the default transport mechanism.

[​](#param-name)

name

string

required

A human-readable identifier for the server

[​](#param-command)

command

string

required

The absolute path to the MCP server executable

[​](#param-args)

args

array

required

Command-line arguments to pass to the server

[​](#param-env)

env

EnvVariable[]

Environment variables to set when launching the server

ShowEnvVariable

[​](#param-name-1)

name

string

The name of the environment variable.

[​](#param-value)

value

string

The value of the environment variable.

Example stdio transport configuration:

Copy

```
{{ "name": "filesystem",  "name": "filesystem", "command": "/path/to/mcp-server",  "command": "/path/to/mcp-server", "args": ["--stdio"],  "args": ["--stdio"], "env": [ "env": [ { { "name": "API_KEY",  "name": "API_KEY", "value": "secret123"  "value": "secret123" } } ] ]}}
```

#### [​](#http-transport) HTTP Transport

When the Agent supports `mcpCapabilities.http`, Clients can specify MCP servers configurations using the HTTP transport.

[​](#param-type)

type

string

required

Must be `"http"` to indicate HTTP transport

[​](#param-name-2)

name

string

required

A human-readable identifier for the server

[​](#param-url)

url

string

required

The URL of the MCP server

[​](#param-headers)

headers

HttpHeader[]

required

HTTP headers to include in requests to the server

ShowHttpHeader

[​](#param-name-3)

name

string

The name of the HTTP header.

[​](#param-value-1)

value

string

The value to set for the HTTP header.

Example HTTP transport configuration:

Copy

```
{{ "type": "http",  "type": "http", "name": "api-server",  "name": "api-server", "url": "https://api.example.com/mcp",  "url": "https://api.example.com/mcp", "headers": [ "headers": [ { { "name": "Authorization",  "name": "Authorization", "value": "Bearer token123"  "value": "Bearer token123" }, }, { { "name": "Content-Type",  "name": "Content-Type", "value": "application/json"  "value": "application/json" } } ] ]}}
```

#### [​](#sse-transport) SSE Transport

When the Agent supports `mcpCapabilities.sse`, Clients can specify MCP servers configurations using the SSE transport.

This transport was deprecated by the MCP spec.

[​](#param-type-1)

type

string

required

Must be `"sse"` to indicate SSE transport

[​](#param-name-4)

name

string

required

A human-readable identifier for the server

[​](#param-url-1)

url

string

required

The URL of the SSE endpoint

[​](#param-headers-1)

headers

HttpHeader[]

required

HTTP headers to include when establishing the SSE connection

ShowHttpHeader

[​](#param-name-5)

name

string

The name of the HTTP header.

[​](#param-value-2)

value

string

The value to set for the HTTP header.

Example SSE transport configuration:

Copy

```
{{ "type": "sse",  "type": "sse", "name": "event-stream",  "name": "event-stream", "url": "https://events.example.com/mcp",  "url": "https://events.example.com/mcp", "headers": [ "headers": [ { { "name": "X-API-Key",  "name": "X-API-Key", "value": "apikey456"  "value": "apikey456" } } ] ]}}
```

### [​](#checking-transport-support) Checking Transport Support

Before using HTTP or SSE transports, Clients **MUST** verify the Agent’s capabilities during initialization:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 0,  "id": 0, "result": { "result": { "protocolVersion": 1,  "protocolVersion": 1, "agentCapabilities": { "agentCapabilities": { "mcpCapabilities": { "mcpCapabilities": { "http": true,  "http": true, "sse": true  "sse": true } } } } } }}}
```

If `mcpCapabilities.http` is `false` or not present, the Agent does not support HTTP transport. If `mcpCapabilities.sse` is `false` or not present, the Agent does not support SSE transport. Agents **SHOULD** connect to all MCP servers specified by the Client. Clients **MAY** use this ability to provide tools directly to the underlying language model by including their own MCP server.

Was this page helpful?

[Previous](/protocol/initialization)[Prompt TurnUnderstanding the core conversation flow](/protocol/prompt-turn)

* [Creating a Session](#creating-a-session)
* [Loading Sessions](#loading-sessions)
* [Checking Support](#checking-support)
* [Loading a Session](#loading-a-session)
* [Session ID](#session-id)
* [Working Directory](#working-directory)
* [MCP Servers](#mcp-servers)
* [Transport Types](#transport-types)
* [Stdio Transport](#stdio-transport)
* [HTTP Transport](#http-transport)
* [SSE Transport](#sse-transport)
* [Checking Transport Support](#checking-transport-support)
