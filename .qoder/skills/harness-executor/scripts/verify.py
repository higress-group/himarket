#!/usr/bin/env python3
"""
Runtime verification pipeline.

Verifies that the application actually works after code changes by:
- Server: Starting the server, checking health, testing endpoints
- CLI: Building and running CLI commands with expected outputs
- Frontend: Using Chrome DevTools Protocol to test pages

Complements static validation (validate.py) with runtime checks.

Resolution priority for config:
1. harness/config/verify.json or .harness/config/verify.json (explicit config)
2. Auto-generated config based on detected adapter
"""

import json
import os
import re
import signal
import socket
import subprocess
import sys
import time
from dataclasses import dataclass, field, asdict
from enum import Enum
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
import urllib.request
import urllib.error

# Import shared modules if available
try:
    from detect_adapter import detect_adapter as _detect_adapter
except ImportError:
    _detect_adapter = None

try:
    from config_resolver import resolve_config, get_harness_root
except ImportError:
    # Inline fallback
    def resolve_config(name: str, project_root: Path, auto_generate=None) -> Optional[Dict]:
        for config_dir in [".harness/config", "harness/config"]:
            config_path = project_root / config_dir / f"{name}.json"
            if config_path.exists():
                try:
                    return json.loads(config_path.read_text())
                except json.JSONDecodeError:
                    pass
        return auto_generate(project_root) if auto_generate else None

    def get_harness_root(project_root: Path) -> Path:
        if (project_root / ".harness").is_dir():
            return project_root / ".harness"
        return project_root / "harness"


class VerifyStatus(str, Enum):
    PASS = "pass"
    FAIL = "fail"
    SKIP = "skip"
    ERROR = "error"


@dataclass
class VerifyResult:
    """Result of a single verification."""
    name: str
    status: VerifyStatus
    duration_seconds: float = 0.0
    message: str = ""
    details: Dict = field(default_factory=dict)


@dataclass
class VerifyReport:
    """Full verification report."""
    project_root: str
    app_type: str
    adapter: str = ""  # Which adapter was used
    timestamp: str = ""
    total_duration_seconds: float = 0.0
    all_passed: bool = False
    results: List[dict] = field(default_factory=list)
    summary: Dict = field(default_factory=dict)


# =============================================================================
# Adapter-Based Detection (Replaces Hardcoded Language Logic)
# =============================================================================

def get_adapter(project_root: Path, verbose: bool = False) -> Dict[str, Any]:
    """Get language adapter for the project."""
    if _detect_adapter is not None:
        return _detect_adapter(project_root, verbose=verbose)

    # Inline fallback detection
    adapter = {
        "language": "generic",
        "display_name": "Unknown",
        "commands": {},
    }

    if (project_root / "go.mod").exists():
        adapter.update({
            "language": "go",
            "display_name": "Go",
            "commands": {
                "start": "go run main.go" if (project_root / "main.go").exists() else "go run cmd/server/main.go",
            },
        })
    elif (project_root / "package.json").exists():
        adapter.update({
            "language": "typescript",
            "display_name": "TypeScript / JavaScript",
            "commands": {"start": "npm start"},
        })
    elif (project_root / "pyproject.toml").exists() or (project_root / "requirements.txt").exists():
        adapter.update({
            "language": "python",
            "display_name": "Python",
            "commands": {"start": "python -m uvicorn main:app"},
        })
    elif (project_root / "Cargo.toml").exists():
        adapter.update({
            "language": "rust",
            "display_name": "Rust",
            "commands": {"start": "cargo run"},
        })

    return adapter


# =============================================================================
# Auto-Detection
# =============================================================================

def detect_app_type(project_root: Path, adapter: Optional[Dict] = None) -> str:
    """
    Auto-detect application type based on project structure.

    Uses adapter route_detection indicators when available, falls back to
    direct file scanning for inline detection.
    """
    if adapter is None:
        adapter = get_adapter(project_root)

    indicators = {
        "server": 0,
        "cli": 0,
        "frontend": 0,
        "library": 0,
    }

    language = adapter.get("language", "generic")

    # Use adapter-defined indicators if available (from detect_adapter.py)
    route_detection = adapter.get("route_detection", {})

    # Scan source files for adapter-defined patterns
    source_extensions = adapter.get("source_extensions", [])
    if not source_extensions:
        # Fallback extensions based on language
        ext_map = {
            "go": [".go"], "typescript": [".ts", ".tsx", ".js", ".jsx"],
            "python": [".py"], "rust": [".rs"], "java": [".java", ".kt"],
        }
        source_extensions = ext_map.get(language, [])

    for ext in source_extensions:
        for src_file in project_root.rglob(f"*{ext}"):
            if src_file.stat().st_size > 100000:
                continue
            try:
                content = src_file.read_text(errors="ignore")
            except OSError:
                continue

            # Check server indicators
            for ind in route_detection.get("server_indicators", []):
                if re.search(ind.get("pattern", ""), content):
                    indicators["server"] += 2

            # Check CLI indicators
            for ind in route_detection.get("cli_indicators", []):
                if re.search(ind.get("pattern", ""), content):
                    indicators["cli"] += 2

            # Check frontend indicators
            for ind in route_detection.get("frontend_indicators", []):
                if re.search(ind.get("pattern", ""), content):
                    indicators["frontend"] += 2

    # If adapter has no route_detection, do basic structural detection
    if not route_detection:
        _fallback_structural_detection(project_root, indicators)

    # Also check TypeScript package.json for frameworks (always useful)
    pkg_json = project_root / "package.json"
    if pkg_json.exists():
        try:
            pkg = json.loads(pkg_json.read_text())
            deps = {**pkg.get("dependencies", {}), **pkg.get("devDependencies", {})}
            if any(fw in deps for fw in ["react", "vue", "svelte", "next", "nuxt", "vite"]):
                indicators["frontend"] += 3
            if any(fw in deps for fw in ["express", "fastify", "koa", "hono", "@nestjs/core"]):
                indicators["server"] += 3
            if any(fw in deps for fw in ["commander", "yargs", "inquirer", "oclif"]):
                indicators["cli"] += 2
        except (json.JSONDecodeError, OSError):
            pass

    # Check for structural indicators
    if (project_root / "cmd" / "server").exists():
        indicators["server"] += 3
    if (project_root / "cmd" / "cli").exists():
        indicators["cli"] += 3

    # Determine type
    max_score = max(indicators.values())
    if max_score == 0:
        return "library"

    high_scores = [k for k, v in indicators.items() if v >= max_score - 1 and v > 0]
    if len(high_scores) > 1:
        return "hybrid"

    return max(indicators, key=indicators.get)


