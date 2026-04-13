#!/usr/bin/env python3
"""
Task-aware verification generator.

Generates runtime verification test cases based on:
1. Task description (what the developer is trying to accomplish)
2. Files changed (which files were modified/created)
3. Code analysis (detecting new routes, commands, components)

This bridges the gap between generic health checks and task-specific validation.
"""

import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass, field, asdict
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple


@dataclass
class TaskContext:
    """Context about the current task for verification generation."""
    description: str
    goal: str = ""
    files_changed: List[str] = field(default_factory=list)
    files_created: List[str] = field(default_factory=list)
    app_type: str = ""  # server, cli, frontend, hybrid


@dataclass
class VerificationSuggestion:
    """A suggested verification test case."""
    type: str  # endpoint, cli_command, page
    name: str
    config: Dict
    confidence: float  # 0.0-1.0, how confident we are this is a valid test
    reason: str  # Why this test was suggested


# =============================================================================
# Code Analysis - Detect New/Modified Features
# =============================================================================

def analyze_go_routes(file_path: Path, content: str) -> List[Dict]:
    """Detect HTTP route handlers in Go code."""
    routes = []

    # Common Go routing patterns
    patterns = [
        # chi router: r.Get("/path", handler)
        r'r\.(Get|Post|Put|Delete|Patch)\s*\(\s*["\']([^"\']+)["\']',
        # gin: r.GET("/path", handler)
        r'r\.(GET|POST|PUT|DELETE|PATCH)\s*\(\s*["\']([^"\']+)["\']',
        # gorilla mux: r.HandleFunc("/path", handler).Methods("GET")
        r'HandleFunc\s*\(\s*["\']([^"\']+)["\'].*?Methods\s*\(\s*["\'](\w+)["\']',
        # echo: e.GET("/path", handler)
        r'e\.(GET|POST|PUT|DELETE|PATCH)\s*\(\s*["\']([^"\']+)["\']',
        # http.HandleFunc("/path", handler)
        r'http\.HandleFunc\s*\(\s*["\']([^"\']+)["\']',
    ]

    for pattern in patterns:
        for match in re.finditer(pattern, content, re.IGNORECASE):
            groups = match.groups()
            if len(groups) == 2:
                method, path = groups[0].upper(), groups[1]
            elif len(groups) == 1:
                method, path = "GET", groups[0]  # HandleFunc default
            else:
                continue

            routes.append({
                "method": method,
                "path": path,
                "file": str(file_path),
                "line": content[:match.start()].count('\n') + 1
            })

    return routes


def analyze_go_cli_commands(file_path: Path, content: str) -> List[Dict]:
    """Detect CLI commands in Go code (cobra, urfave/cli)."""
    commands = []

    # Cobra: &cobra.Command{Use: "command", ...}
    cobra_pattern = r'&cobra\.Command\s*\{[^}]*Use:\s*["\'](\w+)["\']'
    for match in re.finditer(cobra_pattern, content, re.DOTALL):
        commands.append({
            "name": match.group(1),
            "file": str(file_path),
            "type": "cobra"
        })

    # urfave/cli: &cli.Command{Name: "command", ...}
    urfave_pattern = r'&cli\.Command\s*\{[^}]*Name:\s*["\'](\w+)["\']'
    for match in re.finditer(urfave_pattern, content, re.DOTALL):
        commands.append({
            "name": match.group(1),
            "file": str(file_path),
            "type": "urfave"
        })

    return commands


def analyze_ts_routes(file_path: Path, content: str) -> List[Dict]:
    """Detect HTTP route handlers in TypeScript/JavaScript code."""
    routes = []

    # Express/Fastify patterns
    patterns = [
        # app.get('/path', ...)
        r'(?:app|router|server)\.(get|post|put|delete|patch)\s*\(\s*["\'/]([^"\']+)["\']',
        # @Get('/path')
        r'@(Get|Post|Put|Delete|Patch)\s*\(\s*["\']([^"\']+)["\']',
    ]

    for pattern in patterns:
        for match in re.finditer(pattern, content, re.IGNORECASE):
            method, path = match.groups()
            if not path.startswith('/'):
                path = '/' + path
            routes.append({
                "method": method.upper(),
                "path": path,
                "file": str(file_path),
            })

    return routes


