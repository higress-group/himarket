#!/usr/bin/env python3
"""
Validation pipeline runner.

Runs a configurable sequence of validation steps (build, lint, test, quality)
and reports results. Designed to be called by AI agents during the
self-validation loop of harness-executor.

Resolution priority for validation steps:
1. Custom config file (harness/config/validate.json or .harness/config/validate.json)
2. Commands from docs/DEVELOPMENT.md
3. Language adapter defaults (detected automatically)
4. Generic Makefile discovery (when no language is detected)

This script NEVER defaults to a specific language (e.g., Go). Unknown projects
fall back to Makefile target discovery.
"""

import json
import os
import re
import subprocess
import sys
import time
from dataclasses import dataclass, field, asdict
from enum import Enum
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

# Import adapter detection if available, otherwise use inline detection
try:
    from detect_adapter import detect_adapter, get_command
except ImportError:
    # Inline minimal adapter detection for standalone usage
    detect_adapter = None
    get_command = None


class StepStatus(str, Enum):
    PASS = "pass"
    FAIL = "fail"
    SKIP = "skip"
    ERROR = "error"


@dataclass
class ValidationStep:
    """A single validation step."""
    name: str
    command: str
    required: bool = True
    skip_if_missing: Optional[str] = None  # Skip if this file/dir doesn't exist
    timeout: int = 300  # seconds


@dataclass
class StepResult:
    """Result of running a validation step."""
    name: str
    status: StepStatus
    duration_seconds: float = 0.0
    output: str = ""
    error: str = ""
    command: str = ""
    skipped_reason: str = ""


@dataclass
class ValidationReport:
    """Full validation pipeline report."""
    project_root: str
    adapter: str = ""  # Which adapter was used
    timestamp: str = ""
    total_duration_seconds: float = 0.0
    all_passed: bool = False
    steps: List[dict] = field(default_factory=list)
    summary: Dict = field(default_factory=dict)


# ---------------------------------------------------------------------------
# Adapter-Based Step Generation
# ---------------------------------------------------------------------------
# These functions generate validation steps from adapter configuration.
# They replace the old hardcoded GO_STEPS, TS_STEPS, PY_STEPS.
# ---------------------------------------------------------------------------

def adapter_to_steps(adapter: Dict[str, Any], project_root: Path) -> List[ValidationStep]:
    """
    Convert adapter commands to validation steps.

    Standard step sequence: build → lint-arch → lint → test
    Steps are only added if the adapter provides the command.
    """
    steps = []
    commands = adapter.get("commands", {})

    # Build step
    if commands.get("build"):
        steps.append(ValidationStep(
            name="build",
            command=commands["build"],
            required=True,
        ))

    # Architectural lint (layer dependencies)
    if commands.get("lint_arch"):
        # Determine skip condition based on language
        skip_file = None
        if adapter.get("language") == "go":
            skip_file = "Makefile"
        elif adapter.get("language") in ("typescript", "javascript"):
            skip_file = "package.json"
        elif adapter.get("language") == "python":
            skip_file = "scripts/lint_deps.py"

        steps.append(ValidationStep(
            name="lint-arch",
            command=commands["lint_arch"],
            required=True,
            skip_if_missing=skip_file,
        ))

    # General lint
    if commands.get("lint"):
        steps.append(ValidationStep(
            name="lint",
            command=commands["lint"],
            required=True,
        ))

    # Tests
    if commands.get("test"):
        steps.append(ValidationStep(
            name="test",
            command=commands["test"],
            required=True,
            timeout=600,
        ))

    return steps


def discover_makefile_steps(project_root: Path) -> List[ValidationStep]:
    """
    Discover validation commands from Makefile targets.
    Fallback for unrecognized project types.
    """
    makefile = project_root / "Makefile"
    if not makefile.exists():
        return []

    try:
        content = makefile.read_text()
    except OSError:
        return []

    # Extract targets
    targets = set()
    for line in content.splitlines():
        match = re.match(r'^([a-zA-Z_][a-zA-Z0-9_-]*)\s*:', line)
        if match:
            targets.add(match.group(1))

    steps = []

    # Map common target names to validation steps
    target_map = [
        ("build", ["build", "compile"], True),
        ("lint-arch", ["lint-arch", "lint_arch", "check-arch"], True),
        ("lint", ["lint", "check"], True),
        ("test", ["test", "tests", "check"], True),
    ]

    for step_name, target_names, required in target_map:
        for target in target_names:
            if target in targets:
                steps.append(ValidationStep(
                    name=step_name,
                    command=f"make {target}",
                    required=required,
                    timeout=600 if step_name == "test" else 300,
                ))
                break

    return steps