def _fallback_structural_detection(project_root: Path, indicators: Dict[str, int]):
    """Basic structural detection when no adapter patterns are available."""
    # Go
    if (project_root / "go.mod").exists():
        for go_file in list(project_root.rglob("*.go"))[:50]:  # Limit scanning
            try:
                content = go_file.read_text(errors="ignore")
                if "http.ListenAndServe" in content or "gin.Default" in content:
                    indicators["server"] += 2
                if "cobra" in content.lower() or "urfave/cli" in content:
                    indicators["cli"] += 2
            except OSError:
                pass

    # Python
    for py_file in list(project_root.rglob("*.py"))[:50]:
        try:
            content = py_file.read_text(errors="ignore")
            if "FastAPI" in content or "Flask" in content:
                indicators["server"] += 3
            if "click" in content or "argparse" in content:
                indicators["cli"] += 1
        except OSError:
            pass


def generate_default_config(project_root: Path, app_type: str, adapter: Optional[Dict] = None) -> Dict:
    """Generate default verify.json config based on detected type and adapter."""
    if adapter is None:
        adapter = get_adapter(project_root)

    config = {
        "version": "1.0",
        "app_type": app_type,
        "adapter": adapter.get("language", "generic"),
        "auto_detected": True,
        "verification": {},
        "smoke_tests": [],
        "cleanup": {"remove_paths": [], "commands": []},
    }

    # Get commands from adapter
    commands = adapter.get("commands", {})

    # Detect common patterns
    pkg_json = project_root / "package.json"

    if app_type in ("server", "hybrid"):
        # Try to detect server port and health endpoint
        port = 8080
        health_path = "/health"

        if pkg_json.exists():
            try:
                pkg = json.loads(pkg_json.read_text())
                scripts = pkg.get("scripts", {})
                for script in scripts.values():
                    if "PORT=" in script:
                        match = re.search(r"PORT=(\d+)", script)
                        if match:
                            port = int(match.group(1))
            except (json.JSONDecodeError, OSError):
                pass

        # Use adapter's start command if available
        start_command = commands.get("start") or _detect_server_start_command(project_root, adapter)

        config["verification"]["server"] = {
            "start": {
                "command": start_command,
                "env": {"PORT": str(port)},
                "background": True,
            },
            "readiness": {
                "type": "http",
                "endpoint": f"http://localhost:{port}{health_path}",
                "expected_status": 200,
                "timeout_seconds": 30,
                "poll_interval_ms": 500,
            },
            "endpoints": [
                {
                    "name": "health check",
                    "method": "GET",
                    "path": health_path,
                    "expected": {"status": 200},
                }
            ],
            "stop": {"signal": "SIGTERM", "graceful_timeout_seconds": 5},
        }

    if app_type in ("cli", "hybrid"):
        config["verification"]["cli"] = {
            "binary": {
                "build_command": _detect_cli_build_command(project_root, adapter),
                "path": _detect_cli_path(project_root, adapter),
            },
            "commands": [
                {
                    "name": "version flag",
                    "args": ["--version"],
                    "expected": {"exit_code": 0},
                },
                {
                    "name": "help flag",
                    "args": ["--help"],
                    "expected": {"exit_code": 0, "stdout_contains": ["Usage"]},
                },
            ],
        }

    if app_type in ("frontend", "hybrid"):
        port = 3000
        config["verification"]["frontend"] = {
            "dev_server": {
                "command": _detect_frontend_dev_command(project_root, adapter),
                "env": {"PORT": str(port)},
                "background": True,
            },
            "readiness": {
                "type": "http",
                "url": f"http://localhost:{port}",
                "timeout_seconds": 60,
            },
            "browser": {"headless": True, "args": ["--no-sandbox"]},
            "pages": [
                {
                    "name": "homepage loads",
                    "url": "/",
                    "assertions": [
                        {"type": "no_console_errors"},
                        {"type": "element_exists", "selector": "body"},
                    ],
                }
            ],
            "stop": {"signal": "SIGTERM", "graceful_timeout_seconds": 5},
        }

    return config


def _detect_server_start_command(project_root: Path, adapter: Optional[Dict] = None) -> str:
    """Detect server start command using adapter or structural analysis."""
    if adapter:
        # Check adapter's start command first
        start_cmd = adapter.get("commands", {}).get("start")
        if start_cmd:
            return start_cmd

    language = adapter.get("language", "generic") if adapter else "generic"

    # Language-specific inference
    if language == "go" or (project_root / "go.mod").exists():
        if (project_root / "cmd" / "server" / "main.go").exists():
            return "go run cmd/server/main.go"
        if (project_root / "cmd" / "api" / "main.go").exists():
            return "go run cmd/api/main.go"
        if (project_root / "main.go").exists():
            return "go run main.go"

    if language == "typescript" or (project_root / "package.json").exists():
        try:
            pkg = json.loads((project_root / "package.json").read_text())
            scripts = pkg.get("scripts", {})
            if "start" in scripts:
                pm = _detect_pkg_manager(project_root)
                return f"{pm} start"
            if "dev" in scripts:
                pm = _detect_pkg_manager(project_root)
                return f"{pm} run dev"
        except (json.JSONDecodeError, OSError):
            pass
        return "npm start"

    if language == "python" or (project_root / "pyproject.toml").exists():
        # Check for FastAPI main file
        for main_path in ["app/main.py", "src/main.py", "main.py"]:
            if (project_root / main_path).exists():
                module = main_path.replace("/", ".").replace(".py", "")
                return f"python -m uvicorn {module}:app"
        return "python -m uvicorn main:app"

    if language == "rust" or (project_root / "Cargo.toml").exists():
        return "cargo run"

    if language == "java" or (project_root / "pom.xml").exists():
        if (project_root / "mvnw").exists():
            return "./mvnw spring-boot:run"
        return "mvn spring-boot:run"

    if (project_root / "build.gradle").exists() or (project_root / "build.gradle.kts").exists():
        return "./gradlew bootRun"

    return "echo 'No server start command detected'"


def _detect_pkg_manager(project_root: Path) -> str:
    """Detect package manager for TypeScript/Node projects."""
    if (project_root / "pnpm-lock.yaml").exists():
        return "pnpm"
    if (project_root / "yarn.lock").exists():
        return "yarn"
    if (project_root / "bun.lockb").exists():
        return "bun"
    return "npm"


