#!/usr/bin/env python3
"""
Pre-flight checks for runtime verification.

Validates that all prerequisites are satisfied BEFORE attempting to start servers,
run CLI commands, or launch frontend dev servers. This catches environment issues
early — missing env vars, unavailable ports, uninstalled dependencies — so the
developer gets a clear, actionable error instead of a cryptic runtime failure.

Reads prerequisites from:
  1. harness/config/environment.json (preferred — comprehensive environment contract)
  2. harness/config/verify.json (fallback — the "prerequisites" key)
  3. Auto-detects basic requirements from the verify config itself
"""

import json
import os
import re
import shutil
import socket
import subprocess
import sys
import time
from dataclasses import asdict, dataclass, field
from enum import Enum
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
import urllib.request
import urllib.error


class CheckStatus(str, Enum):
    PASS = "pass"
    FAIL = "fail"
    WARN = "warn"
    SKIP = "skip"


@dataclass
class CheckResult:
    """Result of a single pre-flight check."""
    category: str           # e.g., "environment", "dependency", "port", "service", "file"
    name: str               # Human-readable check name
    status: CheckStatus
    message: str = ""
    fix_suggestion: str = ""  # Actionable advice to fix the issue
    required: bool = True     # If False, failure is a warning not a blocker


@dataclass
class PreflightReport:
    """Aggregated pre-flight check report."""
    project_root: str
    timestamp: str = ""
    all_satisfied: bool = False
    results: List[dict] = field(default_factory=list)
    summary: Dict = field(default_factory=dict)
    blockers: List[dict] = field(default_factory=list)    # Required checks that failed
    warnings: List[dict] = field(default_factory=list)    # Optional checks that failed


# =============================================================================
# Environment Variable Checks
# =============================================================================

def check_environment(prereqs: Dict, project_root: Path) -> List[CheckResult]:
    """Check that required environment variables are set."""
    results = []
    env_config = prereqs.get("environment", {})

    # Check required vars
    for var in env_config.get("required_vars", []):
        value = os.environ.get(var)
        if value:
            results.append(CheckResult(
                category="environment",
                name=f"env:{var}",
                status=CheckStatus.PASS,
                message=f"${var} is set",
                required=True,
            ))
        else:
            # Also check .env files as a hint
            dotenv_hint = _check_dotenv_for_var(var, project_root)
            results.append(CheckResult(
                category="environment",
                name=f"env:{var}",
                status=CheckStatus.FAIL,
                message=f"${var} is not set",
                fix_suggestion=dotenv_hint or f"export {var}=<value> or add {var}=<value> to .env",
                required=True,
            ))

    # Check optional vars
    for var in env_config.get("optional_vars", []):
        value = os.environ.get(var)
        results.append(CheckResult(
            category="environment",
            name=f"env:{var}",
            status=CheckStatus.PASS if value else CheckStatus.WARN,
            message=f"${var} is {'set' if value else 'not set (optional)'}",
            required=False,
        ))

    # Check var value patterns (e.g., URL format, non-empty)
    for var_check in env_config.get("value_checks", []):
        var = var_check["var"]
        value = os.environ.get(var, "")
        pattern = var_check.get("pattern")
        if pattern and value:
            if re.match(pattern, value):
                results.append(CheckResult(
                    category="environment",
                    name=f"env:{var}:format",
                    status=CheckStatus.PASS,
                    message=f"${var} matches expected format",
                    required=var_check.get("required", True),
                ))
            else:
                results.append(CheckResult(
                    category="environment",
                    name=f"env:{var}:format",
                    status=CheckStatus.FAIL,
                    message=f"${var} value doesn't match pattern: {pattern}",
                    fix_suggestion=var_check.get("hint", f"Set {var} to a value matching: {pattern}"),
                    required=var_check.get("required", True),
                ))

    return results


def _check_dotenv_for_var(var: str, project_root: Path) -> Optional[str]:
    """Check if a var exists in .env files (as a hint for the user)."""
    for env_file in [".env", ".env.example", ".env.local", ".env.development"]:
        env_path = project_root / env_file
        if env_path.exists():
            try:
                content = env_path.read_text()
                for line in content.splitlines():
                    line = line.strip()
                    if line.startswith(f"{var}="):
                        if env_file == ".env":
                            return f"${var} exists in .env but isn't loaded. Run: source .env or use dotenv"
                        else:
                            return f"${var} found in {env_file}. Copy to .env: cp {env_file} .env"
            except Exception:
                pass
    return None


# =============================================================================
# Dependency Checks
# =============================================================================

def check_dependencies(prereqs: Dict, project_root: Path) -> List[CheckResult]:
    """Check that required tools and runtimes are installed."""
    results = []
    deps_config = prereqs.get("dependencies", {})

    # Check commands exist
    for cmd in deps_config.get("commands", []):
        # cmd can be "node --version" or just "node"
        parts = cmd.split()
        binary = parts[0]

        if shutil.which(binary):
            # Run the command to get version info
            try:
                proc = subprocess.run(
                    cmd, shell=True, capture_output=True, text=True, timeout=10,
                )
                version_output = proc.stdout.strip() or proc.stderr.strip()
                results.append(CheckResult(
                    category="dependency",
                    name=f"cmd:{binary}",
                    status=CheckStatus.PASS,
                    message=f"{binary} available: {version_output[:80]}",
                    required=True,
                ))
            except Exception as e:
                results.append(CheckResult(
                    category="dependency",
                    name=f"cmd:{binary}",
                    status=CheckStatus.PASS,
                    message=f"{binary} found at {shutil.which(binary)}",
                    required=True,
                ))
        else:
            results.append(CheckResult(
                category="dependency",
                name=f"cmd:{binary}",
                status=CheckStatus.FAIL,
                message=f"{binary} not found in PATH",
                fix_suggestion=_suggest_install(binary),
                required=True,
            ))

    # Check minimum versions
    min_versions = deps_config.get("min_versions", {})
    for tool, min_ver in min_versions.items():
        actual_ver = _get_tool_version(tool)
        if actual_ver is None:
            results.append(CheckResult(
                category="dependency",
                name=f"version:{tool}",
                status=CheckStatus.FAIL,
                message=f"{tool} not found, required >= {min_ver}",
                fix_suggestion=_suggest_install(tool),
                required=True,
            ))
        elif _compare_versions(actual_ver, min_ver) >= 0:
            results.append(CheckResult(
                category="dependency",
                name=f"version:{tool}",
                status=CheckStatus.PASS,
                message=f"{tool} {actual_ver} >= {min_ver}",
                required=True,
            ))
        else:
            results.append(CheckResult(
                category="dependency",
                name=f"version:{tool}",
                status=CheckStatus.FAIL,
                message=f"{tool} {actual_ver} < {min_ver} (minimum required)",
                fix_suggestion=f"Upgrade {tool} to >= {min_ver}",
                required=True,
            ))

    # Check package manager dependencies installed
    for pkg_check in deps_config.get("packages_installed", []):
        result = _check_packages_installed(pkg_check, project_root)
        results.append(result)

    return results


