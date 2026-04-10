#!/usr/bin/env python3
"""
Task state management CLI.

Provides a unified interface for managing harness task state, replacing manual
bash commands with a simple CLI.

Usage:
    # Initialize a task
    python3 scripts/task_state.py init "task-name" --phases 3 --description "what we're doing"

    # Checkpoint after each phase (optional for single-phase tasks)
    python3 scripts/task_state.py checkpoint --task-id <ID> --phase 2 --summary "phase 2 done"

    # Complete task (requires verification-report.json from verifier subagent)
    python3 scripts/task_state.py complete --task-id <ID> --summary "all done" --files-changed f1 f2

    # Queries
    python3 scripts/task_state.py show --task-id <ID>
    python3 scripts/task_state.py list

Note: All tasks require functional verification before completion. The `complete` command
checks for harness/trace/verification-report.json and rejects if missing or invalid.
"""

import argparse
import json
import os
import re
import sys
from datetime import datetime
from itertools import chain
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

# Import shared harness root resolver (supports both harness/ and .harness/ layouts)
try:
    from config_resolver import get_harness_root
except ImportError:
    def get_harness_root(project_root: Path) -> Path:
        if (project_root / ".harness").is_dir():
            return project_root / ".harness"
        return project_root / "harness"


# Module-level constants
STATUS_ICONS = {
    "in_progress": "🔄",
    "completed": "✅",
    "failed": "❌",
    "blocked": "🚫",
}


def resolve_task(args) -> Optional[Tuple[Path, str, Path]]:
    """Resolve project root, task ID, and task directory from CLI args.

    Returns (project_root, task_id, task_dir) or None with an error printed.
    """
    project_root = find_project_root(getattr(args, 'project_root', None))
    task_id = args.task_id or get_current_task(project_root)

    if not task_id:
        print("❌ No task ID provided and no current task found", file=sys.stderr)
        return None

    task_dir = get_tasks_dir(project_root) / task_id
    if not task_dir.exists():
        print(f"❌ Task directory not found: {task_dir}", file=sys.stderr)
        return None

    return project_root, task_id, task_dir


def slugify(text: str) -> str:
    """Convert text to a URL-friendly slug."""
    text = text.lower().strip()
    text = re.sub(r'[^\w\s-]', '', text)
    text = re.sub(r'[-\s]+', '-', text)
    return text[:50]  # Limit length


def _write_result_json(
    task_dir: Path,
    summary: str,
    files_changed: Optional[List[str]],
    files_created: Optional[List[str]],
    validation_json: Optional[str],
    completed_at: str
) -> None:
    """Write result.json for a completed task."""
    result_data = {
        "status": "success",
        "files_changed": files_changed or [],
        "files_created": files_created or [],
        "summary": summary,
        "completed_at": completed_at
    }

    if validation_json:
        try:
            result_data["validation"] = json.loads(validation_json)
        except json.JSONDecodeError:
            pass

    (task_dir / "state" / "result.json").write_text(
        json.dumps(result_data, indent=2)
    )


def _record_episode(
    project_root: Path,
    task_description: str,
    task_id: str,
    lessons_json: str,
    timestamp: str,
) -> Optional[Path]:
    """Record task completion to episodic memory. Returns episode file path."""
    memory_dir = get_harness_root(project_root) / "memory" / "episodes"
    memory_dir.mkdir(parents=True, exist_ok=True)

    episode: Dict[str, Any] = {
        "task": task_description,
        "task_id": task_id,
        "outcome": "success",
        "timestamp": timestamp
    }

    try:
        episode["lessons"] = json.loads(lessons_json)
    except json.JSONDecodeError:
        episode["lessons"] = [lessons_json]

    # Use date from timestamp (first 10 chars of ISO format: YYYY-MM-DD)
    episode_file = memory_dir / f"{timestamp[:10]}.jsonl"
    with open(episode_file, "a") as f:
        f.write(json.dumps(episode) + "\n")

    return episode_file


def get_task_id(name: str) -> str:
    """Generate a unique task ID from name + timestamp."""
    slug = slugify(name)
    timestamp = datetime.now().strftime("%Y%m%d-%H%M")
    return f"{slug}-{timestamp}"


def get_tasks_dir(project_root: Path) -> Path:
    """Get the tasks directory path."""
    return get_harness_root(project_root) / "tasks"


def find_project_root(override: str = None) -> Path:
    """Find project root by looking for AGENTS.md or .git.

    Args:
        override: If provided, use this path instead of auto-detection.
                  Useful for worktree mode where state lives in original repo.
    """
    if override:
        return Path(override)
    current = Path.cwd()
    while current != current.parent:
        if (current / "AGENTS.md").exists() or (current / ".git").exists():
            return current
        current = current.parent
    return Path.cwd()