def _detect_cli_build_command(project_root: Path, adapter: Optional[Dict] = None) -> Optional[str]:
    """Detect CLI build command."""
    language = adapter.get("language", "generic") if adapter else "generic"

    if language == "go" or (project_root / "go.mod").exists():
        if (project_root / "cmd" / "cli").exists():
            return "go build -o bin/cli cmd/cli/main.go"
        return "go build -o bin/app ."

    if language == "rust" or (project_root / "Cargo.toml").exists():
        return "cargo build --release"

    return None


def _detect_cli_path(project_root: Path, adapter: Optional[Dict] = None) -> str:
    """Detect CLI executable path."""
    language = adapter.get("language", "generic") if adapter else "generic"

    if language == "go" or (project_root / "go.mod").exists():
        return "bin/cli"
    if language == "typescript" or (project_root / "package.json").exists():
        return "node dist/cli.js"
    if language == "rust" or (project_root / "Cargo.toml").exists():
        return "target/release/app"
    return "./cli"


def _detect_frontend_dev_command(project_root: Path, adapter: Optional[Dict] = None) -> str:
    """Detect frontend dev server command."""
    if (project_root / "package.json").exists():
        try:
            pkg = json.loads((project_root / "package.json").read_text())
            scripts = pkg.get("scripts", {})
            pm = _detect_pkg_manager(project_root)
            if "dev" in scripts:
                return f"{pm} run dev"
            if "start" in scripts:
                return f"{pm} start"
        except (json.JSONDecodeError, OSError):
            pass
    return "npm run dev"


# =============================================================================
# Server Verification
# =============================================================================

class ServerProcess:
    """Manages a background server process."""

    def __init__(self):
        self.process: Optional[subprocess.Popen] = None
        self.output_lines: List[str] = []

    def start(self, command: str, cwd: Path, env: Dict[str, str]) -> bool:
        """Start the server process."""
        full_env = {**os.environ, **env}
        try:
            self.process = subprocess.Popen(
                command,
                shell=True,
                cwd=str(cwd),
                env=full_env,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                preexec_fn=os.setsid,  # Create new process group for cleanup
            )
            return True
        except Exception as e:
            print(f"Failed to start server: {e}", file=sys.stderr)
            return False

    def wait_ready_http(
        self, endpoint: str, expected_status: int, timeout: int, poll_interval_ms: int
    ) -> Tuple[bool, str]:
        """Wait for HTTP endpoint to become ready."""
        start = time.time()
        last_error = ""

        while time.time() - start < timeout:
            try:
                req = urllib.request.Request(endpoint, method="GET")
                with urllib.request.urlopen(req, timeout=5) as resp:
                    if resp.status == expected_status:
                        return True, f"Ready in {time.time() - start:.1f}s"
            except urllib.error.HTTPError as e:
                if e.code == expected_status:
                    return True, f"Ready in {time.time() - start:.1f}s"
                last_error = f"HTTP {e.code}"
            except urllib.error.URLError as e:
                last_error = str(e.reason)
            except Exception as e:
                last_error = str(e)

            time.sleep(poll_interval_ms / 1000)

        return False, f"Timeout after {timeout}s: {last_error}"

    def wait_ready_tcp(self, host: str, port: int, timeout: int) -> Tuple[bool, str]:
        """Wait for TCP port to become ready."""
        start = time.time()
        while time.time() - start < timeout:
            try:
                with socket.create_connection((host, port), timeout=1):
                    return True, f"Port {port} ready in {time.time() - start:.1f}s"
            except (socket.error, socket.timeout):
                pass
            time.sleep(0.5)
        return False, f"Port {port} not ready after {timeout}s"

    def stop(self, signal_name: str = "SIGTERM", graceful_timeout: int = 5):
        """Stop the server process."""
        if not self.process:
            return

        sig = getattr(signal, signal_name, signal.SIGTERM)
        try:
            # Kill the process group
            os.killpg(os.getpgid(self.process.pid), sig)
            self.process.wait(timeout=graceful_timeout)
        except subprocess.TimeoutExpired:
            os.killpg(os.getpgid(self.process.pid), signal.SIGKILL)
            self.process.wait()
        except Exception:
            pass


def verify_server(config: Dict, project_root: Path, verbose: bool = False) -> List[VerifyResult]:
    """Run server verification."""
    results = []
    server = ServerProcess()

    try:
        # Start server
        start_config = config["start"]
        start_time = time.time()

        if verbose:
            print(f"  Starting server: {start_config['command']}")

        if not server.start(
            start_config["command"],
            project_root / start_config.get("working_dir", "."),
            start_config.get("env", {}),
        ):
            results.append(VerifyResult(
                name="server_start",
                status=VerifyStatus.ERROR,
                message="Failed to start server process",
            ))
            return results

        # Wait for readiness
        readiness = config["readiness"]
        if readiness["type"] == "http":
            ready, msg = server.wait_ready_http(
                readiness["endpoint"],
                readiness.get("expected_status", 200),
                readiness["timeout_seconds"],
                readiness.get("poll_interval_ms", 500),
            )
        elif readiness["type"] == "tcp":
            ready, msg = server.wait_ready_tcp(
                readiness.get("host", "localhost"),
                readiness["port"],
                readiness["timeout_seconds"],
            )
        else:
            ready, msg = False, f"Unknown readiness type: {readiness['type']}"

        results.append(VerifyResult(
            name="server_ready",
            status=VerifyStatus.PASS if ready else VerifyStatus.FAIL,
            duration_seconds=round(time.time() - start_time, 2),
            message=msg,
        ))

        if not ready:
            return results

        # Test endpoints
        base_url = readiness.get("endpoint", "").rsplit("/", 1)[0]
        if not base_url and "url" in readiness:
            base_url = readiness["url"].rstrip("/")

        for endpoint in config.get("endpoints", []):
            result = _test_endpoint(endpoint, base_url, verbose)
            results.append(result)

    finally:
        # Always stop server
        stop_config = config.get("stop", {})
        server.stop(
            stop_config.get("signal", "SIGTERM"),
            stop_config.get("graceful_timeout_seconds", 5),
        )

    return results


