# CLI Testing Mixin

This mixin adds CLI tool testing capabilities to the verifier.

## When to Include

Include this mixin when:
- `app_type` is `cli` or `hybrid`
- The project has CLI commands (cobra, click, argparse, etc.)
- Task-specific scenarios involve CLI command verification

## Additional Context

You are testing a command-line tool. The application is invoked from the terminal.

## CLI-Specific Protocol

### Building the CLI

If the CLI needs compilation:
1. Check if binary exists at expected path
2. If not, run build command from adapter
3. Verify binary is executable

Common binary locations:
- Go: `bin/{name}`, `./cmd/{name}/{name}`
- Node: `dist/cli.js`, `bin/cli`
- Python: `src/cli.py`, module invocation `python -m {package}`

### Command Execution

For each CLI test:

```python
# Pseudocode for command execution
command = {
    "binary": cli_binary,
    "args": test["args"],
    "stdin": test.get("stdin"),
    "env": {**os.environ, **test.get("env", {})}
}

result = subprocess.run(
    [command["binary"]] + command["args"],
    input=command["stdin"],
    env=command["env"],
    capture_output=True,
    timeout=30
)

assertions = []
if "exit_code" in test:
    assertions.append(check_exit_code(result, test["exit_code"]))
if "stdout_contains" in test:
    assertions.append(check_stdout_contains(result, test["stdout_contains"]))
if "stdout_matches" in test:
    assertions.append(check_stdout_regex(result, test["stdout_matches"]))
if "file_created" in test:
    assertions.append(check_file_created(test["file_created"]))
```

### Common Assertions

**Exit Code:**
```json
{"type": "exit_code", "expected": 0, "actual": 0, "passed": true}
```

**Stdout Contains:**
```json
{"type": "stdout_contains", "expected": "Success", "found": true, "passed": true}
```

**Stdout Regex:**
```json
{"type": "stdout_matches", "pattern": "Created user \\w+", "matched": true, "passed": true}
```

**File Created:**
```json
{"type": "file_created", "path": "output.json", "exists": true, "passed": true}
```

### Standard Tests

Always include these basic tests:

| Test | Command | Expected |
|------|---------|----------|
| Help | `--help` | Exit 0, shows usage |
| Version | `--version` | Exit 0, shows version |
| No args | (none) | Exit 0 or shows help |
| Invalid flag | `--invalid-flag` | Exit non-zero, error message |

### Side Effect Verification

For commands that modify files or state:

1. **File creation:**
   ```json
   {
     "side_effect": "file_created",
     "path": "output/report.json",
     "verified_by": "file exists and is valid JSON",
     "passed": true
   }
   ```

2. **File modification:**
   ```json
   {
     "side_effect": "file_modified",
     "path": "config.yaml",
     "verified_by": "new_field present in file",
     "passed": true
   }
   ```

3. **Database changes:**
   ```json
   {
     "side_effect": "record_created",
     "verified_by": "SELECT returned 1 row",
     "passed": true
   }
   ```

### Interactive CLI Testing

For commands with prompts:
1. Provide input via stdin
2. Use `--yes` or `--no-interactive` flags if available
3. Set `CI=true` environment variable

### Error Testing

Test error scenarios:

| Scenario | Expected |
|----------|----------|
| Missing required arg | Exit 1, error message |
| Invalid arg value | Exit 1, validation error |
| File not found | Exit 1, file error |
| Permission denied | Exit 1, permission error |
| Invalid config | Exit 1, config error |

### Timeout Handling

- Default command timeout: 30 seconds
- Long operations (export, import): 120 seconds
- Set via `timeout` field in test

### Environment Variables

Common test environment setup:
```json
{
  "env": {
    "CI": "true",
    "NO_COLOR": "1",
    "TERM": "dumb"
  }
}
```

## Framework-Specific Notes

### Cobra (Go)
- Subcommands: `cli subcommand --flag`
- Persistent flags on parent commands
- `--help` auto-generated

### Click (Python)
- Subcommands: `cli subcommand --flag`
- `@click.option` for flags
- `@click.argument` for positional args

### Commander (Node)
- Subcommands: `cli subcommand --flag`
- `.option()` for flags
- `.argument()` for positional args