def get_current_task(project_root: Path) -> Optional[str]:
    """Get the current active task ID from symlink."""
    current_link = get_tasks_dir(project_root) / "current"
    if current_link.is_symlink():
        return current_link.resolve().name
    return None


def init_task(args) -> int:
    """Initialize a new task."""
    project_root = find_project_root(getattr(args, 'project_root', None))
    task_id = get_task_id(args.name)
    task_dir = get_tasks_dir(project_root) / task_id

    # Create directories
    (task_dir / "state").mkdir(parents=True, exist_ok=True)
    (task_dir / "checkpoints").mkdir(parents=True, exist_ok=True)

    # Create task.json
    task_data = {
        "task_id": task_id,
        "task": args.description or args.name,
        "started_at": datetime.now().isoformat(),
        "phase": 1,
        "total_phases": args.phases or 1,
        "status": "in_progress"
    }

    if args.plan_path:
        task_data["plan_path"] = args.plan_path

    (task_dir / "state" / "task.json").write_text(
        json.dumps(task_data, indent=2)
    )

    # Create initial context.json
    context_data = {
        "completed": [],
        "current": f"Phase 1 of {args.phases or 1}",
        "remaining": [f"Phase {i}" for i in range(2, (args.phases or 1) + 1)],
        "key_decisions": [],
        "files_modified": [],
        "files_created": []
    }
    (task_dir / "state" / "context.json").write_text(
        json.dumps(context_data, indent=2)
    )

    # Create symlink to current task
    current_link = get_tasks_dir(project_root) / "current"
    if current_link.is_symlink():
        current_link.unlink()
    current_link.symlink_to(task_id)

    print(f"✅ Task initialized: {task_id}")
    print(f"   Directory: {task_dir}")
    print(f"   Phases: {args.phases or 1}")
    return 0


def checkpoint_task(args) -> int:
    """Save a checkpoint for the current phase."""
    resolved = resolve_task(args)
    if not resolved:
        return 1
    project_root, task_id, task_dir = resolved

    # Load existing context
    context_path = task_dir / "state" / "context.json"
    if context_path.exists():
        context = json.loads(context_path.read_text())
    else:
        context = {
            "completed": [],
            "current": "",
            "remaining": [],
            "key_decisions": [],
            "files_modified": [],
            "files_created": []
        }

    # Update context
    phase_summary = f"Phase {args.phase}: {args.summary}"
    context["completed"].append(phase_summary)
    context["current"] = f"Phase {args.phase + 1}" if args.phase else ""

    if args.decisions:
        try:
            decisions = json.loads(args.decisions)
            context["key_decisions"].extend(decisions)
        except json.JSONDecodeError:
            context["key_decisions"].append(args.decisions)

    if args.files_changed:
        context["files_modified"].extend(args.files_changed)
        context["files_modified"] = list(set(context["files_modified"]))

    if args.files_created:
        context["files_created"].extend(args.files_created)
        context["files_created"] = list(set(context["files_created"]))

    # Write updated context
    context_path.write_text(json.dumps(context, indent=2))

    # Save checkpoint
    checkpoint_path = task_dir / "checkpoints" / f"phase-{args.phase}.json"
    checkpoint_data = {
        "phase": args.phase,
        "summary": args.summary,
        "timestamp": datetime.now().isoformat(),
        "context_snapshot": context
    }
    checkpoint_path.write_text(json.dumps(checkpoint_data, indent=2))

    # Update task.json phase
    task_path = task_dir / "state" / "task.json"
    if task_path.exists():
        task_data = json.loads(task_path.read_text())
        task_data["phase"] = args.phase + 1
        task_path.write_text(json.dumps(task_data, indent=2))

    print(f"✅ Checkpoint saved: Phase {args.phase}")
    print(f"   Summary: {args.summary}")
    return 0


def validate_completion_gate(project_root: Path, skip_gate: bool = False) -> List[str]:
    """Gate check: verify that functional verification was completed before allowing task completion.

    ALL tasks must pass this gate. The verifier subagent must have run and produced
    valid evidence in verification-report.json. There are no exceptions or shortcuts.
    Returns a list of error messages (empty = all checks passed).
    """
    if skip_gate:
        return []

    errors = []
    harness_root = get_harness_root(project_root)
    verify_report = harness_root / "trace" / "verification-report.json"

    # Read and parse in one step (avoids TOCTOU race between exists() and read_text())
    try:
        report = json.loads(verify_report.read_text())
    except FileNotFoundError:
        errors.append(f"Verification skipped: {verify_report.relative_to(project_root)} not found (spawn verifier subagent in Step 5)")
        return errors
    except json.JSONDecodeError as e:
        errors.append(f"verification-report.json is invalid JSON: {e}")
        return errors

    # Allow skip status (for when verification is intentionally skipped with a reason)
    if report.get("overall_status") == "skip":
        skip_reason = report.get("skip_reason", "No reason provided")
        print(f"ℹ️  Verification skipped: {skip_reason}")
        return []

    # Server must have been started
    server = report.get("server", {})
    if not server.get("started"):
        errors.append("Verification report shows server.started=false or missing — the application was not started")

    # Must have at least one HTTP request/response pair as evidence
    all_scenarios = chain(
        report.get("task_specific_scenarios", []),
        report.get("predefined_scenarios", []),
        report.get("additional_checks", []),
    )
    has_http_evidence = any(
        step.get("request") and step.get("response")
        for scenario in all_scenarios
        for step in scenario.get("steps", [])
    )
    if not has_http_evidence:
        errors.append("Verification report lacks HTTP evidence — no request/response pairs found in any scenario")

    return errors


