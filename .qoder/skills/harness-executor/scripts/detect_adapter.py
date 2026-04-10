#!/usr/bin/env python3
"""
Language Adapter Detection

Discovers the best-matching language adapter for a project by examining
project files. Returns adapter configuration as a dictionary.

Resolution priority:
1. Explicit adapter in harness config (harness/config/adapter.json)
2. Auto-detection from project files (highest confidence wins)
3. Generic fallback adapter

Usage:
    python detect_adapter.py [project_root] [--json] [--verbose]

As a library:
    from detect_adapter import detect_adapter
    adapter = detect_adapter(Path("/path/to/project"))
"""

import json
import os
import re
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

# ---------------------------------------------------------------------------
# Adapter Registry
# ---------------------------------------------------------------------------
# Each adapter is defined inline here. The markdown adapter files in
# references/adapters/ serve as documentation + extended config. This script
# carries the minimal detection + command config needed at runtime.
# ---------------------------------------------------------------------------

ADAPTERS: List[Dict[str, Any]] = [
    {
        "language": "go",
        "display_name": "Go",
        "detection": {
            "files": ["go.mod"],
            "confidence": 0.95,
        },
        "commands": {
            "build": "go build ./...",
            "test": "go test ./...",
            "lint": "golangci-lint run",
            "lint_arch": "make lint-arch",
            "format": "gofmt -w .",
            "start": None,
            "dev": None,
        },
        "source_extensions": [".go"],
        "import_pattern": r'"([^"]+)"',
        "env_var_patterns": [
            r'os\.Getenv\("([^"]+)"\)',
            r'os\.LookupEnv\("([^"]+)"\)',
        ],
    },
    {
        "language": "typescript",
        "display_name": "TypeScript / JavaScript",
        "detection": {
            "files": ["package.json", "tsconfig.json"],
            "confidence": 0.90,
        },
        "commands": {
            "build": "{pkg_manager} run build",
            "test": "{pkg_manager} test",
            "lint": "{pkg_manager} run lint",
            "lint_arch": "{pkg_manager} run lint:arch",
            "format": "{pkg_manager} run format",
            "start": "{pkg_manager} start",
            "dev": "{pkg_manager} run dev",
        },
        "source_extensions": [".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs"],
        "import_pattern": r"""from ['"]([^'"]+)['"]|require\(['"]([^'"]+)['"]\)""",
        "env_var_patterns": [
            r"process\.env\.([A-Z_][A-Z0-9_]*)",
            r"""process\.env\[['"]([A-Z_][A-Z0-9_]*)['"]\]""",
        ],
        "package_manager_detection": [
            ("pnpm-lock.yaml", "pnpm"),
            ("yarn.lock", "yarn"),
            ("bun.lockb", "bun"),
            ("package-lock.json", "npm"),
        ],
        "package_manager_default": "npm",
    },
    {
        "language": "python",
        "display_name": "Python",
        "detection": {
            "files": ["pyproject.toml", "setup.py", "requirements.txt", "Pipfile"],
            "confidence": 0.90,
        },
        "commands": {
            "build": None,
            "test": "pytest",
            "lint": "ruff check .",
            "lint_arch": "python scripts/lint_deps.py src/",
            "format": "ruff format .",
            "start": None,
            "dev": None,
        },
        "source_extensions": [".py"],
        "import_pattern": r"^(?:from|import)\s+([\w.]+)",
        "env_var_patterns": [
            r"""os\.environ\.get\(\s*['"]([^'"]+)['"]\)""",
            r"""os\.environ\[['"]([^'"]+)['"]\]""",
            r"""os\.getenv\(\s*['"]([^'"]+)['"]\)""",
        ],
        "package_manager_detection": [
            ("poetry.lock", "poetry"),
            ("uv.lock", "uv"),
            ("Pipfile.lock", "pipenv"),
            ("pdm.lock", "pdm"),
        ],
        "package_manager_default": "pip",
    },
    {
        "language": "java",
        "display_name": "Java / Kotlin",
        "detection": {
            "files": ["pom.xml", "build.gradle", "build.gradle.kts"],
            "confidence": 0.90,
        },
        "commands": {
            "build": None,  # Resolved per build tool
            "test": None,
            "lint": None,
            "lint_arch": None,
            "format": None,
            "start": None,
            "dev": None,
        },
        "source_extensions": [".java", ".kt"],
        "import_pattern": r"^import\s+([\w.]+)",
        "env_var_patterns": [
            r"""System\.getenv\(\s*['"]([^'"]+)['"]\)""",
        ],
        "build_tool_detection": [
            ("pom.xml", "maven", {
                "build": "mvn package -DskipTests",
                "test": "mvn test",
            }),
            ("build.gradle.kts", "gradle", {
                "build": "./gradlew build -x test",
                "test": "./gradlew test",
            }),
            ("build.gradle", "gradle", {
                "build": "./gradlew build -x test",
                "test": "./gradlew test",
            }),
        ],
    },
    {
        "language": "rust",
        "display_name": "Rust",
        "detection": {
            "files": ["Cargo.toml"],
            "confidence": 0.95,
        },
        "commands": {
            "build": "cargo build",
            "test": "cargo test",
            "lint": "cargo clippy -- -D warnings",
            "lint_arch": None,
            "format": "cargo fmt",
            "start": "cargo run",
            "dev": "cargo watch -x run",
        },
        "source_extensions": [".rs"],
        "import_pattern": r"use\s+([\w:]+)",
        "env_var_patterns": [
            r"""std::env::var\(\s*"([^"]+)"\)""",
            r"""env::var\(\s*"([^"]+)"\)""",
        ],
    },
]

