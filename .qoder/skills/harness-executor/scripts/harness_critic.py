#!/usr/bin/env python3
"""
Harness Critic - Automated failure pattern analysis and improvement suggestions.

Implements the "Critic" component from the AutoHarness paper's refinement pipeline.
Analyzes batches of validation failures to identify patterns and suggest harness improvements.

The Critic → Refiner pipeline:
1. Agent executes tasks
2. Failures are logged to harness/trace/failures/
3. Critic (this script) analyzes failure patterns
4. Refiner uses suggestions to update harness rules

Usage:
    python3 scripts/harness_critic.py --failures harness/trace/failures/
    python3 scripts/harness_critic.py --since "24h"  # Last 24 hours
    python3 scripts/harness_critic.py --json  # Machine-readable output
"""

import argparse
import json
import os
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, asdict, field
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, List, Optional, Tuple


@dataclass
class FailureEvent:
    """A single failure event from the trace logs."""
    timestamp: str
    failure_type: str  # lint, build, test, verify
    error_message: str
    file_path: Optional[str] = None
    line_number: Optional[int] = None
    rule_id: Optional[str] = None
    attempted_fix: Optional[str] = None
    outcome: Optional[str] = None  # fixed, still_failed, escalated
    context: Dict = field(default_factory=dict)


@dataclass
class FailurePattern:
    """A detected pattern across multiple failures."""
    pattern_id: str
    pattern_type: str  # layer_violation, naming_issue, missing_rule, opaque_error
    description: str
    occurrence_count: int
    affected_files: List[str]
    example_errors: List[str]
    root_cause_hypothesis: str
    suggested_fix: str
    fix_type: str  # update_layer_map, improve_error_message, add_rule, update_docs
    priority: str  # P0, P1, P2, P3
    confidence: float  # 0.0 to 1.0


@dataclass
class CriticReport:
    """Full analysis report from the Critic."""
    analysis_timestamp: str
    failures_analyzed: int
    time_range: str
    patterns_found: List[FailurePattern]
    summary: Dict
    recommendations: List[Dict]


