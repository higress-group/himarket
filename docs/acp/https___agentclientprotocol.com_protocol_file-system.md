# https://agentclientprotocol.com/protocol/file-system

![light logo](https://mintcdn.com/zed-685ed6d6/FgcZrIi8cEeJJGHC/logo/light.svg?fit=max&auto=format&n=FgcZrIi8cEeJJGHC&q=85&s=5cf9119e471543528e40443ba41baf30)
![dark logo](https://mintcdn.com/zed-685ed6d6/FgcZrIi8cEeJJGHC/logo/dark.svg?fit=max&auto=format&n=FgcZrIi8cEeJJGHC&q=85&s=ef801d8ed18c55ed6d930fe23a92c719)

##### Get Started

##### Protocol

##### Libraries

![light logo](https://mintcdn.com/zed-685ed6d6/FgcZrIi8cEeJJGHC/logo/light.svg?fit=max&auto=format&n=FgcZrIi8cEeJJGHC&q=85&s=5cf9119e471543528e40443ba41baf30)
![dark logo](https://mintcdn.com/zed-685ed6d6/FgcZrIi8cEeJJGHC/logo/dark.svg?fit=max&auto=format&n=FgcZrIi8cEeJJGHC&q=85&s=ef801d8ed18c55ed6d930fe23a92c719)

# File System

Client filesystem access methods

## [​](#checking-support) Checking Support

`initialize`
`{
 "jsonrpc": "2.0",
 "id": 0,
 "result": {
 "protocolVersion": 1,
 "clientCapabilities": {
 "fs": {
 "readTextFile": true,
 "writeTextFile": true
 }
 }
 }
}`
`readTextFile`
`writeTextFile`
`false`

## [​](#reading-files) Reading Files

`fs/read_text_file`
`{
 "jsonrpc": "2.0",
 "id": 3,
 "method": "fs/read_text_file",
 "params": {
 "sessionId": "sess_abc123def456",
 "path": "/home/user/project/src/main.py",
 "line": 10,
 "limit": 50
 }
}`
`{
 "jsonrpc": "2.0",
 "id": 3,
 "result": {
 "content": "def hello_world():\n print('Hello, world!')\n"
 }
}`

## [​](#writing-files) Writing Files

`fs/write_text_file`
`{
 "jsonrpc": "2.0",
 "id": 4,
 "method": "fs/write_text_file",
 "params": {
 "sessionId": "sess_abc123def456",
 "path": "/home/user/project/config.json",
 "content": "{\n \"debug\": true,\n \"version\": \"1.0.0\"\n}"
 }
}`
`{
 "jsonrpc": "2.0",
 "id": 4,
 "result": null
}`

Was this page helpful?
