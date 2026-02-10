# https://agentclientprotocol.com/protocol/agent-plan

[Agent Client Protocol home page](/)

##### Get Started

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

# Agent Plan

How Agents communicate their execution plans

Plans are execution strategies for complex tasks that require multiple steps. Agents may share plans with Clients through [`session/update`](./prompt-turn#3-agent-reports-output) notifications, providing real-time visibility into their thinking and progress.

## [​](#creating-plans) Creating Plans

When the language model creates an execution plan, the Agent **SHOULD** report it to the Client:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "method": "session/update",  "method": "session/update", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "update": { "update": { "sessionUpdate": "plan",  "sessionUpdate": "plan", "entries": [ "entries": [ { { "content": "Analyze the existing codebase structure",  "content": "Analyze the existing codebase structure", "priority": "high",  "priority": "high", "status": "pending"  "status": "pending" }, }, { { "content": "Identify components that need refactoring",  "content": "Identify components that need refactoring", "priority": "high",  "priority": "high", "status": "pending"  "status": "pending" }, }, { { "content": "Create unit tests for critical functions",  "content": "Create unit tests for critical functions", "priority": "medium",  "priority": "medium", "status": "pending"  "status": "pending" } } ] ] } } } }}}
```

[​](#param-entries)

entries

PlanEntry[]

required

An array of [plan entries](#plan-entries) representing the tasks to be accomplished

## [​](#plan-entries) Plan Entries

Each plan entry represents a specific task or goal within the overall execution strategy:

[​](#param-content)

content

string

required

A human-readable description of what this task aims to accomplish

[​](#param-priority)

priority

PlanEntryPriority

required

The relative importance of this task.

* `high`
* `medium`
* `low`

[​](#param-status)

status

required

The current [execution status](#status) of this task

* `pending`
* `in_progress`
* `completed`

## [​](#updating-plans) Updating Plans

As the Agent progresses through the plan, it **SHOULD** report updates by sending more `session/update` notifications with the same structure. The Agent **MUST** send a complete list of all plan entries in each update and their current status. The Client **MUST** replace the current plan completely.

### [​](#dynamic-planning) Dynamic Planning

Plans can evolve during execution. The Agent **MAY** add, remove, or modify plan entries as it discovers new requirements or completes tasks, allowing it to adapt based on what it learns.

Was this page helpful?

[Previous](/protocol/terminals)[Session ModesSwitch between different agent operating modes](/protocol/session-modes)

* [Creating Plans](#creating-plans)
* [Plan Entries](#plan-entries)
* [Updating Plans](#updating-plans)
* [Dynamic Planning](#dynamic-planning)