def _test_endpoint(endpoint: Dict, base_url: str, verbose: bool) -> VerifyResult:
    """Test a single API endpoint."""
    name = endpoint["name"]
    start_time = time.time()

    url = base_url + endpoint["path"]
    method = endpoint.get("method", "GET")
    headers = endpoint.get("headers", {})
    body = endpoint.get("body")
    expected = endpoint["expected"]
    timeout = endpoint.get("timeout_seconds", 10)

    if verbose:
        print(f"  Testing endpoint: {method} {endpoint['path']}")

    try:
        # Prepare request
        data = None
        if body:
            if isinstance(body, dict):
                data = json.dumps(body).encode()
                headers.setdefault("Content-Type", "application/json")
            else:
                data = str(body).encode()

        req = urllib.request.Request(url, data=data, headers=headers, method=method)

        with urllib.request.urlopen(req, timeout=timeout) as resp:
            status = resp.status
            response_body = resp.read().decode()

            # Check status
            expected_status = expected["status"]
            if isinstance(expected_status, list):
                status_ok = status in expected_status
            else:
                status_ok = status == expected_status

            if not status_ok:
                return VerifyResult(
                    name=name,
                    status=VerifyStatus.FAIL,
                    duration_seconds=round(time.time() - start_time, 2),
                    message=f"Expected status {expected_status}, got {status}",
                )

            # Check body assertions
            if "body_contains" in expected:
                for text in expected["body_contains"]:
                    if text not in response_body:
                        return VerifyResult(
                            name=name,
                            status=VerifyStatus.FAIL,
                            duration_seconds=round(time.time() - start_time, 2),
                            message=f"Response body missing: {text}",
                        )

            # Check JSON path assertions
            if "json_path" in expected:
                try:
                    json_body = json.loads(response_body)
                    for assertion in expected["json_path"]:
                        ok, msg = _check_json_path(json_body, assertion)
                        if not ok:
                            return VerifyResult(
                                name=name,
                                status=VerifyStatus.FAIL,
                                duration_seconds=round(time.time() - start_time, 2),
                                message=msg,
                            )
                except json.JSONDecodeError:
                    return VerifyResult(
                        name=name,
                        status=VerifyStatus.FAIL,
                        duration_seconds=round(time.time() - start_time, 2),
                        message="Response is not valid JSON",
                    )

            return VerifyResult(
                name=name,
                status=VerifyStatus.PASS,
                duration_seconds=round(time.time() - start_time, 2),
                message=f"Status {status} OK",
            )

    except urllib.error.HTTPError as e:
        return VerifyResult(
            name=name,
            status=VerifyStatus.FAIL,
            duration_seconds=round(time.time() - start_time, 2),
            message=f"HTTP error: {e.code} {e.reason}",
        )
    except Exception as e:
        return VerifyResult(
            name=name,
            status=VerifyStatus.ERROR,
            duration_seconds=round(time.time() - start_time, 2),
            message=str(e),
        )


def _check_json_path(data: Any, assertion: Dict) -> Tuple[bool, str]:
    """Check a JSON path assertion (simplified implementation)."""
    path = assertion["path"]
    operator = assertion["operator"]
    expected_value = assertion.get("value")

    # Simple JSON path parser (handles $.foo.bar and $.foo[0])
    if not path.startswith("$."):
        return False, f"Invalid JSON path: {path}"

    current = data
    parts = path[2:].replace("[", ".").replace("]", "").split(".")

    for part in parts:
        if not part:
            continue
        if isinstance(current, dict):
            if part not in current:
                if operator == "exists":
                    return False, f"Path {path} does not exist"
                return False, f"Key '{part}' not found in {path}"
            current = current[part]
        elif isinstance(current, list):
            try:
                idx = int(part)
                current = current[idx]
            except (ValueError, IndexError):
                return False, f"Invalid array index: {part}"
        else:
            return False, f"Cannot traverse {type(current)} at {part}"

    # Apply operator
    if operator == "exists":
        return True, ""
    elif operator == "equals":
        if current == expected_value:
            return True, ""
        return False, f"Expected {expected_value}, got {current}"
    elif operator == "contains":
        if expected_value in str(current):
            return True, ""
        return False, f"'{expected_value}' not in '{current}'"
    elif operator == "type":
        type_map = {"string": str, "number": (int, float), "boolean": bool, "array": list, "object": dict}
        expected_type = type_map.get(expected_value)
        if isinstance(current, expected_type):
            return True, ""
        return False, f"Expected type {expected_value}, got {type(current).__name__}"

    return False, f"Unknown operator: {operator}"


# =============================================================================
# CLI Verification
# =============================================================================

def verify_cli(config: Dict, project_root: Path, verbose: bool = False) -> List[VerifyResult]:
    """Run CLI verification."""
    results = []

    binary_config = config["binary"]
    build_cmd = binary_config.get("build_command")
    binary_path = binary_config["path"]

    # Build if needed
    if build_cmd:
        if verbose:
            print(f"  Building CLI: {build_cmd}")

        start_time = time.time()
        try:
            proc = subprocess.run(
                build_cmd,
                shell=True,
                cwd=str(project_root),
                capture_output=True,
                text=True,
                timeout=120,
            )
            if proc.returncode != 0:
                results.append(VerifyResult(
                    name="cli_build",
                    status=VerifyStatus.FAIL,
                    duration_seconds=round(time.time() - start_time, 2),
                    message=f"Build failed: {proc.stderr[:500]}",
                ))
                return results

            results.append(VerifyResult(
                name="cli_build",
                status=VerifyStatus.PASS,
                duration_seconds=round(time.time() - start_time, 2),
                message="Build succeeded",
            ))
        except subprocess.TimeoutExpired:
            results.append(VerifyResult(
                name="cli_build",
                status=VerifyStatus.ERROR,
                message="Build timeout",
            ))
            return results

    # Resolve binary path
    if not binary_path.startswith("/"):
        binary_path = str(project_root / binary_path)

    # Test commands
    for cmd_test in config.get("commands", []):
        result = _test_cli_command(cmd_test, binary_path, project_root, verbose)
        results.append(result)

    return results


