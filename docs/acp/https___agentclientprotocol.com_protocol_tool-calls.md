# https://agentclientprotocol.com/protocol/tool-calls

[Agent Client Protocol home page](/)

##### Get Started

* [ACP Registry](/get-started/registry)

* [Session Setup](/protocol/session-setup)
* [Prompt Turn](/protocol/prompt-turn)
* [Tool Calls](/protocol/tool-calls)
* [File System](/protocol/file-system)
* [Agent Plan](/protocol/agent-plan)
* [Session Modes](/protocol/session-modes)
* [Session Config Options](/protocol/session-config-options)
* [Slash Commands](/protocol/slash-commands)

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

# Tool Calls

How Agents report tool call execution

Tool calls represent actions that language models request Agents to perform during a [prompt turn](./prompt-turn). When an LLM determines it needs to interact with external systems—like reading files, running code, or fetching data—it generates tool calls that the Agent executes on its behalf. Agents report tool calls through [`session/update`](./prompt-turn#3-agent-reports-output) notifications, allowing Clients to display real-time progress and results to users. While Agents handle the actual execution, they may leverage Client capabilities like [permission requests](#requesting-permission) or [file system access](./file-system) to provide a richer, more integrated experience.

## [​](#creating) Creating

When the language model requests a tool invocation, the Agent **SHOULD** report it to the Client:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "method": "session/update",  "method": "session/update", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "update": { "update": { "sessionUpdate": "tool_call",  "sessionUpdate": "tool_call", "toolCallId": "call_001",  "toolCallId": "call_001", "title": "Reading configuration file",  "title": "Reading configuration file", "kind": "read",  "kind": "read", "status": "pending"  "status": "pending" } } } }}}
```

[​](#param-tool-call-id)

toolCallId

ToolCallId

required

A unique identifier for this tool call within the session

[​](#param-title)

title

string

required

A human-readable title describing what the tool is doing

[​](#param-kind)

kind

ToolKind

The category of tool being invoked.

Showkinds

* `read` - Reading files or data - `edit` - Modifying files or content - `delete` - Removing files or data - `move` - Moving or renaming files - `search` - Searching for information - `execute` - Running commands or code - `think` - Internal reasoning or planning - `fetch` - Retrieving external data
* `other` - Other tool types (default)

Tool kinds help Clients choose appropriate icons and optimize how they display tool execution progress.

[​](#param-status)

status

ToolCallStatus

The current [execution status](#status) (defaults to `pending`)

[​](#param-content)

content

ToolCallContent[]

[Content produced](#content) by the tool call

[​](#param-locations)

locations

ToolCallLocation[]

[File locations](#following-the-agent) affected by this tool call

[​](#param-raw-input)

rawInput

object

The raw input parameters sent to the tool

[​](#param-raw-output)

rawOutput

object

The raw output returned by the tool

## [​](#updating) Updating

As tools execute, Agents send updates to report progress and results. Updates use the `session/update` notification with `tool_call_update`:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "method": "session/update",  "method": "session/update", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "update": { "update": { "sessionUpdate": "tool_call_update",  "sessionUpdate": "tool_call_update", "toolCallId": "call_001",  "toolCallId": "call_001", "status": "in_progress",  "status": "in_progress", "content": [ "content": [ { { "type": "content",  "type": "content", "content": { "content": { "type": "text",  "type": "text", "text": "Found 3 configuration files..."  "text": "Found 3 configuration files..." } } } } ] ] } } } }}}
```

All fields except `toolCallId` are optional in updates. Only the fields being changed need to be included.

## [​](#requesting-permission) Requesting Permission

The Agent **MAY** request permission from the user before executing a tool call by calling the `session/request_permission` method:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 5,  "id": 5, "method": "session/request_permission",  "method": "session/request_permission", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "toolCall": { "toolCall": { "toolCallId": "call_001"  "toolCallId": "call_001" }, }, "options": [ "options": [ { { "optionId": "allow-once",  "optionId": "allow-once", "name": "Allow once",  "name": "Allow once", "kind": "allow_once"  "kind": "allow_once" }, }, { { "optionId": "reject-once",  "optionId": "reject-once", "name": "Reject",  "name": "Reject", "kind": "reject_once"  "kind": "reject_once" } } ] ] } }}}
```

[​](#param-session-id)

sessionId

SessionId

required

The session ID for this request

[​](#param-tool-call)

toolCall

ToolCallUpdate

required

The tool call update containing details about the operation

[​](#param-options)

options

PermissionOption[]

required

Available [permission options](#permission-options) for the user to choose from

The Client responds with the user’s decision:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 5,  "id": 5, "result": { "result": { "outcome": { "outcome": { "outcome": "selected",  "outcome": "selected", "optionId": "allow-once"  "optionId": "allow-once" } } } }}}
```