def analyze_python_routes(file_path: Path, content: str) -> List[Dict]:
    """Detect HTTP route handlers in Python code."""
    routes = []

    # FastAPI: @app.get("/path")
    fastapi_pattern = r'@(?:app|router)\.(get|post|put|delete|patch)\s*\(\s*["\']([^"\']+)["\']'
    for match in re.finditer(fastapi_pattern, content, re.IGNORECASE):
        routes.append({
            "method": match.group(1).upper(),
            "path": match.group(2),
            "file": str(file_path),
        })

    # Flask: @app.route("/path", methods=["GET"])
    flask_pattern = r'@(?:app|bp)\.route\s*\(\s*["\']([^"\']+)["\'](?:.*?methods\s*=\s*\[([^\]]+)\])?'
    for match in re.finditer(flask_pattern, content, re.IGNORECASE | re.DOTALL):
        path = match.group(1)
        methods_str = match.group(2)
        if methods_str:
            methods = re.findall(r'["\'](\w+)["\']', methods_str)
        else:
            methods = ["GET"]
        for method in methods:
            routes.append({
                "method": method.upper(),
                "path": path,
                "file": str(file_path),
            })

    return routes


def analyze_python_cli_commands(file_path: Path, content: str) -> List[Dict]:
    """Detect CLI commands in Python code (click, typer, argparse)."""
    commands = []

    # Click: @click.command()
    click_pattern = r'@click\.command\s*\([^)]*name\s*=\s*["\'](\w+)["\']'
    for match in re.finditer(click_pattern, content):
        commands.append({"name": match.group(1), "file": str(file_path), "type": "click"})

    # Typer: @app.command()
    typer_pattern = r'@app\.command\s*\([^)]*name\s*=\s*["\'](\w+)["\']'
    for match in re.finditer(typer_pattern, content):
        commands.append({"name": match.group(1), "file": str(file_path), "type": "typer"})

    # Also detect function names with @click.command() decorator
    click_func_pattern = r'@click\.command\s*\(\s*\)\s*\ndef\s+(\w+)'
    for match in re.finditer(click_func_pattern, content):
        commands.append({"name": match.group(1), "file": str(file_path), "type": "click"})

    return commands


def analyze_file(file_path: Path) -> Tuple[List[Dict], List[Dict]]:
    """Analyze a single file for routes and CLI commands."""
    routes = []
    commands = []

    if not file_path.exists():
        return routes, commands

    try:
        content = file_path.read_text()
    except Exception:
        return routes, commands

    suffix = file_path.suffix.lower()

    if suffix == ".go":
        routes = analyze_go_routes(file_path, content)
        commands = analyze_go_cli_commands(file_path, content)
    elif suffix in (".ts", ".js", ".tsx", ".jsx"):
        routes = analyze_ts_routes(file_path, content)
    elif suffix == ".py":
        routes = analyze_python_routes(file_path, content)
        commands = analyze_python_cli_commands(file_path, content)

    return routes, commands


def analyze_changed_files(
    project_root: Path,
    files_changed: List[str],
    files_created: List[str]
) -> Tuple[List[Dict], List[Dict]]:
    """Analyze all changed/created files for new features."""
    all_routes = []
    all_commands = []

    all_files = set(files_changed + files_created)

    for file_path_str in all_files:
        file_path = project_root / file_path_str
        routes, commands = analyze_file(file_path)
        all_routes.extend(routes)
        all_commands.extend(commands)

    return all_routes, all_commands


# =============================================================================
# Task Description Analysis - Extract Expected Behaviors
# =============================================================================