def _test_cli_command(cmd_test: Dict, binary_path: str, project_root: Path, verbose: bool) -> VerifyResult:
    """Test a single CLI command."""
    name = cmd_test["name"]
    args = cmd_test.get("args", [])
    stdin_input = cmd_test.get("stdin")
    env = cmd_test.get("env", {})
    expected = cmd_test["expected"]
    timeout = cmd_test.get("timeout_seconds", 30)

    if verbose:
        print(f"  Testing CLI: {' '.join(args)}")

    start_time = time.time()
    full_cmd = [binary_path] + args
    full_env = {**os.environ, **env}

    try:
        proc = subprocess.run(
            full_cmd,
            cwd=str(project_root),
            env=full_env,
            input=stdin_input,
            capture_output=True,
            text=True,
            timeout=timeout,
        )

        # Check exit code
        expected_code = expected["exit_code"]
        if isinstance(expected_code, list):
            code_ok = proc.returncode in expected_code
        else:
            code_ok = proc.returncode == expected_code

        if not code_ok:
            return VerifyResult(
                name=name,
                status=VerifyStatus.FAIL,
                duration_seconds=round(time.time() - start_time, 2),
                message=f"Expected exit code {expected_code}, got {proc.returncode}",
                details={"stdout": proc.stdout[:500], "stderr": proc.stderr[:500]},
            )

        # Check stdout assertions
        if "stdout_contains" in expected:
            for text in expected["stdout_contains"]:
                if text not in proc.stdout:
                    return VerifyResult(
                        name=name,
                        status=VerifyStatus.FAIL,
                        duration_seconds=round(time.time() - start_time, 2),
                        message=f"stdout missing: {text}",
                    )

        if "stdout_matches" in expected:
            pattern = expected["stdout_matches"]
            if not re.search(pattern, proc.stdout):
                return VerifyResult(
                    name=name,
                    status=VerifyStatus.FAIL,
                    duration_seconds=round(time.time() - start_time, 2),
                    message=f"stdout doesn't match pattern: {pattern}",
                )

        # Check file assertions
        if "file_created" in expected:
            for file_path in expected["file_created"]:
                if not Path(file_path).exists():
                    return VerifyResult(
                        name=name,
                        status=VerifyStatus.FAIL,
                        duration_seconds=round(time.time() - start_time, 2),
                        message=f"Expected file not created: {file_path}",
                    )

        return VerifyResult(
            name=name,
            status=VerifyStatus.PASS,
            duration_seconds=round(time.time() - start_time, 2),
            message="Command succeeded",
        )

    except subprocess.TimeoutExpired:
        return VerifyResult(
            name=name,
            status=VerifyStatus.ERROR,
            duration_seconds=timeout,
            message=f"Command timeout after {timeout}s",
        )
    except Exception as e:
        return VerifyResult(
            name=name,
            status=VerifyStatus.ERROR,
            duration_seconds=round(time.time() - start_time, 2),
            message=str(e),
        )


# =============================================================================
# Frontend Verification (CDP)
# =============================================================================

def verify_frontend(config: Dict, project_root: Path, verbose: bool = False) -> List[VerifyResult]:
    """Run frontend verification using Chrome DevTools Protocol."""
    results = []
    server = ServerProcess()

    try:
        # Start dev server
        dev_server = config["dev_server"]
        if verbose:
            print(f"  Starting dev server: {dev_server['command']}")

        start_time = time.time()
        if not server.start(
            dev_server["command"],
            project_root / dev_server.get("working_dir", "."),
            dev_server.get("env", {}),
        ):
            results.append(VerifyResult(
                name="dev_server_start",
                status=VerifyStatus.ERROR,
                message="Failed to start dev server",
            ))
            return results

        # Wait for readiness
        readiness = config["readiness"]
        if readiness["type"] == "http":
            ready, msg = server.wait_ready_http(
                readiness["url"],
                200,
                readiness["timeout_seconds"],
                500,
            )
        else:
            ready, msg = False, "Unknown readiness type"

        results.append(VerifyResult(
            name="dev_server_ready",
            status=VerifyStatus.PASS if ready else VerifyStatus.FAIL,
            duration_seconds=round(time.time() - start_time, 2),
            message=msg,
        ))

        if not ready:
            return results

        # Run page tests with CDP
        base_url = readiness["url"].rstrip("/")
        browser_config = config.get("browser", {})

        for page_test in config.get("pages", []):
            result = _test_page_cdp(page_test, base_url, browser_config, project_root, verbose)
            results.append(result)

    finally:
        stop_config = config.get("stop", {})
        server.stop(
            stop_config.get("signal", "SIGTERM"),
            stop_config.get("graceful_timeout_seconds", 5),
        )

    return results


def _find_chrome() -> Optional[str]:
    """Find Chrome/Chromium executable."""
    candidates = [
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
        "/usr/bin/google-chrome",
        "/usr/bin/chromium",
        "/usr/bin/chromium-browser",
        "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
    ]
    for path in candidates:
        if Path(path).exists():
            return path

    # Try which
    try:
        result = subprocess.run(["which", "google-chrome"], capture_output=True, text=True)
        if result.returncode == 0:
            return result.stdout.strip()
    except:
        pass

    return None


def _test_page_cdp(
    page_test: Dict,
    base_url: str,
    browser_config: Dict,
    project_root: Path,
    verbose: bool,
) -> VerifyResult:
    """Test a page using Chrome DevTools Protocol."""
    name = page_test["name"]
    start_time = time.time()

    # Find Chrome
    chrome_path = browser_config.get("executable") or _find_chrome()
    if not chrome_path:
        return VerifyResult(
            name=name,
            status=VerifyStatus.SKIP,
            message="Chrome/Chromium not found",
        )

    # Build URL
    url = page_test["url"]
    if not url.startswith("http"):
        url = base_url + url

    if verbose:
        print(f"  Testing page: {url}")

    # For now, use a simple approach with headless Chrome and screenshot
    # A full CDP implementation would use websockets to control Chrome
    # This simplified version just checks if the page loads without errors

    try:
        import tempfile
        import shutil

        with tempfile.TemporaryDirectory() as tmpdir:
            user_data = Path(tmpdir) / "chrome-data"
            user_data.mkdir()

            args = [
                chrome_path,
                "--headless" if browser_config.get("headless", True) else "",
                f"--user-data-dir={user_data}",
                "--disable-gpu",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                f"--screenshot={tmpdir}/screenshot.png",
                "--window-size=1280,720",
                f"--virtual-time-budget=5000",  # 5 seconds of virtual time
                url,
            ]
            args = [a for a in args if a]  # Remove empty strings
            args.extend(browser_config.get("args", []))

            proc = subprocess.run(
                args,
                capture_output=True,
                text=True,
                timeout=30,
            )

            # Check if screenshot was created (indicates page loaded)
            screenshot_path = Path(tmpdir) / "screenshot.png"
            if screenshot_path.exists():
                # Save screenshot if configured
                if "screenshot" in page_test:
                    dest = project_root / page_test["screenshot"]["path"]
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy(screenshot_path, dest)

                return VerifyResult(
                    name=name,
                    status=VerifyStatus.PASS,
                    duration_seconds=round(time.time() - start_time, 2),
                    message="Page loaded successfully",
                )
            else:
                return VerifyResult(
                    name=name,
                    status=VerifyStatus.FAIL,
                    duration_seconds=round(time.time() - start_time, 2),
                    message=f"Page failed to load: {proc.stderr[:200]}",
                )

    except subprocess.TimeoutExpired:
        return VerifyResult(
            name=name,
            status=VerifyStatus.ERROR,
            message="Page load timeout",
        )
    except Exception as e:
        return VerifyResult(
            name=name,
            status=VerifyStatus.ERROR,
            duration_seconds=round(time.time() - start_time, 2),
            message=str(e),
        )