def _get_tool_version(tool: str) -> Optional[str]:
    """Extract version from a tool's --version output."""
    if not shutil.which(tool):
        return None
    try:
        proc = subprocess.run(
            [tool, "--version"], capture_output=True, text=True, timeout=10,
        )
        output = proc.stdout.strip() + proc.stderr.strip()
        # Try to extract a semver-like version string
        match = re.search(r"(\d+\.\d+(?:\.\d+)?)", output)
        if match:
            return match.group(1)
    except Exception:
        pass
    return None


def _compare_versions(a: str, b: str) -> int:
    """Compare two version strings. Returns >0 if a>b, 0 if equal, <0 if a<b."""
    def _parts(v):
        return [int(x) for x in v.split(".")]
    pa, pb = _parts(a), _parts(b)
    # Pad shorter list
    while len(pa) < len(pb):
        pa.append(0)
    while len(pb) < len(pa):
        pb.append(0)
    for x, y in zip(pa, pb):
        if x != y:
            return x - y
    return 0


def _suggest_install(binary: str) -> str:
    """Suggest how to install a missing tool."""
    suggestions = {
        "node": "Install Node.js: https://nodejs.org/ or use nvm: nvm install --lts",
        "npm": "npm comes with Node.js. Install Node.js first.",
        "pnpm": "Install pnpm: npm install -g pnpm",
        "yarn": "Install yarn: npm install -g yarn",
        "bun": "Install bun: curl -fsSL https://bun.sh/install | bash",
        "go": "Install Go: https://go.dev/dl/",
        "python3": "Install Python 3: https://www.python.org/downloads/",
        "pip": "pip comes with Python. Install Python first.",
        "uv": "Install uv: curl -LsSf https://astral.sh/uv/install.sh | sh",
        "java": "Install Java 17+: https://adoptium.net/",
        "mvn": "Install Maven: https://maven.apache.org/install.html",
        "gradle": "Install Gradle: https://gradle.org/install/",
        "docker": "Install Docker: https://docs.docker.com/get-docker/",
        "docker-compose": "Install Docker Compose: https://docs.docker.com/compose/install/",
        "kubectl": "Install kubectl: https://kubernetes.io/docs/tasks/tools/",
        "helm": "Install Helm: https://helm.sh/docs/intro/install/",
        "curl": "Install curl via your system package manager",
        "jq": "Install jq: brew install jq (macOS) or apt install jq (Linux)",
        "google-chrome": "Install Chrome: https://www.google.com/chrome/",
        "chromium": "Install Chromium via your system package manager",
    }
    return suggestions.get(binary, f"Install {binary} and ensure it's in your PATH")


def _check_packages_installed(pkg_check: Dict, project_root: Path) -> CheckResult:
    """Check if project dependencies are installed (node_modules, vendor, etc.)."""
    pkg_type = pkg_check.get("type", "auto")
    check_path = pkg_check.get("path")

    if pkg_type == "npm" or (pkg_type == "auto" and (project_root / "package.json").exists()):
        node_modules = project_root / (check_path or "node_modules")
        if node_modules.exists() and any(node_modules.iterdir()):
            return CheckResult(
                category="dependency",
                name="packages:npm",
                status=CheckStatus.PASS,
                message="node_modules exists and is non-empty",
                required=True,
            )
        else:
            return CheckResult(
                category="dependency",
                name="packages:npm",
                status=CheckStatus.FAIL,
                message="node_modules missing or empty",
                fix_suggestion="Run: npm install",
                required=True,
            )

    if pkg_type == "go" or (pkg_type == "auto" and (project_root / "go.mod").exists()):
        # Go modules are auto-downloaded, just check go.sum exists
        if (project_root / "go.sum").exists():
            return CheckResult(
                category="dependency",
                name="packages:go",
                status=CheckStatus.PASS,
                message="go.sum exists (modules will be auto-downloaded)",
                required=True,
            )
        else:
            return CheckResult(
                category="dependency",
                name="packages:go",
                status=CheckStatus.WARN,
                message="go.sum missing — modules may need downloading",
                fix_suggestion="Run: go mod download",
                required=False,
            )

    if pkg_type == "python" or (pkg_type == "auto" and (
        (project_root / "requirements.txt").exists() or (project_root / "pyproject.toml").exists()
    )):
        # Check if virtual env is active or key packages importable
        venv_active = os.environ.get("VIRTUAL_ENV") is not None
        return CheckResult(
            category="dependency",
            name="packages:python",
            status=CheckStatus.PASS if venv_active else CheckStatus.WARN,
            message="Virtual environment active" if venv_active else "No virtual environment detected",
            fix_suggestion="" if venv_active else "Activate a virtualenv or run: pip install -r requirements.txt",
            required=False,
        )

    return CheckResult(
        category="dependency",
        name="packages:unknown",
        status=CheckStatus.SKIP,
        message="Could not determine package type",
    )


# =============================================================================
# Port Availability Checks
# =============================================================================