def extract_api_expectations(description: str, goal: str) -> List[Dict]:
    """Extract expected API behaviors from task description."""
    expectations = []
    text = f"{description} {goal}".lower()

    # Common CRUD patterns
    crud_patterns = [
        (r'\b(create|add|new|register)\s+(\w+)', "POST", "/{entity}s"),
        (r'\b(list|get all|fetch all|retrieve all)\s+(\w+)', "GET", "/{entity}s"),
        (r'\b(get|fetch|retrieve|view)\s+(\w+)\s+by\s+id', "GET", "/{entity}s/{id}"),
        (r'\b(update|modify|edit)\s+(\w+)', "PUT", "/{entity}s/{id}"),
        (r'\b(delete|remove)\s+(\w+)', "DELETE", "/{entity}s/{id}"),
    ]

    for pattern, method, path_template in crud_patterns:
        match = re.search(pattern, text)
        if match:
            entity = match.group(2).rstrip('s')  # Singularize
            path = path_template.replace("{entity}", entity)
            expectations.append({
                "method": method,
                "path": path,
                "entity": entity,
                "from_description": True
            })

    # Authentication patterns
    if re.search(r'\b(login|authenticate|auth)\b', text):
        expectations.append({"method": "POST", "path": "/login", "entity": "auth"})
    if re.search(r'\b(logout|sign out)\b', text):
        expectations.append({"method": "POST", "path": "/logout", "entity": "auth"})
    if re.search(r'\b(register|signup|sign up)\b', text):
        expectations.append({"method": "POST", "path": "/register", "entity": "auth"})

    return expectations


def extract_cli_expectations(description: str, goal: str) -> List[Dict]:
    """Extract expected CLI commands from task description."""
    expectations = []
    text = f"{description} {goal}".lower()

    # Common CLI patterns
    patterns = [
        (r'\b(export|dump)\s+(?:to\s+)?(\w+)', ["export", "--format", "{format}"]),
        (r'\b(import|load)\s+(?:from\s+)?(\w+)', ["import", "--file", "{file}"]),
        (r'\b(generate|create|init)\s+(\w+)', ["generate", "{type}"]),
        (r'\b(migrate|migration)\b', ["migrate"]),
        (r'\b(sync|synchronize)\b', ["sync"]),
        (r'\b(backup)\b', ["backup"]),
        (r'\b(restore)\b', ["restore"]),
    ]

    for pattern, args_template in patterns:
        if re.search(pattern, text):
            expectations.append({
                "command": args_template[0],
                "args": args_template,
                "from_description": True
            })

    return expectations


# =============================================================================
# Verification Generator
# =============================================================================

def generate_endpoint_test(
    route: Dict,
    expectations: List[Dict],
    existing_endpoints: List[Dict]
) -> Optional[VerificationSuggestion]:
    """Generate a verification test for an API endpoint."""

    # Skip if already tested
    for existing in existing_endpoints:
        if existing.get("path") == route["path"] and existing.get("method") == route["method"]:
            return None

    method = route["method"]
    path = route["path"]

    # Determine expected status codes based on method
    if method == "GET":
        expected_status = [200, 404]
    elif method == "POST":
        expected_status = [200, 201, 400]
    elif method in ("PUT", "PATCH"):
        expected_status = [200, 204, 400, 404]
    elif method == "DELETE":
        expected_status = [200, 204, 404]
    else:
        expected_status = [200]

    # Generate test name
    name = f"{method.lower()}_{path.replace('/', '_').strip('_')}"

    # Build test config
    config = {
        "name": name,
        "method": method,
        "path": path,
        "expected": {
            "status": expected_status[0] if len(expected_status) == 1 else expected_status
        }
    }

    # Add body for POST/PUT
    if method in ("POST", "PUT", "PATCH"):
        config["headers"] = {"Content-Type": "application/json"}
        config["body"] = {}  # Placeholder - will need actual test data

    # Check if this matches an expectation
    confidence = 0.6  # Default confidence
    reason = f"Detected {method} route at {path}"

    for exp in expectations:
        if exp.get("method") == method:
            # Check path similarity
            exp_path = exp.get("path", "")
            if exp_path and (path.startswith(exp_path.replace("{id}", "")) or
                            exp_path.replace("{id}", ":id") in path):
                confidence = 0.9
                reason = f"Matches expected {method} {exp_path} from task description"
                break

    return VerificationSuggestion(
        type="endpoint",
        name=name,
        config=config,
        confidence=confidence,
        reason=reason
    )