GENERIC_ADAPTER: Dict[str, Any] = {
    "language": "generic",
    "display_name": "Generic (Auto-Discovery)",
    "detection": {
        "files": [],
        "confidence": 0.10,
    },
    "commands": {
        "build": None,
        "test": None,
        "lint": None,
        "lint_arch": None,
        "format": None,
        "start": None,
        "dev": None,
    },
    "source_extensions": [],
    "import_pattern": None,
    "env_var_patterns": [],
}


# ---------------------------------------------------------------------------
# Detection Logic
# ---------------------------------------------------------------------------

def detect_adapter(project_root: Path, verbose: bool = False) -> Dict[str, Any]:
    """
    Detect the best-matching language adapter for the project.

    Resolution:
    1. Explicit adapter in harness config
    2. Auto-detection (highest confidence match)
    3. Generic fallback
    """
    project_root = Path(project_root).resolve()

    # 1. Check for explicit adapter override
    explicit = _load_explicit_adapter(project_root)
    if explicit:
        if verbose:
            print(f"[detect_adapter] Using explicit adapter: {explicit['language']}", file=sys.stderr)
        return explicit

    # 2. Auto-detect from project files
    candidates: List[Tuple[float, Dict[str, Any]]] = []

    for adapter in ADAPTERS:
        detection = adapter["detection"]
        matched = False

        for fname in detection["files"]:
            if (project_root / fname).exists():
                matched = True
                break

        if matched:
            confidence = detection["confidence"]
            candidates.append((confidence, adapter))
            if verbose:
                print(f"[detect_adapter] Candidate: {adapter['language']} (confidence={confidence})", file=sys.stderr)

    if candidates:
        # Sort by confidence descending, return best match
        candidates.sort(key=lambda x: -x[0])
        best = candidates[0][1]

        # Post-process: resolve package manager, build tool, etc.
        resolved = _resolve_adapter(best, project_root)
        if verbose:
            print(f"[detect_adapter] Selected: {resolved['language']}", file=sys.stderr)
        return resolved

    # 3. Fallback: try Makefile discovery
    generic = dict(GENERIC_ADAPTER)
    generic = _discover_from_makefile(generic, project_root)
    generic = _discover_from_readme(generic, project_root)
    if verbose:
        print(f"[detect_adapter] Fallback to generic adapter", file=sys.stderr)
    return generic


def _load_explicit_adapter(project_root: Path) -> Optional[Dict[str, Any]]:
    """Load explicit adapter override from harness config."""
    for config_path in [
        project_root / ".harness" / "config" / "adapter.json",
        project_root / "harness" / "config" / "adapter.json",
    ]:
        if config_path.exists():
            try:
                return json.loads(config_path.read_text())
            except (json.JSONDecodeError, OSError):
                continue
    return None


def _resolve_adapter(adapter: Dict[str, Any], project_root: Path) -> Dict[str, Any]:
    """Post-process adapter: resolve package manager, build tool, templates."""
    resolved = dict(adapter)
    resolved["commands"] = dict(adapter.get("commands", {}))

    # Resolve package manager for TS/Python
    if "package_manager_detection" in adapter:
        pkg_manager = adapter.get("package_manager_default", "npm")
        for lockfile, manager in adapter["package_manager_detection"]:
            if (project_root / lockfile).exists():
                pkg_manager = manager
                break
        resolved["_resolved_package_manager"] = pkg_manager

        # Replace {pkg_manager} in commands
        for key, cmd in resolved["commands"].items():
            if cmd and "{pkg_manager}" in cmd:
                resolved["commands"][key] = cmd.replace("{pkg_manager}", pkg_manager)

    # Resolve build tool for Java
    if "build_tool_detection" in adapter:
        for manifest, tool_name, tool_commands in adapter["build_tool_detection"]:
            if (project_root / manifest).exists():
                resolved["_resolved_build_tool"] = tool_name
                for key, cmd in tool_commands.items():
                    resolved["commands"][key] = cmd
                break

    return resolved