def complete_task(args) -> int:
    """Mark a task as complete."""
    resolved = resolve_task(args)
    if not resolved:
        return 1
    project_root, task_id, task_dir = resolved

    # Gate check: ensure Phase 4 verification pipeline was completed
    skip_gate = getattr(args, 'skip_gate', False)
    gate_errors = validate_completion_gate(project_root, skip_gate)
    if gate_errors:
        print("❌ Cannot complete task — verification steps were skipped:", file=sys.stderr)
        print("", file=sys.stderr)
        for err in gate_errors:
            print(f"   ⚠ {err}", file=sys.stderr)
        print("", file=sys.stderr)
        print("   Fix: Run the verifier subagent (Step 5) to generate verification-report.json.", file=sys.stderr)
        print("   Skip: Use --skip-gate to bypass (not recommended).", file=sys.stderr)
        return 1

    # Capture timestamp once for consistency
    now = datetime.now().isoformat()

    # Update task.json
    task_path = task_dir / "state" / "task.json"
    task_data = json.loads(task_path.read_text()) if task_path.exists() else {}
    task_data["status"] = "completed"
    task_data["completed_at"] = now
    task_data["summary"] = args.summary

    if args.files_changed:
        task_data["files_changed"] = args.files_changed
    if args.files_created:
        task_data["files_created"] = args.files_created

    task_path.write_text(json.dumps(task_data, indent=2))

    # Write result.json
    _write_result_json(
        task_dir,
        summary=args.summary,
        files_changed=args.files_changed,
        files_created=args.files_created,
        validation_json=args.validation,
        completed_at=now
    )

    # Record to episodic memory if lessons provided
    if args.lessons:
        episode_file = _record_episode(
            project_root,
            task_description=task_data.get("task", "Unknown task"),
            task_id=task_id,
            lessons_json=args.lessons,
            timestamp=now
        )
        print(f"   Lessons recorded to: {episode_file}")

    # Remove current symlink
    current_link = get_tasks_dir(project_root) / "current"
    if current_link.is_symlink():
        current_link.unlink()

    print(f"✅ Task completed: {task_id}")
    print(f"   Summary: {args.summary}")
    return 0


def show_task(args) -> int:
    """Show details of a specific task."""
    resolved = resolve_task(args)
    if not resolved:
        return 1
    _project_root, task_id, task_dir = resolved

    # Load task data
    task_path = task_dir / "state" / "task.json"
    task_data = json.loads(task_path.read_text()) if task_path.exists() else {}

    context_path = task_dir / "state" / "context.json"
    context_data = json.loads(context_path.read_text()) if context_path.exists() else {}

    result_path = task_dir / "state" / "result.json"
    result_data = json.loads(result_path.read_text()) if result_path.exists() else None

    if args.json:
        output = {
            "task": task_data,
            "context": context_data,
            "result": result_data
        }
        print(json.dumps(output, indent=2))
    else:
        print(f"\n{'=' * 50}")
        print(f"Task: {task_id}")
        print(f"{'=' * 50}\n")

        status = task_data.get("status", "unknown")
        print(f"Status: {STATUS_ICONS.get(status, '?')} {status.upper()}")
        print(f"Description: {task_data.get('task', 'N/A')}")
        print(f"Phase: {task_data.get('phase', '?')}/{task_data.get('total_phases', '?')}")
        print(f"Started: {task_data.get('started_at', 'N/A')}")

        if context_data.get("completed"):
            print(f"\nCompleted phases:")
            for phase in context_data["completed"]:
                print(f"  ✓ {phase}")

        if context_data.get("current"):
            print(f"\nCurrent: {context_data['current']}")

        if context_data.get("key_decisions"):
            print(f"\nKey decisions:")
            for decision in context_data["key_decisions"]:
                print(f"  • {decision}")

        if context_data.get("files_modified"):
            print(f"\nFiles modified: {', '.join(context_data['files_modified'])}")

        if result_data:
            print(f"\nResult: {result_data.get('status', 'N/A')}")
            print(f"Summary: {result_data.get('summary', 'N/A')}")

        print()

    return 0


