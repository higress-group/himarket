# https://agentclientprotocol.com/protocol/overview

[Skip to main content](#content-area)

[Agent Client Protocol home page](/)

[Protocol](/get-started/introduction)[RFDs](/rfds/about)[Community](/community/communication)[Updates](/updates)[Brand](/brand)

[Protocol](/get-started/introduction)[RFDs](/rfds/about)[Community](/community/communication)[Updates](/updates)[Brand](/brand)

Protocol

# Overview

How the Agent Client Protocol works

The Agent Client Protocol allows [Agents](#agent) and [Clients](#client) to communicate by exposing methods that each side can call and sending notifications to inform each other of events.

## [​](#communication-model) Communication Model

The protocol follows the [JSON-RPC 2.0](https://www.jsonrpc.org/specification) specification with two types of messages:

* **Methods**: Request-response pairs that expect a result or error
* **Notifications**: One-way messages that don’t expect a response

## [​](#message-flow) Message Flow

A typical flow follows this pattern:

1

Initialization Phase

* Client → Agent: `initialize` to establish connection
* Client → Agent: `authenticate` if required by the Agent

2

Session Setup - either:

* Client → Agent: `session/new` to create a new session
* Client → Agent: `session/load` to resume an existing session if supported

3

Prompt Turn

* Client → Agent: `session/prompt` to send user message
* Agent → Client: `session/update` notifications for progress updates
* Agent → Client: File operations or permission requests as needed
* Client → Agent: `session/cancel` to interrupt processing if needed
* Turn ends and the Agent sends the `session/prompt` response with a stop reason

## [​](#agent) Agent

Agents are programs that use generative AI to autonomously modify code. They typically run as subprocesses of the Client.

### [​](#baseline-methods) Baseline Methods

[​](#param-initialize)

initialize

[Schema](./schema#initialize)

[Negotiate versions and exchange capabilities.](./initialization).

[​](#param-authenticate)

authenticate

[Schema](./schema#authenticate)

Authenticate with the Agent (if required).

[​](#param-session-new)

session/new

[Schema](./schema#session%2Fnew)

[Create a new conversation session](./session-setup#creating-a-session).

[​](#param-session-prompt)

session/prompt

[Schema](./schema#session%2Fprompt)

[Send user prompts](./prompt-turn#1-user-message) to the Agent.

### [​](#optional-methods) Optional Methods

[​](#param-session-load)

session/load

[Schema](./schema#session%2Fload)

[Load an existing session](./session-setup#loading-sessions) (requires
`loadSession` capability).

[​](#param-session-set-mode)

session/set\_mode

[Schema](./schema#session%2Fset-mode)

[Switch between agent operating
modes](./session-modes#setting-the-current-mode).

### [​](#notifications) Notifications

[​](#param-session-cancel)

session/cancel

[Schema](./schema#session%2Fcancel)

[Cancel ongoing operations](./prompt-turn#cancellation) (no response
expected).

## [​](#client) Client

Clients provide the interface between users and agents. They are typically code editors (IDEs, text editors) but can also be other UIs for interacting with agents. Clients manage the environment, handle user interactions, and control access to resources.

### [​](#baseline-methods-2) Baseline Methods

[​](#param-session-request-permission)

session/request\_permission

[Schema](./schema#session%2Frequest_permission)

[Request user authorization](./tool-calls#requesting-permission) for tool
calls.

### [​](#optional-methods-2) Optional Methods

[​](#param-fs-read-text-file)

fs/read\_text\_file

[Schema](./schema#fs%2Fread_text_file)

[Read file contents](./file-system#reading-files) (requires `fs.readTextFile`
capability).

[​](#param-fs-write-text-file)

fs/write\_text\_file

[Schema](./schema#fs%2Fwrite_text_file)

[Write file contents](./file-system#writing-files) (requires
`fs.writeTextFile` capability).

[​](#param-terminal-create)

terminal/create

[Schema](./schema#terminal%2Fcreate)

[Create a new terminal](./terminals) (requires `terminal` capability).

[​](#param-terminal-output)

terminal/output

[Schema](./schema#terminal%2Foutput)

Get terminal output and exit status (requires `terminal` capability).

[​](#param-terminal-release)

terminal/release

[Schema](./schema#terminal%2Frelease)

Release a terminal (requires `terminal` capability).

[​](#param-terminal-wait-for-exit)

terminal/wait\_for\_exit

[Schema](./schema#terminal%2Fwait_for_exit)

Wait for terminal command to exit (requires `terminal` capability).

[​](#param-terminal-kill)

[Schema](./schema#terminal%2Fkill)

Kill terminal command without releasing (requires `terminal` capability).

### [​](#notifications-2) Notifications

[​](#param-session-update)

[Schema](./schema#session%2Fupdate)

[Send session updates](./prompt-turn#3-agent-reports-output) to inform the
Client of changes (no response expected). This includes: - [Message
chunks](./content) (agent, user, thought) - [Tool calls and
updates](./tool-calls) - [Plans](./agent-plan) - [Available commands
updates](./slash-commands#advertising-commands) - [Mode
changes](./session-modes#from-the-agent)

## [​](#argument-requirements) Argument requirements

* All file paths in the protocol **MUST** be absolute.
* Line numbers are 1-based

## [​](#error-handling) Error Handling

All methods follow standard JSON-RPC 2.0 [error handling](https://www.jsonrpc.org/specification#error_object):

* Successful responses include a `result` field
* Errors include an `error` object with `code` and `message`
* Notifications never receive responses (success or error)

## [​](#extensibility) Extensibility

The protocol provides built-in mechanisms for adding custom functionality while maintaining compatibility:

* Add custom data using `_meta` fields
* Create custom methods by prefixing their name with underscore (`_`)
* Advertise custom capabilities during initialization

Learn about [protocol extensibility](./extensibility) to understand how to use these mechanisms.

## [​](#next-steps) Next Steps

* Learn about [Initialization](./initialization) to understand version and capability negotiation
* Understand [Session Setup](./session-setup) for creating and loading sessions
* Review the [Prompt Turn](./prompt-turn) lifecycle
* Explore [Extensibility](./extensibility) to add custom features

* [Communication Model](#communication-model)
* [Message Flow](#message-flow)
* [Agent](#agent)
* [Baseline Methods](#baseline-methods)
* [Optional Methods](#optional-methods)
* [Notifications](#notifications)
* [Client](#client)
* [Baseline Methods](#baseline-methods-2)
* [Optional Methods](#optional-methods-2)
* [Notifications](#notifications-2)
* [Argument requirements](#argument-requirements)
* [Error Handling](#error-handling)
* [Extensibility](#extensibility)
* [Next Steps](#next-steps)

Responses are generated using AI and may contain mistakes.