def generate_cli_test(
    command: Dict,
    expectations: List[Dict],
    existing_commands: List[Dict]
) -> Optional[VerificationSuggestion]:
    """Generate a verification test for a CLI command."""

    cmd_name = command["name"]

    # Skip if already tested
    for existing in existing_commands:
        if existing.get("name") == cmd_name or cmd_name in str(existing.get("args", [])):
            return None

    config = {
        "name": f"test_{cmd_name}",
        "args": [cmd_name],
        "expected": {
            "exit_code": 0
        }
    }

    # Add --help test for new commands
    help_config = {
        "name": f"test_{cmd_name}_help",
        "args": [cmd_name, "--help"],
        "expected": {
            "exit_code": 0,
            "stdout_contains": ["Usage", cmd_name]
        }
    }

    confidence = 0.7
    reason = f"Detected new CLI command: {cmd_name}"

    for exp in expectations:
        if exp.get("command") == cmd_name:
            confidence = 0.9
            reason = f"Matches expected command from task description"
            break

    return VerificationSuggestion(
        type="cli_command",
        name=f"test_{cmd_name}",
        config=config,
        confidence=confidence,
        reason=reason
    )


def generate_task_verification(
    task_context: TaskContext,
    project_root: Path,
    existing_config: Optional[Dict] = None
) -> Dict:
    """Generate task-specific verification configuration.

    Returns:
        Dict with 'suggestions' (list of VerificationSuggestion) and
        'updated_config' (the merged verify.json config)
    """

    suggestions: List[VerificationSuggestion] = []

    # Load existing config or create base
    if existing_config:
        config = existing_config.copy()
    else:
        config = {
            "version": "1.0",
            "app_type": task_context.app_type or "unknown",
            "auto_detected": True,
            "verification": {},
            "smoke_tests": [],
            "cleanup": {}
        }

    # Get existing endpoints/commands to avoid duplicates
    existing_endpoints = []
    existing_commands = []

    if "server" in config.get("verification", {}):
        existing_endpoints = config["verification"]["server"].get("endpoints", [])
    if "cli" in config.get("verification", {}):
        existing_commands = config["verification"]["cli"].get("commands", [])

    # Analyze changed files
    routes, commands = analyze_changed_files(
        project_root,
        task_context.files_changed,
        task_context.files_created
    )

    # Extract expectations from task description
    api_expectations = extract_api_expectations(
        task_context.description,
        task_context.goal
    )
    cli_expectations = extract_cli_expectations(
        task_context.description,
        task_context.goal
    )

    # Generate endpoint tests
    for route in routes:
        suggestion = generate_endpoint_test(route, api_expectations, existing_endpoints)
        if suggestion:
            suggestions.append(suggestion)

    # Generate CLI tests
    for command in commands:
        suggestion = generate_cli_test(command, cli_expectations, existing_commands)
        if suggestion:
            suggestions.append(suggestion)

    # Update config with high-confidence suggestions
    for suggestion in suggestions:
        if suggestion.confidence >= 0.6:  # Threshold for auto-adding
            if suggestion.type == "endpoint":
                if "server" not in config["verification"]:
                    config["verification"]["server"] = {
                        "endpoints": [],
                        "start": {"command": "", "background": True},
                        "readiness": {"type": "http", "endpoint": "", "timeout_seconds": 30},
                        "stop": {"signal": "SIGTERM", "graceful_timeout_seconds": 5}
                    }
                config["verification"]["server"]["endpoints"].append(suggestion.config)

            elif suggestion.type == "cli_command":
                if "cli" not in config["verification"]:
                    config["verification"]["cli"] = {
                        "binary": {"path": ""},
                        "commands": []
                    }
                config["verification"]["cli"]["commands"].append(suggestion.config)

    # Add task metadata for traceability
    config["_task_verification"] = {
        "task_description": task_context.description[:200],
        "files_analyzed": task_context.files_changed + task_context.files_created,
        "routes_detected": len(routes),
        "commands_detected": len(commands),
        "suggestions_count": len(suggestions),
        "auto_added_count": sum(1 for s in suggestions if s.confidence >= 0.6)
    }

    return {
        "suggestions": [asdict(s) for s in suggestions],
        "updated_config": config,
        "summary": {
            "routes_found": routes,
            "commands_found": commands,
            "api_expectations": api_expectations,
            "cli_expectations": cli_expectations,
        }
    }