# =============================================================================
# Smoke Tests
# =============================================================================

def run_smoke_tests(smoke_tests: List[Dict], project_root: Path, verbose: bool) -> List[VerifyResult]:
    """Run smoke tests."""
    results = []

    for test in smoke_tests:
        name = test["name"]
        test_type = test["type"]
        required = test.get("required", True)

        if verbose:
            print(f"  Smoke test: {name}")

        start_time = time.time()

        if test_type == "command":
            try:
                proc = subprocess.run(
                    test["command"],
                    shell=True,
                    cwd=str(project_root),
                    capture_output=True,
                    text=True,
                    timeout=60,
                )
                expected = test.get("expected_exit_code", 0)
                passed = proc.returncode == expected
                results.append(VerifyResult(
                    name=f"smoke_{name}",
                    status=VerifyStatus.PASS if passed else (VerifyStatus.FAIL if required else VerifyStatus.SKIP),
                    duration_seconds=round(time.time() - start_time, 2),
                    message=f"Exit code: {proc.returncode}" if passed else f"Expected {expected}, got {proc.returncode}",
                ))
            except Exception as e:
                results.append(VerifyResult(
                    name=f"smoke_{name}",
                    status=VerifyStatus.ERROR,
                    message=str(e),
                ))

        elif test_type == "file_exists":
            missing = [p for p in test.get("paths", []) if not (project_root / p).exists()]
            if missing:
                results.append(VerifyResult(
                    name=f"smoke_{name}",
                    status=VerifyStatus.FAIL if required else VerifyStatus.SKIP,
                    message=f"Missing files: {missing}",
                ))
            else:
                results.append(VerifyResult(
                    name=f"smoke_{name}",
                    status=VerifyStatus.PASS,
                    message="All files exist",
                ))

        elif test_type == "http":
            try:
                with urllib.request.urlopen(test["url"], timeout=10) as resp:
                    expected = test.get("expected_status", 200)
                    passed = resp.status == expected
                    results.append(VerifyResult(
                        name=f"smoke_{name}",
                        status=VerifyStatus.PASS if passed else VerifyStatus.FAIL,
                        message=f"Status: {resp.status}",
                    ))
            except Exception as e:
                results.append(VerifyResult(
                    name=f"smoke_{name}",
                    status=VerifyStatus.FAIL if required else VerifyStatus.SKIP,
                    message=str(e),
                ))

    return results


# =============================================================================
# Prerequisites (Environment Preflight Checks)
# =============================================================================

@dataclass
class PrerequisiteResult:
    """Result of a prerequisite check."""
    name: str
    check_type: str
    passed: bool
    required: bool
    message: str


def run_prerequisites(
    prerequisites: Dict,
    project_root: Path,
    verbose: bool = False
) -> Tuple[bool, List[PrerequisiteResult]]:
    """
    Run environment prerequisite checks before verification.

    Returns:
        (all_required_passed, results)
    """
    results = []

    if prerequisites.get("skip"):
        if verbose:
            print("  Prerequisites: SKIPPED (skip=true in config)")
        return True, results

    # Check databases
    for db in prerequisites.get("databases", []):
        result = _check_database(db, verbose)
        results.append(result)

    # Check environment variables
    for env_var in prerequisites.get("env_vars", []):
        result = _check_env_var(env_var, verbose)
        results.append(result)

    # Check external services
    for service in prerequisites.get("services", []):
        result = _check_service(service, verbose)
        results.append(result)

    # Check custom commands
    for cmd in prerequisites.get("commands", []):
        result = _check_command(cmd, project_root, verbose)
        results.append(result)

    # Check paths
    for path_check in prerequisites.get("paths", []):
        result = _check_path(path_check, project_root, verbose)
        results.append(result)

    # Determine if all required checks passed
    all_required_passed = all(
        r.passed for r in results if r.required
    )

    return all_required_passed, results


def _check_database(db: Dict, verbose: bool) -> PrerequisiteResult:
    """Check database connectivity."""
    name = db.get("name", "Database")
    db_type = db.get("type", "custom")
    required = db.get("required", True)
    timeout = db.get("timeout_seconds", 5)

    # Get host and port from env or direct values
    host = db.get("host") or os.environ.get(db.get("host_env", ""), "")
    port_str = str(db.get("port") or os.environ.get(db.get("port_env", ""), ""))

    if not host:
        return PrerequisiteResult(
            name=name, check_type="database", passed=False, required=required,
            message=f"Host not configured (env: {db.get('host_env')})"
        )

    try:
        port = int(port_str) if port_str else _default_port(db_type)
    except ValueError:
        return PrerequisiteResult(
            name=name, check_type="database", passed=False, required=required,
            message=f"Invalid port: {port_str}"
        )

    if verbose:
        print(f"  Checking {name} at {host}:{port}...")

    # TCP connectivity check
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return PrerequisiteResult(
                name=name, check_type="database", passed=True, required=required,
                message=f"Connected to {host}:{port}"
            )
    except (socket.error, socket.timeout) as e:
        return PrerequisiteResult(
            name=name, check_type="database", passed=False, required=required,
            message=f"Cannot connect to {host}:{port} - {e}"
        )


def _default_port(db_type: str) -> int:
    """Return default port for database type."""
    ports = {
        "mysql": 3306,
        "postgres": 5432,
        "redis": 6379,
        "mongodb": 27017,
        "sqlite": 0,  # Not applicable
    }
    return ports.get(db_type, 0)


def _check_env_var(env_var: Dict, verbose: bool) -> PrerequisiteResult:
    """Check environment variable."""
    name = env_var["name"]
    required = env_var.get("required", True)
    pattern = env_var.get("pattern")
    not_empty = env_var.get("not_empty", False)

    value = os.environ.get(name)

    if value is None:
        return PrerequisiteResult(
            name=f"env:{name}", check_type="env_var", passed=False, required=required,
            message=f"Environment variable {name} is not set"
        )

    if not_empty and not value:
        return PrerequisiteResult(
            name=f"env:{name}", check_type="env_var", passed=False, required=required,
            message=f"Environment variable {name} is empty"
        )

    if pattern:
        if not re.match(pattern, value):
            return PrerequisiteResult(
                name=f"env:{name}", check_type="env_var", passed=False, required=required,
                message=f"Environment variable {name} does not match pattern {pattern}"
            )

    if verbose:
        # Don't log the actual value for security
        print(f"  env:{name} = [set]")

    return PrerequisiteResult(
        name=f"env:{name}", check_type="env_var", passed=True, required=required,
        message="OK"
    )