def load_config_file(project_root: Path) -> Optional[Dict]:
    """
    Load custom validation config from harness config.

    Searches in order:
    1. .harness/config/validate.json (monorepo style)
    2. harness/config/validate.json (standard)

    Config file format:
    {
      "steps": [
        {"name": "build", "command": "pnpm build", "required": true, "timeout": 300},
        {"name": "lint-arch", "command": "pnpm lint:arch", "required": true},
        {"name": "test", "command": "pnpm test", "required": true, "timeout": 600}
      ]
    }
    """
    for config_dir in [".harness/config", "harness/config"]:
        config_path = project_root / config_dir / "validate.json"
        if config_path.exists():
            try:
                return json.loads(config_path.read_text())
            except json.JSONDecodeError as e:
                print(f"Warning: Invalid JSON in {config_path}: {e}", file=sys.stderr)
    return None


def steps_from_config(config: Dict) -> List[ValidationStep]:
    """Convert config dict to ValidationStep list."""
    steps = []
    for step_config in config.get("steps", []):
        steps.append(ValidationStep(
            name=step_config.get("name", "unknown"),
            command=step_config.get("command", ""),
            required=step_config.get("required", True),
            skip_if_missing=step_config.get("skip_if_missing"),
            timeout=step_config.get("timeout", 300),
        ))
    return steps


def parse_dev_commands(project_root: Path) -> Dict[str, str]:
    """
    Try to parse build/test/lint commands from docs/DEVELOPMENT.md.
    Returns a dict mapping step names to commands.
    """
    dev_md = project_root / "docs" / "DEVELOPMENT.md"
    if not dev_md.exists():
        return {}

    commands = {}
    try:
        content = dev_md.read_text()
    except OSError:
        return {}

    # Look for code blocks in build/test/lint sections
    sections = re.split(r'^#+\s+', content, flags=re.MULTILINE)

    command_patterns = {
        'build': r'(?:build|compile|make)',
        'test': r'(?:test|testing)',
        'lint': r'(?:lint|linting|check)',
    }

    for section in sections:
        section_lower = section.lower()
        for cmd_name, pattern in command_patterns.items():
            if re.search(pattern, section_lower):
                # Extract first code block
                code_match = re.search(r'```(?:bash|sh|shell)?\n([^\n]+)', section)
                if code_match:
                    cmd = code_match.group(1).strip()
                    if cmd and not cmd.startswith('#'):
                        commands[cmd_name] = cmd
                        break

    return commands


def steps_from_dev_commands(commands: Dict[str, str]) -> List[ValidationStep]:
    """Convert DEVELOPMENT.md commands to validation steps."""
    steps = []
    step_order = ['build', 'lint-arch', 'lint', 'test']

    for step_name in step_order:
        if step_name in commands:
            steps.append(ValidationStep(
                name=step_name,
                command=commands[step_name],
                required=True,
                timeout=600 if step_name == 'test' else 300,
            ))

    return steps


def get_default_steps(project_root: Path, verbose: bool = False) -> Tuple[List[ValidationStep], str]:
    """
    Get validation steps for a project.

    Resolution priority:
    1. Custom config file (harness/config/validate.json)
    2. Commands from docs/DEVELOPMENT.md
    3. Language adapter defaults
    4. Makefile target discovery (fallback)

    Returns:
        Tuple of (steps, adapter_name)
    """
    # 1. Check for custom config first (highest priority)
    config = load_config_file(project_root)
    if config:
        if verbose:
            print("[validate] Using custom config from harness/config/validate.json", file=sys.stderr)
        return steps_from_config(config), "custom_config"

    # 2. Try to parse DEVELOPMENT.md
    dev_commands = parse_dev_commands(project_root)
    if dev_commands:
        steps = steps_from_dev_commands(dev_commands)
        if steps:
            if verbose:
                print("[validate] Using commands from docs/DEVELOPMENT.md", file=sys.stderr)
            return steps, "development_md"

    # 3. Use adapter detection
    if detect_adapter is not None:
        adapter = detect_adapter(project_root, verbose=verbose)
        adapter_name = adapter.get("language", "generic")

        if adapter_name != "generic":
            steps = adapter_to_steps(adapter, project_root)
            if steps:
                if verbose:
                    print(f"[validate] Using {adapter_name} adapter", file=sys.stderr)
                return steps, adapter_name
    else:
        # Inline minimal adapter detection when detect_adapter not available
        adapter_name = _inline_detect_language(project_root)
        if adapter_name != "generic":
            steps = _inline_get_language_steps(adapter_name, project_root)
            if steps:
                if verbose:
                    print(f"[validate] Using inline {adapter_name} detection", file=sys.stderr)
                return steps, adapter_name

    # 4. Fallback: Makefile discovery
    makefile_steps = discover_makefile_steps(project_root)
    if makefile_steps:
        if verbose:
            print("[validate] Using Makefile target discovery", file=sys.stderr)
        return makefile_steps, "makefile"

    # 5. Ultimate fallback: empty steps with warning
    print("Warning: No validation steps found. Check your project setup.", file=sys.stderr)
    return [], "none"


