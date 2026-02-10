# https://agentclientprotocol.com/protocol/prompt-turn

[Agent Client Protocol home page](/)

##### Get Started

* [ACP Registry](/get-started/registry)

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

# Prompt Turn

Understanding the core conversation flow

A prompt turn represents a complete interaction cycle between the [Client](./overview#client) and [Agent](./overview#agent), starting with a user message and continuing until the Agent completes its response. This may involve multiple exchanges with the language model and tool invocations. Before sending prompts, Clients **MUST** first complete the [initialization](./initialization) phase and [session setup](./session-setup).

## [​](#the-prompt-turn-lifecycle) The Prompt Turn Lifecycle

A prompt turn follows a structured flow that enables rich interactions between the user, Agent, and any connected tools.   

### [​](#1-user-message) 1. User Message

The turn begins when the Client sends a `session/prompt`:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 2,  "id": 2, "method": "session/prompt",  "method": "session/prompt", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "prompt": [ "prompt": [ { { "type": "text",  "type": "text", "text": "Can you analyze this code for potential issues?"  "text": "Can you analyze this code for potential issues?" }, }, { { "type": "resource",  "type": "resource", "resource": { "resource": { "uri": "file:///home/user/project/main.py",  "uri": "file:///home/user/project/main.py", "mimeType": "text/x-python",  "mimeType": "text/x-python", "text": "def process_data(items):\n for item in items:\n print(item)"  "text": "def process_data(items): \n for item in items: \n print(item)" } } } } ] ] } }}}
```

[​](#param-session-id)

sessionId

SessionId

The [ID](./session-setup#session-id) of the session to send this message to.

[​](#param-prompt)

prompt

ContentBlock[]

The contents of the user message, e.g. text, images, files, etc.Clients **MUST** restrict types of content according to the [Prompt Capabilities](./initialization#prompt-capabilities) established during [initialization](./initialization).[Learn more about Content](./content)

### [​](#2-agent-processing) 2. Agent Processing

Upon receiving the prompt request, the Agent processes the user’s message and sends it to the language model, which **MAY** respond with text content, tool calls, or both.

### [​](#3-agent-reports-output) 3. Agent Reports Output

The Agent reports the model’s output to the Client via `session/update` notifications. This may include the Agent’s plan for accomplishing the task:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "method": "session/update",  "method": "session/update", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "update": { "update": { "sessionUpdate": "plan",  "sessionUpdate": "plan", "entries": [ "entries": [ { { "content": "Check for syntax errors",  "content": "Check for syntax errors", "priority": "high",  "priority": "high", "status": "pending"  "status": "pending" }, }, { { "content": "Identify potential type issues",  "content": "Identify potential type issues", "priority": "medium",  "priority": "medium", "status": "pending"  "status": "pending" }, }, { { "content": "Review error handling patterns",  "content": "Review error handling patterns", "priority": "medium",  "priority": "medium", "status": "pending"  "status": "pending" }, }, { { "content": "Suggest improvements",  "content": "Suggest improvements", "priority": "low",  "priority": "low", "status": "pending"  "status": "pending" } } ] ] } } } }}}
```

[Learn more about Agent Plans](./agent-plan) The Agent then reports text responses from the model:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "method": "session/update",  "method": "session/update", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "update": { "update": { "sessionUpdate": "agent_message_chunk",  "sessionUpdate": "agent_message_chunk", "content": { "content": { "type": "text",  "type": "text", "text": "I'll analyze your code for potential issues. Let me examine it..."  "text": "I'll analyze your code for potential issues. Let me examine it..." } } } } } }}}
```

If the model requested tool calls, these are also reported immediately:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "method": "session/update",  "method": "session/update", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "update": { "update": { "sessionUpdate": "tool_call",  "sessionUpdate": "tool_call", "toolCallId": "call_001",  "toolCallId": "call_001", "title": "Analyzing Python code",  "title": "Analyzing Python code", "kind": "other",  "kind": "other", "status": "pending"  "status": "pending" } } } }}}
```

### [​](#4-check-for-completion) 4. Check for Completion

If there are no pending tool calls, the turn ends and the Agent **MUST** respond to the original `session/prompt` request with a `StopReason`:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 2,  "id": 2, "result": { "result": { "stopReason": "end_turn"  "stopReason": "end_turn" } }}}
```

Agents **MAY** stop the turn at any point by returning the corresponding [`StopReason`](#stop-reasons).

### [​](#5-tool-invocation-and-status-reporting) 5. Tool Invocation and Status Reporting

Before proceeding with execution, the Agent **MAY** request permission from the Client via the `session/request_permission` method. Once permission is granted (if required), the Agent **SHOULD** invoke the tool and report a status update marking the tool as `in_progress`:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "method": "session/update",  "method": "session/update", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "update": { "update": { "sessionUpdate": "tool_call_update",  "sessionUpdate": "tool_call_update", "toolCallId": "call_001",  "toolCallId": "call_001", "status": "in_progress"  "status": "in_progress" } } } }}}
```

