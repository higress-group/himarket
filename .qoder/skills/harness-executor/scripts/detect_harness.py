#!/usr/bin/env python3
"""
Harness detection and validation script.

Checks if the current project has harness infrastructure and reports its status.
"""

import json
import os
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple


def check_file_exists(base_path: Path, relative_path: str) -> Tuple[bool, Optional[int]]:
    """Check if a file exists and return (exists, line_count)."""
    full_path = base_path / relative_path
    if full_path.exists() and full_path.is_file():
        try:
            line_count = len(full_path.read_text().splitlines())
            return True, line_count
        except Exception:
            return True, None
    return False, None


def check_dir_exists(base_path: Path, relative_path: str) -> bool:
    """Check if a directory exists."""
    return (base_path / relative_path).is_dir()


def detect_harness(project_root: Path) -> Dict:
    """
    Detect harness infrastructure in the project.

    Returns a dict with:
    - has_harness: bool
    - score: float (0-100)
    - components: dict of component -> status
    - missing: list of missing components
    - recommendations: list of suggestions
    """
    result = {
        "has_harness": False,
        "score": 0.0,
        "components": {},
        "missing": [],
        "recommendations": []
    }

    # Required: AGENTS.md
    agents_exists, agents_lines = check_file_exists(project_root, "AGENTS.md")
    result["components"]["AGENTS.md"] = {
        "exists": agents_exists,
        "lines": agents_lines,
        "required": True
    }

    if not agents_exists:
        result["missing"].append("AGENTS.md (required)")
        result["recommendations"].append(
            "No harness infrastructure found. "
            "Bootstrap harness by invoking harness-creator skill: Skill(skill=\"harness-creator\"). "
            "This will create AGENTS.md, docs/, scripts/, and harness/ automatically."
        )
        result["bootstrap_needed"] = True
        return result  # No harness without AGENTS.md

    result["has_harness"] = True
    points = 20  # Base points for having AGENTS.md

    # Check AGENTS.md line count
    if agents_lines:
        if 80 <= agents_lines <= 120:
            points += 5  # Ideal range
        elif agents_lines > 200:
            result["recommendations"].append(
                f"AGENTS.md is {agents_lines} lines. Consider trimming to ~100 lines."
            )

    # Documentation hierarchy
    docs = [
        ("docs/ARCHITECTURE.md", 15),
        ("docs/DEVELOPMENT.md", 10),
        ("docs/QUALITY.md", 10),
        ("docs/TESTING.md", 5),
        ("docs/SECURITY.md", 5),
    ]

    for doc_path, doc_points in docs:
        exists, lines = check_file_exists(project_root, doc_path)
        result["components"][doc_path] = {"exists": exists, "lines": lines}
        if exists:
            points += doc_points
        else:
            result["missing"].append(doc_path)

    # Design docs directory
    design_exists = check_dir_exists(project_root, "docs/design")
    result["components"]["docs/design/"] = {"exists": design_exists}
    if design_exists:
        design_files = list((project_root / "docs/design").glob("*.md"))
        result["components"]["docs/design/"]["file_count"] = len(design_files)
        if len(design_files) >= 3:
            points += 5
    else:
        result["missing"].append("docs/design/")

    # References directory
    refs_exists = check_dir_exists(project_root, "docs/references")
    result["components"]["docs/references/"] = {"exists": refs_exists}
    if refs_exists:
        ref_files = list((project_root / "docs/references").glob("*.md"))
        result["components"]["docs/references/"]["file_count"] = len(ref_files)
        if len(ref_files) >= 2:
            points += 5
    else:
        result["missing"].append("docs/references/")

    # Linters (scripts/)
    scripts_exists = check_dir_exists(project_root, "scripts")
    if scripts_exists:
        # Support multiple languages: Go, TypeScript, Python
        lint_scripts = (
            list((project_root / "scripts").glob("lint*.go")) +
            list((project_root / "scripts").glob("lint*.ts")) +
            list((project_root / "scripts").glob("lint*.py")) +
            list((project_root / "scripts").glob("lint_*.py"))  # Python convention
        )
        result["components"]["scripts/lint-*"] = {
            "exists": len(lint_scripts) > 0,
            "file_count": len(lint_scripts)
        }
        if len(lint_scripts) >= 2:
            points += 10
    else:
        result["components"]["scripts/lint-*"] = {"exists": False}
        result["missing"].append("scripts/lint-*.go, lint-*.ts, or lint_*.py")

    # Harness directory
    harness_exists = check_dir_exists(project_root, "harness")
    result["components"]["harness/"] = {"exists": harness_exists}

    if harness_exists:
        # Check harness subdirectories
        harness_subdirs = ["trace", "eval", "selftest", "quality", "cleanup"]
        for subdir in harness_subdirs:
            subdir_exists = check_dir_exists(project_root, f"harness/{subdir}")
            result["components"][f"harness/{subdir}/"] = {"exists": subdir_exists}
            if subdir_exists:
                points += 3

        # Check eval datasets
        datasets_exists = check_dir_exists(project_root, "harness/eval/datasets")
        if datasets_exists:
            categories = ["file_ops", "code_gen", "debugging", "refactoring"]
            for cat in categories:
                cat_path = project_root / "harness/eval/datasets" / cat
                if cat_path.is_dir():
                    task_count = len(list(cat_path.glob("*.json")))
                    result["components"][f"harness/eval/datasets/{cat}/"] = {
                        "exists": True,
                        "task_count": task_count
                    }
                    if task_count >= 5:
                        points += 2
                    elif task_count > 0:
                        points += 1
    else:
        result["missing"].append("harness/")
        result["recommendations"].append(
            "Create harness/ directory with trace/, eval/, selftest/, quality/, cleanup/ subdirectories."
        )

    # Makefile with lint-arch target OR package.json with lint:arch script
    makefile_exists, _ = check_file_exists(project_root, "Makefile")
    pkg_json_exists, _ = check_file_exists(project_root, "package.json")

    has_lint_arch = False
    if makefile_exists:
        makefile_content = (project_root / "Makefile").read_text()
        has_lint_arch = "lint-arch" in makefile_content
        result["components"]["Makefile:lint-arch"] = {"exists": has_lint_arch}

    if pkg_json_exists and not has_lint_arch:
        pkg_content = (project_root / "package.json").read_text()
        has_lint_arch = '"lint:arch"' in pkg_content or '"lint-arch"' in pkg_content
        result["components"]["package.json:lint:arch"] = {"exists": has_lint_arch}

    if has_lint_arch:
        points += 5
    elif not makefile_exists and not pkg_json_exists:
        result["components"]["build:lint-arch"] = {"exists": False}

    result["score"] = min(100.0, points)

    # Generate recommendations based on score
    if result["score"] < 50:
        result["recommendations"].append(
            "Harness infrastructure is incomplete. Run harness-creator skill to fill gaps."
        )
    elif result["score"] < 80:
        result["recommendations"].append(
            "Harness is functional but could be improved. Check missing components."
        )

    return result


