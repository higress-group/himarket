# https://agentclientprotocol.com/protocol/session-modes

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

# Session Modes

Switch between different agent operating modes

You can now use [Session Config Options](./session-config-options). Dedicated session mode methods will be removed in a future version of the protocol. Until then, you can offer both to clients for backwards compatibility.

Agents can provide a set of modes they can operate in. Modes often affect the system prompts used, the availability of tools, and whether they request permission before running.

## [​](#initial-state) Initial state

During [Session Setup](./session-setup) the Agent **MAY** return a list of modes it can operate in and the currently active mode:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 1,  "id": 1, "result": { "result": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "modes": { "modes": { "currentModeId": "ask",  "currentModeId": "ask", "availableModes": [ "availableModes": [ { { "id": "ask",  "id": "ask", "name": "Ask",  "name": "Ask", "description": "Request permission before making any changes"  "description": "Request permission before making any changes" }, }, { { "id": "architect",  "id": "architect", "name": "Architect",  "name": "Architect", "description": "Design and plan software systems without implementation"  "description": "Design and plan software systems without implementation" }, }, { { "id": "code",  "id": "code", "name": "Code",  "name": "Code", "description": "Write and modify code with full tool access"  "description": "Write and modify code with full tool access" } } ] ] } } } }}}
```

[​](#param-modes)

modes

SessionModeState

The current mode state for the session

### [​](#sessionmodestate) SessionModeState

[​](#param-current-mode-id)

currentModeId

SessionModeId

required

The ID of the mode that is currently active

[​](#param-available-modes)

availableModes

SessionMode[]

required

The set of modes that the Agent can operate in

### [​](#sessionmode) SessionMode

[​](#param-id)

id

SessionModeId

required

Unique identifier for this mode

[​](#param-name)

name

string

required

Human-readable name of the mode

[​](#param-description)

description

string

Optional description providing more details about what this mode does

## [​](#setting-the-current-mode) Setting the current mode

The current mode can be changed at any point during a session, whether the Agent is idle or generating a response.

### [​](#from-the-client) From the Client

Typically, Clients display the available modes to the user and allow them to change the current one, which they can do by calling the [`session/set_mode`](./schema#session%2Fset-mode) method.

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 2,  "id": 2, "method": "session/set_mode",  "method": "session/set_mode", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "modeId": "code"  "modeId": "code" } }}}
```

[​](#param-session-id)

sessionId

SessionId

required

The ID of the session to set the mode for

[​](#param-mode-id)

SessionModeId

required

The ID of the mode to switch to. Must be one of the modes listed in `availableModes`

### [​](#from-the-agent) From the Agent

The Agent can also change its own mode and let the Client know by sending the `current_mode_update` session notification:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "method": "session/update",  "method": "session/update", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "update": { "update": { "sessionUpdate": "current_mode_update",  "sessionUpdate": "current_mode_update", "modeId": "code"  "modeId": "code" } } } }}}
```

#### [​](#exiting-plan-modes) Exiting plan modes

A common case where an Agent might switch modes is from within a special “exit mode” tool that can be provided to the language model during plan/architect modes. The language model can call this tool when it determines it’s ready to start implementing a solution. This “switch mode” tool will usually request permission before running, which it can do just like any other tool:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 3,  "id": 3, "method": "session/request_permission",  "method": "session/request_permission", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "toolCall": { "toolCall": { "toolCallId": "call_switch_mode_001",  "toolCallId": "call_switch_mode_001", "title": "Ready for implementation",  "title": "Ready for implementation", "kind": "switch_mode",  "kind": "switch_mode", "status": "pending",  "status": "pending", "content": [ "content": [ { { "type": "text",  "type": "text", "text": "## Implementation Plan..."  "text": "## Implementation Plan..." } } ] ] }, }, "options": [ "options": [ { { "optionId": "code",  "optionId": "code", "name": "Yes, and auto-accept all actions",  "name": "Yes, and auto-accept all actions", "kind": "allow_always"  "kind": "allow_always" }, }, { { "optionId": "ask",  "optionId": "ask", "name": "Yes, and manually accept actions",  "name": "Yes, and manually accept actions", "kind": "allow_once"  "kind": "allow_once" }, }, { { "optionId": "reject",  "optionId": "reject", "name": "No, stay in architect mode",  "name": "No, stay in architect mode", "kind": "reject_once"  "kind": "reject_once" } } ] ] } }}}
```

When an option is chosen, the tool runs, setting the mode and sending the `current_mode_update` notification mentioned above. [Learn more about permission requests](./tool-calls#requesting-permission)

Was this page helpful?

[Previous](/protocol/agent-plan)[Session Config OptionsFlexible configuration selectors for agent sessions](/protocol/session-config-options)

* [Initial state](#initial-state)
* [SessionModeState](#sessionmodestate)
* [SessionMode](#sessionmode)
* [Setting the current mode](#setting-the-current-mode)
* [From the Client](#from-the-client)
* [From the Agent](#from-the-agent)
* [Exiting plan modes](#exiting-plan-modes)