class HarnessCritic:
    """
    Analyzes failure patterns to suggest harness improvements.

    Key insight from AutoHarness: The Critic consolidates errors from multiple
    failed steps, identifies patterns, and provides structured feedback for
    the Refiner to use when updating harness rules.
    """

    def __init__(self, project_root: Path):
        self.project_root = project_root
        self.failures: List[FailureEvent] = []
        self.patterns: List[FailurePattern] = []

    def load_failures_from_dir(self, failures_dir: Path) -> int:
        """Load failure events from a directory of JSON/JSONL files."""
        count = 0

        if not failures_dir.exists():
            return 0

        # Load from .json files
        for json_file in failures_dir.glob("*.json"):
            try:
                content = json_file.read_text()
                data = json.loads(content)
                if isinstance(data, list):
                    for item in data:
                        self.failures.append(self._parse_failure(item))
                        count += 1
                else:
                    self.failures.append(self._parse_failure(data))
                    count += 1
            except (json.JSONDecodeError, KeyError) as e:
                print(f"Warning: Could not parse {json_file}: {e}", file=sys.stderr)

        # Load from .jsonl files (one event per line)
        for jsonl_file in failures_dir.glob("*.jsonl"):
            try:
                for line in jsonl_file.read_text().splitlines():
                    if line.strip():
                        data = json.loads(line)
                        self.failures.append(self._parse_failure(data))
                        count += 1
            except (json.JSONDecodeError, KeyError) as e:
                print(f"Warning: Could not parse line in {jsonl_file}: {e}", file=sys.stderr)

        return count

    def load_failures_from_episodes(self, episodes_dir: Path, since: Optional[timedelta] = None) -> int:
        """Load failures from episodic memory files."""
        count = 0
        cutoff = datetime.now() - since if since else None

        if not episodes_dir.exists():
            return 0

        for episode_file in episodes_dir.glob("*.json"):
            try:
                content = episode_file.read_text()
                data = json.loads(content)

                # Check timestamp if filtering
                if cutoff and "timestamp" in data:
                    ts = datetime.fromisoformat(data["timestamp"].replace("Z", "+00:00"))
                    if ts.replace(tzinfo=None) < cutoff:
                        continue

                # Extract failures from key_events
                if "key_events" in data:
                    for event in data["key_events"]:
                        if event.get("event") in ["lint_failure", "build_failure", "test_failure", "validation_failure"]:
                            failure = FailureEvent(
                                timestamp=event.get("timestamp", ""),
                                failure_type=event["event"].replace("_failure", ""),
                                error_message=event.get("details", ""),
                                attempted_fix=event.get("resolution"),
                                context={"lesson": event.get("lesson")}
                            )
                            self.failures.append(failure)
                            count += 1
            except (json.JSONDecodeError, KeyError) as e:
                print(f"Warning: Could not parse {episode_file}: {e}", file=sys.stderr)

        return count

    def _parse_failure(self, data: Dict) -> FailureEvent:
        """Parse a failure event from raw data."""
        return FailureEvent(
            timestamp=data.get("timestamp", ""),
            failure_type=data.get("failure_type", data.get("type", "unknown")),
            error_message=data.get("error_message", data.get("message", data.get("details", ""))),
            file_path=data.get("file_path", data.get("file")),
            line_number=data.get("line_number", data.get("line")),
            rule_id=data.get("rule_id", data.get("rule")),
            attempted_fix=data.get("attempted_fix", data.get("resolution")),
            outcome=data.get("outcome"),
            context=data.get("context", {})
        )

    def analyze(self) -> CriticReport:
        """
        Analyze all loaded failures and identify patterns.

        This is the core "Critic" function from AutoHarness - it takes
        multiple failed steps and consolidates them into actionable feedback.
        """
        if not self.failures:
            return CriticReport(
                analysis_timestamp=datetime.now().isoformat(),
                failures_analyzed=0,
                time_range="N/A",
                patterns_found=[],
                summary={"total_failures": 0},
                recommendations=[]
            )

        # Group failures by type
        by_type = defaultdict(list)
        for f in self.failures:
            by_type[f.failure_type].append(f)

        # Group by file path
        by_file = defaultdict(list)
        for f in self.failures:
            if f.file_path:
                by_file[f.file_path].append(f)

        # Group by error message similarity
        by_message = self._group_by_message_similarity()

        # Detect patterns
        self._detect_layer_violation_patterns(by_message)
        self._detect_naming_patterns(by_message)
        self._detect_opaque_error_patterns(by_message)
        self._detect_missing_rule_patterns(by_type)
        self._detect_repeat_failure_patterns(by_file)

        # Calculate time range
        timestamps = [f.timestamp for f in self.failures if f.timestamp]
        time_range = "Unknown"
        if timestamps:
            try:
                sorted_ts = sorted(timestamps)
                time_range = f"{sorted_ts[0]} to {sorted_ts[-1]}"
            except:
                pass

        # Generate recommendations
        recommendations = self._generate_recommendations()

        return CriticReport(
            analysis_timestamp=datetime.now().isoformat(),
            failures_analyzed=len(self.failures),
            time_range=time_range,
            patterns_found=self.patterns,
            summary={
                "total_failures": len(self.failures),
                "by_type": {k: len(v) for k, v in by_type.items()},
                "patterns_detected": len(self.patterns),
                "high_priority_patterns": len([p for p in self.patterns if p.priority in ["P0", "P1"]])
            },
            recommendations=recommendations
        )

    def _group_by_message_similarity(self) -> Dict[str, List[FailureEvent]]:
        """Group failures by similar error messages."""
        groups = defaultdict(list)

        for failure in self.failures:
            # Normalize the message for grouping
            normalized = self._normalize_message(failure.error_message)
            groups[normalized].append(failure)

        return groups

    def _normalize_message(self, message: str) -> str:
        """Normalize error message for grouping similar errors."""
        if not message:
            return "empty"

        # Remove file paths and line numbers
        normalized = re.sub(r'[a-zA-Z0-9_/\\-]+\.(go|ts|py|js):\d+', 'FILE:LINE', message)
        # Remove specific package names but keep structure
        normalized = re.sub(r'internal/[a-zA-Z0-9_/]+', 'internal/PKG', normalized)
        # Remove quotes around identifiers
        normalized = re.sub(r'"[^"]{1,50}"', '"ID"', normalized)
        # Truncate for grouping
        return normalized[:200].lower()

    def _detect_layer_violation_patterns(self, by_message: Dict[str, List[FailureEvent]]) -> None:
        """Detect patterns in layer violation failures."""
        layer_violations = []

        for normalized, failures in by_message.items():
            if any(word in normalized for word in ["layer", "import", "forbidden", "violation", "cannot import"]):
                layer_violations.extend(failures)

        if len(layer_violations) >= 2:
            # Extract which packages are commonly involved
            packages = defaultdict(int)
            for f in layer_violations:
                # Try to extract package names from error message
                pkg_matches = re.findall(r'internal/[a-zA-Z0-9_/]+', f.error_message)
                for pkg in pkg_matches:
                    packages[pkg] += 1

            most_common = sorted(packages.items(), key=lambda x: -x[1])[:3]

            self.patterns.append(FailurePattern(
                pattern_id=f"layer-violation-{len(self.patterns)+1}",
                pattern_type="layer_violation",
                description=f"Repeated layer violations ({len(layer_violations)} occurrences)",
                occurrence_count=len(layer_violations),
                affected_files=list(set(f.file_path for f in layer_violations if f.file_path)),
                example_errors=[f.error_message for f in layer_violations[:3]],
                root_cause_hypothesis=self._hypothesize_layer_violation_cause(most_common),
                suggested_fix=self._suggest_layer_violation_fix(most_common),
                fix_type="update_layer_map",
                priority="P1" if len(layer_violations) >= 5 else "P2",
                confidence=min(0.9, 0.5 + len(layer_violations) * 0.1)
            ))

    def _hypothesize_layer_violation_cause(self, common_packages: List[Tuple[str, int]]) -> str:
        """Generate hypothesis for why layer violations are happening."""
        if not common_packages:
            return "Unknown - no package patterns detected"

        pkg, count = common_packages[0]
        if count >= 3:
            return f"Package '{pkg}' is frequently involved in violations - may be in wrong layer or missing from layer map"
        return "Multiple packages involved - possible architectural ambiguity"

    def _suggest_layer_violation_fix(self, common_packages: List[Tuple[str, int]]) -> str:
        """Suggest fix for layer violation patterns."""
        if not common_packages:
            return "Review and update layer definitions in ARCHITECTURE.md"

        fixes = []
        for pkg, count in common_packages[:2]:
            if count >= 2:
                fixes.append(f"Add '{pkg}' to layer map in scripts/lint-deps or review its placement")

        if fixes:
            return "; ".join(fixes)
        return "Review layer hierarchy in ARCHITECTURE.md and ensure lint-deps.go matches"

    def _detect_naming_patterns(self, by_message: Dict[str, List[FailureEvent]]) -> None:
        """Detect patterns in naming convention failures."""
        naming_failures = []

        for normalized, failures in by_message.items():
            if any(word in normalized for word in ["naming", "convention", "name", "case", "format"]):
                naming_failures.extend(failures)

        if len(naming_failures) >= 2:
            self.patterns.append(FailurePattern(
                pattern_id=f"naming-{len(self.patterns)+1}",
                pattern_type="naming_issue",
                description=f"Repeated naming convention violations ({len(naming_failures)} occurrences)",
                occurrence_count=len(naming_failures),
                affected_files=list(set(f.file_path for f in naming_failures if f.file_path)),
                example_errors=[f.error_message for f in naming_failures[:3]],
                root_cause_hypothesis="Naming conventions may be unclear or inconsistently documented",
                suggested_fix="Add explicit naming examples to DEVELOPMENT.md; consider auto-fix in linter",
                fix_type="update_docs",
                priority="P2",
                confidence=0.7
            ))

    def _detect_opaque_error_patterns(self, by_message: Dict[str, List[FailureEvent]]) -> None:
        """Detect errors that don't have clear fix suggestions."""
        opaque_failures = []

        for normalized, failures in by_message.items():
            for f in failures:
                # Check if error message lacks actionable guidance
                msg_lower = f.error_message.lower()
                has_fix_guidance = any(word in msg_lower for word in [
                    "fix:", "fix option", "to fix", "instead", "should be",
                    "try", "consider", "move", "rename", "remove", "add"
                ])
                if not has_fix_guidance and len(f.error_message) < 100:
                    opaque_failures.append(f)

        if len(opaque_failures) >= 3:
            # Group opaque errors by rule/type
            by_rule = defaultdict(list)
            for f in opaque_failures:
                rule = f.rule_id or f.failure_type or "unknown"
                by_rule[rule].append(f)

            for rule, failures in by_rule.items():
                if len(failures) >= 2:
                    self.patterns.append(FailurePattern(
                        pattern_id=f"opaque-{rule}-{len(self.patterns)+1}",
                        pattern_type="opaque_error",
                        description=f"Error messages for '{rule}' lack actionable guidance ({len(failures)} occurrences)",
                        occurrence_count=len(failures),
                        affected_files=list(set(f.file_path for f in failures if f.file_path)),
                        example_errors=[f.error_message for f in failures[:3]],
                        root_cause_hypothesis=f"Linter rule '{rule}' produces errors that don't explain how to fix",
                        suggested_fix=f"Update error message in linter to include: WHAT + WHY + HOW (with fix options)",
                        fix_type="improve_error_message",
                        priority="P1",  # Opaque errors waste agent time
                        confidence=0.8
                    ))

    def _detect_missing_rule_patterns(self, by_type: Dict[str, List[FailureEvent]]) -> None:
        """Detect potential missing linter rules based on build/test failures."""
        # If we have build failures that could have been caught earlier
        build_failures = by_type.get("build", [])
        lint_failures = by_type.get("lint", [])

        # Check for build failures that suggest missing lint rules
        preventable = []
        for bf in build_failures:
            msg_lower = bf.error_message.lower()
            # Common patterns that could be caught by linting
            if any(pattern in msg_lower for pattern in [
                "undefined", "not declared", "cannot find", "no such",
                "duplicate", "redeclared", "unused"
            ]):
                preventable.append(bf)

        if len(preventable) >= 2:
            self.patterns.append(FailurePattern(
                pattern_id=f"missing-rule-{len(self.patterns)+1}",
                pattern_type="missing_rule",
                description=f"Build failures that could be caught by lint rules ({len(preventable)} occurrences)",
                occurrence_count=len(preventable),
                affected_files=list(set(f.file_path for f in preventable if f.file_path)),
                example_errors=[f.error_message for f in preventable[:3]],
                root_cause_hypothesis="Some code issues are only caught at build time, not lint time",
                suggested_fix="Add stricter static analysis rules to catch these issues earlier",
                fix_type="add_rule",
                priority="P2",
                confidence=0.6
            ))

    def _detect_repeat_failure_patterns(self, by_file: Dict[str, List[FailureEvent]]) -> None:
        """Detect files that repeatedly fail."""
        repeat_failures = {f: failures for f, failures in by_file.items() if len(failures) >= 3}

        if repeat_failures:
            hotspots = sorted(repeat_failures.items(), key=lambda x: -len(x[1]))[:5]

            for file_path, failures in hotspots:
                self.patterns.append(FailurePattern(
                    pattern_id=f"hotspot-{len(self.patterns)+1}",
                    pattern_type="failure_hotspot",
                    description=f"File '{file_path}' has repeated failures ({len(failures)} occurrences)",
                    occurrence_count=len(failures),
                    affected_files=[file_path],
                    example_errors=[f.error_message for f in failures[:3]],
                    root_cause_hypothesis=f"This file may have structural issues or unclear requirements",
                    suggested_fix=f"Review design doc for this component; consider refactoring or adding tests",
                    fix_type="update_docs",
                    priority="P1" if len(failures) >= 5 else "P2",
                    confidence=0.7
                ))

    def _generate_recommendations(self) -> List[Dict]:
        """Generate prioritized recommendations based on patterns."""
        recommendations = []

        # Sort patterns by priority
        p0 = [p for p in self.patterns if p.priority == "P0"]
        p1 = [p for p in self.patterns if p.priority == "P1"]
        p2 = [p for p in self.patterns if p.priority == "P2"]

        for pattern in p0 + p1 + p2:
            recommendations.append({
                "priority": pattern.priority,
                "action": pattern.suggested_fix,
                "type": pattern.fix_type,
                "pattern": pattern.pattern_id,
                "confidence": pattern.confidence,
                "impact": f"Affects {len(pattern.affected_files)} files, {pattern.occurrence_count} occurrences"
            })

        return recommendations