def check_ports(prereqs: Dict, project_root: Path) -> List[CheckResult]:
    """Check that required ports are free."""
    results = []
    ports_config = prereqs.get("ports", {})

    for port in ports_config.get("required_free", []):
        if isinstance(port, dict):
            port_num = port["port"]
            name = port.get("name", f"port {port_num}")
        else:
            port_num = port
            name = f"port {port_num}"

        is_free, occupant = _check_port_free(port_num)
        if is_free:
            results.append(CheckResult(
                category="port",
                name=f"port:{port_num}",
                status=CheckStatus.PASS,
                message=f"Port {port_num} is available",
                required=True,
            ))
        else:
            results.append(CheckResult(
                category="port",
                name=f"port:{port_num}",
                status=CheckStatus.FAIL,
                message=f"Port {port_num} ({name}) is in use" + (f" by {occupant}" if occupant else ""),
                fix_suggestion=f"Kill the process using port {port_num}: lsof -ti :{port_num} | xargs kill -9",
                required=True,
            ))

    return results


def _check_port_free(port: int) -> Tuple[bool, Optional[str]]:
    """Check if a port is free. Returns (is_free, occupant_info)."""
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(1)
            result = s.connect_ex(("localhost", port))
            if result != 0:
                return True, None  # Port is free

            # Port is in use, try to identify what's using it
            occupant = None
            try:
                proc = subprocess.run(
                    ["lsof", "-i", f":{port}", "-t"],
                    capture_output=True, text=True, timeout=5,
                )
                if proc.stdout.strip():
                    pid = proc.stdout.strip().split("\n")[0]
                    ps_proc = subprocess.run(
                        ["ps", "-p", pid, "-o", "comm="],
                        capture_output=True, text=True, timeout=5,
                    )
                    occupant = ps_proc.stdout.strip()
            except Exception:
                pass
            return False, occupant
    except Exception:
        return True, None  # Assume free if we can't check


# =============================================================================
# External Service Checks
# =============================================================================

def check_services(prereqs: Dict, project_root: Path) -> List[CheckResult]:
    """Check that external services (databases, caches, etc.) are reachable."""
    results = []
    services_config = prereqs.get("services", {})

    for svc in services_config.get("checks", []):
        name = svc.get("name", "unknown-service")
        svc_type = svc["type"]
        required = svc.get("required", True)

        if svc_type == "tcp":
            host = svc.get("host", "localhost")
            port = svc["port"]
            timeout = svc.get("timeout_seconds", 3)
            try:
                with socket.create_connection((host, port), timeout=timeout):
                    results.append(CheckResult(
                        category="service",
                        name=f"service:{name}",
                        status=CheckStatus.PASS,
                        message=f"{name} reachable at {host}:{port}",
                        required=required,
                    ))
            except (socket.error, socket.timeout):
                results.append(CheckResult(
                    category="service",
                    name=f"service:{name}",
                    status=CheckStatus.FAIL if required else CheckStatus.WARN,
                    message=f"{name} unreachable at {host}:{port}",
                    fix_suggestion=svc.get("fix_suggestion", f"Start {name} or check if it's running on {host}:{port}"),
                    required=required,
                ))

        elif svc_type == "http":
            url = svc["url"]
            timeout = svc.get("timeout_seconds", 5)
            expected_status = svc.get("expected_status", 200)
            try:
                req = urllib.request.Request(url, method="GET")
                with urllib.request.urlopen(req, timeout=timeout) as resp:
                    if resp.status == expected_status:
                        results.append(CheckResult(
                            category="service",
                            name=f"service:{name}",
                            status=CheckStatus.PASS,
                            message=f"{name} responded OK at {url}",
                            required=required,
                        ))
                    else:
                        results.append(CheckResult(
                            category="service",
                            name=f"service:{name}",
                            status=CheckStatus.WARN,
                            message=f"{name} responded with {resp.status} (expected {expected_status})",
                            required=required,
                        ))
            except Exception as e:
                results.append(CheckResult(
                    category="service",
                    name=f"service:{name}",
                    status=CheckStatus.FAIL if required else CheckStatus.WARN,
                    message=f"{name} unreachable: {str(e)[:100]}",
                    fix_suggestion=svc.get("fix_suggestion", f"Start {name} or verify URL: {url}"),
                    required=required,
                ))

        elif svc_type == "command":
            # Run a custom command to check service health
            command = svc["command"]
            timeout = svc.get("timeout_seconds", 10)
            try:
                proc = subprocess.run(
                    command, shell=True, capture_output=True, text=True, timeout=timeout,
                )
                if proc.returncode == 0:
                    results.append(CheckResult(
                        category="service",
                        name=f"service:{name}",
                        status=CheckStatus.PASS,
                        message=f"{name} check passed",
                        required=required,
                    ))
                else:
                    results.append(CheckResult(
                        category="service",
                        name=f"service:{name}",
                        status=CheckStatus.FAIL if required else CheckStatus.WARN,
                        message=f"{name} check failed: {proc.stderr[:100]}",
                        fix_suggestion=svc.get("fix_suggestion", f"Start {name}"),
                        required=required,
                    ))
            except Exception as e:
                results.append(CheckResult(
                    category="service",
                    name=f"service:{name}",
                    status=CheckStatus.FAIL if required else CheckStatus.WARN,
                    message=f"{name} check error: {str(e)[:100]}",
                    fix_suggestion=svc.get("fix_suggestion", f"Start {name}"),
                    required=required,
                ))

    return results


# =============================================================================
# File Checks
# =============================================================================

