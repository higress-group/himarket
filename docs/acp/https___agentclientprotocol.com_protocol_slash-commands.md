# https://agentclientprotocol.com/protocol/slash-commands

[Skip to main content](#content-area)

[Agent Client Protocol home page](/)

[Protocol](/get-started/introduction)[RFDs](/rfds/about)[Community](/community/communication)[Updates](/updates)[Brand](/brand)

[Protocol](/get-started/introduction)[RFDs](/rfds/about)[Community](/community/communication)[Updates](/updates)[Brand](/brand)

Protocol

# Slash Commands

Advertise available slash commands to clients

Agents can advertise a set of slash commands that users can invoke. These commands provide quick access to specific agent capabilities and workflows. Commands are run as part of regular [prompt](./prompt-turn) requests where the Client includes the command text in the prompt.

## [​](#advertising-commands) Advertising commands

After creating a session, the Agent **MAY** send a list of available commands via the `available_commands_update` session notification:

Copy

```
{
  "jsonrpc": "2.0",
  "method": "session/update",
  "params": {
    "sessionId": "sess_abc123def456",
    "update": {
      "sessionUpdate": "available_commands_update",
      "availableCommands": [
        {
          "name": "web",
          "description": "Search the web for information",
          "input": {
            "hint": "query to search for"
          }
        },
        {
          "name": "test",
          "description": "Run tests for the current project"
        },
        {
          "name": "plan",
          "description": "Create a detailed implementation plan",
          "input": {
            "hint": "description of what to plan"
          }
        }
      ]
    }
  }
}

```

[​](#param-available-commands)

availableCommands

AvailableCommand[]

The list of commands available in this session

### [​](#availablecommand) AvailableCommand

[​](#param-name)

name

string

required

The command name (e.g., “web”, “test”, “plan”)

[​](#param-description)

description

string

required

Human-readable description of what the command does

[​](#param-input)

input

AvailableCommandInput

Optional input specification for the command

### [​](#availablecommandinput) AvailableCommandInput

Currently supports unstructured text input:

[​](#param-hint)

hint

string

required

A hint to display when the input hasn’t been provided yet

## [​](#dynamic-updates) Dynamic updates

The Agent can update the list of available commands at any time during a session by sending another `available_commands_update` notification. This allows commands to be added based on context, removed when no longer relevant, or modified with updated descriptions.

## [​](#running-commands) Running commands

Commands are included as regular user messages in prompt requests:

Copy

```
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "session/prompt",
  "params": {
    "sessionId": "sess_abc123def456",
    "prompt": [
      {
        "type": "text",
        "text": "/web agent client protocol"
      }
    ]
  }
}

```

The Agent recognizes the command prefix and processes it accordingly. Commands may be accompanied by any other user message content types (images, audio, etc.) in the same prompt array.

* [Advertising commands](#advertising-commands)
* [AvailableCommand](#availablecommand)
* [AvailableCommandInput](#availablecommandinput)
* [Dynamic updates](#dynamic-updates)
* [Running commands](#running-commands)

Responses are generated using AI and may contain mistakes.
