#!/usr/bin/env python3
"""
Trajectory Compiler — Detect repeated agent patterns and compile to scripts.

Analyzes episodic and procedural memory to find tasks that are consistently
executed with the same steps, then generates deterministic scripts that can
replace LLM reasoning for those tasks ($0 per invocation).

Usage:
    python3 scripts/compile_trajectory.py detect           # Find compilation candidates
    python3 scripts/compile_trajectory.py compile <name>   # Compile a specific procedure
    python3 scripts/compile_trajectory.py registry          # Show compiled policies
    python3 scripts/compile_trajectory.py stats             # Compilation statistics

See references/trajectory-compilation.md for the full conceptual framework.
"""

import argparse
import json
import os
import re
import sys
from collections import Counter
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple


MEMORY_ROOT = "harness/memory"
EPISODES_DIR = f"{MEMORY_ROOT}/episodes"
PROCEDURES_DIR = f"{MEMORY_ROOT}/procedures"
COMPILED_DIR = "harness/compiled"
REGISTRY_FILE = f"{COMPILED_DIR}/registry.json"
SCRIPTS_DIR = "scripts/compiled"


# ─── Detection ───────────────────────────────────────────────────────────────

def load_all_episodes(project_root: Path) -> List[Dict]:
    """Load all episodes from JSONL files."""
    episodes_dir = project_root / EPISODES_DIR
    if not episodes_dir.exists():
        return []

    episodes = []
    for jsonl_file in sorted(episodes_dir.glob("*.jsonl")):
        for line in jsonl_file.read_text().strip().split('\n'):
            line = line.strip()
            if line:
                try:
                    episodes.append(json.loads(line))
                except json.JSONDecodeError:
                    continue
    return episodes


def load_all_procedures(project_root: Path) -> List[Dict]:
    """Load all procedures from JSON files."""
    procedures_dir = project_root / PROCEDURES_DIR
    if not procedures_dir.exists():
        return []

    procedures = []
    for json_file in sorted(procedures_dir.glob("*.json")):
        try:
            data = json.loads(json_file.read_text())
            data["_source_file"] = str(json_file)
            procedures.append(data)
        except (json.JSONDecodeError, OSError):
            continue
    return procedures


def parse_success_rate(sr: Any) -> Optional[float]:
    """Parse success rate from various formats."""
    if isinstance(sr, (int, float)):
        return float(sr)
    if isinstance(sr, str) and "/" in sr:
        try:
            num, den = sr.split("/")
            return int(num) / int(den)
        except (ValueError, ZeroDivisionError):
            return None
    return None


def detect_candidates(project_root: Path, min_occurrences: int = 3, min_success: float = 0.8) -> List[Dict]:
    """Detect procedures that are candidates for trajectory compilation.

    A good candidate has:
    1. Been executed >= min_occurrences times
    2. Success rate >= min_success
    3. Consistent step sequence across runs
    """
    episodes = load_all_episodes(project_root)
    procedures = load_all_procedures(project_root)
    candidates = []

    # Signal 1: Procedural memory with high success rate
    for proc in procedures:
        sr = parse_success_rate(proc.get("success_rate"))
        steps = proc.get("steps", [])

        if sr is not None and sr >= min_success and len(steps) >= 2:
            candidates.append({
                "name": proc.get("procedure", "unknown"),
                "source": "procedural_memory",
                "source_file": proc.get("_source_file", ""),
                "success_rate": sr,
                "step_count": len(steps),
                "steps": steps,
                "strategies": proc.get("strategies", []),
                "last_used": proc.get("last_used", "unknown"),
                "compilable": True,
                "reason": f"Success rate {sr:.0%} with {len(steps)} consistent steps"
            })

    # Signal 2: Repeated patterns in episodic memory
    procedure_counts: Counter = Counter()
    procedure_outcomes: Dict[str, List[str]] = {}

    for ep in episodes:
        # Look for procedure field or task patterns
        proc_name = ep.get("procedure", "")
        if not proc_name:
            # Try to infer from task name
            task = ep.get("task", "")
            if task:
                # Normalize: "Implement JWT auth" and "Implement OAuth auth" → "implement-auth"
                proc_name = re.sub(r'\s+', '-', task.lower().strip())

        if proc_name:
            procedure_counts[proc_name] += 1
            outcome = ep.get("outcome", "unknown")
            procedure_outcomes.setdefault(proc_name, []).append(outcome)

    for proc_name, count in procedure_counts.most_common():
        if count < min_occurrences:
            continue

        outcomes = procedure_outcomes[proc_name]
        success_count = sum(1 for o in outcomes if o == "success")
        sr = success_count / len(outcomes) if outcomes else 0

        # Check if already in candidates from procedural memory
        already_found = any(c["name"].lower().replace(" ", "-") == proc_name for c in candidates)
        if already_found:
            continue

        if sr >= min_success:
            candidates.append({
                "name": proc_name,
                "source": "episodic_repetition",
                "occurrence_count": count,
                "success_rate": sr,
                "compilable": True,
                "reason": f"Executed {count} times with {sr:.0%} success rate"
            })
        else:
            candidates.append({
                "name": proc_name,
                "source": "episodic_repetition",
                "occurrence_count": count,
                "success_rate": sr,
                "compilable": False,
                "reason": f"Low success rate ({sr:.0%}) — needs stabilization before compilation"
            })

    # Sort: compilable first, then by success rate
    candidates.sort(key=lambda c: (c["compilable"], c.get("success_rate", 0)), reverse=True)
    return candidates