def _inline_detect_language(project_root: Path) -> str:
    """Minimal inline language detection (when detect_adapter.py not available)."""
    if (project_root / "go.mod").exists():
        return "go"
    elif (project_root / "package.json").exists():
        return "typescript"
    elif (project_root / "pyproject.toml").exists() or (project_root / "requirements.txt").exists():
        return "python"
    elif (project_root / "Cargo.toml").exists():
        return "rust"
    elif (project_root / "pom.xml").exists() or (project_root / "build.gradle").exists():
        return "java"
    return "generic"


def _inline_get_language_steps(language: str, project_root: Path) -> List[ValidationStep]:
    """Minimal inline step generation (when detect_adapter.py not available)."""
    # Detect package manager for TypeScript
    pkg_manager = "npm"
    if language == "typescript":
        for lockfile, manager in [("pnpm-lock.yaml", "pnpm"), ("yarn.lock", "yarn"), ("bun.lockb", "bun")]:
            if (project_root / lockfile).exists():
                pkg_manager = manager
                break

    steps_by_language = {
        "go": [
            ValidationStep("build", "go build ./...", True),
            ValidationStep("lint-arch", "make lint-arch", True, "Makefile"),
            ValidationStep("test", "go test ./...", True, None, 600),
        ],
        "typescript": [
            ValidationStep("build", f"{pkg_manager} run build", True),
            ValidationStep("lint-arch", f"{pkg_manager} run lint:arch", True, "package.json"),
            ValidationStep("lint", f"{pkg_manager} run lint", True),
            ValidationStep("test", f"{pkg_manager} {'test' if pkg_manager == 'yarn' else 'run test'}", True, None, 600),
        ],
        "python": [
            ValidationStep("lint-arch", "python scripts/lint_deps.py src/", True, "scripts/lint_deps.py"),
            ValidationStep("lint", "ruff check .", True),
            ValidationStep("test", "pytest", True, None, 600),
        ],
        "rust": [
            ValidationStep("build", "cargo build", True),
            ValidationStep("lint", "cargo clippy -- -D warnings", True),
            ValidationStep("test", "cargo test", True, None, 600),
        ],
        "java": [
            # Will be overridden by build tool detection if needed
            ValidationStep("build", "./gradlew build -x test" if (project_root / "build.gradle").exists() else "mvn package -DskipTests", True),
            ValidationStep("test", "./gradlew test" if (project_root / "build.gradle").exists() else "mvn test", True, None, 600),
        ],
    }

    return steps_by_language.get(language, [])


def run_step(step: ValidationStep, project_root: Path) -> StepResult:
    """Run a single validation step and return the result."""
    result = StepResult(name=step.name, status=StepStatus.SKIP, command=step.command)

    # Check skip condition
    if step.skip_if_missing:
        check_path = project_root / step.skip_if_missing
        if not check_path.exists():
            result.skipped_reason = f"{step.skip_if_missing} not found"
            return result

    start_time = time.time()

    try:
        proc = subprocess.run(
            step.command,
            shell=True,
            cwd=str(project_root),
            capture_output=True,
            text=True,
            timeout=step.timeout,
        )

        result.duration_seconds = round(time.time() - start_time, 2)
        result.output = proc.stdout[-2000:] if len(proc.stdout) > 2000 else proc.stdout
        result.error = proc.stderr[-2000:] if len(proc.stderr) > 2000 else proc.stderr

        if proc.returncode == 0:
            result.status = StepStatus.PASS
        else:
            result.status = StepStatus.FAIL

    except subprocess.TimeoutExpired:
        result.duration_seconds = round(time.time() - start_time, 2)
        result.status = StepStatus.ERROR
        result.error = f"Timeout after {step.timeout}s"

    except Exception as e:
        result.duration_seconds = round(time.time() - start_time, 2)
        result.status = StepStatus.ERROR
        result.error = str(e)

    return result


