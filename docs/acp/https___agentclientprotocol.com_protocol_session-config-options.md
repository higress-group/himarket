# https://agentclientprotocol.com/protocol/session-config-options

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

# Session Config Options

Flexible configuration selectors for agent sessions

Agents can provide an arbitrary list of configuration options for a session, allowing Clients to offer users customizable selectors for things like models, modes, reasoning levels, and more.

Session Config Options are the preferred way to expose session-level configuration. If an Agent provides `configOptions`, Clients **SHOULD** use them instead of the [`modes`](./session-modes) field. Modes will be removed in a future version of the protocol.

## [​](#initial-state) Initial State

During [Session Setup](./session-setup) the Agent **MAY** return a list of configuration options and their current values:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 1,  "id": 1, "result": { "result": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "configOptions": [ "configOptions": [ { { "id": "mode",  "id": "mode", "name": "Session Mode",  "name": "Session Mode", "description": "Controls how the agent requests permission",  "description": "Controls how the agent requests permission", "category": "mode",  "category": "mode", "type": "select",  "type": "select", "currentValue": "ask",  "currentValue": "ask", "options": [ "options": [ { { "value": "ask",  "value": "ask", "name": "Ask",  "name": "Ask", "description": "Request permission before making any changes"  "description": "Request permission before making any changes" }, }, { { "value": "code",  "value": "code", "name": "Code",  "name": "Code", "description": "Write and modify code with full tool access"  "description": "Write and modify code with full tool access" } } ] ] }, }, { { "id": "model",  "id": "model", "name": "Model",  "name": "Model", "category": "model",  "category": "model", "type": "select",  "type": "select", "currentValue": "model-1",  "currentValue": "model-1", "options": [ "options": [ { { "value": "model-1",  "value": "model-1", "name": "Model 1",  "name": "Model 1", "description": "The fastest model"  "description": "The fastest model" }, }, { { "value": "model-2",  "value": "model-2", "name": "Model 2",  "name": "Model 2", "description": "The most powerful model"  "description": "The most powerful model" } } ] ] } } ] ] } }}}
```

[​](#param-config-options)

configOptions

ConfigOption[]

The list of configuration options available for this session. The order of this array represents the Agent’s preferred priority. Clients **SHOULD** respect this ordering when displaying options.

### [​](#configoption) ConfigOption

[​](#param-id)

id

string

required

Unique identifier for this configuration option. Used when setting values.

[​](#param-name)

name

string

required

Human-readable label for the option

[​](#param-description)

description

string

Optional description providing more details about what this option controls

[​](#param-category)

category

ConfigOptionCategory

Optional [semantic category](#option-categories) to help Clients provide consistent UX.

[​](#param-type)

type

ConfigOptionType

required

The type of input control. Currently only `select` is supported.

[​](#param-current-value)

currentValue

string

required

The currently selected value for this option

[​](#param-options)

options

ConfigOptionValue[]

required

The available values for this option

### [​](#configoptionvalue) ConfigOptionValue

[​](#param-value)

value

string

required

The value identifier used when setting this option

[​](#param-name-1)

name

string

required

Human-readable name to display

[​](#param-description-1)

description

string

Optional description of what this value does

## [​](#option-categories) Option Categories

Each config option **MAY** include a `category` field. Categories are semantic metadata intended to help Clients provide consistent UX, such as attaching keyboard shortcuts, choosing icons, or deciding placement.

Categories are for UX purposes only and **MUST NOT** be required for correctness. Clients **MUST** handle missing or unknown categories gracefully.

Category names beginning with `_` are free for custom use (e.g., `_my_custom_category`). Category names that do not begin with `_` are reserved for the ACP spec.

| Category | Description |
| --- | --- |
| `mode` | Session mode selector |
| `model` | Model selector |
| `thought_level` | Thought/reasoning level selector |

When multiple options share the same category, Clients **SHOULD** use the array ordering to resolve ties, preferring earlier options in the list for prominent placement or keyboard shortcuts.

## [​](#option-ordering) Option Ordering

The order of the `configOptions` array is significant. Agents **SHOULD** place higher-priority options first in the list. Clients **SHOULD**:

* Display options in the order provided by the Agent
* Use ordering to resolve ties when multiple options share the same category
* If displaying a limited number of options, prefer those at the beginning of the list

## [​](#default-values-and-graceful-degradation) Default Values and Graceful Degradation

Agents **MUST** always provide a default value for every configuration option. This ensures the Agent can operate correctly even if:

* The Client doesn’t support configuration options
* The Client chooses not to display certain options
* The Client receives an option type it doesn’t recognize

If a Client receives an option with an unrecognized `type`, it **SHOULD** ignore that option. The Agent will continue using its default value.

## [​](#setting-a-config-option) Setting a Config Option

The current value of a config option can be changed at any point during a session, whether the Agent is idle or generating a response.

### [​](#from-the-client) From the Client

Clients can change a config option value by calling the `session/set_config_option` method:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 2,  "id": 2, "method": "session/set_config_option",  "method": "session/set_config_option", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "configId": "mode",  "configId": "mode", "value": "code"  "value": "code" } }}}
```