# ─── Compilation ─────────────────────────────────────────────────────────────

def extract_parameters(steps: List[Dict]) -> List[Dict]:
    """Analyze steps to identify parameterizable elements."""
    params = []
    param_patterns = [
        (r'\{(\w+)\}', "template variable"),
        (r'<(\w+)>', "placeholder"),
        (r'\$\{(\w+)\}', "shell variable"),
    ]

    seen = set()
    for step in steps:
        action = step.get("action", "")
        notes = step.get("notes", "")
        text = f"{action} {notes}"

        for pattern, ptype in param_patterns:
            for match in re.finditer(pattern, text):
                name = match.group(1)
                if name not in seen:
                    seen.add(name)
                    params.append({
                        "name": name,
                        "type": ptype,
                        "found_in": action[:60]
                    })

    return params


def generate_script(procedure: Dict, project_root: Path) -> str:
    """Generate a bash script from a compiled procedure."""
    name = procedure.get("procedure", procedure.get("name", "unknown"))
    steps = procedure.get("steps", [])
    slug = re.sub(r'[^a-z0-9]+', '-', name.lower()).strip('-')
    sr = procedure.get("success_rate", "unknown")

    lines = [
        "#!/bin/bash",
        f"# scripts/compiled/{slug}.sh",
        f"# Compiled from procedure: {name}",
        f"# Source: {procedure.get('_source_file', 'harness/memory/procedures/')}",
        f"# Success rate at compilation: {sr}",
        f"# Compiled: {datetime.utcnow().isoformat()}Z",
        "#",
        "# This script replaces LLM reasoning for this task type.",
        "# If it fails, fall back to agent execution.",
        "",
        'set -euo pipefail',
        "",
    ]

    # Detect parameters from steps
    params = extract_parameters(steps)
    if params:
        lines.append("# Parameters")
        for i, p in enumerate(params):
            lines.append(f'{p["name"].upper()}="${{{i+1}:?Usage: {slug}.sh {" ".join(f"<{pp["name"]}>" for pp in params)}}}"')
        lines.append("")

    # Generate step comments and placeholders
    lines.append("# ─── Steps ───")
    lines.append("")

    for step in steps:
        step_num = step.get("step", "?")
        action = step.get("action", "unknown action")
        notes = step.get("notes", "")

        lines.append(f"# Step {step_num}: {action}")
        if notes:
            lines.append(f"# Note: {notes}")
        lines.append(f'echo "→ Step {step_num}: {action}"')
        lines.append("# TODO: Implement this step based on the action description above")
        lines.append("")

    # Validation
    lines.extend([
        "# ─── Validation ───",
        "",
        f'VALIDATION_CMD="{procedure.get("validation", "echo No validation configured")}"',
        "",
        "echo \"→ Running validation...\"",
        "if ! eval $VALIDATION_CMD; then",
        '    echo "⚠️ Compiled policy hit an edge case. Falling back to agent execution."',
        f'    echo "Please run the task manually: {name}"',
        "    exit 1",
        "fi",
        "",
        'echo "✅ Done"',
    ])

    return "\n".join(lines) + "\n"


def generate_makefile_target(name: str, slug: str, params: List[Dict]) -> str:
    """Generate a Makefile target for the compiled script."""
    param_str = " ".join(f"$({p['name'].upper()})" for p in params) if params else ""
    return f""".PHONY: {slug}
{slug}:
\t@scripts/compiled/{slug}.sh {param_str}
"""


