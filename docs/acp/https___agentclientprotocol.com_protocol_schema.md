# https://agentclientprotocol.com/protocol/schema

Schema - Agent Client Protocol

[Skip to main content](#content-area)

[Agent Client Protocol home page![light logo](https://mintcdn.com/zed-685ed6d6/FgcZrIi8cEeJJGHC/logo/light.svg?fit=max&auto=format&n=FgcZrIi8cEeJJGHC&q=85&s=5cf9119e471543528e40443ba41baf30)![dark logo](https://mintcdn.com/zed-685ed6d6/FgcZrIi8cEeJJGHC/logo/dark.svg?fit=max&auto=format&n=FgcZrIi8cEeJJGHC&q=85&s=ef801d8ed18c55ed6d930fe23a92c719)](/)

Search...

Ctrl K

##### Get Started

* [Introduction](/get-started/introduction)
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

[Agent Client Protocol home page![light logo](https://mintcdn.com/zed-685ed6d6/FgcZrIi8cEeJJGHC/logo/light.svg?fit=max&auto=format&n=FgcZrIi8cEeJJGHC&q=85&s=5cf9119e471543528e40443ba41baf30)![dark logo](https://mintcdn.com/zed-685ed6d6/FgcZrIi8cEeJJGHC/logo/dark.svg?fit=max&auto=format&n=FgcZrIi8cEeJJGHC&q=85&s=ef801d8ed18c55ed6d930fe23a92c719)](/)

Search...

Ctrl K

* [GitHub](https://github.com/agentclientprotocol/agent-client-protocol)
* [Zed Industries](https://zed.dev)
* [JetBrains](https://jetbrains.com)

Search...

Navigation

Protocol

Schema

[Protocol](/get-started/introduction)[RFDs](/rfds/about)[Community](/community/communication)[Updates](/updates)[Brand](/brand)

[Protocol](/get-started/introduction)[RFDs](/rfds/about)[Community](/community/communication)[Updates](/updates)[Brand](/brand)

Protocol

# Schema

Copy page

Schema definitions for the Agent Client Protocol

Copy page

## [​](#agent) Agent

Defines the interface that all ACP-compliant agents must implement.
Agents are programs that use generative AI to autonomously modify code. They handle
requests from clients and execute tasks using language models and tools.

### [​](#authenticate) authenticate

Authenticates the client using the specified authentication method.
Called when the agent requires authentication before allowing session creation.
The client provides the authentication method ID that was advertised during initialization.
After successful authentication, the client can proceed to create sessions with
`new_session` without receiving an `auth_required` error.
See protocol docs: [Initialization](https://agentclientprotocol.com/protocol/initialization)

#### [​](#authenticaterequest) AuthenticateRequest

Request parameters for the authenticate method.
Specifies which authentication method to use.
**Type:** Object
**Properties:**

[​](#param-meta)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-method-id)

methodId

string

required

The ID of the authentication method to use.
Must be one of the methods advertised in the initialize response.

#### [​](#authenticateresponse) AuthenticateResponse

Response to the `authenticate` method.
**Type:** Object
**Properties:**

[​](#param-meta-1)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

### [​](#initialize) initialize

Establishes the connection with a client and negotiates protocol capabilities.
This method is called once at the beginning of the connection to:

* Negotiate the protocol version to use
* Exchange capability information between client and agent
* Determine available authentication methods

The agent should respond with its supported protocol version and capabilities.
See protocol docs: [Initialization](https://agentclientprotocol.com/protocol/initialization)

#### [​](#initializerequest) InitializeRequest

Request parameters for the initialize method.
Sent by the client to establish connection and negotiate capabilities.
See protocol docs: [Initialization](https://agentclientprotocol.com/protocol/initialization)
**Type:** Object
**Properties:**

[​](#param-meta-2)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-client-capabilities)

clientCapabilities

[ClientCapabilities](#clientcapabilities)

Capabilities supported by the client.

* Default: `{"fs":{"readTextFile":false,"writeTextFile":false},"terminal":false}`

[​](#param-client-info)

clientInfo

[Implementation](#implementation) | null

Information about the Client name and version sent to the Agent.Note: in future versions of the protocol, this will be required.

[​](#param-protocol-version)

protocolVersion

[ProtocolVersion](#protocolversion)

required

The latest protocol version supported by the client.

#### [​](#initializeresponse) InitializeResponse

Response to the `initialize` method.
Contains the negotiated protocol version and agent capabilities.
See protocol docs: [Initialization](https://agentclientprotocol.com/protocol/initialization)
**Type:** Object
**Properties:**

[​](#param-meta-3)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-agent-capabilities)

agentCapabilities

[AgentCapabilities](#agentcapabilities)

Capabilities supported by the agent.

* Default: `{"loadSession":false,"mcpCapabilities":{"http":false,"sse":false},"promptCapabilities":{"audio":false,"embeddedContext":false,"image":false},"sessionCapabilities":{}}`

[​](#param-agent-info)

agentInfo

[Implementation](#implementation) | null

Information about the Agent name and version sent to the Client.Note: in future versions of the protocol, this will be required.

[​](#param-auth-methods)

authMethods

[AuthMethod[]](#authmethod)

Authentication methods supported by the agent.

* Default: `[]`

[​](#param-protocol-version-1)

protocolVersion

[ProtocolVersion](#protocolversion)

required

The protocol version the client specified if supported by the agent,
or the latest protocol version supported by the agent.The client should disconnect, if it doesn’t support this version.

### [​](#session/cancel) session/cancel

Cancels ongoing operations for a session.
This is a notification sent by the client to cancel an ongoing prompt turn.
Upon receiving this notification, the Agent SHOULD:

* Stop all language model requests as soon as possible
* Abort all tool call invocations in progress
* Send any pending `session/update` notifications
* Respond to the original `session/prompt` request with `StopReason::Cancelled`

See protocol docs: [Cancellation](https://agentclientprotocol.com/protocol/prompt-turn#cancellation)

#### [​](#cancelnotification) CancelNotification

Notification to cancel ongoing operations for a session.
See protocol docs: [Cancellation](https://agentclientprotocol.com/protocol/prompt-turn#cancellation)
**Type:** Object
**Properties:**

[​](#param-meta-4)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-session-id)

sessionId

[SessionId](#sessionid)

required

The ID of the session to cancel operations for.

### [​](#session/load) session/load

Loads an existing session to resume a previous conversation.
This method is only available if the agent advertises the `loadSession` capability.
The agent should:

* Restore the session context and conversation history
* Connect to the specified MCP servers
* Stream the entire conversation history back to the client via notifications

See protocol docs: [Loading Sessions](https://agentclientprotocol.com/protocol/session-setup#loading-sessions)

#### [​](#loadsessionrequest) LoadSessionRequest

Request parameters for loading an existing session.
Only available if the Agent supports the `loadSession` capability.
See protocol docs: [Loading Sessions](https://agentclientprotocol.com/protocol/session-setup#loading-sessions)
**Type:** Object
**Properties:**

[​](#param-meta-5)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-cwd)

cwd

string

required

The working directory for this session.

[​](#param-mcp-servers)

mcpServers

[McpServer[]](#mcpserver)

required

List of MCP servers to connect to for this session.

[​](#param-session-id-1)

sessionId

[SessionId](#sessionid)

required

The ID of the session to load.

#### [​](#loadsessionresponse) LoadSessionResponse

Response from loading an existing session.
**Type:** Object
**Properties:**

[​](#param-meta-6)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-modes)

modes

[SessionModeState](#sessionmodestate) | null

Initial mode state if supported by the AgentSee protocol docs: [Session Modes](https://agentclientprotocol.com/protocol/session-modes)

### [​](#session/new) session/new

Creates a new conversation session with the agent.
Sessions represent independent conversation contexts with their own history and state.
The agent should:

* Create a new session context
* Connect to any specified MCP servers
* Return a unique session ID for future requests

May return an `auth_required` error if the agent requires authentication.
See protocol docs: [Session Setup](https://agentclientprotocol.com/protocol/session-setup)

#### [​](#newsessionrequest) NewSessionRequest

Request parameters for creating a new session.
See protocol docs: [Creating a Session](https://agentclientprotocol.com/protocol/session-setup#creating-a-session)
**Type:** Object
**Properties:**

[​](#param-meta-7)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-cwd-1)

cwd

string

required

The working directory for this session. Must be an absolute path.

[​](#param-mcp-servers-1)

mcpServers

[McpServer[]](#mcpserver)

required

List of MCP (Model Context Protocol) servers the agent should connect to.

#### [​](#newsessionresponse) NewSessionResponse

Response from creating a new session.
See protocol docs: [Creating a Session](https://agentclientprotocol.com/protocol/session-setup#creating-a-session)
**Type:** Object
**Properties:**

[​](#param-meta-8)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-modes-1)

modes

[SessionModeState](#sessionmodestate) | null

Initial mode state if supported by the AgentSee protocol docs: [Session Modes](https://agentclientprotocol.com/protocol/session-modes)

[​](#param-session-id-2)

sessionId

[SessionId](#sessionid)

required

Unique identifier for the created session.Used in all subsequent requests for this conversation.

### [​](#session/prompt) session/prompt

Processes a user prompt within a session.
This method handles the whole lifecycle of a prompt:

* Receives user messages with optional context (files, images, etc.)
* Processes the prompt using language models
* Reports language model content and tool calls to the Clients
* Requests permission to run tools
* Executes any requested tool calls
* Returns when the turn is complete with a stop reason

See protocol docs: [Prompt Turn](https://agentclientprotocol.com/protocol/prompt-turn)

#### [​](#promptrequest) PromptRequest

Request parameters for sending a user prompt to the agent.
Contains the user’s message and any additional context.
See protocol docs: [User Message](https://agentclientprotocol.com/protocol/prompt-turn#1-user-message)
**Type:** Object
**Properties:**

[​](#param-meta-9)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-prompt)

prompt

[ContentBlock[]](#contentblock)

required

The blocks of content that compose the user’s message.As a baseline, the Agent MUST support `ContentBlock::Text` and `ContentBlock::ResourceLink`,
while other variants are optionally enabled via `PromptCapabilities`.The Client MUST adapt its interface according to `PromptCapabilities`.The client MAY include referenced pieces of context as either
`ContentBlock::Resource` or `ContentBlock::ResourceLink`.When available, `ContentBlock::Resource` is preferred
as it avoids extra round-trips and allows the message to include
pieces of context from sources the agent may not have access to.

[​](#param-session-id-3)

sessionId

[SessionId](#sessionid)

required

The ID of the session to send this user message to

#### [​](#promptresponse) PromptResponse

Response from processing a user prompt.
See protocol docs: [Check for Completion](https://agentclientprotocol.com/protocol/prompt-turn#4-check-for-completion)
**Type:** Object
**Properties:**

[​](#param-meta-10)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-stop-reason)

stopReason

[StopReason](#stopreason)

required

Indicates why the agent stopped processing the turn.

### [​](#session/set_mode) session/set\_mode

Sets the current mode for a session.
Allows switching between different agent modes (e.g., “ask”, “architect”, “code”)
that affect system prompts, tool availability, and permission behaviors.
The mode must be one of the modes advertised in `availableModes` during session
creation or loading. Agents may also change modes autonomously and notify the
client via `current_mode_update` notifications.
This method can be called at any time during a session, whether the Agent is
idle or actively generating a response.
See protocol docs: [Session Modes](https://agentclientprotocol.com/protocol/session-modes)

#### [​](#setsessionmoderequest) SetSessionModeRequest

Request parameters for setting a session mode.
**Type:** Object
**Properties:**

[​](#param-meta-11)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-mode-id)

modeId

[SessionModeId](#sessionmodeid)

required

The ID of the mode to set.

[​](#param-session-id-4)

sessionId

[SessionId](#sessionid)

required

The ID of the session to set the mode for.

#### [​](#setsessionmoderesponse) SetSessionModeResponse

Response to `session/set_mode` method.
**Type:** Object
**Properties:**

[​](#param-meta-12)

\_meta

object | null

## [​](#client) Client

Defines the interface that ACP-compliant clients must implement.
Clients are typically code editors (IDEs, text editors) that provide the interface
between users and AI agents. They manage the environment, handle user interactions,
and control access to resources.


### [​](#fs/read_text_file) fs/read\_text\_file

Reads content from a text file in the client’s file system.
Only available if the client advertises the `fs.readTextFile` capability.
Allows the agent to access file contents within the client’s environment.
See protocol docs: [Client](https://agentclientprotocol.com/protocol/overview#client)

#### [​](#readtextfilerequest) ReadTextFileRequest

Request to read content from a text file.
Only available if the client supports the `fs.readTextFile` capability.
**Type:** Object
**Properties:**

[​](#param-meta-13)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-limit)

limit

integer | null

Maximum number of lines to read.

* Minimum: `0`

[​](#param-line)

line

integer | null

Line number to start reading from (1-based).

* Minimum: `0`

[​](#param-path)

path

string

required

Absolute path to the file to read.

[​](#param-session-id-5)

sessionId

[SessionId](#sessionid)

required

The session ID for this request.

#### [​](#readtextfileresponse) ReadTextFileResponse

Response containing the contents of a text file.
**Type:** Object
**Properties:**

[​](#param-meta-14)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-content)

content

string

required

### [​](#fs/write_text_file) fs/write\_text\_file

Writes content to a text file in the client’s file system.
Only available if the client advertises the `fs.writeTextFile` capability.
Allows the agent to create or modify files within the client’s environment.
See protocol docs: [Client](https://agentclientprotocol.com/protocol/overview#client)

#### [​](#writetextfilerequest) WriteTextFileRequest

Request to write content to a text file.
Only available if the client supports the `fs.writeTextFile` capability.
**Type:** Object
**Properties:**

[​](#param-meta-15)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-content-1)

content

string

required

The text content to write to the file.

[​](#param-path-1)

path

string

required

Absolute path to the file to write.

[​](#param-session-id-6)

sessionId

[SessionId](#sessionid)

required

The session ID for this request.

#### [​](#writetextfileresponse) WriteTextFileResponse

Response to `fs/write_text_file`
**Type:** Object
**Properties:**

[​](#param-meta-16)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

### [​](#session/request_permission) session/request\_permission

Requests permission from the user for a tool call operation.
Called by the agent when it needs user authorization before executing
a potentially sensitive operation. The client should present the options
to the user and return their decision.
If the client cancels the prompt turn via `session/cancel`, it MUST
respond to this request with `RequestPermissionOutcome::Cancelled`.
See protocol docs: [Requesting Permission](https://agentclientprotocol.com/protocol/tool-calls#requesting-permission)

#### [​](#requestpermissionrequest) RequestPermissionRequest

Request for user permission to execute a tool call.
Sent when the agent needs authorization before performing a sensitive operation.
See protocol docs: [Requesting Permission](https://agentclientprotocol.com/protocol/tool-calls#requesting-permission)
**Type:** Object
**Properties:**

[​](#param-meta-17)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-options)

options

[PermissionOption[]](#permissionoption)

required

Available permission options for the user to choose from.

[​](#param-session-id-7)

sessionId

[SessionId](#sessionid)

required

The session ID for this request.

[​](#param-tool-call)

toolCall

[ToolCallUpdate](#toolcallupdate)

required

Details about the tool call requiring permission.

#### [​](#requestpermissionresponse) RequestPermissionResponse

Response to a permission request.
**Type:** Object
**Properties:**

[​](#param-meta-18)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-outcome)

outcome

[RequestPermissionOutcome](#requestpermissionoutcome)

required

The user’s decision on the permission request.

### [​](#session/update) session/update

Handles session update notifications from the agent.
This is a notification endpoint (no response expected) that receives
real-time updates about session progress, including message chunks,
tool calls, and execution plans.
Note: Clients SHOULD continue accepting tool call updates even after
sending a `session/cancel` notification, as the agent may send final
updates before responding with the cancelled stop reason.
See protocol docs: [Agent Reports Output](https://agentclientprotocol.com/protocol/prompt-turn#3-agent-reports-output)

#### [​](#sessionnotification) SessionNotification

Notification containing a session update from the agent.
Used to stream real-time progress and results during prompt processing.
See protocol docs: [Agent Reports Output](https://agentclientprotocol.com/protocol/prompt-turn#3-agent-reports-output)
**Type:** Object
**Properties:**

[​](#param-meta-19)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-session-id-8)

sessionId

[SessionId](#sessionid)

required

The ID of the session this update pertains to.

[​](#param-update)

update

[SessionUpdate](#sessionupdate)

required

The actual update content.

### [​](#terminal/create) terminal/create

Executes a command in a new terminal
Only available if the `terminal` Client capability is set to `true`.
Returns a `TerminalId` that can be used with other terminal methods
to get the current output, wait for exit, and kill the command.
The `TerminalId` can also be used to embed the terminal in a tool call
by using the `ToolCallContent::Terminal` variant.
The Agent is responsible for releasing the terminal by using the `terminal/release`
method.
See protocol docs: [Terminals](https://agentclientprotocol.com/protocol/terminals)

#### [​](#createterminalrequest) CreateTerminalRequest

Request to create a new terminal and execute a command.
**Type:** Object
**Properties:**

[​](#param-meta-20)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-args)

args

"string"[]

Array of command arguments.

[​](#param-command)

command

string

required

The command to execute.

[​](#param-cwd-2)

cwd

string | null

Working directory for the command (absolute path).

[​](#param-env)

env

[EnvVariable[]](#envvariable)

Environment variables for the command.

[​](#param-output-byte-limit)

outputByteLimit

integer | null

Maximum number of output bytes to retain.When the limit is exceeded, the Client truncates from the beginning of the output
to stay within the limit.The Client MUST ensure truncation happens at a character boundary to maintain valid
string output, even if this means the retained output is slightly less than the
specified limit.

* Minimum: `0`

[​](#param-session-id-9)

sessionId

[SessionId](#sessionid)

required

The session ID for this request.

#### [​](#createterminalresponse) CreateTerminalResponse

Response containing the ID of the created terminal.
**Type:** Object
**Properties:**

[​](#param-meta-21)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-terminal-id)

terminalId

string

required

The unique identifier for the created terminal.

### [​](#terminal/kill) terminal/kill

Kills the terminal command without releasing the terminal
While `terminal/release` will also kill the command, this method will keep
the `TerminalId` valid so it can be used with other methods.
This method can be helpful when implementing command timeouts which terminate
the command as soon as elapsed, and then get the final output so it can be sent
to the model.
Note: `terminal/release` when `TerminalId` is no longer needed.
See protocol docs: [Terminals](https://agentclientprotocol.com/protocol/terminals)

#### [​](#killterminalcommandrequest) KillTerminalCommandRequest

Request to kill a terminal command without releasing the terminal.
**Type:** Object
**Properties:**

[​](#param-meta-22)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-session-id-10)

sessionId

[SessionId](#sessionid)

required

The session ID for this request.

[​](#param-terminal-id-1)

terminalId

string

required

The ID of the terminal to kill.

#### [​](#killterminalcommandresponse) KillTerminalCommandResponse

Response to terminal/kill command method
**Type:** Object
**Properties:**

[​](#param-meta-23)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

### [​](#terminal/output) terminal/output

Gets the terminal output and exit status
Returns the current content in the terminal without waiting for the command to exit.
If the command has already exited, the exit status is included.
See protocol docs: [Terminals](https://agentclientprotocol.com/protocol/terminals)

#### [​](#terminaloutputrequest) TerminalOutputRequest

Request to get the current output and status of a terminal.
**Type:** Object
**Properties:**

[​](#param-meta-24)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-session-id-11)

sessionId

[SessionId](#sessionid)

required

The session ID for this request.

[​](#param-terminal-id-2)

terminalId

string

required

The ID of the terminal to get output from.

#### [​](#terminaloutputresponse) TerminalOutputResponse

Response containing the terminal output and exit status.
**Type:** Object
**Properties:**

[​](#param-meta-25)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-exit-status)

exitStatus

[TerminalExitStatus](#terminalexitstatus) | null

Exit status if the command has completed.

[​](#param-output)

output

string

required

The terminal output captured so far.

[​](#param-truncated)

truncated

boolean

required

Whether the output was truncated due to byte limits.

### [​](#terminal/release) terminal/release

Releases a terminal
The command is killed if it hasn’t exited yet. Use `terminal/wait_for_exit`
to wait for the command to exit before releasing the terminal.
After release, the `TerminalId` can no longer be used with other `terminal/*` methods,
but tool calls that already contain it, continue to display its output.
The `terminal/kill` method can be used to terminate the command without releasing
the terminal, allowing the Agent to call `terminal/output` and other methods.
See protocol docs: [Terminals](https://agentclientprotocol.com/protocol/terminals)

#### [​](#releaseterminalrequest) ReleaseTerminalRequest

Request to release a terminal and free its resources.
**Type:** Object
**Properties:**

[​](#param-meta-26)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-session-id-12)

sessionId

[SessionId](#sessionid)

required

The session ID for this request.

[​](#param-terminal-id-3)

terminalId

string

required

The ID of the terminal to release.

#### [​](#releaseterminalresponse) ReleaseTerminalResponse

Response to terminal/release method
**Type:** Object
**Properties:**

[​](#param-meta-27)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

### [​](#terminal/wait_for_exit) terminal/wait\_for\_exit

Waits for the terminal command to exit and return its exit status
See protocol docs: [Terminals](https://agentclientprotocol.com/protocol/terminals)

#### [​](#waitforterminalexitrequest) WaitForTerminalExitRequest

Request to wait for a terminal command to exit.
**Type:** Object
**Properties:**

[​](#param-meta-28)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-session-id-13)

sessionId

[SessionId](#sessionid)

required

The session ID for this request.

[​](#param-terminal-id-4)

terminalId

string

required

The ID of the terminal to wait for.

#### [​](#waitforterminalexitresponse) WaitForTerminalExitResponse

Response containing the exit status of a terminal command.
**Type:** Object
**Properties:**

[​](#param-meta-29)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-exit-code)

exitCode

integer | null

The process exit code (may be null if terminated by signal).

* Minimum: `0`

[​](#param-signal)

signal

string | null

The signal that terminated the process (may be null if exited normally).

## [​](#agentcapabilities) AgentCapabilities

Capabilities supported by the agent.
Advertised during initialization to inform the client about
available features and content types.
See protocol docs: [Agent Capabilities](https://agentclientprotocol.com/protocol/initialization#agent-capabilities)
**Type:** Object
**Properties:**

[​](#param-meta-30)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-load-session)

loadSession

boolean

Whether the agent supports `session/load`.

* Default: `false`

[​](#param-mcp-capabilities)

mcpCapabilities

[McpCapabilities](#mcpcapabilities)

MCP capabilities supported by the agent.

* Default: `{"http":false,"sse":false}`

[​](#param-prompt-capabilities)

promptCapabilities

[PromptCapabilities](#promptcapabilities)

Prompt capabilities supported by the agent.

* Default: `{"audio":false,"embeddedContext":false,"image":false}`

[​](#param-session-capabilities)

sessionCapabilities

[SessionCapabilities](#sessioncapabilities)

* Default: `{}`

## [​](#annotations) Annotations

Optional annotations for the client. The client can use annotations to inform how objects are used or displayed
**Type:** Object
**Properties:**

[​](#param-meta-31)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-audience)

audience

[Role[]](#role) | null

[​](#param-last-modified)

lastModified

string | null

[​](#param-priority)

priority

number | null

## [​](#audiocontent) AudioContent

Audio provided to or from an LLM.
**Type:** Object
**Properties:**

[​](#param-meta-32)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-annotations)

annotations

[Annotations](#annotations) | null

[​](#param-data)

data

string

required

[​](#param-mime-type)

mimeType

string

required

## [​](#authmethod) AuthMethod

Describes an available authentication method.
**Type:** Object
**Properties:**

[​](#param-meta-33)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-description)

description

string | null

Optional description providing more details about this authentication method.

[​](#param-id)

id

string

required

Unique identifier for this authentication method.

[​](#param-name)

name

string

required

Human-readable name of the authentication method.

## [​](#availablecommand) AvailableCommand

Information about a command.
**Type:** Object
**Properties:**

[​](#param-meta-34)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-description-1)

description

string

required

Human-readable description of what the command does.

[​](#param-input)

input

[AvailableCommandInput](#availablecommandinput) | null

Input for the command if required

[​](#param-name-1)

name

string

required

Command name (e.g., `create_plan`, `research_codebase`).

## [​](#availablecommandinput) AvailableCommandInput

The input specification for a command.
**Type:** Union

[​](#param-unstructured)

unstructured

All text that was typed after the command name is provided as input.

Show Properties

[​](#param-meta-35)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-hint)

hint

string

required

A hint to display when the input hasn’t been provided yet

## [​](#availablecommandsupdate) AvailableCommandsUpdate

Available commands are ready or have changed
**Type:** Object
**Properties:**

[​](#param-meta-36)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-available-commands)

availableCommands

[AvailableCommand[]](#availablecommand)

required

Commands the agent can execute

## [​](#blobresourcecontents) BlobResourceContents

Binary resource contents.
**Type:** Object
**Properties:**

[​](#param-meta-37)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-blob)

blob

string

required

[​](#param-mime-type-1)

mimeType

string | null

[​](#param-uri)

uri

string

required

## [​](#clientcapabilities) ClientCapabilities

Capabilities supported by the client.
Advertised during initialization to inform the agent about
available features and methods.
See protocol docs: [Client Capabilities](https://agentclientprotocol.com/protocol/initialization#client-capabilities)
**Type:** Object
**Properties:**

[​](#param-meta-38)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-fs)

fs

[FileSystemCapability](#filesystemcapability)

File system capabilities supported by the client.
Determines which file operations the agent can request.

* Default: `{"readTextFile":false,"writeTextFile":false}`

[​](#param-terminal)

terminal

boolean

Whether the Client support all `terminal/*` methods.

* Default: `false`

## [​](#content) Content

Standard content block (text, images, resources).
**Type:** Object
**Properties:**

[​](#param-meta-39)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-content-2)

content

[ContentBlock](#contentblock)

required

The actual content block.

## [​](#contentblock) ContentBlock

Content blocks represent displayable information in the Agent Client Protocol.
They provide a structured way to handle various types of user-facing content—whether
it’s text from language models, images for analysis, or embedded resources for context.
Content blocks appear in:

* User prompts sent via `session/prompt`
* Language model output streamed through `session/update` notifications
* Progress updates and results from tool calls

This structure is compatible with the Model Context Protocol (MCP), enabling
agents to seamlessly forward content from MCP tool outputs without transformation.
See protocol docs: [Content](https://agentclientprotocol.com/protocol/content)
**Type:** Union

[​](#param-text)

text

object

Text content. May be plain text or formatted with Markdown.All agents MUST support text content blocks in prompts.
Clients SHOULD render this text as Markdown.

Show Properties

[​](#param-meta-40)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-annotations-1)

annotations

[Annotations](#annotations) | null

[​](#param-text-1)

text

string

required

[​](#param-type)

type

string

required

[​](#param-image)

image

object

Images for visual context or analysis.Requires the `image` prompt capability when included in prompts.

Show Properties

[​](#param-meta-41)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-annotations-2)

annotations

[Annotations](#annotations) | null

[​](#param-data-1)

data

string

required

[​](#param-mime-type-2)

mimeType

string

required

[​](#param-type-1)

type

string

required

[​](#param-uri-1)

uri

string | null

[​](#param-audio)

audio

object

Audio data for transcription or analysis.Requires the `audio` prompt capability when included in prompts.

Show Properties

[​](#param-meta-42)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-annotations-3)

annotations

[Annotations](#annotations) | null

[​](#param-data-2)

data

string

required

[​](#param-mime-type-3)

mimeType

string

required

[​](#param-type-2)

type

string

required

[​](#param-resource-link)

resource\_link

object

References to resources that the agent can access.All agents MUST support resource links in prompts.

Show Properties

[​](#param-meta-43)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-annotations-4)

annotations

[Annotations](#annotations) | null

[​](#param-description-2)

description

string | null

[​](#param-mime-type-4)

mimeType

string | null

[​](#param-name-2)

name

string

required

[​](#param-size)

size

integer | null

[​](#param-title)

title

string | null

[​](#param-type-3)

type

string

required

[​](#param-uri-2)

uri

string

required

[​](#param-resource)

resource

object

Complete resource contents embedded directly in the message.Preferred for including context as it avoids extra round-trips.Requires the `embeddedContext` prompt capability when included in prompts.

Show Properties

[​](#param-meta-44)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-annotations-5)

annotations

[Annotations](#annotations) | null

[​](#param-resource-1)

resource

[EmbeddedResourceResource](#embeddedresourceresource)

required

[​](#param-type-4)

type

string

required

## [​](#contentchunk) ContentChunk

A streamed item of content
**Type:** Object
**Properties:**

[​](#param-meta-45)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-content-3)

content

[ContentBlock](#contentblock)

required

A single item of content

## [​](#currentmodeupdate) CurrentModeUpdate

The current mode of the session has changed
See protocol docs: [Session Modes](https://agentclientprotocol.com/protocol/session-modes)
**Type:** Object
**Properties:**

[​](#param-meta-46)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-current-mode-id)

currentModeId

[SessionModeId](#sessionmodeid)

required

The ID of the current mode

## [​](#diff) Diff

A diff representing file modifications.
Shows changes to files in a format suitable for display in the client UI.
See protocol docs: [Content](https://agentclientprotocol.com/protocol/tool-calls#content)
**Type:** Object
**Properties:**

[​](#param-meta-47)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-new-text)

newText

string

required

The new content after modification.

[​](#param-old-text)

oldText

string | null

The original content (None for new files).

[​](#param-path-2)

path

string

required

The file path being modified.

## [​](#embeddedresource) EmbeddedResource

The contents of a resource, embedded into a prompt or tool call result.
**Type:** Object
**Properties:**

[​](#param-meta-48)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-annotations-6)

annotations

[Annotations](#annotations) | null

[​](#param-resource-2)

resource

[EmbeddedResourceResource](#embeddedresourceresource)

required

## [​](#embeddedresourceresource) EmbeddedResourceResource

Resource content that can be embedded in a message.
**Type:** Union

[​](#param-text-resource-contents)

TextResourceContents

Show Properties

[​](#param-meta-49)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-mime-type-5)

mimeType

string | null

[​](#param-text-2)

text

string

required

[​](#param-uri-3)

uri

string

required

[​](#param-blob-resource-contents)

BlobResourceContents

Show Properties

[​](#param-meta-50)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-blob-1)

blob

string

required

[​](#param-mime-type-6)

mimeType

string | null

[​](#param-uri-4)

uri

string

required

## [​](#envvariable) EnvVariable

An environment variable to set when launching an MCP server.
**Type:** Object
**Properties:**

[​](#param-meta-51)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-name-3)

name

string

required

The name of the environment variable.

[​](#param-value)

value

string

required

The value to set for the environment variable.

## [​](#error) Error

JSON-RPC error object.
Represents an error that occurred during method execution, following the
JSON-RPC 2.0 error object specification with optional additional data.
See protocol docs: [JSON-RPC Error Object](https://www.jsonrpc.org/specification#error_object)
**Type:** Object
**Properties:**

[​](#param-code)

code

[ErrorCode](#errorcode)

required

A number indicating the error type that occurred. This must be an integer as
defined in the JSON-RPC specification.

[​](#param-data-3)

data

object

Optional primitive or structured value that contains additional information
about the error. This may include debugging information or context-specific
details.

[​](#param-message)

message

string

required

A string providing a short description of the error. The message should be
limited to a concise single sentence.

## [​](#errorcode) ErrorCode

Predefined error codes for common JSON-RPC and ACP-specific errors.
These codes follow the JSON-RPC 2.0 specification for standard errors
and use the reserved range (-32000 to -32099) for protocol-specific errors.
**Type:** Union

[​](#param-32700)

-32700

int32

**Parse error**: Invalid JSON was received by the server. An error occurred on
the server while parsing the JSON text.

[​](#param-32600)

-32600

int32

**Invalid request**: The JSON sent is not a valid Request object.

[​](#param-32601)

-32601

int32

**Method not found**: The method does not exist or is not available.

[​](#param-32602)

-32602

int32

**Invalid params**: Invalid method parameter(s).

[​](#param-32603)

-32603

int32

**Internal error**: Internal JSON-RPC error. Reserved for
implementation-defined server errors.

[​](#param-32000)

-32000

int32

**Authentication required**: Authentication is required before this operation
can be performed.

[​](#param-32002)

-32002

int32

**Resource not found**: A given resource, such as a file, was not found.

[​](#param-other)

Other

int32

Other undefined error code.

## [​](#extnotification) ExtNotification

Allows the Agent to send an arbitrary notification that is not part of the ACP spec.
Extension notifications provide a way to send one-way messages for custom functionality
while maintaining protocol compatibility.
See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

## [​](#extrequest) ExtRequest

Allows for sending an arbitrary request that is not part of the ACP spec.
Extension methods provide a way to add custom functionality while maintaining
protocol compatibility.
See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

## [​](#extresponse) ExtResponse

Allows for sending an arbitrary response to an `ExtRequest` that is not part of the ACP spec.
Extension methods provide a way to add custom functionality while maintaining
protocol compatibility.
See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

## [​](#filesystemcapability) FileSystemCapability

Filesystem capabilities supported by the client.
File system capabilities that a client may support.
See protocol docs: [FileSystem](https://agentclientprotocol.com/protocol/initialization#filesystem)
**Type:** Object
**Properties:**

[​](#param-meta-52)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-read-text-file)

readTextFile

boolean

Whether the Client supports `fs/read_text_file` requests.

* Default: `false`

[​](#param-write-text-file)

writeTextFile

boolean

Whether the Client supports `fs/write_text_file` requests.

* Default: `false`

## [​](#httpheader) HttpHeader

An HTTP header to set when making requests to the MCP server.
**Type:** Object
**Properties:**

[​](#param-meta-53)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-name-4)

name

string

required

The name of the HTTP header.

[​](#param-value-1)

value

string

required

The value to set for the HTTP header.

## [​](#imagecontent) ImageContent

An image provided to or from an LLM.
**Type:** Object
**Properties:**

[​](#param-meta-54)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-annotations-7)

annotations

[Annotations](#annotations) | null

[​](#param-data-4)

data

string

required

[​](#param-mime-type-7)

mimeType

string

required

[​](#param-uri-5)

uri

string | null

## [​](#implementation) Implementation

Metadata about the implementation of the client or agent.
Describes the name and version of an MCP implementation, with an optional
title for UI representation.
**Type:** Object
**Properties:**

[​](#param-meta-55)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-name-5)

name

string

required

Intended for programmatic or logical use, but can be used as a display
name fallback if title isn’t present.

[​](#param-title-1)

title

string | null

Intended for UI and end-user contexts — optimized to be human-readable
and easily understood.If not provided, the name should be used for display.

[​](#param-version)

version

string

required

Version of the implementation. Can be displayed to the user or used
for debugging or metrics purposes. (e.g. “1.0.0”).

## [​](#mcpcapabilities) McpCapabilities

MCP capabilities supported by the agent
**Type:** Object
**Properties:**

[​](#param-meta-56)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-http)

http

boolean

Agent supports `McpServer::Http`.

* Default: `false`

[​](#param-sse)

sse

boolean

Agent supports `McpServer::Sse`.

* Default: `false`

## [​](#mcpserver) McpServer

Configuration for connecting to an MCP (Model Context Protocol) server.
MCP servers provide tools and context that the agent can use when
processing prompts.
See protocol docs: [MCP Servers](https://agentclientprotocol.com/protocol/session-setup#mcp-servers)
**Type:** Union

[​](#param-http-1)

http

object

HTTP transport configurationOnly available when the Agent capabilities indicate `mcp_capabilities.http` is `true`.

Show Properties

[​](#param-meta-57)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-headers)

headers

[HttpHeader[]](#httpheader)

required

HTTP headers to set when making requests to the MCP server.

[​](#param-name-6)

name

string

required

Human-readable name identifying this MCP server.

[​](#param-type-5)

type

string

required

[​](#param-url)

url

string

required

URL to the MCP server.

[​](#param-sse-1)

sse

object

SSE transport configurationOnly available when the Agent capabilities indicate `mcp_capabilities.sse` is `true`.

Show Properties

[​](#param-meta-58)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-headers-1)

headers

[HttpHeader[]](#httpheader)

required

HTTP headers to set when making requests to the MCP server.

[​](#param-name-7)

name

string

required

Human-readable name identifying this MCP server.

[​](#param-type-6)

type

string

required

[​](#param-url-1)

url

string

required

URL to the MCP server.

[​](#param-stdio)

stdio

Stdio transport configurationAll Agents MUST support this transport.

Show Properties

[​](#param-meta-59)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-args-1)

args

"string"[]

required

Command-line arguments to pass to the MCP server.

[​](#param-command-1)

command

string

required

Path to the MCP server executable.

[​](#param-env-1)

env

[EnvVariable[]](#envvariable)

required

Environment variables to set when launching the MCP server.

[​](#param-name-8)

name

string

required

Human-readable name identifying this MCP server.

## [​](#mcpserverhttp) McpServerHttp

HTTP transport configuration for MCP.
**Type:** Object
**Properties:**

[​](#param-meta-60)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-headers-2)

headers

[HttpHeader[]](#httpheader)

required

HTTP headers to set when making requests to the MCP server.

[​](#param-name-9)

name

string

required

Human-readable name identifying this MCP server.

[​](#param-url-2)

url

string

required

URL to the MCP server.

## [​](#mcpserversse) McpServerSse

SSE transport configuration for MCP.
**Type:** Object
**Properties:**

[​](#param-meta-61)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-headers-3)

headers

[HttpHeader[]](#httpheader)

required

HTTP headers to set when making requests to the MCP server.

[​](#param-name-10)

name

string

required

Human-readable name identifying this MCP server.

[​](#param-url-3)

url

string

required

URL to the MCP server.

## [​](#mcpserverstdio) McpServerStdio

Stdio transport configuration for MCP.
**Type:** Object
**Properties:**

[​](#param-meta-62)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-args-2)

args

"string"[]

required

Command-line arguments to pass to the MCP server.

[​](#param-command-2)

command

string

required

Path to the MCP server executable.

[​](#param-env-2)

env

[EnvVariable[]](#envvariable)

required

Environment variables to set when launching the MCP server.

[​](#param-name-11)

name

string

required

Human-readable name identifying this MCP server.

## [​](#permissionoption) PermissionOption

An option presented to the user when requesting permission.
**Type:** Object
**Properties:**

[​](#param-meta-63)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-kind)

kind

[PermissionOptionKind](#permissionoptionkind)

required

Hint about the nature of this permission option.

[​](#param-name-12)

name

string

required

Human-readable label to display to the user.

[​](#param-option-id)

optionId

[PermissionOptionId](#permissionoptionid)

required

Unique identifier for this permission option.

## [​](#permissionoptionid) PermissionOptionId

Unique identifier for a permission option.
**Type:** `string`

## [​](#permissionoptionkind) PermissionOptionKind

The type of permission option being presented to the user.
Helps clients choose appropriate icons and UI treatment.
**Type:** Union

[​](#param-allow-once)

allow\_once

string

Allow this operation only this time.

[​](#param-allow-always)

allow\_always

string

Allow this operation and remember the choice.

[​](#param-reject-once)

reject\_once

string

Reject this operation only this time.

[​](#param-reject-always)

reject\_always

string

Reject this operation and remember the choice.

## [​](#plan) Plan

An execution plan for accomplishing complex tasks.
Plans consist of multiple entries representing individual tasks or goals.
Agents report plans to clients to provide visibility into their execution strategy.
Plans can evolve during execution as the agent discovers new requirements or completes tasks.
See protocol docs: [Agent Plan](https://agentclientprotocol.com/protocol/agent-plan)
**Type:** Object
**Properties:**

[​](#param-meta-64)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-entries)

entries

[PlanEntry[]](#planentry)

required

The list of tasks to be accomplished.When updating a plan, the agent must send a complete list of all entries
with their current status. The client replaces the entire plan with each update.

## [​](#planentry) PlanEntry

A single entry in the execution plan.
Represents a task or goal that the assistant intends to accomplish
as part of fulfilling the user’s request.
See protocol docs: [Plan Entries](https://agentclientprotocol.com/protocol/agent-plan#plan-entries)
**Type:** Object
**Properties:**

[​](#param-meta-65)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-content-4)

content

string

required

Human-readable description of what this task aims to accomplish.

[​](#param-priority-1)

priority

[PlanEntryPriority](#planentrypriority)

required

The relative importance of this task.
Used to indicate which tasks are most critical to the overall goal.

[​](#param-status)

status

[PlanEntryStatus](#planentrystatus)

required

Current execution status of this task.

## [​](#planentrypriority) PlanEntryPriority

Priority levels for plan entries.
Used to indicate the relative importance or urgency of different
tasks in the execution plan.
See protocol docs: [Plan Entries](https://agentclientprotocol.com/protocol/agent-plan#plan-entries)
**Type:** Union

[​](#param-high)

high

string

High priority task - critical to the overall goal.

[​](#param-medium)

medium

string

Medium priority task - important but not critical.

[​](#param-low)

low

string

Low priority task - nice to have but not essential.

## [​](#planentrystatus) PlanEntryStatus

Status of a plan entry in the execution flow.
Tracks the lifecycle of each task from planning through completion.
See protocol docs: [Plan Entries](https://agentclientprotocol.com/protocol/agent-plan#plan-entries)
**Type:** Union

[​](#param-pending)

pending

string

The task has not started yet.

[​](#param-in-progress)

in\_progress

string

The task is currently being worked on.

[​](#param-completed)

completed

string

The task has been successfully completed.

## [​](#promptcapabilities) PromptCapabilities

Prompt capabilities supported by the agent in `session/prompt` requests.
Baseline agent functionality requires support for `ContentBlock::Text`
and `ContentBlock::ResourceLink` in prompt requests.
Other variants must be explicitly opted in to.
Capabilities for different types of content in prompt requests.
Indicates which content types beyond the baseline (text and resource links)
the agent can process.
See protocol docs: [Prompt Capabilities](https://agentclientprotocol.com/protocol/initialization#prompt-capabilities)
**Type:** Object
**Properties:**

[​](#param-meta-66)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-audio-1)

audio

boolean

Agent supports `ContentBlock::Audio`.

* Default: `false`

[​](#param-embedded-context)

embeddedContext

boolean

Agent supports embedded context in `session/prompt` requests.When enabled, the Client is allowed to include `ContentBlock::Resource`
in prompt requests for pieces of context that are referenced in the message.

* Default: `false`

[​](#param-image-1)

image

boolean

Agent supports `ContentBlock::Image`.

* Default: `false`

## [​](#protocolversion) ProtocolVersion

Protocol version identifier.
This version is only bumped for breaking changes.
Non-breaking changes should be introduced via capabilities.
**Type:** `integer (uint16)`

| Constraint | Value |
| --- | --- |
| Minimum | `0` |
| Maximum | `65535` |

## [​](#requestid) RequestId

JSON RPC Request Id
An identifier established by the Client that MUST contain a String, Number, or NULL value if included. If it is not included it is assumed to be a notification. The value SHOULD normally not be Null [1] and Numbers SHOULD NOT contain fractional parts [2]
The Server MUST reply with the same value in the Response object if included. This member is used to correlate the context between the two objects.
[1] The use of Null as a value for the id member in a Request object is discouraged, because this specification uses a value of Null for Responses with an unknown id. Also, because JSON-RPC 1.0 uses an id value of Null for Notifications this could cause confusion in handling.
[2] Fractional parts may be problematic, since many decimal fractions cannot be represented exactly as binary fractions.
**Type:** Union

[​](#param-null)

null

null

[​](#param-number)

Number

int64

[​](#param-str)

Str

string

## [​](#requestpermissionoutcome) RequestPermissionOutcome

The outcome of a permission request.
**Type:** Union

[​](#param-cancelled)

cancelled

object

The prompt turn was cancelled before the user responded.When a client sends a `session/cancel` notification to cancel an ongoing
prompt turn, it MUST respond to all pending `session/request_permission`
requests with this `Cancelled` outcome.See protocol docs: [Cancellation](https://agentclientprotocol.com/protocol/prompt-turn#cancellation)

Show Properties

[​](#param-outcome-1)

outcome

string

required

[​](#param-selected)

selected

object

The user selected one of the provided options.

Show Properties

[​](#param-meta-67)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-option-id-1)

optionId

[PermissionOptionId](#permissionoptionid)

required

The ID of the option the user selected.

[​](#param-outcome-2)

outcome

string

required

## [​](#resourcelink) ResourceLink

A resource that the server is capable of reading, included in a prompt or tool call result.
**Type:** Object
**Properties:**

[​](#param-meta-68)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-annotations-8)

annotations

[Annotations](#annotations) | null

[​](#param-description-3)

description

string | null

[​](#param-mime-type-8)

mimeType

string | null

[​](#param-name-13)

name

string

required

[​](#param-size-1)

size

integer | null

[​](#param-title-2)

title

string | null

[​](#param-uri-6)

uri

string

required

## [​](#role) Role

The sender or recipient of messages and data in a conversation.
**Type:** Enumeration

| Value |
| --- |
| `"assistant"` |
| `"user"` |

## [​](#selectedpermissionoutcome) SelectedPermissionOutcome

The user selected one of the provided options.
**Type:** Object
**Properties:**

[​](#param-meta-69)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-option-id-2)

optionId

[PermissionOptionId](#permissionoptionid)

required

The ID of the option the user selected.

## [​](#sessioncapabilities) SessionCapabilities

Session capabilities supported by the agent.
As a baseline, all Agents **MUST** support `session/new`, `session/prompt`, `session/cancel`, and `session/update`.
Optionally, they **MAY** support other session methods and notifications by specifying additional capabilities.
Note: `session/load` is still handled by the top-level `load_session` capability. This will be unified in future versions of the protocol.
See protocol docs: [Session Capabilities](https://agentclientprotocol.com/protocol/initialization#session-capabilities)
**Type:** Object
**Properties:**

[​](#param-meta-70)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

## [​](#sessionid) SessionId

A unique identifier for a conversation session between a client and agent.
Sessions maintain their own context, conversation history, and state,
allowing multiple independent interactions with the same agent.
See protocol docs: [Session ID](https://agentclientprotocol.com/protocol/session-setup#session-id)
**Type:** `string`

## [​](#sessionmode) SessionMode

A mode the agent can operate in.
See protocol docs: [Session Modes](https://agentclientprotocol.com/protocol/session-modes)
**Type:** Object
**Properties:**

[​](#param-meta-71)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-description-4)

description

string | null

[​](#param-id-1)

id

[SessionModeId](#sessionmodeid)

required

[​](#param-name-14)

name

string

required

## [​](#sessionmodeid) SessionModeId

Unique identifier for a Session Mode.
**Type:** `string`

## [​](#sessionmodestate) SessionModeState

The set of modes and the one currently active.
**Type:** Object
**Properties:**

[​](#param-meta-72)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-available-modes)

availableModes

[SessionMode[]](#sessionmode)

required

The set of modes that the Agent can operate in

[​](#param-current-mode-id-1)

currentModeId

[SessionModeId](#sessionmodeid)

required

The current mode the Agent is in.

## [​](#sessionupdate) SessionUpdate

Different types of updates that can be sent during session processing.
These updates provide real-time feedback about the agent’s progress.
See protocol docs: [Agent Reports Output](https://agentclientprotocol.com/protocol/prompt-turn#3-agent-reports-output)
**Type:** Union

[​](#param-user-message-chunk)

user\_message\_chunk

object

A chunk of the user’s message being streamed.

Show Properties

[​](#param-meta-73)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-content-5)

content

[ContentBlock](#contentblock)

required

A single item of content

[​](#param-session-update)

sessionUpdate

string

required

[​](#param-agent-message-chunk)

agent\_message\_chunk

object

A chunk of the agent’s response being streamed.

Show Properties

[​](#param-meta-74)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-content-6)

content

[ContentBlock](#contentblock)

required

A single item of content

[​](#param-session-update-1)

sessionUpdate

string

required

[​](#param-agent-thought-chunk)

agent\_thought\_chunk

object

A chunk of the agent’s internal reasoning being streamed.

Show Properties

[​](#param-meta-75)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-content-7)

content

[ContentBlock](#contentblock)

required

A single item of content

[​](#param-session-update-2)

sessionUpdate

string

required

[​](#param-tool-call)

tool\_call

object

Notification that a new tool call has been initiated.

Show Properties

[​](#param-meta-76)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-content-8)

content

[ToolCallContent[]](#toolcallcontent)

Content produced by the tool call.

[​](#param-kind-1)

kind

[ToolKind](#toolkind)

The category of tool being invoked.
Helps clients choose appropriate icons and UI treatment.

[​](#param-locations)

locations

[ToolCallLocation[]](#toolcalllocation)

File locations affected by this tool call.
Enables “follow-along” features in clients.

[​](#param-raw-input)

rawInput

object

Raw input parameters sent to the tool.

[​](#param-raw-output)

rawOutput

object

Raw output returned by the tool.

[​](#param-session-update-3)

sessionUpdate

string

required

[​](#param-status-1)

status

[ToolCallStatus](#toolcallstatus)

Current execution status of the tool call.

[​](#param-title-3)

title

string

required

Human-readable title describing what the tool is doing.

[​](#param-tool-call-id)

toolCallId

[ToolCallId](#toolcallid)

required

Unique identifier for this tool call within the session.

[​](#param-tool-call-update)

tool\_call\_update

object

Update on the status or results of a tool call.

Show Properties

[​](#param-meta-77)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-content-9)

content

[ToolCallContent[]](#toolcallcontent) | null

Replace the content collection.

[​](#param-kind-2)

kind

[ToolKind](#toolkind) | null

Update the tool kind.

[​](#param-locations-1)

locations

[ToolCallLocation[]](#toolcalllocation) | null

Replace the locations collection.

[​](#param-raw-input-1)

rawInput

object

Update the raw input.

[​](#param-raw-output-1)

rawOutput

object

Update the raw output.

[​](#param-session-update-4)

sessionUpdate

string

required

[​](#param-status-2)

status

[ToolCallStatus](#toolcallstatus) | null

Update the execution status.

[​](#param-title-4)

title

string | null

Update the human-readable title.

[​](#param-tool-call-id-1)

toolCallId

[ToolCallId](#toolcallid)

required

The ID of the tool call being updated.

[​](#param-plan)

plan

object

The agent’s execution plan for complex tasks.
See protocol docs: [Agent Plan](https://agentclientprotocol.com/protocol/agent-plan)

Show Properties

[​](#param-meta-78)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-entries-1)

entries

[PlanEntry[]](#planentry)

required

The list of tasks to be accomplished.When updating a plan, the agent must send a complete list of all entries
with their current status. The client replaces the entire plan with each update.

[​](#param-session-update-5)

sessionUpdate

string

required

[​](#param-available-commands-update)

available\_commands\_update

object

Available commands are ready or have changed

Show Properties

[​](#param-meta-79)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-available-commands-1)

availableCommands

[AvailableCommand[]](#availablecommand)

required

Commands the agent can execute

[​](#param-session-update-6)

sessionUpdate

string

required

[​](#param-current-mode-update)

current\_mode\_update

object

The current mode of the session has changedSee protocol docs: [Session Modes](https://agentclientprotocol.com/protocol/session-modes)

Show Properties

[​](#param-meta-80)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-current-mode-id-2)

currentModeId

[SessionModeId](#sessionmodeid)

required

The ID of the current mode

[​](#param-session-update-7)

sessionUpdate

string

required

## [​](#stopreason) StopReason

Reasons why an agent stops processing a prompt turn.
See protocol docs: [Stop Reasons](https://agentclientprotocol.com/protocol/prompt-turn#stop-reasons)
**Type:** Union

[​](#param-end-turn)

end\_turn

string

The turn ended successfully.

[​](#param-max-tokens)

max\_tokens

string

The turn ended because the agent reached the maximum number of tokens.

[​](#param-max-turn-requests)

max\_turn\_requests

string

The turn ended because the agent reached the maximum number of allowed agent
requests between user turns.

[​](#param-refusal)

refusal

string

The turn ended because the agent refused to continue. The user prompt and
everything that comes after it won’t be included in the next prompt, so this
should be reflected in the UI.

[​](#param-cancelled-1)

cancelled

string

The turn was cancelled by the client via `session/cancel`.This stop reason MUST be returned when the client sends a `session/cancel`
notification, even if the cancellation causes exceptions in underlying operations.
Agents should catch these exceptions and return this semantically meaningful
response to confirm successful cancellation.

## [​](#terminal) Terminal

Embed a terminal created with `terminal/create` by its id.
The terminal must be added before calling `terminal/release`.
See protocol docs: [Terminal](https://agentclientprotocol.com/protocol/terminals)
**Type:** Object
**Properties:**

[​](#param-meta-81)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-terminal-id-5)

terminalId

string

required

## [​](#terminalexitstatus) TerminalExitStatus

Exit status of a terminal command.
**Type:** Object
**Properties:**

[​](#param-meta-82)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-exit-code-1)

exitCode

integer | null

The process exit code (may be null if terminated by signal).

* Minimum: `0`

[​](#param-signal-1)

signal

string | null

The signal that terminated the process (may be null if exited normally).

## [​](#textcontent) TextContent

Text provided to or from an LLM.
**Type:** Object
**Properties:**

[​](#param-meta-83)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-annotations-9)

annotations

[Annotations](#annotations) | null

[​](#param-text-3)

text

string

required

## [​](#textresourcecontents) TextResourceContents

Text-based resource contents.
**Type:** Object
**Properties:**

[​](#param-meta-84)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-mime-type-9)

mimeType

string | null

[​](#param-text-4)

text

string

required

[​](#param-uri-7)

uri

string

required

## [​](#toolcall) ToolCall

Represents a tool call that the language model has requested.
Tool calls are actions that the agent executes on behalf of the language model,
such as reading files, executing code, or fetching data from external sources.
See protocol docs: [Tool Calls](https://agentclientprotocol.com/protocol/tool-calls)
**Type:** Object
**Properties:**

[​](#param-meta-85)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-content-10)

content

[ToolCallContent[]](#toolcallcontent)

Content produced by the tool call.

[​](#param-kind-3)

kind

[ToolKind](#toolkind)

The category of tool being invoked.
Helps clients choose appropriate icons and UI treatment.

[​](#param-locations-2)

locations

[ToolCallLocation[]](#toolcalllocation)

File locations affected by this tool call.
Enables “follow-along” features in clients.

[​](#param-raw-input-2)

rawInput

object

Raw input parameters sent to the tool.

[​](#param-raw-output-2)

rawOutput

object

Raw output returned by the tool.

[​](#param-status-3)

status

[ToolCallStatus](#toolcallstatus)

Current execution status of the tool call.

[​](#param-title-5)

title

string

required

Human-readable title describing what the tool is doing.

[​](#param-tool-call-id-2)

toolCallId

[ToolCallId](#toolcallid)

required

Unique identifier for this tool call within the session.

## [​](#toolcallcontent) ToolCallContent

Content produced by a tool call.
Tool calls can produce different types of content including
standard content blocks (text, images) or file diffs.
See protocol docs: [Content](https://agentclientprotocol.com/protocol/tool-calls#content)
**Type:** Union

[​](#param-content-11)

content

object

Standard content block (text, images, resources).

Show Properties

[​](#param-meta-86)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-content-12)

content

[ContentBlock](#contentblock)

required

The actual content block.

[​](#param-type-7)

type

string

required

[​](#param-diff)

diff

object

File modification shown as a diff.

Show Properties

[​](#param-meta-87)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-new-text-1)

newText

string

required

The new content after modification.

[​](#param-old-text-1)

oldText

string | null

The original content (None for new files).

[​](#param-path-3)

path

string

required

The file path being modified.

[​](#param-type-8)

type

string

required

[​](#param-terminal-1)

terminal

object

Embed a terminal created with `terminal/create` by its id.The terminal must be added before calling `terminal/release`.See protocol docs: [Terminal](https://agentclientprotocol.com/protocol/terminals)

Show Properties

[​](#param-meta-88)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-terminal-id-6)

terminalId

string

required

[​](#param-type-9)

type

string

required

## [​](#toolcallid) ToolCallId

Unique identifier for a tool call within a session.
**Type:** `string`

## [​](#toolcalllocation) ToolCallLocation

A file location being accessed or modified by a tool.
Enables clients to implement “follow-along” features that track
which files the agent is working with in real-time.
See protocol docs: [Following the Agent](https://agentclientprotocol.com/protocol/tool-calls#following-the-agent)
**Type:** Object
**Properties:**

[​](#param-meta-89)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-line-1)

line

integer | null

Optional line number within the file.

* Minimum: `0`

[​](#param-path-4)

path

string

required

The file path being accessed or modified.

## [​](#toolcallstatus) ToolCallStatus

Execution status of a tool call.
Tool calls progress through different statuses during their lifecycle.
See protocol docs: [Status](https://agentclientprotocol.com/protocol/tool-calls#status)
**Type:** Union

[​](#param-pending-1)

pending

string

The tool call hasn’t started running yet because the input is either streaming
or we’re awaiting approval.

[​](#param-in-progress-1)

in\_progress

string

The tool call is currently running.

[​](#param-completed-1)

completed

string

The tool call completed successfully.

[​](#param-failed)

failed

string

The tool call failed with an error.

## [​](#toolcallupdate) ToolCallUpdate

An update to an existing tool call.
Used to report progress and results as tools execute. All fields except
the tool call ID are optional - only changed fields need to be included.
See protocol docs: [Updating](https://agentclientprotocol.com/protocol/tool-calls#updating)
**Type:** Object
**Properties:**

[​](#param-meta-90)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-content-13)

content

[ToolCallContent[]](#toolcallcontent) | null

Replace the content collection.

[​](#param-kind-4)

kind

[ToolKind](#toolkind) | null

Update the tool kind.

[​](#param-locations-3)

locations

[ToolCallLocation[]](#toolcalllocation) | null

Replace the locations collection.

[​](#param-raw-input-3)

rawInput

object

Update the raw input.

[​](#param-raw-output-3)

rawOutput

object

Update the raw output.

[​](#param-status-4)

status

[ToolCallStatus](#toolcallstatus) | null

Update the execution status.

[​](#param-title-6)

title

string | null

Update the human-readable title.

[​](#param-tool-call-id-3)

toolCallId

[ToolCallId](#toolcallid)

required

The ID of the tool call being updated.

## [​](#toolkind) ToolKind

Categories of tools that can be invoked.
Tool kinds help clients choose appropriate icons and optimize how they
display tool execution progress.
See protocol docs: [Creating](https://agentclientprotocol.com/protocol/tool-calls#creating)
**Type:** Union

[​](#param-read)

read

string

Reading files or data.

[​](#param-edit)

edit

string

Modifying files or content.

[​](#param-delete)

delete

string

Removing files or data.

[​](#param-move)

move

string

Moving or renaming files.

[​](#param-search)

search

string

Searching for information.

[​](#param-execute)

execute

string

Running commands or code.

[​](#param-think)

think

string

Internal reasoning or planning.

[​](#param-fetch)

fetch

string

Retrieving external data.

[​](#param-switch-mode)

switch\_mode

string

Switching the current session mode.

[​](#param-other)

other

string

Other tool types (default).

## [​](#unstructuredcommandinput) UnstructuredCommandInput

All text that was typed after the command name is provided as input.
**Type:** Object
**Properties:**

[​](#param-meta-91)

\_meta

object | null

The \_meta property is reserved by ACP to allow clients and agents to attach additional
metadata to their interactions. Implementations MUST NOT make assumptions about values at
these keys.See protocol docs: [Extensibility](https://agentclientprotocol.com/protocol/extensibility)

[​](#param-hint-1)

hint

string

required

A hint to display when the input hasn’t been provided yet

Was this page helpful?

YesNo

[Previous](/protocol/transports)[KotlinKotlin library for the Agent Client Protocol

Next](/libraries/kotlin)

Ctrl+I

[github](https://github.com/agentclientprotocol/agent-client-protocol)

[Powered by](https://www.mintlify.com?utm_campaign=poweredBy&utm_medium=referral&utm_source=zed-685ed6d6)

On this page

* [Agent](#agent)
* [authenticate](#authenticate)
* [AuthenticateRequest](#authenticaterequest)
* [AuthenticateResponse](#authenticateresponse)
* [initialize](#initialize)
* [InitializeRequest](#initializerequest)
* [InitializeResponse](#initializeresponse)
* [session/cancel](#session%2Fcancel)
* [CancelNotification](#cancelnotification)
* [session/load](#session%2Fload)
* [LoadSessionRequest](#loadsessionrequest)
* [LoadSessionResponse](#loadsessionresponse)
* [session/new](#session%2Fnew)
* [NewSessionRequest](#newsessionrequest)
* [NewSessionResponse](#newsessionresponse)
* [session/prompt](#session%2Fprompt)
* [PromptRequest](#promptrequest)
* [PromptResponse](#promptresponse)
* [session/set\_mode](#session%2Fset_mode)
* [SetSessionModeRequest](#setsessionmoderequest)
* [SetSessionModeResponse](#setsessionmoderesponse)
* [Client](#client)
* [fs/read\_text\_file](#fs%2Fread_text_file)
* [ReadTextFileRequest](#readtextfilerequest)
* [ReadTextFileResponse](#readtextfileresponse)
* [fs/write\_text\_file](#fs%2Fwrite_text_file)
* [WriteTextFileRequest](#writetextfilerequest)
* [WriteTextFileResponse](#writetextfileresponse)
* [session/request\_permission](#session%2Frequest_permission)
* [RequestPermissionRequest](#requestpermissionrequest)
* [RequestPermissionResponse](#requestpermissionresponse)
* [session/update](#session%2Fupdate)
* [SessionNotification](#sessionnotification)
* [terminal/create](#terminal%2Fcreate)
* [CreateTerminalRequest](#createterminalrequest)
* [CreateTerminalResponse](#createterminalresponse)
* [terminal/kill](#terminal%2Fkill)
* [KillTerminalCommandRequest](#killterminalcommandrequest)
* [KillTerminalCommandResponse](#killterminalcommandresponse)
* [terminal/output](#terminal%2Foutput)
* [TerminalOutputRequest](#terminaloutputrequest)
* [TerminalOutputResponse](#terminaloutputresponse)
* [terminal/release](#terminal%2Frelease)
* [ReleaseTerminalRequest](#releaseterminalrequest)
* [ReleaseTerminalResponse](#releaseterminalresponse)
* [terminal/wait\_for\_exit](#terminal%2Fwait_for_exit)
* [WaitForTerminalExitRequest](#waitforterminalexitrequest)
* [WaitForTerminalExitResponse](#waitforterminalexitresponse)
* [AgentCapabilities](#agentcapabilities)
* [Annotations](#annotations)
* [AudioContent](#audiocontent)
* [AuthMethod](#authmethod)
* [AvailableCommand](#availablecommand)
* [AvailableCommandInput](#availablecommandinput)
* [AvailableCommandsUpdate](#availablecommandsupdate)
* [BlobResourceContents](#blobresourcecontents)
* [ClientCapabilities](#clientcapabilities)
* [Content](#content)
* [ContentBlock](#contentblock)
* [ContentChunk](#contentchunk)
* [CurrentModeUpdate](#currentmodeupdate)
* [Diff](#diff)
* [EmbeddedResource](#embeddedresource)
* [EmbeddedResourceResource](#embeddedresourceresource)
* [EnvVariable](#envvariable)
* [Error](#error)
* [ErrorCode](#errorcode)
* [ExtNotification](#extnotification)
* [ExtRequest](#extrequest)
* [ExtResponse](#extresponse)
* [FileSystemCapability](#filesystemcapability)
* [HttpHeader](#httpheader)
* [ImageContent](#imagecontent)
* [Implementation](#implementation)
* [McpCapabilities](#mcpcapabilities)
* [McpServer](#mcpserver)
* [McpServerHttp](#mcpserverhttp)
* [McpServerSse](#mcpserversse)
* [McpServerStdio](#mcpserverstdio)
* [PermissionOption](#permissionoption)
* [PermissionOptionId](#permissionoptionid)
* [PermissionOptionKind](#permissionoptionkind)
* [Plan](#plan)
* [PlanEntry](#planentry)
* [PlanEntryPriority](#planentrypriority)
* [PlanEntryStatus](#planentrystatus)
* [PromptCapabilities](#promptcapabilities)
* [ProtocolVersion](#protocolversion)
* [RequestId](#requestid)
* [RequestPermissionOutcome](#requestpermissionoutcome)
* [ResourceLink](#resourcelink)
* [Role](#role)
* [SelectedPermissionOutcome](#selectedpermissionoutcome)
* [SessionCapabilities](#sessioncapabilities)
* [SessionId](#sessionid)
* [SessionMode](#sessionmode)
* [SessionModeId](#sessionmodeid)
* [SessionModeState](#sessionmodestate)
* [SessionUpdate](#sessionupdate)
* [StopReason](#stopreason)
* [Terminal](#terminal)
* [TerminalExitStatus](#terminalexitstatus)
* [TextContent](#textcontent)
* [TextResourceContents](#textresourcecontents)
* [ToolCall](#toolcall)
* [ToolCallContent](#toolcallcontent)
* [ToolCallId](#toolcallid)
* [ToolCallLocation](#toolcalllocation)
* [ToolCallStatus](#toolcallstatus)
* [ToolCallUpdate](#toolcallupdate)
* [ToolKind](#toolkind)
* [UnstructuredCommandInput](#unstructuredcommandinput)

Assistant

Responses are generated using AI and may contain mistakes.