def list_tasks(args) -> int:
    """List all tasks."""
    project_root = find_project_root(getattr(args, 'project_root', None))
    tasks_dir = get_tasks_dir(project_root)

    if not tasks_dir.exists():
        print("No tasks directory found.")
        return 0

    current_task = get_current_task(project_root)
    tasks = []

    for task_path in tasks_dir.iterdir():
        if task_path.is_dir() and task_path.name != "current":
            task_json = task_path / "state" / "task.json"
            if task_json.exists():
                data = json.loads(task_json.read_text())
                tasks.append({
                    "id": task_path.name,
                    "task": data.get("task", "N/A"),
                    "status": data.get("status", "unknown"),
                    "phase": f"{data.get('phase', '?')}/{data.get('total_phases', '?')}",
                    "started": data.get("started_at", "N/A")[:10],
                    "is_current": task_path.name == current_task
                })

    if args.json:
        print(json.dumps(tasks, indent=2))
    else:
        if not tasks:
            print("No tasks found.")
            return 0

        print(f"\n{'=' * 70}")
        print("Tasks")
        print(f"{'=' * 70}\n")

        for task in sorted(tasks, key=lambda x: x["started"], reverse=True):
            icon = STATUS_ICONS.get(task["status"], "?")
            current_marker = " ← CURRENT" if task["is_current"] else ""
            print(f"{icon} [{task['status']:12}] {task['id']}{current_marker}")
            print(f"   {task['task'][:60]}...")
            print(f"   Phase: {task['phase']} | Started: {task['started']}")
            print()

    return 0


def main():
    parser = argparse.ArgumentParser(description="Harness task state management")
    subparsers = parser.add_subparsers(dest="command", help="Available commands")

    # init command
    init_parser = subparsers.add_parser("init", help="Initialize a new task")
    init_parser.add_argument("name", help="Task name")
    init_parser.add_argument("--phases", type=int, default=1, help="Number of phases")
    init_parser.add_argument("--description", "-d", help="Task description")
    init_parser.add_argument("--plan-path", help="Path to execution plan file")
    init_parser.add_argument("--project-root", help="Override project root (for worktree mode)")

    # checkpoint command
    checkpoint_parser = subparsers.add_parser("checkpoint", help="Save a phase checkpoint")
    checkpoint_parser.add_argument("--task-id", help="Task ID (defaults to current)")
    checkpoint_parser.add_argument("--phase", type=int, required=True, help="Phase number")
    checkpoint_parser.add_argument("--summary", "-s", required=True, help="Phase summary")
    checkpoint_parser.add_argument("--decisions", help="Key decisions (JSON array or string)")
    checkpoint_parser.add_argument("--files-changed", nargs="+", help="Files modified")
    checkpoint_parser.add_argument("--files-created", nargs="+", help="Files created")
    checkpoint_parser.add_argument("--project-root", help="Override project root (for worktree mode)")

    # complete command
    complete_parser = subparsers.add_parser("complete", help="Mark task as complete")
    complete_parser.add_argument("--task-id", help="Task ID (defaults to current)")
    complete_parser.add_argument("--summary", "-s", required=True, help="Completion summary")
    complete_parser.add_argument("--files-changed", nargs="+", help="Files modified")
    complete_parser.add_argument("--files-created", nargs="+", help="Files created")
    complete_parser.add_argument("--validation", help="Validation results (JSON)")
    complete_parser.add_argument("--lessons", help="Lessons learned (JSON array or string)")
    complete_parser.add_argument("--skip-gate", action="store_true", help="Skip verification gate check (not recommended)")
    complete_parser.add_argument("--project-root", help="Override project root (for worktree mode)")

    # show command
    show_parser = subparsers.add_parser("show", help="Show task details")
    show_parser.add_argument("--task-id", help="Task ID (defaults to current)")
    show_parser.add_argument("--json", action="store_true", help="Output as JSON")
    show_parser.add_argument("--project-root", help="Override project root (for worktree mode)")

    # list command
    list_parser = subparsers.add_parser("list", help="List all tasks")
    list_parser.add_argument("--json", action="store_true", help="Output as JSON")
    list_parser.add_argument("--project-root", help="Override project root (for worktree mode)")

    args = parser.parse_args()

    if args.command == "init":
        return init_task(args)
    elif args.command == "checkpoint":
        return checkpoint_task(args)
    elif args.command == "complete":
        return complete_task(args)
    elif args.command == "show":
        return show_task(args)
    elif args.command == "list":
        return list_tasks(args)
    else:
        parser.print_help()
        return 1


if __name__ == "__main__":
    sys.exit(main())