def compile_procedure(project_root: Path, procedure_name: str) -> Dict:
    """Compile a specific procedure into a script."""
    procedures = load_all_procedures(project_root)

    # Find matching procedure
    target = None
    for proc in procedures:
        if proc.get("procedure", "").lower() == procedure_name.lower():
            target = proc
            break
        slug = re.sub(r'[^a-z0-9]+', '-', proc.get("procedure", "").lower()).strip('-')
        if slug == procedure_name.lower():
            target = proc
            break

    if not target:
        return {"error": f"Procedure '{procedure_name}' not found in {PROCEDURES_DIR}/"}

    name = target.get("procedure", procedure_name)
    slug = re.sub(r'[^a-z0-9]+', '-', name.lower()).strip('-')
    steps = target.get("steps", [])
    params = extract_parameters(steps)

    # Generate script
    script_content = generate_script(target, project_root)

    # Create directories
    scripts_dir = project_root / SCRIPTS_DIR
    compiled_dir = project_root / COMPILED_DIR
    scripts_dir.mkdir(parents=True, exist_ok=True)
    compiled_dir.mkdir(parents=True, exist_ok=True)

    # Write script
    script_path = scripts_dir / f"{slug}.sh"
    script_path.write_text(script_content)
    script_path.chmod(0o755)

    # Update registry
    registry = load_registry(project_root)
    registry_entry = {
        "name": slug,
        "procedure": name,
        "command": f"scripts/compiled/{slug}.sh" + (" " + " ".join(f"<{p['name']}>" for p in params) if params else ""),
        "makefile_target": f"make {slug}" + (" " + " ".join(f"{p['name'].upper()}=<value>" for p in params) if params else ""),
        "compiled_from": target.get("_source_file", f"{PROCEDURES_DIR}/{slug}.json"),
        "success_rate_at_compilation": target.get("success_rate", "unknown"),
        "compiled_at": datetime.utcnow().isoformat() + "Z",
        "step_count": len(steps),
        "parameters": [p["name"] for p in params],
        "executions_since_compilation": 0,
        "failures_since_compilation": 0
    }

    # Update or add entry
    existing_idx = next(
        (i for i, p in enumerate(registry.get("policies", []))
         if p["name"] == slug), None
    )
    if existing_idx is not None:
        registry["policies"][existing_idx] = registry_entry
    else:
        registry.setdefault("policies", []).append(registry_entry)

    save_registry(project_root, registry)

    # Generate Makefile target suggestion
    makefile_target = generate_makefile_target(name, slug, params)

    return {
        "success": True,
        "script_path": str(script_path.relative_to(project_root)),
        "registry_updated": True,
        "makefile_target": makefile_target,
        "parameters": params,
        "message": f"Compiled '{name}' → {script_path.relative_to(project_root)}\n"
                   f"Add to Makefile:\n{makefile_target}\n"
                   f"⚠️ Review the generated script and fill in TODO sections before use."
    }


# ─── Registry ───────────────────────────────────────────────────────────────

def load_registry(project_root: Path) -> Dict:
    """Load the compiled policies registry."""
    registry_path = project_root / REGISTRY_FILE
    if registry_path.exists():
        try:
            return json.loads(registry_path.read_text())
        except (json.JSONDecodeError, OSError):
            pass
    return {"policies": [], "total_estimated_savings": "$0.00"}


def save_registry(project_root: Path, registry: Dict):
    """Save the compiled policies registry."""
    registry_path = project_root / REGISTRY_FILE
    registry_path.parent.mkdir(parents=True, exist_ok=True)
    registry_path.write_text(json.dumps(registry, indent=2) + "\n")


def show_registry(project_root: Path) -> Dict:
    """Show the current state of compiled policies."""
    registry = load_registry(project_root)
    policies = registry.get("policies", [])

    # Calculate stats
    total_executions = sum(p.get("executions_since_compilation", 0) for p in policies)
    total_failures = sum(p.get("failures_since_compilation", 0) for p in policies)

    needs_recompile = []
    for p in policies:
        execs = p.get("executions_since_compilation", 0)
        fails = p.get("failures_since_compilation", 0)
        if execs > 0 and fails / execs > 0.1:
            needs_recompile.append(p["name"])

    return {
        "policies": policies,
        "total_policies": len(policies),
        "total_executions": total_executions,
        "total_failures": total_failures,
        "failure_rate": total_failures / total_executions if total_executions > 0 else 0,
        "needs_recompile": needs_recompile
    }


# ─── Stats ───────────────────────────────────────────────────────────────────

