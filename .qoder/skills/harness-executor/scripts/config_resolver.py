#!/usr/bin/env python3
"""
Configuration Resolver

Resolves harness configuration files using a priority chain that supports
multiple project layouts (monorepo, standard, user-level).

Resolution chain (first match wins):
1. .harness/config/{name}.json     — monorepo-friendly hidden directory
2. harness/config/{name}.json      — standard harness layout (backward compatible)
3. .claude/harness/{name}.json     — user-level override

Usage:
    python config_resolver.py <config_name> [project_root] [--json] [--create-default]

As a library:
    from config_resolver import resolve_config, get_harness_root
    config = resolve_config("verify", Path("/path/to/project"))
    harness_root = get_harness_root(Path("/path/to/project"))
"""

import json
import os
import sys
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Tuple

# ---------------------------------------------------------------------------
# Resolution Chain
# ---------------------------------------------------------------------------

RESOLUTION_CHAIN: List[str] = [
    ".harness/config/{name}.json",
    "harness/config/{name}.json",
    ".claude/harness/{name}.json",
]

# Known config names and their descriptions
KNOWN_CONFIGS = {
    "validate": "Static validation pipeline configuration (build/lint/test steps)",
    "verify": "Runtime verification configuration (server/CLI/frontend smoke tests)",
    "environment": "Runtime ecosystem contract (databases, services, secrets, ports)",
    "adapter": "Explicit language adapter override",
}


# ---------------------------------------------------------------------------
# Core API
# ---------------------------------------------------------------------------

def resolve_config(
    name: str,
    project_root: Path,
    auto_generate: Optional[Callable[[Path], Dict[str, Any]]] = None,
) -> Optional[Dict[str, Any]]:
    """
    Resolve a configuration file by name.

    Args:
        name: Config name (e.g., "verify", "validate", "environment")
        project_root: Project root directory
        auto_generate: Optional function to generate config if not found.
                       Receives project_root, returns config dict.

    Returns:
        Parsed config dict, or None if not found and no auto_generate.
    """
    project_root = Path(project_root).resolve()

    for pattern in RESOLUTION_CHAIN:
        config_path = project_root / pattern.format(name=name)
        if config_path.exists():
            try:
                content = config_path.read_text(encoding="utf-8")
                config = json.loads(content)
                # Attach metadata about where it was found
                config["_resolved_from"] = str(config_path)
                config["_resolution_method"] = "file"
                return config
            except (json.JSONDecodeError, OSError) as e:
                print(f"Warning: Failed to parse {config_path}: {e}", file=sys.stderr)
                continue

    # Not found in any location
    if auto_generate:
        config = auto_generate(project_root)
        if config:
            config["_resolved_from"] = "auto_generated"
            config["_resolution_method"] = "auto_generate"
            return config

    return None


def resolve_config_path(name: str, project_root: Path) -> Optional[Path]:
    """
    Find the path to an existing config file (without reading it).
    Returns None if no config file exists.
    """
    project_root = Path(project_root).resolve()

    for pattern in RESOLUTION_CHAIN:
        config_path = project_root / pattern.format(name=name)
        if config_path.exists():
            return config_path

    return None


def get_config_write_path(name: str, project_root: Path) -> Path:
    """
    Get the path where a new config file should be written.

    Respects existing convention:
    - If .harness/ exists → write to .harness/config/
    - If harness/ exists → write to harness/config/
    - Default → harness/config/ (backward compatible)
    """
    project_root = Path(project_root).resolve()

    if (project_root / ".harness").exists():
        return project_root / ".harness" / "config" / f"{name}.json"
    elif (project_root / "harness").exists():
        return project_root / "harness" / "config" / f"{name}.json"
    else:
        # Default to standard layout
        return project_root / "harness" / "config" / f"{name}.json"