As the tool runs, the Agent **MAY** send additional updates, providing real-time feedback about tool execution progress. While tools execute on the Agent, they **MAY** leverage Client capabilities such as the file system (`fs`) methods to access resources within the Client’s environment. When the tool completes, the Agent sends another update with the final status and any content:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "method": "session/update",  "method": "session/update", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "update": { "update": { "sessionUpdate": "tool_call_update",  "sessionUpdate": "tool_call_update", "toolCallId": "call_001",  "toolCallId": "call_001", "status": "completed",  "status": "completed", "content": [ "content": [ { { "type": "content",  "type": "content", "content": { "content": { "type": "text",  "type": "text", "text": "Analysis complete:\n- No syntax errors found\n- Consider adding type hints for better clarity\n- The function could benefit from error handling for empty lists"  "text": "Analysis complete: \n - No syntax errors found \n - Consider adding type hints for better clarity \n - The function could benefit from error handling for empty lists" } } } } ] ] } } } }}}
```

[Learn more about Tool Calls](./tool-calls)

### [​](#6-continue-conversation) 6. Continue Conversation

The Agent sends the tool results back to the language model as another request. The cycle returns to [step 2](#2-agent-processing), continuing until the language model completes its response without requesting additional tool calls or the turn gets stopped by the Agent or cancelled by the Client.

## [​](#stop-reasons) Stop Reasons

When an Agent stops a turn, it must specify the corresponding `StopReason`:

[​](#param-end-turn)

end\_turn

The language model finishes responding without requesting more tools

[​](#param-max-tokens)

max\_tokens

The maximum token limit is reached

[​](#param-max-turn-requests)

max\_turn\_requests

The maximum number of model requests in a single turn is exceeded

[​](#param-refusal)

refusal

The Agent refuses to continue

[​](#param-cancelled)

The Client cancels the turn

## [​](#cancellation) Cancellation

Clients **MAY** cancel an ongoing prompt turn at any time by sending a `session/cancel` notification:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "method": "session/cancel",  "method": "session/cancel", "params": { "params": { "sessionId": "sess_abc123def456"  "sessionId": "sess_abc123def456" } }}}
```

The Client **SHOULD** preemptively mark all non-finished tool calls pertaining to the current turn as `cancelled` as soon as it sends the `session/cancel` notification. The Client **MUST** respond to all pending `session/request_permission` requests with the `cancelled` outcome. When the Agent receives this notification, it **SHOULD** stop all language model requests and all tool call invocations as soon as possible. After all ongoing operations have been successfully aborted and pending updates have been sent, the Agent **MUST** respond to the original `session/prompt` request with the `cancelled` [stop reason](#stop-reasons).

API client libraries and tools often throw an exception when their operation is aborted, which may propagate as an error response to `session/prompt`.Clients often display unrecognized errors from the Agent to the user, which would be undesirable for cancellations as they aren’t considered errors.Agents **MUST** catch these errors and return the semantically meaningful `cancelled` stop reason, so that Clients can reliably confirm the cancellation.

The Agent **MAY** send `session/update` notifications with content or tool call updates after receiving the `session/cancel` notification, but it **MUST** ensure that it does so before responding to the `session/prompt` request. The Client **SHOULD** still accept tool call updates received after sending `session/cancel`. 

---

 Once a prompt turn completes, the Client may send another `session/prompt` to continue the conversation, building on the context established in previous turns.

Was this page helpful?

[Previous](/protocol/session-setup)[ContentUnderstanding content blocks in the Agent Client Protocol](/protocol/content)

* [The Prompt Turn Lifecycle](#the-prompt-turn-lifecycle)
* [1. User Message](#1-user-message)
* [2. Agent Processing](#2-agent-processing)
* [3. Agent Reports Output](#3-agent-reports-output)
* [4. Check for Completion](#4-check-for-completion)
* [5. Tool Invocation and Status Reporting](#5-tool-invocation-and-status-reporting)
* [6. Continue Conversation](#6-continue-conversation)
* [Stop Reasons](#stop-reasons)
* [Cancellation](#cancellation)