def compilation_stats(project_root: Path) -> Dict:
    """Comprehensive compilation statistics."""
    episodes = load_all_episodes(project_root)
    procedures = load_all_procedures(project_root)
    registry = load_registry(project_root)
    candidates = detect_candidates(project_root)

    compilable = [c for c in candidates if c.get("compilable")]
    compiled = registry.get("policies", [])
    compiled_names = {p["name"] for p in compiled}

    return {
        "memory": {
            "total_episodes": len(episodes),
            "total_procedures": len(procedures),
        },
        "candidates": {
            "total": len(candidates),
            "compilable": len(compilable),
            "not_ready": len(candidates) - len(compilable),
        },
        "compiled": {
            "total": len(compiled),
            "uncompiled_candidates": [c["name"] for c in compilable
                                       if re.sub(r'[^a-z0-9]+', '-', c["name"].lower()).strip('-')
                                       not in compiled_names],
        },
        "potential_savings": f"~${len(compilable) * 2.40:.2f}/run if all candidates compiled"
    }


# ─── CLI ─────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Trajectory Compiler — detect and compile repeated agent patterns",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s detect                       Find compilation candidates
  %(prog)s detect --min-occurrences 2   Lower threshold
  %(prog)s compile add-api-endpoint     Compile a specific procedure
  %(prog)s registry                     Show compiled policies
  %(prog)s stats                        Overall compilation statistics
        """
    )

    subparsers = parser.add_subparsers(dest="command")

    # detect
    detect_p = subparsers.add_parser("detect", help="Detect compilation candidates")
    detect_p.add_argument("--min-occurrences", type=int, default=3)
    detect_p.add_argument("--min-success", type=float, default=0.8)
    detect_p.add_argument("--json", action="store_true")

    # compile
    compile_p = subparsers.add_parser("compile", help="Compile a procedure to script")
    compile_p.add_argument("name", help="Procedure name to compile")
    compile_p.add_argument("--json", action="store_true")

    # registry
    reg_p = subparsers.add_parser("registry", help="Show compiled policies registry")
    reg_p.add_argument("--json", action="store_true")

    # stats
    stats_p = subparsers.add_parser("stats", help="Compilation statistics")
    stats_p.add_argument("--json", action="store_true")

    parser.add_argument("--project-root", default=".", help="Project root")

    args = parser.parse_args()
    if not args.command:
        parser.print_help()
        sys.exit(1)

    project_root = Path(args.project_root).resolve()

    if args.command == "detect":
        candidates = detect_candidates(project_root, args.min_occurrences, args.min_success)
        if args.json:
            print(json.dumps({"candidates": candidates}, indent=2))
        else:
            if not candidates:
                print("No compilation candidates found yet.")
                print("Candidates appear after procedures are executed 3+ times with high success.")
                return
            compilable = [c for c in candidates if c.get("compilable")]
            print(f"Found {len(candidates)} candidate(s) ({len(compilable)} compilable):\n")
            for c in candidates:
                icon = "✅" if c["compilable"] else "⏳"
                print(f"  {icon} {c['name']}")
                print(f"     Source: {c['source']}")
                print(f"     {c['reason']}")
                if c.get("steps"):
                    print(f"     Steps: {len(c['steps'])}")
                print()

    elif args.command == "compile":
        result = compile_procedure(project_root, args.name)
        if args.json:
            print(json.dumps(result, indent=2))
        else:
            if "error" in result:
                print(f"✗ {result['error']}")
                sys.exit(1)
            print(result["message"])

    elif args.command == "registry":
        info = show_registry(project_root)
        if args.json:
            print(json.dumps(info, indent=2))
        else:
            policies = info["policies"]
            if not policies:
                print("No compiled policies yet. Run 'detect' to find candidates.")
                return
            print(f"Compiled Policies ({len(policies)}):\n")
            for p in policies:
                execs = p.get("executions_since_compilation", 0)
                fails = p.get("failures_since_compilation", 0)
                rate = f"{fails}/{execs} failures" if execs > 0 else "no runs yet"
                print(f"  📦 {p['name']}")
                print(f"     Command: {p.get('command', '?')}")
                print(f"     Compiled: {p.get('compiled_at', '?')[:10]}")
                print(f"     Since compilation: {rate}")
                print()
            if info["needs_recompile"]:
                print(f"⚠ Needs recompilation: {', '.join(info['needs_recompile'])}")

    elif args.command == "stats":
        stats = compilation_stats(project_root)
        if args.json:
            print(json.dumps(stats, indent=2))
        else:
            m = stats["memory"]
            c = stats["candidates"]
            comp = stats["compiled"]
            print("Trajectory Compilation Statistics\n")
            print(f"  Memory: {m['total_episodes']} episodes, {m['total_procedures']} procedures")
            print(f"  Candidates: {c['total']} total ({c['compilable']} compilable, {c['not_ready']} not ready)")
            print(f"  Compiled: {comp['total']} policies")
            if comp["uncompiled_candidates"]:
                print(f"  Ready to compile: {', '.join(comp['uncompiled_candidates'])}")
            print(f"\n  {stats['potential_savings']}")


if __name__ == "__main__":
    main()
