# https://agentclientprotocol.com/protocol/content

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

# Content

Understanding content blocks in the Agent Client Protocol

Content blocks represent displayable information that flows through the Agent Client Protocol. They provide a structured way to handle various types of user-facing content—whether it’s text from language models, images for analysis, or embedded resources for context. Content blocks appear in:

* User prompts sent via [`session/prompt`](./prompt-turn#1-user-message)
* Language model output streamed through [`session/update`](./prompt-turn#3-agent-reports-output) notifications
* Progress updates and results from [tool calls](./tool-calls)

## [​](#content-types) Content Types

The Agent Client Protocol uses the same `ContentBlock` structure as the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/specification/2025-06-18/schema#contentblock). This design choice enables Agents to seamlessly forward content from MCP tool outputs without transformation.

### [​](#text-content) Text Content

Plain text messages form the foundation of most interactions.

Copy

```
{{ "type": "text",  "type": "text", "text": "What's the weather like today?"  "text": "What's the weather like today?"}}
```

All Agents **MUST** support text content blocks when included in prompts.

[​](#param-text)

text

string

required

The text content to display

[​](#param-annotations)

annotations

Annotations

Optional metadata about how the content should be used or displayed. [Learn more](https://modelcontextprotocol.io/specification/2025-06-18/server/resources#annotations).

### [​](#image-content) Image Content

Images can be included for visual context or analysis.

Copy

```
{{ "type": "image",  "type": "image", "mimeType": "image/png",  "mimeType": "image/png", "data": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB..."  "data": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB..."}}
```

 Requires the `image` [prompt capability](./initialization#prompt-capabilities) when included in prompts.

[​](#param-data)

data

string

required

Base64-encoded image data

[​](#param-mime-type)

mimeType

string

required

The MIME type of the image (e.g., “image/png”, “image/jpeg”)

[​](#param-uri)

uri

string

Optional URI reference for the image source

[​](#param-annotations-1)

annotations

Annotations

Optional metadata about how the content should be used or displayed. [Learn more](https://modelcontextprotocol.io/specification/2025-06-18/server/resources#annotations).

### [​](#audio-content) Audio Content

Audio data for transcription or analysis.

Copy

```
{{ "type": "audio",  "type": "audio", "mimeType": "audio/wav",  "mimeType": "audio/wav", "data": "UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAAB..."  "data": "UklGRiQAAABXQVZFZm10IBAAAAABAAEAQB8AAAB..."}}
```

 Requires the `audio` [prompt capability](./initialization#prompt-capabilities) when included in prompts.

[​](#param-data-1)

data

string

required

Base64-encoded audio data

[​](#param-mime-type-1)

mimeType

string

required

The MIME type of the audio (e.g., “audio/wav”, “audio/mp3”)

[​](#param-annotations-2)

annotations

Annotations

Optional metadata about how the content should be used or displayed. [Learn more](https://modelcontextprotocol.io/specification/2025-06-18/server/resources#annotations).

### [​](#embedded-resource) Embedded Resource

Complete resource contents embedded directly in the message.

Copy

```
{{ "type": "resource",  "type": "resource", "resource": { "resource": { "uri": "file:///home/user/script.py",  "uri": "file:///home/user/script.py", "mimeType": "text/x-python",  "mimeType": "text/x-python", "text": "def hello():\n print('Hello, world!')"  "text": "def hello(): \n print('Hello, world!')" } }}}
```

This is the preferred way to include context in prompts, such as when using @-mentions to reference files or other resources. By embedding the content directly in the request, Clients can include context from sources that the Agent may not have direct access to.  Requires the `embeddedContext` [prompt capability](./initialization#prompt-capabilities) when included in prompts.

[​](#param-resource)

resource

EmbeddedResourceResource

required

The embedded resource contents, which can be either:

ShowText Resource

[​](#param-uri-1)

uri

string

required

The URI identifying the resource

[​](#param-text-1)

text

string

required

The text content of the resource

[​](#param-mime-type-2)

mimeType

string

Optional MIME type of the text content

ShowBlob Resource

[​](#param-uri-2)

uri

string

required

The URI identifying the resource

[​](#param-blob)

blob

string

required

Base64-encoded binary data

[​](#param-mime-type-3)

mimeType

string

Optional MIME type of the blob

[​](#param-annotations-3)

annotations

Annotations

Optional metadata about how the content should be used or displayed. [Learn more](https://modelcontextprotocol.io/specification/2025-06-18/server/resources#annotations).

### [​](#resource-link) Resource Link

References to resources that the Agent can access.

Copy

```
{{ "type": "resource_link",  "type": "resource_link", "uri": "file:///home/user/document.pdf",  "uri": "file:///home/user/document.pdf", "name": "document.pdf",  "name": "document.pdf", "mimeType": "application/pdf",  "mimeType": "application/pdf", "size": 1024000  "size": 1024000}}
```

[​](#param-uri-3)

uri

string

required

The URI of the resource

[​](#param-name)

name

string

required

A human-readable name for the resource

[​](#param-mime-type-4)

mimeType

string

The MIME type of the resource

[​](#param-title)

string

Optional display title for the resource

[​](#param-description)

string

Optional description of the resource contents

[​](#param-size)

Optional size of the resource in bytes

[​](#param-annotations-4)

annotations

Annotations

Optional metadata about how the content should be used or displayed. [Learn more](https://modelcontextprotocol.io/specification/2025-06-18/server/resources#annotations).

Was this page helpful?

[Previous](/protocol/prompt-turn)[Tool CallsHow Agents report tool call execution](/protocol/tool-calls)

* [Content Types](#content-types)
* [Text Content](#text-content)
* [Image Content](#image-content)
* [Audio Content](#audio-content)
* [Embedded Resource](#embedded-resource)
* [Resource Link](#resource-link)