def _discover_from_makefile(adapter: Dict[str, Any], project_root: Path) -> Dict[str, Any]:
    """Discover commands from Makefile targets."""
    makefile = project_root / "Makefile"
    if not makefile.exists():
        return adapter

    try:
        content = makefile.read_text()
    except OSError:
        return adapter

    target_map = {
        "build": ["build", "compile"],
        "test": ["test", "check"],
        "lint": ["lint"],
        "lint_arch": ["lint-arch", "lint_arch"],
        "format": ["fmt", "format"],
        "start": ["run", "start", "serve"],
        "dev": ["dev", "watch"],
    }

    resolved = dict(adapter)
    resolved["commands"] = dict(adapter.get("commands", {}))

    # Extract targets from Makefile
    targets = set()
    for line in content.splitlines():
        match = re.match(r'^([a-zA-Z_][a-zA-Z0-9_-]*)\s*:', line)
        if match:
            targets.add(match.group(1))

    for cmd_key, target_names in target_map.items():
        if resolved["commands"].get(cmd_key) is None:
            for target_name in target_names:
                if target_name in targets:
                    resolved["commands"][cmd_key] = f"make {target_name}"
                    break

    return resolved


def _discover_from_readme(adapter: Dict[str, Any], project_root: Path) -> Dict[str, Any]:
    """Try to discover commands from README code blocks."""
    for readme_name in ["README.md", "readme.md", "README.rst", "README"]:
        readme = project_root / readme_name
        if readme.exists():
            break
    else:
        return adapter

    # Minimal discovery — just look for common command patterns
    # Full parsing is overkill for fallback mode
    return adapter


# ---------------------------------------------------------------------------
# Utility Functions (for use by other scripts)
# ---------------------------------------------------------------------------

def get_command(adapter: Dict[str, Any], command_name: str) -> Optional[str]:
    """Get a resolved command from the adapter, or None if not available."""
    return adapter.get("commands", {}).get(command_name)


def get_source_extensions(adapter: Dict[str, Any]) -> List[str]:
    """Get source file extensions for this language."""
    return adapter.get("source_extensions", [])


def get_env_var_patterns(adapter: Dict[str, Any]) -> List[str]:
    """Get regex patterns for detecting environment variable usage."""
    return adapter.get("env_var_patterns", [])


# ---------------------------------------------------------------------------
# CLI Interface
# ---------------------------------------------------------------------------

def main():
    import argparse

    parser = argparse.ArgumentParser(description="Detect language adapter for a project")
    parser.add_argument("project_root", nargs="?", default=".",
                        help="Project root directory (default: current directory)")
    parser.add_argument("--json", action="store_true",
                        help="Output as JSON")
    parser.add_argument("--verbose", "-v", action="store_true",
                        help="Verbose output")
    parser.add_argument("--command", "-c", type=str,
                        help="Get a specific command (build, test, lint, etc.)")

    args = parser.parse_args()
    project_root = Path(args.project_root).resolve()

    if not project_root.is_dir():
        print(f"Error: {project_root} is not a directory", file=sys.stderr)
        sys.exit(1)

    adapter = detect_adapter(project_root, verbose=args.verbose)

    if args.command:
        cmd = get_command(adapter, args.command)
        if cmd:
            print(cmd)
        else:
            print(f"No '{args.command}' command for {adapter['display_name']}", file=sys.stderr)
            sys.exit(1)
    elif args.json:
        # Clean internal fields for JSON output
        output = {k: v for k, v in adapter.items() if not k.startswith("_")}
        print(json.dumps(output, indent=2))
    else:
        print(f"Language:  {adapter['display_name']}")
        print(f"Adapter:   {adapter['language']}")
        if adapter.get("_resolved_package_manager"):
            print(f"Pkg Mgr:   {adapter['_resolved_package_manager']}")
        if adapter.get("_resolved_build_tool"):
            print(f"Build Tool: {adapter['_resolved_build_tool']}")
        print(f"\nCommands:")
        for key, cmd in adapter.get("commands", {}).items():
            if cmd:
                print(f"  {key:12s} → {cmd}")


if __name__ == "__main__":
    main()