def run_pipeline(
    project_root: Path,
    steps: Optional[List[ValidationStep]] = None,
    stop_on_failure: bool = True,
    verbose: bool = False,
) -> ValidationReport:
    """Run the full validation pipeline."""
    adapter_name = ""

    if steps is None:
        steps, adapter_name = get_default_steps(project_root, verbose=verbose)

    report = ValidationReport(
        project_root=str(project_root),
        adapter=adapter_name,
        timestamp=time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    )

    start_time = time.time()
    all_passed = True
    passed = 0
    failed = 0
    skipped = 0

    for step in steps:
        result = run_step(step, project_root)
        report.steps.append(asdict(result))

        if result.status == StepStatus.PASS:
            passed += 1
        elif result.status == StepStatus.SKIP:
            skipped += 1
        else:
            if step.required:
                all_passed = False
                failed += 1
                if stop_on_failure:
                    # Skip remaining steps
                    break
            else:
                failed += 1

    report.total_duration_seconds = round(time.time() - start_time, 2)
    report.all_passed = all_passed
    report.summary = {
        "total": len(steps),
        "passed": passed,
        "failed": failed,
        "skipped": skipped,
        "all_required_passed": all_passed,
    }

    return report


def main():
    """Main entry point."""
    import argparse

    parser = argparse.ArgumentParser(description="Run validation pipeline")
    parser.add_argument("path", nargs="?", default=".", help="Project root path")
    parser.add_argument("--json", action="store_true", help="Output as JSON")
    parser.add_argument(
        "--steps", nargs="+",
        choices=["build", "lint-arch", "lint", "test", "quality-score", "typecheck"],
        help="Run only specific steps"
    )
    parser.add_argument("--no-stop-on-failure", action="store_true",
                       help="Continue running steps even if one fails")
    parser.add_argument("--output", type=str, help="Save report to file")
    parser.add_argument("--verbose", "-v", action="store_true",
                       help="Show detection details")

    args = parser.parse_args()
    project_root = Path(args.path).resolve()

    if not project_root.is_dir():
        print(f"Error: {project_root} is not a directory", file=sys.stderr)
        sys.exit(1)

    # Filter steps if specified
    steps = None
    if args.steps:
        all_steps, _ = get_default_steps(project_root, verbose=args.verbose)
        steps = [s for s in all_steps if s.name in args.steps]

    report = run_pipeline(
        project_root,
        steps=steps,
        stop_on_failure=not args.no_stop_on_failure,
        verbose=args.verbose,
    )

    if args.json:
        report_dict = asdict(report)
        output = json.dumps(report_dict, indent=2)
        if args.output:
            Path(args.output).write_text(output)
        print(output)
    else:
        # Human-readable output
        print(f"\n{'=' * 50}")
        print(f"Validation Report: {project_root.name}")
        if report.adapter:
            print(f"Adapter: {report.adapter}")
        print(f"{'=' * 50}\n")

        for step_data in report.steps:
            status_icons = {
                "pass": "✅",
                "fail": "❌",
                "skip": "⏭️",
                "error": "💥",
            }
            icon = status_icons.get(step_data["status"], "?")
            duration = f" ({step_data['duration_seconds']}s)" if step_data["duration_seconds"] else ""
            print(f"  {icon} {step_data['name']}: {step_data['status'].upper()}{duration}")

            if step_data["status"] == "skip" and step_data["skipped_reason"]:
                print(f"     Skipped: {step_data['skipped_reason']}")
            elif step_data["status"] in ("fail", "error") and step_data["error"]:
                # Show first 3 lines of error
                error_lines = step_data["error"].strip().splitlines()[:3]
                for line in error_lines:
                    print(f"     {line}")

        s = report.summary
        print(f"\nSummary: {s['passed']} passed, {s['failed']} failed, {s['skipped']} skipped")
        print(f"Duration: {report.total_duration_seconds}s")
        print(f"Result: {'✅ ALL PASSED' if report.all_passed else '❌ FAILURES DETECTED'}\n")

        if args.output:
            Path(args.output).write_text(json.dumps(asdict(report), indent=2))
            print(f"Report saved to: {args.output}")

    sys.exit(0 if report.all_passed else 1)


if __name__ == "__main__":
    main()