def check_files(prereqs: Dict, project_root: Path) -> List[CheckResult]:
    """Check that required files exist."""
    results = []
    files_config = prereqs.get("files", {})

    for file_path in files_config.get("required", []):
        full_path = project_root / file_path
        if full_path.exists():
            results.append(CheckResult(
                category="file",
                name=f"file:{file_path}",
                status=CheckStatus.PASS,
                message=f"{file_path} exists",
                required=True,
            ))
        else:
            # Check for example/template files
            fix = _suggest_file_fix(file_path, project_root)
            results.append(CheckResult(
                category="file",
                name=f"file:{file_path}",
                status=CheckStatus.FAIL,
                message=f"{file_path} not found",
                fix_suggestion=fix,
                required=True,
            ))

    for file_path in files_config.get("optional", []):
        full_path = project_root / file_path
        results.append(CheckResult(
            category="file",
            name=f"file:{file_path}",
            status=CheckStatus.PASS if full_path.exists() else CheckStatus.WARN,
            message=f"{file_path} {'exists' if full_path.exists() else 'not found (optional)'}",
            required=False,
        ))

    # Check file content patterns (e.g., "config.yaml must contain 'database:' key")
    for content_check in files_config.get("content_checks", []):
        file_path = content_check["path"]
        full_path = project_root / file_path
        if not full_path.exists():
            results.append(CheckResult(
                category="file",
                name=f"file:{file_path}:content",
                status=CheckStatus.FAIL,
                message=f"{file_path} not found (cannot check content)",
                required=content_check.get("required", True),
            ))
            continue
        try:
            content = full_path.read_text()
            pattern = content_check.get("contains")
            if pattern and pattern not in content:
                results.append(CheckResult(
                    category="file",
                    name=f"file:{file_path}:content",
                    status=CheckStatus.FAIL,
                    message=f"{file_path} missing expected content: {pattern[:50]}",
                    fix_suggestion=content_check.get("fix_suggestion", f"Add '{pattern}' to {file_path}"),
                    required=content_check.get("required", True),
                ))
            else:
                results.append(CheckResult(
                    category="file",
                    name=f"file:{file_path}:content",
                    status=CheckStatus.PASS,
                    message=f"{file_path} content check passed",
                    required=content_check.get("required", True),
                ))
        except Exception as e:
            results.append(CheckResult(
                category="file",
                name=f"file:{file_path}:content",
                status=CheckStatus.FAIL,
                message=f"Cannot read {file_path}: {e}",
                required=content_check.get("required", True),
            ))

    return results


def _suggest_file_fix(file_path: str, project_root: Path) -> str:
    """Suggest how to create a missing file."""
    base = Path(file_path).name
    stem = Path(file_path).stem

    # Check for example files
    for suffix in [".example", ".template", ".sample", ".dist"]:
        example = project_root / (file_path + suffix)
        if example.exists():
            return f"Copy from template: cp {file_path}{suffix} {file_path}"

    # Check for example in same directory
    parent = (project_root / file_path).parent
    if parent.exists():
        for f in parent.iterdir():
            if f.name.startswith(stem) and any(
                x in f.name for x in ["example", "template", "sample"]
            ):
                rel = f.relative_to(project_root)
                return f"Copy from template: cp {rel} {file_path}"

    return f"Create {file_path} with the required configuration"


# =============================================================================
# Auto-Detection from verify.json
# =============================================================================

def infer_prerequisites_from_config(config: Dict, project_root: Path) -> Dict:
    """Infer basic prerequisites from the verify.json config itself.

    When no explicit prerequisites are defined, we can still check things
    that the verify config implicitly requires — like ports for servers,
    commands for CLI builds, and browsers for frontend tests.
    """
    prereqs: Dict[str, Any] = {
        "environment": {"required_vars": [], "optional_vars": []},
        "dependencies": {"commands": []},
        "ports": {"required_free": []},
        "services": {"checks": []},
        "files": {"required": [], "optional": []},
    }

    verification = config.get("verification", {})

    # Infer from server config
    if "server" in verification:
        server = verification["server"]

        # Infer port from readiness endpoint
        readiness = server.get("readiness", {})
        endpoint = readiness.get("endpoint", "")
        if endpoint:
            port_match = re.search(r":(\d+)", endpoint)
            if port_match:
                prereqs["ports"]["required_free"].append(int(port_match.group(1)))

        # Infer env vars from server start config
        env = server.get("start", {}).get("env", {})
        # Don't require these — they're defaults that verify.json provides.
        # But if they reference ${VAR} patterns, those ARE required.
        cmd = server.get("start", {}).get("command", "")
        for var_match in re.finditer(r"\$\{?(\w+)\}?", cmd):
            var = var_match.group(1)
            if var not in ("PATH", "HOME", "USER", "SHELL"):
                prereqs["environment"]["required_vars"].append(var)

        # Infer runtime from start command
        if "go run" in cmd:
            prereqs["dependencies"]["commands"].append("go version")
        elif "npm" in cmd:
            prereqs["dependencies"]["commands"].append("node --version")
            prereqs["dependencies"]["commands"].append("npm --version")
        elif "python" in cmd or "uvicorn" in cmd:
            prereqs["dependencies"]["commands"].append("python3 --version")
        elif "java" in cmd or "mvn" in cmd:
            prereqs["dependencies"]["commands"].append("java -version")

    # Infer from CLI config
    if "cli" in verification:
        cli = verification["cli"]
        build_cmd = cli.get("binary", {}).get("build_command", "")
        if "go build" in build_cmd:
            prereqs["dependencies"]["commands"].append("go version")
        elif "npm run build" in build_cmd:
            prereqs["dependencies"]["commands"].append("node --version")

    # Infer from frontend config
    if "frontend" in verification:
        frontend = verification["frontend"]

        # Port from readiness URL
        readiness = frontend.get("readiness", {})
        url = readiness.get("url", "")
        if url:
            port_match = re.search(r":(\d+)", url)
            if port_match:
                prereqs["ports"]["required_free"].append(int(port_match.group(1)))

        # Frontend always needs Node.js
        dev_cmd = frontend.get("dev_server", {}).get("command", "")
        if "npm" in dev_cmd or "pnpm" in dev_cmd or "yarn" in dev_cmd:
            prereqs["dependencies"]["commands"].append("node --version")
            prereqs["dependencies"]["commands"].append("npm --version")

        # Browser for CDP tests
        if frontend.get("pages"):
            # Check if Chrome is available (optional — frontend verification skips if not found)
            prereqs["dependencies"]["commands"].append("google-chrome --version")

    # Deduplicate
    prereqs["dependencies"]["commands"] = list(set(prereqs["dependencies"]["commands"]))
    prereqs["ports"]["required_free"] = list(set(prereqs["ports"]["required_free"]))
    prereqs["environment"]["required_vars"] = list(set(prereqs["environment"]["required_vars"]))

    return prereqs


# =============================================================================
# Environment Bootstrap
# =============================================================================

@dataclass
class BootstrapAction:
    """A single bootstrap action that was taken."""
    category: str           # "env_var", "dependency", "service", "database", "script"
    name: str
    action: str             # What was done
    success: bool
    message: str = ""


# Compose filenames in priority order (Docker's v2 convention first)
_COMPOSE_FILENAMES = ("compose.yml", "compose.yaml", "docker-compose.yml", "docker-compose.yaml")