[​](#param-session-id)

sessionId

SessionId

required

The ID of the session

[​](#param-config-id)

string

required

The `id` of the configuration option to change

[​](#param-value-1)

value

string

required

The new value to set. Must be one of the values listed in the option’s `options` array.

The Agent **MUST** respond with the complete list of all configuration options and their current values:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "id": 2,  "id": 2, "result": { "result": { "configOptions": [ "configOptions": [ { { "id": "mode",  "id": "mode", "name": "Session Mode",  "name": "Session Mode", "type": "select",  "type": "select", "currentValue": "code",  "currentValue": "code", "options": [...]  "options": [...] }, }, { { "id": "model",  "id": "model", "name": "Model",  "name": "Model", "type": "select",  "type": "select", "currentValue": "model-1",  "currentValue": "model-1", "options": [...]  "options": [...] } } ] ] } }}}
```

The response always contains the **complete** configuration state. This allows Agents to reflect dependent changes. For example, if changing the model affects available reasoning options, or if an option’s available values change based on another selection.

### [​](#from-the-agent) From the Agent

The Agent can also change configuration options and notify the Client by sending a `config_options_update` session notification:

Copy

```
{{ "jsonrpc": "2.0",  "jsonrpc": "2.0", "method": "session/update",  "method": "session/update", "params": { "params": { "sessionId": "sess_abc123def456",  "sessionId": "sess_abc123def456", "update": { "update": { "sessionUpdate": "config_options_update",  "sessionUpdate": "config_options_update", "configOptions": [ "configOptions": [ { { "id": "mode",  "id": "mode", "name": "Session Mode",  "name": "Session Mode", "type": "select",  "type": "select", "currentValue": "code",  "currentValue": "code", "options": [...]  "options": [...] }, }, { { "id": "model",  "id": "model", "name": "Model",  "name": "Model", "type": "select",  "type": "select", "currentValue": "model-2",  "currentValue": "model-2", "options": [...]  "options": [...] } } ] ] } } } }}}
```

This notification also contains the complete configuration state. Common reasons an Agent might update configuration options include:

* Switching modes after completing a planning phase
* Falling back to a different model due to rate limits or errors
* Adjusting available options based on context discovered during execution

## [​](#relationship-to-session-modes) Relationship to Session Modes

Session Config Options supersede the older [Session Modes](./session-modes) API. However, during the transition period, Agents that provide mode-like configuration **SHOULD** send both:

* `configOptions` with a `category: "mode"` option for Clients that support config options
* `modes` for Clients that only support the older API

If an Agent provides both `configOptions` and `modes` in the session response:

* Clients that support config options **SHOULD** use `configOptions` exclusively and ignore `modes`
* Clients that don’t support config options **SHOULD** fall back to `modes`
* Agents **SHOULD** keep both in sync to ensure consistent behavior regardless of which field the Client uses

[Learn about the Session Modes API](../session-modes)

Was this page helpful?

[Previous](/protocol/session-modes)[Slash CommandsAdvertise available slash commands to clients](/protocol/slash-commands)

* [Initial State](#initial-state)
* [ConfigOption](#configoption)
* [ConfigOptionValue](#configoptionvalue)
* [Option Categories](#option-categories)
* [Option Ordering](#option-ordering)
* [Default Values and Graceful Degradation](#default-values-and-graceful-degradation)
* [Setting a Config Option](#setting-a-config-option)
* [From the Client](#from-the-client)
* [From the Agent](#from-the-agent)
* [Relationship to Session Modes](#relationship-to-session-modes)