Clients **MAY** automatically allow or reject permission requests according to the user settings. If the current prompt turn gets [cancelled](./prompt-turn#cancellation), the Client **MUST** respond with the `"cancelled"` outcome:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 5,  "id": 5, "result": { "result": { "outcome": { "outcome": { "outcome": "cancelled"  "outcome": "cancelled" } } } }}}
```

[​](#param-outcome)

outcome

RequestPermissionOutcome

required

The user’s decision, either: - `cancelled` - The [prompt turn was cancelled](./prompt-turn#cancellation) - `selected` with an `optionId` - The ID of the selected permission option

### [​](#permission-options) Permission Options

Each permission option provided to the Client contains:

[​](#param-option-id)

optionId

string

required

Unique identifier for this option

[​](#param-name)

name

string

required

Human-readable label to display to the user

[​](#param-kind-1)

kind

PermissionOptionKind

required

A hint to help Clients choose appropriate icons and UI treatment for each option.

* `allow_once` - Allow this operation only this time
* `allow_always` - Allow this operation and remember the choice
* `reject_once` - Reject this operation only this time
* `reject_always` - Reject this operation and remember the choice

## [​](#status) Status

Tool calls progress through different statuses during their lifecycle:

[​](#param-pending)

pending

The tool call hasn’t started running yet because the input is either streaming or awaiting approval

[​](#param-in-progress)

in\_progress

The tool call is currently running

[​](#param-completed)

completed

The tool call completed successfully

[​](#param-failed)

failed

The tool call failed with an error

## [​](#content) Content

Tool calls can produce different types of content:

### [​](#regular-content) Regular Content

Standard [content blocks](./content) like text, images, or resources:

Copy

```
{{ "type": "content",  "type": "content", "content": { "content": { "type": "text",  "type": "text", "text": "Analysis complete. Found 3 issues."  "text": "Analysis complete. Found 3 issues." } }}}
```

### [​](#diffs) Diffs

File modifications shown as diffs:

Copy

```
{{ "type": "diff",  "type": "diff", "path": "/home/user/project/src/config.json",  "path": "/home/user/project/src/config.json", "oldText": "{\n \"debug\": false\n}",  "oldText": "{\n  \" debug \": false \n}", "newText": "{\n \"debug\": true\n}"  "newText": "{\n  \" debug \": true \n}"}}
```

[​](#param-path)

path

string

required

The absolute file path being modified

[​](#param-old-text)

oldText

string

The original content (null for new files)

[​](#param-new-text)

string

required

The new content after modification

### [​](#terminals) Terminals

Live terminal output from command execution:

Copy

```
{{ "type": "terminal",  "type": "terminal", "terminalId": "term_xyz789"  "terminalId": "term_xyz789"}}
```

[​](#param-terminal-id)

string

required

The ID of a terminal created with `terminal/create`

When a terminal is embedded in a tool call, the Client displays live output as it’s generated and continues to display it even after the terminal is released. [Learn more about Terminals](./terminals)

## [​](#following-the-agent) Following the Agent

Tool calls can report file locations they’re working with, enabling Clients to implement “follow-along” features that track which files the Agent is accessing or modifying in real-time.

Copy

```
{{ "path": "/home/user/project/src/main.py",  "path": "/home/user/project/src/main.py", "line": 42  "line": 42}}
```

[​](#param-path-1)

path

string

required

The absolute file path being accessed or modified

[​](#param-line)

Optional line number within the file

Was this page helpful?

[Previous](/protocol/content)[File SystemClient filesystem access methods](/protocol/file-system)

* [Creating](#creating)
* [Updating](#updating)
* [Requesting Permission](#requesting-permission)
* [Permission Options](#permission-options)
* [Status](#status)
* [Content](#content)
* [Regular Content](#regular-content)
* [Diffs](#diffs)
* [Terminals](#terminals)
* [Following the Agent](#following-the-agent)