def _run_bootstrap_cmd(
    cmd,
    *,
    category: str,
    name: str,
    verb: str,
    cwd: Path,
    timeout: int = 120,
    shell: bool = False,
) -> BootstrapAction:
    """Run a command and return a BootstrapAction describing the result.

    This is the workhorse for all bootstrap operations — run a subprocess,
    capture the first 200 chars of output, and report success/failure.
    """
    try:
        proc = subprocess.run(
            cmd, shell=shell, cwd=str(cwd),
            capture_output=True, text=True, timeout=timeout,
        )
        ok = proc.returncode == 0
        return BootstrapAction(
            category=category, name=name,
            action=f"Ran {verb}" if ok else f"Tried {verb}",
            success=ok,
            message=(proc.stdout if ok else proc.stderr)[:200],
        )
    except subprocess.TimeoutExpired:
        return BootstrapAction(
            category=category, name=name,
            action=f"Tried {verb}",
            success=False, message=f"Timed out after {timeout}s",
        )
    except Exception as e:
        return BootstrapAction(
            category=category, name=name,
            action=f"Tried {verb}",
            success=False, message=str(e)[:200],
        )


def _collect_startable_services(items: List[Dict], default_name: str) -> List[Dict]:
    """Extract service startup info from databases[] or services[] entries."""
    result = []
    for item in items:
        setup = item.get("setup", {})
        result.append({
            "name": item.get("name", default_name),
            "type": item.get("type", "unknown"),
            "docker_compose_service": setup.get("docker_compose_service"),
            "docker_image": setup.get("docker_image"),
            "mock_alternative": item.get("test_alternatives", {}).get("mock"),
            "connection": item.get("connection", {}),
        })
    return result


def bootstrap_environment(
    project_root: Path,
    env_config: Optional[Dict],
    failed_checks: List[CheckResult],
    verbose: bool = False,
) -> List[BootstrapAction]:
    """Attempt to fix failed preflight checks by bootstrapping the environment.

    Instead of just reporting failures, we try to fix them using whatever
    is available — project scripts, test values, package managers, local
    services, and Docker as a last resort.

    Priority chain (lightest first):
      1. Project setup scripts (setup-env.sh, Makefile targets)
      2. Test-safe environment variables from environment.json
      3. Mock/in-memory alternatives for services
      4. Service startup (docker-compose, docker run)
      5. Package manager install (npm install, go mod download, pip install)
      6. Database migrations and seed data
    """
    actions: List[BootstrapAction] = []
    cfg = env_config or {}

    # ── Step 1: Run project setup scripts first (most reliable) ──
    setup_script = cfg.get("scripts", {}).get("setup_env")
    if setup_script:
        setup_path = project_root / setup_script
        if setup_path.exists():
            if verbose:
                print(f"  [BOOTSTRAP] Running project setup script: {setup_script}")
            actions.append(_run_bootstrap_cmd(
                ["bash", str(setup_path)],
                category="script", name="setup-env",
                verb=setup_script, cwd=project_root, timeout=120,
            ))

    # ── Step 2: Set test-safe environment variables ──
    _bootstrap_env_vars(cfg.get("env_vars", {}), cfg.get("secrets", []), actions, verbose)

    # ── Step 3: Use mock/in-memory alternatives + Start services ──
    _bootstrap_services(project_root, cfg, failed_checks, actions, verbose)

    # ── Step 4: Install missing dependencies (after services are up) ──
    _bootstrap_dependencies(project_root, failed_checks, actions, verbose)

    # ── Step 5: Run migrations and seed data (now that DB is running) ──
    _bootstrap_data(project_root, cfg, actions, verbose)

    return actions


def _bootstrap_env_vars(
    env_vars: Dict, secrets: List[Dict],
    actions: List[BootstrapAction], verbose: bool,
):
    """Set environment variables from test_value definitions and legacy secrets."""
    # From env_vars.required — only if test_value_ok
    for var_name, var_info in env_vars.get("required", {}).items():
        if isinstance(var_info, dict) and var_info.get("test_value_ok") and var_info.get("test_value"):
            if not os.environ.get(var_name):
                os.environ[var_name] = var_info["test_value"]
                if verbose:
                    print(f"  [BOOTSTRAP] Set ${var_name} from test_value")
                actions.append(BootstrapAction(
                    category="env_var", name=var_name,
                    action="Set from environment.json test_value",
                    success=True, message=f"${var_name} = {var_info['test_value'][:20]}...",
                ))

    # From env_vars.optional — use default values
    for var_name, var_info in env_vars.get("optional", {}).items():
        if isinstance(var_info, dict) and var_info.get("default"):
            if not os.environ.get(var_name):
                os.environ[var_name] = var_info["default"]
                if verbose:
                    print(f"  [BOOTSTRAP] Set ${var_name} from default: {var_info['default']}")
                actions.append(BootstrapAction(
                    category="env_var", name=var_name,
                    action="Set from environment.json default",
                    success=True, message=f"${var_name} = {var_info['default']}",
                ))

    # Legacy secrets with test_value
    for secret in secrets:
        if secret.get("test_value_ok") and secret.get("test_value"):
            var_name = secret["name"]
            if not os.environ.get(var_name):
                os.environ[var_name] = secret["test_value"]
                if verbose:
                    print(f"  [BOOTSTRAP] Set ${var_name} from secret test_value")
                actions.append(BootstrapAction(
                    category="env_var", name=var_name,
                    action="Set from secret test_value",
                    success=True, message=f"${var_name} set for test",
                ))