def parse_time_delta(s: str) -> timedelta:
    """Parse a time delta string like '24h', '7d', '30m'."""
    match = re.match(r'^(\d+)([hdm])$', s.lower())
    if not match:
        raise ValueError(f"Invalid time format: {s}. Use format like '24h', '7d', '30m'")

    value = int(match.group(1))
    unit = match.group(2)

    if unit == 'h':
        return timedelta(hours=value)
    elif unit == 'd':
        return timedelta(days=value)
    elif unit == 'm':
        return timedelta(minutes=value)

    return timedelta(hours=24)


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(
        description="Analyze failure patterns and suggest harness improvements (AutoHarness Critic)"
    )
    parser.add_argument(
        "--failures", "-f",
        type=str,
        help="Path to failures directory (harness/trace/failures/)"
    )
    parser.add_argument(
        "--episodes", "-e",
        type=str,
        help="Path to episodic memory directory (harness/memory/episodes/)"
    )
    parser.add_argument(
        "--since", "-s",
        type=str,
        help="Only analyze failures since (e.g., '24h', '7d', '30m')"
    )
    parser.add_argument(
        "--path", "-p",
        type=str,
        default=".",
        help="Project root path"
    )
    parser.add_argument(
        "--json", "-j",
        action="store_true",
        help="Output as JSON"
    )
    parser.add_argument(
        "--min-occurrences", "-m",
        type=int,
        default=2,
        help="Minimum occurrences to report a pattern"
    )
    parser.add_argument(
        "--output", "-o",
        type=str,
        help="Save report to file"
    )

    args = parser.parse_args()
    project_root = Path(args.path).resolve()

    if not project_root.is_dir():
        print(f"Error: {project_root} is not a directory", file=sys.stderr)
        sys.exit(1)

    critic = HarnessCritic(project_root)

    # Parse time filter
    since = None
    if args.since:
        try:
            since = parse_time_delta(args.since)
        except ValueError as e:
            print(f"Error: {e}", file=sys.stderr)
            sys.exit(1)

    # Load failures from specified sources
    total_loaded = 0

    if args.failures:
        failures_dir = Path(args.failures)
        if failures_dir.is_absolute():
            total_loaded += critic.load_failures_from_dir(failures_dir)
        else:
            total_loaded += critic.load_failures_from_dir(project_root / args.failures)

    if args.episodes:
        episodes_dir = Path(args.episodes)
        if episodes_dir.is_absolute():
            total_loaded += critic.load_failures_from_episodes(episodes_dir, since)
        else:
            total_loaded += critic.load_failures_from_episodes(project_root / args.episodes, since)

    # Default: try standard locations
    if not args.failures and not args.episodes:
        total_loaded += critic.load_failures_from_dir(project_root / "harness" / "trace" / "failures")
        total_loaded += critic.load_failures_from_episodes(project_root / "harness" / "memory" / "episodes", since)

    if total_loaded == 0:
        print("No failures found to analyze.", file=sys.stderr)
        print(f"Looked in: {project_root / 'harness' / 'trace' / 'failures'}", file=sys.stderr)
        print(f"       and: {project_root / 'harness' / 'memory' / 'episodes'}", file=sys.stderr)
        sys.exit(0)

    # Run analysis
    report = critic.analyze()

    # Filter patterns by min occurrences
    report.patterns_found = [p for p in report.patterns_found if p.occurrence_count >= args.min_occurrences]

    # Output
    if args.json:
        output = json.dumps({
            "analysis_timestamp": report.analysis_timestamp,
            "failures_analyzed": report.failures_analyzed,
            "time_range": report.time_range,
            "patterns_found": [asdict(p) for p in report.patterns_found],
            "summary": report.summary,
            "recommendations": report.recommendations
        }, indent=2)

        if args.output:
            Path(args.output).write_text(output)
            print(f"Report saved to: {args.output}")
        else:
            print(output)
    else:
        # Human-readable output
        print(f"\n{'=' * 60}")
        print(f"Harness Critic Analysis Report")
        print(f"{'=' * 60}\n")

        print(f"Analyzed: {report.failures_analyzed} failures")
        print(f"Time range: {report.time_range}")
        print(f"Patterns detected: {len(report.patterns_found)}")

        if report.summary.get("by_type"):
            print("\nFailures by type:")
            for ftype, count in report.summary["by_type"].items():
                print(f"  • {ftype}: {count}")

        if report.patterns_found:
            print(f"\n{'─' * 60}")
            print("DETECTED PATTERNS")
            print(f"{'─' * 60}\n")

            for pattern in report.patterns_found:
                priority_icon = {"P0": "🔴", "P1": "🟠", "P2": "🟡", "P3": "🟢"}.get(pattern.priority, "⚪")
                print(f"{priority_icon} [{pattern.priority}] {pattern.description}")
                print(f"   Type: {pattern.pattern_type}")
                print(f"   Files affected: {len(pattern.affected_files)}")
                print(f"   Root cause: {pattern.root_cause_hypothesis}")
                print(f"   Suggested fix: {pattern.suggested_fix}")
                print(f"   Confidence: {pattern.confidence:.0%}")
                print()

        if report.recommendations:
            print(f"{'─' * 60}")
            print("RECOMMENDATIONS (Priority Order)")
            print(f"{'─' * 60}\n")

            for i, rec in enumerate(report.recommendations, 1):
                print(f"{i}. [{rec['priority']}] {rec['action']}")
                print(f"   Type: {rec['type']}")
                print(f"   Impact: {rec['impact']}")
                print()

        if args.output:
            # Save as JSON even in human-readable mode
            output = json.dumps({
                "patterns_found": [asdict(p) for p in report.patterns_found],
                "recommendations": report.recommendations
            }, indent=2)
            Path(args.output).write_text(output)
            print(f"Report saved to: {args.output}")

    sys.exit(0)


if __name__ == "__main__":
    main()