def _check_service(service: Dict, verbose: bool) -> PrerequisiteResult:
    """Check external service connectivity."""
    name = service.get("name", "Service")
    service_type = service.get("type", "http")
    required = service.get("required", True)
    timeout = service.get("timeout_seconds", 5)

    if verbose:
        print(f"  Checking service: {name}...")

    if service_type == "http":
        url = service.get("url", "")
        expected_status = service.get("expected_status", 200)
        try:
            req = urllib.request.Request(url, method="GET")
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                if resp.status == expected_status:
                    return PrerequisiteResult(
                        name=name, check_type="service", passed=True, required=required,
                        message=f"HTTP {resp.status} from {url}"
                    )
                else:
                    return PrerequisiteResult(
                        name=name, check_type="service", passed=False, required=required,
                        message=f"Expected {expected_status}, got {resp.status}"
                    )
        except Exception as e:
            return PrerequisiteResult(
                name=name, check_type="service", passed=False, required=required,
                message=f"Cannot reach {url} - {e}"
            )

    elif service_type == "tcp":
        host = service.get("host", "localhost")
        port = service.get("port", 80)
        try:
            with socket.create_connection((host, port), timeout=timeout):
                return PrerequisiteResult(
                    name=name, check_type="service", passed=True, required=required,
                    message=f"TCP {host}:{port} reachable"
                )
        except (socket.error, socket.timeout) as e:
            return PrerequisiteResult(
                name=name, check_type="service", passed=False, required=required,
                message=f"Cannot connect to {host}:{port} - {e}"
            )

    return PrerequisiteResult(
        name=name, check_type="service", passed=False, required=required,
        message=f"Unknown service type: {service_type}"
    )


def _check_command(cmd: Dict, project_root: Path, verbose: bool) -> PrerequisiteResult:
    """Check custom command."""
    name = cmd.get("name", "Command")
    command = cmd.get("command", "")
    expected_exit = cmd.get("expected_exit_code", 0)
    required = cmd.get("required", True)
    timeout = cmd.get("timeout_seconds", 10)

    if verbose:
        print(f"  Running: {name}...")

    try:
        proc = subprocess.run(
            command,
            shell=True,
            cwd=str(project_root),
            capture_output=True,
            timeout=timeout
        )
        if proc.returncode == expected_exit:
            return PrerequisiteResult(
                name=name, check_type="command", passed=True, required=required,
                message=f"Exit code {proc.returncode}"
            )
        else:
            return PrerequisiteResult(
                name=name, check_type="command", passed=False, required=required,
                message=f"Expected exit {expected_exit}, got {proc.returncode}"
            )
    except subprocess.TimeoutExpired:
        return PrerequisiteResult(
            name=name, check_type="command", passed=False, required=required,
            message=f"Timeout after {timeout}s"
        )
    except Exception as e:
        return PrerequisiteResult(
            name=name, check_type="command", passed=False, required=required,
            message=str(e)
        )


def _check_path(path_check: Dict, project_root: Path, verbose: bool) -> PrerequisiteResult:
    """Check file/directory exists."""
    path = path_check.get("path", "")
    path_type = path_check.get("type", "file")
    required = path_check.get("required", True)

    full_path = project_root / path if not os.path.isabs(path) else Path(path)

    if verbose:
        print(f"  Checking path: {path}...")

    if not full_path.exists():
        return PrerequisiteResult(
            name=f"path:{path}", check_type="path", passed=False, required=required,
            message=f"{path_type.title()} not found: {path}"
        )

    if path_type == "file" and not full_path.is_file():
        return PrerequisiteResult(
            name=f"path:{path}", check_type="path", passed=False, required=required,
            message=f"Expected file, found directory: {path}"
        )

    if path_type == "directory" and not full_path.is_dir():
        return PrerequisiteResult(
            name=f"path:{path}", check_type="path", passed=False, required=required,
            message=f"Expected directory, found file: {path}"
        )

    return PrerequisiteResult(
        name=f"path:{path}", check_type="path", passed=True, required=required,
        message="OK"
    )


# =============================================================================
# Main Pipeline
# =============================================================================

def load_config(project_root: Path) -> Optional[Dict]:
    """Load verify.json config file using config resolver chain."""
    return resolve_config("verify", project_root)