def write_config(name: str, project_root: Path, config: Dict[str, Any]) -> Path:
    """
    Write a config file to the appropriate location.

    Returns the path where the config was written.
    """
    # Remove internal metadata before writing
    clean_config = {k: v for k, v in config.items() if not k.startswith("_")}

    config_path = get_config_write_path(name, project_root)
    config_path.parent.mkdir(parents=True, exist_ok=True)
    config_path.write_text(
        json.dumps(clean_config, indent=2, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    return config_path


# ---------------------------------------------------------------------------
# Harness Root Resolution
# ---------------------------------------------------------------------------

def get_harness_root(project_root: Path) -> Path:
    """
    Get the harness root directory for a project.

    Returns:
        .harness/ if it exists, otherwise harness/ (created if needed).
    """
    project_root = Path(project_root).resolve()

    if (project_root / ".harness").is_dir():
        return project_root / ".harness"
    return project_root / "harness"


def get_harness_subdir(project_root: Path, *subdirs: str) -> Path:
    """
    Get a subdirectory under the harness root, creating it if needed.

    Example:
        get_harness_subdir(root, "tasks", task_id, "state")
        → harness/tasks/<task_id>/state/
    """
    harness_root = get_harness_root(project_root)
    result = harness_root.joinpath(*subdirs)
    return result


# ---------------------------------------------------------------------------
# Project Root Detection
# ---------------------------------------------------------------------------

def find_project_root(start_dir: Optional[Path] = None) -> Path:
    """
    Walk up from start_dir to find the project root.

    Detection order:
    1. AGENTS.md exists (harness convention)
    2. .git/ exists (git root)
    3. go.mod / package.json / pyproject.toml / Cargo.toml / pom.xml exists
    4. Fall back to start_dir
    """
    if start_dir is None:
        start_dir = Path.cwd()
    start_dir = Path(start_dir).resolve()

    current = start_dir
    while True:
        # Harness marker
        if (current / "AGENTS.md").exists():
            return current
        # Git root
        if (current / ".git").exists():
            return current
        # Language manifest files
        for manifest in ["go.mod", "package.json", "pyproject.toml", "Cargo.toml", "pom.xml"]:
            if (current / manifest).exists():
                return current

        parent = current.parent
        if parent == current:
            break  # Reached filesystem root
        current = parent

    return start_dir  # Fallback


# ---------------------------------------------------------------------------
# CLI Interface
# ---------------------------------------------------------------------------

def main():
    import argparse

    parser = argparse.ArgumentParser(description="Resolve harness configuration files")
    parser.add_argument("config_name", help="Config name: verify, validate, environment, adapter")
    parser.add_argument("project_root", nargs="?", default=".",
                        help="Project root directory (default: current directory)")
    parser.add_argument("--json", action="store_true",
                        help="Output resolved config as JSON")
    parser.add_argument("--path-only", action="store_true",
                        help="Only output the config file path (if found)")
    parser.add_argument("--write-path", action="store_true",
                        help="Output where a new config would be written")
    parser.add_argument("--harness-root", action="store_true",
                        help="Output the harness root directory")

    args = parser.parse_args()
    project_root = Path(args.project_root).resolve()

    if not project_root.is_dir():
        print(f"Error: {project_root} is not a directory", file=sys.stderr)
        sys.exit(1)

    if args.harness_root:
        print(get_harness_root(project_root))
        return

    if args.write_path:
        print(get_config_write_path(args.config_name, project_root))
        return

    if args.path_only:
        path = resolve_config_path(args.config_name, project_root)
        if path:
            print(path)
        else:
            print(f"Config '{args.config_name}' not found", file=sys.stderr)
            sys.exit(1)
        return

    config = resolve_config(args.config_name, project_root)
    if config:
        if args.json:
            output = {k: v for k, v in config.items() if not k.startswith("_")}
            print(json.dumps(output, indent=2))
        else:
            source = config.get("_resolved_from", "unknown")
            print(f"Config:  {args.config_name}")
            print(f"Source:  {source}")
            print(f"Keys:    {', '.join(k for k in config if not k.startswith('_'))}")
    else:
        desc = KNOWN_CONFIGS.get(args.config_name, "Unknown config")
        print(f"Config '{args.config_name}' not found in:")
        for pattern in RESOLUTION_CHAIN:
            print(f"  - {project_root / pattern.format(name=args.config_name)}")
        print(f"\nDescription: {desc}")
        print(f"Write path:  {get_config_write_path(args.config_name, project_root)}")
        sys.exit(1)


if __name__ == "__main__":
    main()
