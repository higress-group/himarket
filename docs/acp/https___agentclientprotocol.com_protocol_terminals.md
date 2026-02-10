# https://agentclientprotocol.com/protocol/terminals

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

# Terminals

Executing and managing terminal commands

The terminal methods allow Agents to execute shell commands within the Client’s environment. These methods enable Agents to run build processes, execute scripts, and interact with command-line tools while providing real-time output streaming and process control.

## [​](#checking-support) Checking Support

Before attempting to use terminal methods, Agents **MUST** verify that the Client supports this capability by checking the [Client Capabilities](./initialization#client-capabilities) field in the `initialize` response:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 0,  "id": 0, "result": { "result": { "protocolVersion": 1,  "protocolVersion": 1, "clientCapabilities": { "clientCapabilities": { "terminal": true  "terminal": true } } } }}}
```

If `terminal` is `false` or not present, the Agent **MUST NOT** attempt to call any terminal methods.

## [​](#executing-commands) Executing Commands

The `terminal/create` method starts a command in a new terminal:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 5,  "id": 5, "method": "terminal/create",  "method": "terminal/create", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "command": "npm",  "command": "npm", "args": ["test", "--coverage"],  "args": ["test", "--coverage"], "env": [ "env": [ { { "name": "NODE_ENV",  "name": "NODE_ENV", "value": "test"  "value": "test" } } ], ], "cwd": "/home/user/project",  "cwd": "/home/user/project", "outputByteLimit": 1048576  "outputByteLimit": 1048576 } }}}
```

[​](#param-session-id)

sessionId

SessionId

required

The [Session ID](./session-setup#session-id) for this request

[​](#param-command)

command

string

required

The command to execute

[​](#param-args)

args

string[]

Array of command arguments

[​](#param-env)

env

EnvVariable[]

Environment variables for the command.Each variable has:

* `name`: The environment variable name
* `value`: The environment variable value

[​](#param-cwd)

cwd

string

Working directory for the command (absolute path)

[​](#param-output-byte-limit)

outputByteLimit

number

Maximum number of output bytes to retain. Once exceeded, earlier output is truncated to stay within this limit.When the limit is exceeded, the Client truncates from the beginning of the output to stay within the limit.The Client **MUST** ensure truncation happens at a character boundary to maintain valid string output, even if this means the retained output is slightly less than the specified limit.

The Client returns a Terminal ID immediately without waiting for completion:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 5,  "id": 5, "result": { "result": { "terminalId": "term_xyz789"  "terminalId": "term_xyz789" } }}}
```

This allows the command to run in the background while the Agent performs other operations. After creating the terminal, the Agent can use the `terminal/wait_for_exit` method to wait for the command to complete.

The Agent **MUST** release the terminal using `terminal/release` when it’s no longer needed.

## [​](#embedding-in-tool-calls) Embedding in Tool Calls

Terminals can be embedded directly in [tool calls](./tool-calls) to provide real-time output to users:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "method": "session/update",  "method": "session/update", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "update": { "update": { "sessionUpdate": "tool_call",  "sessionUpdate": "tool_call", "toolCallId": "call_002",  "toolCallId": "call_002", "title": "Running tests",  "title": "Running tests", "kind": "execute",  "kind": "execute", "status": "in_progress",  "status": "in_progress", "content": [ "content": [ { { "type": "terminal",  "type": "terminal", "terminalId": "term_xyz789"  "terminalId": "term_xyz789" } } ] ] } } } }}}
```

When a terminal is embedded in a tool call, the Client displays live output as it’s generated and continues to display it even after the terminal is released.

## [​](#getting-output) Getting Output

The `terminal/output` method retrieves the current terminal output without waiting for the command to complete:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 6,  "id": 6, "method": "terminal/output",  "method": "terminal/output", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "terminalId": "term_xyz789"  "terminalId": "term_xyz789" } }}}
```

The Client responds with the current output and exit status (if the command has finished):

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 6,  "id": 6, "result": { "result": { "output": "Running tests...\n✓ All tests passed (42 total)\n",  "output": "Running tests... \n✓ All tests passed (42 total) \n ", "truncated": false,  "truncated": false, "exitStatus": { "exitStatus": { "exitCode": 0,  "exitCode": 0, "signal": null  "signal": null } } } }}}
```

[​](#param-output)

output

string

required

The terminal output captured so far

[​](#param-truncated)

truncated

boolean

required

Whether the output was truncated due to byte limits

[​](#param-exit-status)

exitStatus

TerminalExitStatus

Present only if the command has exited. Contains:

* `exitCode`: The process exit code (may be null)
* `signal`: The signal that terminated the process (may be null)

## [​](#waiting-for-exit) Waiting for Exit

The `terminal/wait_for_exit` method returns once the command completes:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 7,  "id": 7, "method": "terminal/wait_for_exit",  "method": "terminal/wait_for_exit", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "terminalId": "term_xyz789"  "terminalId": "term_xyz789" } }}}
```

The Client responds once the command exits:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 7,  "id": 7, "result": { "result": { "exitCode": 0,  "exitCode": 0, "signal": null  "signal": null } }}}
```

[​](#param-exit-code)

number

The process exit code (may be null if terminated by signal)

[​](#param-signal)

string

The signal that terminated the process (may be null if exited normally)

## [​](#killing-commands) Killing Commands

The `terminal/kill` method terminates a command without releasing the terminal:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 8,  "id": 8, "method": "terminal/kill",  "method": "terminal/kill", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "terminalId": "term_xyz789"  "terminalId": "term_xyz789" } }}}
```

After killing a command, the terminal remains valid and can be used with:

* `terminal/output` to get the final output
* `terminal/wait_for_exit` to get the exit status

The Agent **MUST** still call `terminal/release` when it’s done using it.

### [​](#building-a-timeout) Building a Timeout

Agents can implement command timeouts by combining terminal methods:

1. Create a terminal with `terminal/create`
2. Start a timer for the desired timeout duration
3. Concurrently wait for either the timer to expire or `terminal/wait_for_exit` to return
4. If the timer expires first:
   * Call `terminal/kill` to terminate the command
   * Call `terminal/output` to retrieve any final output
   * Include the output in the response to the model
5. Call `terminal/release` when done

## [​](#releasing-terminals) Releasing Terminals

The `terminal/release` kills the command if still running and releases all resources:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 9,  "id": 9, "method": "terminal/release",  "method": "terminal/release", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "terminalId": "term_xyz789"  "terminalId": "term_xyz789" } }}}
```

After release the terminal ID becomes invalid for all other `terminal/*` methods. If the terminal was added to a tool call, the client **SHOULD** continue to display its output after release.

Was this page helpful?

[Previous](/protocol/file-system)[Agent PlanHow Agents communicate their execution plans](/protocol/agent-plan)

* [Checking Support](#checking-support)
* [Executing Commands](#executing-commands)
* [Embedding in Tool Calls](#embedding-in-tool-calls)
* [Getting Output](#getting-output)
* [Waiting for Exit](#waiting-for-exit)
* [Killing Commands](#killing-commands)
* [Building a Timeout](#building-a-timeout)
* [Releasing Terminals](#releasing-terminals)