def main():
    """Main entry point."""
    import argparse

    parser = argparse.ArgumentParser(description="Detect harness infrastructure")
    parser.add_argument("path", nargs="?", default=".", help="Project root path")
    parser.add_argument("--json", action="store_true", help="Output as JSON")
    parser.add_argument("--quiet", action="store_true", help="Only output score")

    args = parser.parse_args()

    project_root = Path(args.path).resolve()

    if not project_root.is_dir():
        print(f"Error: {project_root} is not a directory", file=sys.stderr)
        sys.exit(1)

    result = detect_harness(project_root)

    if args.json:
        print(json.dumps(result, indent=2))
    elif args.quiet:
        print(f"{result['score']:.1f}")
    else:
        # Human-readable output
        print(f"\n{'=' * 50}")
        print(f"Harness Detection Report: {project_root.name}")
        print(f"{'=' * 50}\n")

        status = "✅ DETECTED" if result["has_harness"] else "❌ NOT FOUND"
        print(f"Harness Status: {status}")
        print(f"Score: {result['score']:.1f}/100\n")

        print("Components:")
        for name, info in result["components"].items():
            if info.get("exists"):
                extra = ""
                if "lines" in info and info["lines"]:
                    extra = f" ({info['lines']} lines)"
                elif "file_count" in info:
                    extra = f" ({info['file_count']} files)"
                elif "task_count" in info:
                    extra = f" ({info['task_count']} tasks)"
                print(f"  ✓ {name}{extra}")
            else:
                print(f"  ✗ {name}")

        if result["missing"]:
            print(f"\nMissing ({len(result['missing'])}):")
            for item in result["missing"]:
                print(f"  - {item}")

        if result["recommendations"]:
            print("\nRecommendations:")
            for rec in result["recommendations"]:
                print(f"  → {rec}")

        print()

    # Exit with non-zero if no harness
    sys.exit(0 if result["has_harness"] else 1)


if __name__ == "__main__":
    main()