def _bootstrap_dependencies(
    project_root: Path, failed_checks: List[CheckResult],
    actions: List[BootstrapAction], verbose: bool,
):
    """Install missing project dependencies."""
    failed_deps = [c for c in failed_checks if c.category == "dependency"]
    if not failed_deps:
        return

    for check in failed_deps:
        if "packages:npm" in check.name:
            if verbose:
                print("  [BOOTSTRAP] Installing npm dependencies...")
            pkg_mgr = "npm"
            if (project_root / "pnpm-lock.yaml").exists():
                pkg_mgr = "pnpm"
            elif (project_root / "yarn.lock").exists():
                pkg_mgr = "yarn"
            elif (project_root / "bun.lockb").exists():
                pkg_mgr = "bun"

            actions.append(_run_bootstrap_cmd(
                [pkg_mgr, "install"],
                category="dependency", name=f"packages:{pkg_mgr}",
                verb=f"{pkg_mgr} install", cwd=project_root, timeout=180,
            ))

        elif "packages:go" in check.name:
            if verbose:
                print("  [BOOTSTRAP] Downloading Go modules...")
            actions.append(_run_bootstrap_cmd(
                ["go", "mod", "download"],
                category="dependency", name="packages:go",
                verb="go mod download", cwd=project_root, timeout=120,
            ))

        elif "packages:python" in check.name:
            if verbose:
                print("  [BOOTSTRAP] Installing Python dependencies...")
            req_file = project_root / "requirements.txt"
            pyproject = project_root / "pyproject.toml"

            cmd = None
            if req_file.exists():
                cmd = ["pip", "install", "-r", str(req_file)]
            elif pyproject.exists():
                if shutil.which("uv"):
                    cmd = ["uv", "pip", "install", "-e", "."]
                else:
                    cmd = ["pip", "install", "-e", "."]

            if cmd:
                actions.append(_run_bootstrap_cmd(
                    cmd, category="dependency", name="packages:python",
                    verb=" ".join(cmd[:3]), cwd=project_root, timeout=180,
                ))


def _bootstrap_services(
    project_root: Path, cfg: Dict,
    failed_checks: List[CheckResult], actions: List[BootstrapAction], verbose: bool,
):
    """Start services that preflight detected as unreachable.

    Strategy (lightest-weight first):
      1. Mock/in-memory alternatives
      2. docker-compose up (batch start)
      3. docker run (individual containers)
    """
    services_to_start = (
        _collect_startable_services(cfg.get("databases", []), "database")
        + _collect_startable_services(cfg.get("services", []), "service")
    )
    if not services_to_start:
        return

    # Strategy 1: mock/in-memory alternatives
    for svc in services_to_start:
        mock_alt = svc.get("mock_alternative")
        if mock_alt:
            if verbose:
                print(f"  [BOOTSTRAP] Using mock alternative for {svc['name']}: {mock_alt}")
            actions.append(BootstrapAction(
                category="service", name=svc["name"],
                action=f"Mock available: {mock_alt}",
                success=True,
                message=f"Can use mock instead of real service: {mock_alt}",
            ))

    # Strategy 2: docker-compose up (batch start)
    compose_file = next(
        (project_root / name for name in _COMPOSE_FILENAMES if (project_root / name).exists()),
        None,
    )
    compose_services = [
        s["docker_compose_service"] for s in services_to_start
        if s.get("docker_compose_service")
    ]
    if compose_file and compose_services:
        compose_cmd = _detect_compose_command()
        if compose_cmd:
            svc_names = " ".join(compose_services)
            if verbose:
                print(f"  [BOOTSTRAP] Starting via docker-compose: {svc_names}")
            actions.append(_run_bootstrap_cmd(
                compose_cmd + ["up", "-d"] + compose_services,
                category="service", name="docker-compose",
                verb=svc_names, cwd=project_root, timeout=120,
            ))

    # Strategy 3: docker run for services without compose
    has_docker = shutil.which("docker")
    for svc in services_to_start:
        if svc.get("docker_compose_service"):
            continue
        docker_image = svc.get("docker_image")
        if not docker_image or not has_docker:
            continue

        conn = svc.get("connection", {})
        default_port = conn.get("default_port")
        port_args = ["-p", f"{default_port}:{default_port}"] if default_port else []
        container_name = f"harness-{svc['name']}"

        if verbose:
            print(f"  [BOOTSTRAP] Starting {svc['name']} via docker run: {docker_image}")
        try:
            check_proc = subprocess.run(
                ["docker", "ps", "-a", "--filter", f"name=^{container_name}$",
                 "--format", "{{.Status}}"],
                capture_output=True, text=True, timeout=10,
            )
            if check_proc.stdout.strip():
                if check_proc.stdout.strip().startswith("Exited"):
                    subprocess.run(
                        ["docker", "start", container_name],
                        capture_output=True, text=True, timeout=30,
                    )
                    actions.append(BootstrapAction(
                        category="service", name=svc["name"],
                        action=f"Restarted existing container {container_name}",
                        success=True, message="Container restarted",
                    ))
                # else: container already running, no action needed
            else:
                cmd = ["docker", "run", "-d", "--name", container_name] + port_args + [docker_image]
                proc = subprocess.run(
                    cmd, capture_output=True, text=True, timeout=60,
                )
                actions.append(BootstrapAction(
                    category="service", name=svc["name"],
                    action=f"Started container from {docker_image}",
                    success=proc.returncode == 0,
                    message=(proc.stdout if proc.returncode == 0 else proc.stderr)[:200],
                ))
        except Exception as e:
            actions.append(BootstrapAction(
                category="service", name=svc["name"],
                action=f"Tried docker run {docker_image}",
                success=False, message=str(e)[:200],
            ))


def _bootstrap_data(
    project_root: Path, cfg: Dict,
    actions: List[BootstrapAction], verbose: bool,
):
    """Run database migrations and seed data if configured."""
    for db in cfg.get("databases", []):
        setup = db.get("setup", {})
        db_name = db.get("name", "database")

        for cmd_key, label in [("migration_command", "migration"), ("seed_command", "seed")]:
            cmd_str = setup.get(cmd_key)
            if cmd_str:
                if verbose:
                    print(f"  [BOOTSTRAP] Running {label} for {db_name}: {cmd_str}")
                actions.append(_run_bootstrap_cmd(
                    cmd_str, category="database", name=f"{db_name}-{label}",
                    verb=f"{label}: {cmd_str[:60]}", cwd=project_root, timeout=60, shell=True,
                ))

    seed_script = cfg.get("scripts", {}).get("seed_data")
    if seed_script:
        seed_path = project_root / seed_script
        if seed_path.exists():
            if verbose:
                print(f"  [BOOTSTRAP] Running seed script: {seed_script}")
            actions.append(_run_bootstrap_cmd(
                ["bash", str(seed_path)],
                category="database", name="seed-script",
                verb=seed_script, cwd=project_root, timeout=60,
            ))