def run_verification(
    project_root: Path,
    verify_type: Optional[str] = None,
    no_cleanup: bool = False,
    verbose: bool = False,
    skip_prerequisites: bool = False,
) -> VerifyReport:
    """Run the full verification pipeline."""
    # Get adapter first
    adapter = get_adapter(project_root, verbose=verbose)

    report = VerifyReport(
        project_root=str(project_root),
        app_type="unknown",
        adapter=adapter.get("language", "generic"),
        timestamp=time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    )

    start_time = time.time()

    # Load or generate config
    config = load_config(project_root)
    if not config:
        if verbose:
            print(f"No verify.json found, auto-detecting with {adapter.get('display_name', 'generic')} adapter...")
        app_type = detect_app_type(project_root, adapter)
        config = generate_default_config(project_root, app_type, adapter)

        # Save generated config for future use
        harness_root = get_harness_root(project_root)
        config_dir = harness_root / "config"
        config_dir.mkdir(parents=True, exist_ok=True)
        config_path = config_dir / "verify.json"
        config_path.write_text(json.dumps(config, indent=2))
        if verbose:
            print(f"Generated {config_path}")

    report.app_type = config.get("app_type", "unknown")

    # Run prerequisites check FIRST
    prerequisites = config.get("prerequisites", {})
    if prerequisites and not skip_prerequisites:
        if verbose:
            print("\n[Prerequisites Check]")
        prereq_passed, prereq_results = run_prerequisites(prerequisites, project_root, verbose)

        # Add prerequisite results to report
        for pr in prereq_results:
            status = VerifyStatus.PASS if pr.passed else (VerifyStatus.FAIL if pr.required else VerifyStatus.SKIP)
            report.results.append({
                "name": f"prereq_{pr.name}",
                "status": status.value,
                "duration_seconds": 0,
                "message": pr.message,
                "details": {"check_type": pr.check_type, "required": pr.required}
            })

        if not prereq_passed:
            # Abort verification - environment not ready
            failed_required = [pr for pr in prereq_results if pr.required and not pr.passed]
            report.total_duration_seconds = round(time.time() - start_time, 2)
            report.all_passed = False
            report.summary = {
                "total": len(prereq_results),
                "passed": sum(1 for pr in prereq_results if pr.passed),
                "failed": sum(1 for pr in prereq_results if not pr.passed and pr.required),
                "skipped": 0,
                "errors": 0,
                "aborted": True,
                "abort_reason": f"Prerequisites failed: {', '.join(pr.name for pr in failed_required)}"
            }
            if verbose:
                print(f"\n❌ Prerequisites FAILED - verification aborted")
                for pr in failed_required:
                    print(f"   • {pr.name}: {pr.message}")
            return report
        elif verbose:
            print("  ✅ All prerequisites passed")

    # Determine what to verify
    verification = config.get("verification", {})
    types_to_run = []

    if verify_type:
        if verify_type in verification:
            types_to_run = [verify_type]
        else:
            print(f"Warning: {verify_type} not configured", file=sys.stderr)
    else:
        types_to_run = list(verification.keys())

    # Run verifications
    all_results = list(report.results)  # Include prerequisite results

    if "server" in types_to_run and "server" in verification:
        if verbose:
            print("\n[Server Verification]")
        results = verify_server(verification["server"], project_root, verbose)
        all_results.extend([asdict(r) for r in results])

    if "cli" in types_to_run and "cli" in verification:
        if verbose:
            print("\n[CLI Verification]")
        results = verify_cli(verification["cli"], project_root, verbose)
        all_results.extend([asdict(r) for r in results])

    if "frontend" in types_to_run and "frontend" in verification:
        if verbose:
            print("\n[Frontend Verification]")
        results = verify_frontend(verification["frontend"], project_root, verbose)
        all_results.extend([asdict(r) for r in results])

    # Run smoke tests
    if config.get("smoke_tests"):
        if verbose:
            print("\n[Smoke Tests]")
        results = run_smoke_tests(config["smoke_tests"], project_root, verbose)
        all_results.extend([asdict(r) for r in results])

    # Cleanup
    if not no_cleanup and "cleanup" in config:
        cleanup = config["cleanup"]
        for path in cleanup.get("remove_paths", []):
            try:
                full_path = project_root / path
                if full_path.exists():
                    if full_path.is_dir():
                        import shutil
                        shutil.rmtree(full_path)
                    else:
                        full_path.unlink()
            except Exception as e:
                if verbose:
                    print(f"Cleanup warning: {e}")

        for cmd in cleanup.get("commands", []):
            try:
                subprocess.run(cmd, shell=True, cwd=str(project_root), timeout=30)
            except:
                pass

    # Build report
    report.results = all_results
    report.total_duration_seconds = round(time.time() - start_time, 2)

    # Count results - all_results is now list of dicts
    passed = sum(1 for r in all_results if r.get("status") == "pass")
    failed = sum(1 for r in all_results if r.get("status") == "fail")
    skipped = sum(1 for r in all_results if r.get("status") == "skip")
    errors = sum(1 for r in all_results if r.get("status") == "error")

    report.all_passed = failed == 0 and errors == 0
    report.summary = {
        "total": len(all_results),
        "passed": passed,
        "failed": failed,
        "skipped": skipped,
        "errors": errors,
    }

    return report


def main():
    """Main entry point."""
    import argparse

    parser = argparse.ArgumentParser(description="Runtime verification pipeline")
    parser.add_argument("path", nargs="?", default=".", help="Project root path")
    parser.add_argument("--json", action="store_true", help="Output as JSON")
    parser.add_argument("--type", choices=["server", "cli", "frontend"],
                        help="Run only specific verification type")
    parser.add_argument("--no-cleanup", action="store_true", help="Skip cleanup step")
    parser.add_argument("-v", "--verbose", action="store_true", help="Verbose output")
    parser.add_argument("--output", type=str, help="Save report to file")
    parser.add_argument("--generate-config", action="store_true",
                        help="Only generate config, don't run verification")
    parser.add_argument("--skip-prerequisites", action="store_true",
                        help="Skip environment prerequisite checks")

    args = parser.parse_args()
    project_root = Path(args.path).resolve()

    if not project_root.is_dir():
        print(f"Error: {project_root} is not a directory", file=sys.stderr)
        sys.exit(2)

    # Generate config only
    if args.generate_config:
        config = load_config(project_root)
        if config:
            source = config.get("_resolved_from", "harness/config/verify.json")
            print(f"Config already exists at {source}")
        else:
            adapter = get_adapter(project_root, verbose=args.verbose)
            app_type = detect_app_type(project_root, adapter)
            config = generate_default_config(project_root, app_type, adapter)
            harness_root = get_harness_root(project_root)
            config_dir = harness_root / "config"
            config_dir.mkdir(parents=True, exist_ok=True)
            config_path = config_dir / "verify.json"
            config_path.write_text(json.dumps(config, indent=2))
            print(f"Generated {config_path}")
            print(f"Detected app type: {app_type}")
            print(f"Adapter: {adapter.get('display_name', 'generic')}")
        sys.exit(0)

    # Run verification
    report = run_verification(
        project_root,
        verify_type=args.type,
        no_cleanup=args.no_cleanup,
        verbose=args.verbose,
        skip_prerequisites=args.skip_prerequisites,
    )

    # Output
    if args.json:
        output = json.dumps(asdict(report), indent=2)
        if args.output:
            Path(args.output).write_text(output)
        print(output)
    else:
        print(f"\n{'=' * 50}")
        print(f"Runtime Verification: {project_root.name}")
        print(f"App Type: {report.app_type}")
        if report.adapter:
            print(f"Adapter: {report.adapter}")
        print(f"{'=' * 50}\n")

        for result in report.results:
            icons = {"pass": "✅", "fail": "❌", "skip": "⏭️", "error": "💥"}
            icon = icons.get(result["status"], "?")
            duration = f" ({result['duration_seconds']}s)" if result.get("duration_seconds") else ""
            print(f"  {icon} {result['name']}: {result['status'].upper()}{duration}")
            if result.get("message"):
                print(f"     {result['message']}")

        s = report.summary
        print(f"\nSummary: {s['passed']} passed, {s['failed']} failed, {s['skipped']} skipped, {s['errors']} errors")
        print(f"Duration: {report.total_duration_seconds}s")
        print(f"Result: {'✅ ALL PASSED' if report.all_passed else '❌ FAILURES DETECTED'}\n")

        if args.output:
            Path(args.output).write_text(json.dumps(asdict(report), indent=2))
            print(f"Report saved to: {args.output}")

    sys.exit(0 if report.all_passed else 1)


if __name__ == "__main__":
    main()