# =============================================================================
# Main Entry Point
# =============================================================================

def load_existing_config(project_root: Path) -> Optional[Dict]:
    """Load existing verify.json if it exists."""
    config_path = project_root / "harness" / "config" / "verify.json"
    if config_path.exists():
        try:
            return json.loads(config_path.read_text())
        except:
            return None
    return None


def save_config(project_root: Path, config: Dict) -> Path:
    """Save verify.json config."""
    config_dir = project_root / "harness" / "config"
    config_dir.mkdir(parents=True, exist_ok=True)
    config_path = config_dir / "verify.json"
    config_path.write_text(json.dumps(config, indent=2))
    return config_path


def main():
    """CLI entry point."""
    import argparse

    parser = argparse.ArgumentParser(
        description="Generate task-aware runtime verification config"
    )
    parser.add_argument("path", nargs="?", default=".", help="Project root path")
    parser.add_argument("--description", "-d", required=True,
                        help="Task description (what is being implemented)")
    parser.add_argument("--goal", "-g", default="",
                        help="Task goal (expected outcome)")
    parser.add_argument("--files-changed", nargs="*", default=[],
                        help="List of modified files")
    parser.add_argument("--files-created", nargs="*", default=[],
                        help="List of created files")
    parser.add_argument("--app-type", choices=["server", "cli", "frontend", "hybrid"],
                        help="Application type (auto-detected if not specified)")
    parser.add_argument("--json", action="store_true", help="Output as JSON")
    parser.add_argument("--dry-run", action="store_true",
                        help="Show suggestions without modifying verify.json")
    parser.add_argument("--confidence-threshold", type=float, default=0.6,
                        help="Minimum confidence to auto-add tests (0.0-1.0)")

    args = parser.parse_args()
    project_root = Path(args.path).resolve()

    task_context = TaskContext(
        description=args.description,
        goal=args.goal,
        files_changed=args.files_changed,
        files_created=args.files_created,
        app_type=args.app_type or ""
    )

    existing_config = load_existing_config(project_root)

    result = generate_task_verification(task_context, project_root, existing_config)

    if args.json:
        print(json.dumps(result, indent=2))
    else:
        print(f"\n{'=' * 60}")
        print("Task-Aware Verification Generator")
        print(f"{'=' * 60}\n")

        print(f"Task: {task_context.description[:80]}...")
        print(f"Files analyzed: {len(task_context.files_changed + task_context.files_created)}")

        # Summary
        summary = result["summary"]
        print(f"\nDetected:")
        print(f"  - Routes: {len(summary['routes_found'])}")
        print(f"  - CLI Commands: {len(summary['commands_found'])}")

        # Suggestions
        suggestions = result["suggestions"]
        if suggestions:
            print(f"\nSuggested Verification Tests ({len(suggestions)}):\n")
            for s in suggestions:
                confidence_bar = "=" * int(s["confidence"] * 10)
                auto_add = "AUTO" if s["confidence"] >= args.confidence_threshold else "MANUAL"
                print(f"  [{auto_add}] {s['name']}")
                print(f"         Type: {s['type']}, Confidence: [{confidence_bar:<10}] {s['confidence']:.0%}")
                print(f"         Reason: {s['reason']}")
                print()
        else:
            print("\nNo new verification tests suggested.")

        # Save config
        if not args.dry_run:
            config_path = save_config(project_root, result["updated_config"])
            print(f"\nUpdated: {config_path}")

            meta = result["updated_config"].get("_task_verification", {})
            print(f"  - Auto-added {meta.get('auto_added_count', 0)} tests")
        else:
            print("\n[Dry run - no changes made]")

    return 0


if __name__ == "__main__":
    sys.exit(main())