def _detect_compose_command() -> Optional[List[str]]:
    """Detect available docker-compose command (v1 or v2)."""
    # Docker Compose v2 (docker compose)
    try:
        proc = subprocess.run(
            ["docker", "compose", "version"],
            capture_output=True, text=True, timeout=5,
        )
        if proc.returncode == 0:
            return ["docker", "compose"]
    except Exception:
        pass

    # Docker Compose v1 (docker-compose)
    if shutil.which("docker-compose"):
        return ["docker-compose"]

    return None


# =============================================================================
# Main Pipeline
# =============================================================================


def load_environment_config(project_root: Path) -> Optional[Dict]:
    """Load environment.json for comprehensive prerequisite checking.

    environment.json is generated by harness-creator and describes the complete
    runtime ecosystem: databases, services, secrets, ports, etc.
    """
    env_path = project_root / "harness" / "config" / "environment.json"
    if env_path.exists():
        try:
            return json.loads(env_path.read_text())
        except json.JSONDecodeError:
            return None
    return None


def infer_prerequisites_from_environment(env_config: Dict) -> Dict:
    """Convert environment.json into the prerequisites format used by check functions.

    This translates the richer environment.json schema into the same format that
    verify.json prerequisites use, so all existing check functions work unchanged.
    """
    prereqs: Dict[str, Any] = {
        "environment": [],
        "dependencies": [],
        "ports": [],
        "services": [],
        "files": [],
    }

    # Databases → env var checks + TCP service checks
    for db in env_config.get("databases", []):
        conn = db.get("connection", {})
        required = db.get("required", True)

        # Check env vars for database connection
        for env_key in ["host_env", "port_env", "user_env", "password_env",
                        "database_env", "url_env"]:
            var_name = conn.get(env_key)
            if var_name:
                prereqs["environment"].append({
                    "name": var_name,
                    "required": required,
                    "not_empty": True,
                    "description": f"{db.get('name', 'database')} - {db.get('purpose', '')}",
                })

        # TCP connectivity check for required databases
        if required and conn.get("default_port"):
            host_env = conn.get("host_env", "")
            host = os.environ.get(host_env, "localhost") if host_env else "localhost"
            prereqs["services"].append({
                "name": f"database-{db.get('name', 'db')}",
                "type": "tcp",
                "host": host,
                "port": conn["default_port"],
                "required": required,
                "description": f"Database: {db.get('name', '')} ({db.get('type', '')})",
            })

    # Services → service checks
    for svc in env_config.get("services", []):
        conn = svc.get("connection", {})
        required = svc.get("required", True)

        # URL env var check
        url_env = conn.get("url_env")
        if url_env:
            prereqs["environment"].append({
                "name": url_env,
                "required": required,
                "not_empty": True,
                "description": f"{svc.get('name', 'service')} - {svc.get('purpose', '')}",
            })

        # Health endpoint check for HTTP services
        health = conn.get("health_endpoint")
        if health and svc.get("type") == "http":
            url_env_val = os.environ.get(url_env, conn.get("default_url", ""))
            if url_env_val:
                prereqs["services"].append({
                    "name": f"service-{svc.get('name', 'svc')}",
                    "type": "http",
                    "url": f"{url_env_val.rstrip('/')}{health}",
                    "required": required,
                    "description": f"Service: {svc.get('name', '')}",
                })

    # Secrets → env var checks
    for secret in env_config.get("secrets", []):
        prereqs["environment"].append({
            "name": secret["name"],
            "required": secret.get("required", True),
            "not_empty": True,
            "description": secret.get("purpose", ""),
        })

    # Ports → port availability checks
    for port_info in env_config.get("ports", []):
        test_port = port_info.get("test_port", port_info.get("default"))
        if test_port:
            prereqs["ports"].append({
                "port": test_port,
                "required": True,
                "description": f"{port_info.get('name', 'port')} - {port_info.get('purpose', '')}",
            })

    # Files → file checks
    for f in env_config.get("files", {}).get("required", []):
        prereqs["files"].append({
            "path": f.get("path", ""),
            "required": True,
            "description": f.get("purpose", ""),
        })

    return prereqs


def run_preflight(
    project_root: Path,
    config: Optional[Dict] = None,
    verbose: bool = False,
    bootstrap: bool = False,
) -> PreflightReport:
    """Run all pre-flight checks, optionally bootstrapping the environment.

    Priority for prerequisite sources:
    1. harness/config/environment.json (comprehensive environment contract)
    2. verify.json explicit "prerequisites" key
    3. Inferred from verify.json config

    When bootstrap=True:
      - First runs normal checks to identify failures
      - Then attempts to fix each failure (set env vars, install deps, start services)
      - Re-runs checks to verify fixes worked
      - Reports both the bootstrap actions taken and the final check state
    """
    report = PreflightReport(
        project_root=str(project_root),
        timestamp=time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    )

    # Try environment.json first (preferred source)
    env_config = load_environment_config(project_root)
    prereqs = {}

    if env_config:
        if verbose:
            print("Found environment.json — deriving prerequisites...")
        prereqs = infer_prerequisites_from_environment(env_config)
    else:
        # Fall back to verify.json
        if config is None:
            config_path = project_root / "harness" / "config" / "verify.json"
            if config_path.exists():
                try:
                    config = json.loads(config_path.read_text())
                except json.JSONDecodeError:
                    config = {}
            else:
                config = {}

        prereqs = config.get("prerequisites", {})
        if not prereqs:
            if verbose:
                print("No explicit prerequisites — inferring from verify config...")
            prereqs = infer_prerequisites_from_config(config, project_root)

    # ── Initial check pass ──
    all_results = _run_all_checks(prereqs, project_root, verbose)

    # ── Bootstrap: attempt to fix failures ──
    bootstrap_actions = []
    if bootstrap:
        failed_checks = [
            r for r in all_results
            if r.status == CheckStatus.FAIL and r.required
        ]
        if failed_checks:
            if verbose:
                print(f"\n[Bootstrap] {len(failed_checks)} blocker(s) detected — attempting to fix...")
            bootstrap_actions = bootstrap_environment(
                project_root, env_config, failed_checks, verbose,
            )
            if bootstrap_actions:
                # Re-run checks after bootstrap
                if verbose:
                    print("\n[Bootstrap] Re-checking prerequisites after bootstrap...")
                all_results = _run_all_checks(prereqs, project_root, verbose)
        elif verbose:
            print("\n[Bootstrap] No blockers — environment is ready")

    # Build report
    report.results = [asdict(r) for r in all_results]

    passed = sum(1 for r in all_results if r.status == CheckStatus.PASS)
    failed = sum(1 for r in all_results if r.status == CheckStatus.FAIL)
    warned = sum(1 for r in all_results if r.status == CheckStatus.WARN)
    skipped = sum(1 for r in all_results if r.status == CheckStatus.SKIP)

    report.blockers = [
        asdict(r) for r in all_results
        if r.status == CheckStatus.FAIL and r.required
    ]
    report.warnings = [
        asdict(r) for r in all_results
        if r.status in (CheckStatus.FAIL, CheckStatus.WARN) and not r.required
    ]

    report.all_satisfied = len(report.blockers) == 0
    report.summary = {
        "total": len(all_results),
        "passed": passed,
        "failed": failed,
        "warnings": warned,
        "skipped": skipped,
        "blockers": len(report.blockers),
    }

    # Include bootstrap actions in report if any were taken
    if bootstrap_actions:
        report.summary["bootstrap_actions"] = [asdict(a) for a in bootstrap_actions]
        report.summary["bootstrap_attempted"] = True
        report.summary["bootstrap_fixed"] = sum(1 for a in bootstrap_actions if a.success)

    return report


def _run_all_checks(
    prereqs: Dict, project_root: Path, verbose: bool,
) -> List[CheckResult]:
    """Run all preflight check categories and return results."""
    all_results: List[CheckResult] = []

    if verbose:
        print("\n[Environment Variables]")
    all_results.extend(check_environment(prereqs, project_root))

    if verbose:
        print("[Dependencies]")
    all_results.extend(check_dependencies(prereqs, project_root))

    if verbose:
        print("[Port Availability]")
    all_results.extend(check_ports(prereqs, project_root))

    if verbose:
        print("[External Services]")
    all_results.extend(check_services(prereqs, project_root))

    if verbose:
        print("[Required Files]")
    all_results.extend(check_files(prereqs, project_root))

    return all_results


def main():
    """Main entry point."""
    import argparse

    parser = argparse.ArgumentParser(
        description="Pre-flight checks for runtime verification"
    )
    parser.add_argument("path", nargs="?", default=".", help="Project root path")
    parser.add_argument("--json", action="store_true", help="Output as JSON")
    parser.add_argument("-v", "--verbose", action="store_true", help="Verbose output")
    parser.add_argument("--output", type=str, help="Save report to file")
    parser.add_argument(
        "--config", type=str,
        help="Path to verify.json (default: harness/config/verify.json)",
    )
    parser.add_argument(
        "--bootstrap", action="store_true",
        help="Actively fix failed checks: set test env vars, install deps, "
             "start services, run migrations. Uses environment.json as the "
             "source of truth for how to bootstrap each component.",
    )

    args = parser.parse_args()
    project_root = Path(args.path).resolve()

    if not project_root.is_dir():
        print(f"Error: {project_root} is not a directory", file=sys.stderr)
        sys.exit(2)

    # Load config
    config = None
    if args.config:
        config_path = Path(args.config)
        if config_path.exists():
            config = json.loads(config_path.read_text())
        else:
            print(f"Warning: Config file not found: {args.config}", file=sys.stderr)

    report = run_preflight(
        project_root, config=config, verbose=args.verbose,
        bootstrap=args.bootstrap,
    )

    # Output
    if args.json:
        output = json.dumps(asdict(report), indent=2)
        if args.output:
            Path(args.output).parent.mkdir(parents=True, exist_ok=True)
            Path(args.output).write_text(output)
        print(output)
    else:
        print(f"\n{'=' * 55}")
        print(f"Pre-flight Checks: {project_root.name}")
        print(f"{'=' * 55}\n")

        # Group by category
        categories = {}
        for result in report.results:
            cat = result["category"]
            if cat not in categories:
                categories[cat] = []
            categories[cat].append(result)

        cat_labels = {
            "environment": "Environment Variables",
            "dependency": "Dependencies",
            "port": "Port Availability",
            "service": "External Services",
            "file": "Required Files",
        }

        for cat, results in categories.items():
            label = cat_labels.get(cat, cat.title())
            print(f"  [{label}]")
            for r in results:
                icons = {"pass": "OK", "fail": "FAIL", "warn": "WARN", "skip": "SKIP"}
                icon = icons.get(r["status"], "?")
                print(f"    [{icon:4s}] {r['name']}: {r['message']}")
                if r.get("fix_suggestion") and r["status"] in ("fail", "warn"):
                    print(f"           -> {r['fix_suggestion']}")
            print()

        # Summary
        s = report.summary
        print(f"Summary: {s['passed']} passed, {s['failed']} failed, "
              f"{s['warnings']} warnings, {s['skipped']} skipped")

        if report.blockers:
            print(f"\nBLOCKERS ({len(report.blockers)}):")
            for b in report.blockers:
                print(f"  - {b['name']}: {b['message']}")
                if b.get("fix_suggestion"):
                    print(f"    Fix: {b['fix_suggestion']}")

        # Show bootstrap actions if any
        bootstrap_actions = report.summary.get("bootstrap_actions", [])
        if bootstrap_actions:
            print(f"\nBOOTSTRAP ACTIONS ({len(bootstrap_actions)}):")
            for a in bootstrap_actions:
                status = "OK" if a["success"] else "FAIL"
                print(f"  [{status:4s}] {a['category']}:{a['name']} — {a['action']}")
                if a.get("message"):
                    print(f"         {a['message'][:100]}")

        print(f"\nResult: {'ALL PREREQUISITES MET' if report.all_satisfied else 'PREREQUISITES NOT MET'}\n")

        if args.output:
            Path(args.output).parent.mkdir(parents=True, exist_ok=True)
            Path(args.output).write_text(json.dumps(asdict(report), indent=2))
            print(f"Report saved to: {args.output}")

    sys.exit(0 if report.all_satisfied else 1)


if __name__ == "__main__":
    main()
