#!/usr/bin/env python3
"""
Memory Query CLI — Search and retrieve agent memory.

Queries the three types of agent memory:
  - Episodic: What happened (past events, failures, recoveries)
  - Semantic: What the agent knows (codebase facts, conventions)
  - Procedural: How to do things (learned workflows, success rates)

Usage:
    python3 scripts/memory_query.py search "layer violation"
    python3 scripts/memory_query.py episodes --since 7d
    python3 scripts/memory_query.py procedures --min-success 0.8
    python3 scripts/memory_query.py knowledge
    python3 scripts/memory_query.py stats
    python3 scripts/memory_query.py search "auth" --type episodic --json
"""

import argparse
import json
import os
import re
import sys
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any, Dict, List, Optional


MEMORY_ROOT = "harness/memory"
EPISODES_DIR = f"{MEMORY_ROOT}/episodes"
KNOWLEDGE_DIR = f"{MEMORY_ROOT}/knowledge"
PROCEDURES_DIR = f"{MEMORY_ROOT}/procedures"


def parse_duration(duration_str: str) -> timedelta:
    """Parse duration string like '7d', '24h', '30m' into timedelta."""
    match = re.match(r'^(\d+)([dhm])$', duration_str)
    if not match:
        raise ValueError(f"Invalid duration: {duration_str}. Use format: 7d, 24h, 30m")
    value, unit = int(match.group(1)), match.group(2)
    if unit == 'd':
        return timedelta(days=value)
    elif unit == 'h':
        return timedelta(hours=value)
    elif unit == 'm':
        return timedelta(minutes=value)
    return timedelta()


def load_jsonl(filepath: Path) -> List[Dict]:
    """Load a JSONL file, returning list of parsed objects."""
    entries = []
    if not filepath.exists():
        return entries
    for line in filepath.read_text().strip().split('\n'):
        line = line.strip()
        if line:
            try:
                entries.append(json.loads(line))
            except json.JSONDecodeError:
                continue
    return entries


def load_json(filepath: Path) -> Optional[Dict]:
    """Load a JSON file."""
    if not filepath.exists():
        return None
    try:
        return json.loads(filepath.read_text())
    except (json.JSONDecodeError, OSError):
        return None


# ─── Episodic Memory ────────────────────────────────────────────────────────

def query_episodes(
    project_root: Path,
    since: Optional[str] = None,
    keyword: Optional[str] = None,
    outcome: Optional[str] = None
) -> List[Dict]:
    """Query episodic memory entries."""
    episodes_dir = project_root / EPISODES_DIR
    if not episodes_dir.exists():
        return []

    cutoff = None
    if since:
        cutoff = datetime.utcnow() - parse_duration(since)

    results = []
    for jsonl_file in sorted(episodes_dir.glob("*.jsonl"), reverse=True):
        # Quick date filter from filename (YYYY-MM-DD.jsonl)
        if cutoff:
            try:
                file_date = datetime.strptime(jsonl_file.stem, "%Y-%m-%d")
                if file_date.date() < cutoff.date() - timedelta(days=1):
                    continue
            except ValueError:
                pass

        entries = load_jsonl(jsonl_file)
        for entry in entries:
            # Time filter
            if cutoff and "timestamp" in entry:
                try:
                    entry_time = datetime.fromisoformat(entry["timestamp"].replace("Z", "+00:00"))
                    if entry_time.replace(tzinfo=None) < cutoff:
                        continue
                except (ValueError, TypeError):
                    pass

            # Outcome filter
            if outcome and entry.get("outcome") != outcome:
                continue

            # Keyword filter
            if keyword:
                entry_text = json.dumps(entry).lower()
                if keyword.lower() not in entry_text:
                    continue

            results.append(entry)

    return results


# ─── Semantic Memory (Knowledge) ────────────────────────────────────────────

def query_knowledge(project_root: Path, keyword: Optional[str] = None) -> List[Dict]:
    """Query semantic memory — facts about the codebase."""
    knowledge_dir = project_root / KNOWLEDGE_DIR
    if not knowledge_dir.exists():
        return []

    results = []
    for json_file in sorted(knowledge_dir.glob("*.json")):
        data = load_json(json_file)
        if data is None:
            continue

        if keyword:
            text = json.dumps(data).lower()
            if keyword.lower() not in text:
                continue

        results.append({
            "source": json_file.name,
            "updated": data.get("updated", "unknown"),
            "content": data
        })

    return results


# ─── Procedural Memory ──────────────────────────────────────────────────────

def query_procedures(
    project_root: Path,
    keyword: Optional[str] = None,
    min_success_rate: Optional[float] = None
) -> List[Dict]:
    """Query procedural memory — learned workflows."""
    procedures_dir = project_root / PROCEDURES_DIR
    if not procedures_dir.exists():
        return []

    results = []
    for json_file in sorted(procedures_dir.glob("*.json")):
        data = load_json(json_file)
        if data is None:
            continue

        # Parse success rate
        success_rate = None
        sr_str = data.get("success_rate", "")
        if isinstance(sr_str, str) and "/" in sr_str:
            parts = sr_str.split("/")
            try:
                success_rate = int(parts[0]) / int(parts[1])
            except (ValueError, ZeroDivisionError):
                pass
        elif isinstance(sr_str, (int, float)):
            success_rate = float(sr_str)

        # Success rate filter
        if min_success_rate is not None and success_rate is not None:
            if success_rate < min_success_rate:
                continue

        # Keyword filter
        if keyword:
            text = json.dumps(data).lower()
            if keyword.lower() not in text:
                continue

        results.append({
            "source": json_file.name,
            "procedure": data.get("procedure", json_file.stem),
            "success_rate": sr_str,
            "success_rate_numeric": success_rate,
            "steps": data.get("steps", []),
            "strategies": data.get("strategies", []),
            "last_used": data.get("last_used", "unknown")
        })

    # Sort by success rate (highest first)
    results.sort(key=lambda x: x.get("success_rate_numeric") or 0, reverse=True)
    return results


# ─── Unified Search ─────────────────────────────────────────────────────────

def search_all(
    project_root: Path,
    keyword: str,
    memory_type: Optional[str] = None
) -> Dict[str, List]:
    """Search across all memory types for a keyword."""
    results = {}

    if memory_type is None or memory_type == "episodic":
        results["episodic"] = query_episodes(project_root, keyword=keyword)

    if memory_type is None or memory_type == "semantic":
        results["semantic"] = query_knowledge(project_root, keyword=keyword)

    if memory_type is None or memory_type == "procedural":
        results["procedural"] = query_procedures(project_root, keyword=keyword)

    return results


# ─── Stats ───────────────────────────────────────────────────────────────────

def memory_stats(project_root: Path) -> Dict:
    """Get statistics about all memory stores."""
    stats: Dict[str, Any] = {
        "episodic": {"files": 0, "entries": 0, "date_range": ""},
        "semantic": {"files": 0},
        "procedural": {"files": 0, "procedures": []},
        "total_size_bytes": 0
    }

    # Episodic
    episodes_dir = project_root / EPISODES_DIR
    if episodes_dir.exists():
        jsonl_files = sorted(episodes_dir.glob("*.jsonl"))
        stats["episodic"]["files"] = len(jsonl_files)
        total_entries = 0
        for f in jsonl_files:
            total_entries += len(load_jsonl(f))
            stats["total_size_bytes"] += f.stat().st_size
        stats["episodic"]["entries"] = total_entries
        if jsonl_files:
            stats["episodic"]["date_range"] = f"{jsonl_files[0].stem} to {jsonl_files[-1].stem}"

    # Semantic
    knowledge_dir = project_root / KNOWLEDGE_DIR
    if knowledge_dir.exists():
        json_files = list(knowledge_dir.glob("*.json"))
        stats["semantic"]["files"] = len(json_files)
        for f in json_files:
            stats["total_size_bytes"] += f.stat().st_size

    # Procedural
    procedures_dir = project_root / PROCEDURES_DIR
    if procedures_dir.exists():
        json_files = list(procedures_dir.glob("*.json"))
        stats["procedural"]["files"] = len(json_files)
        for f in json_files:
            data = load_json(f)
            if data:
                stats["procedural"]["procedures"].append({
                    "name": data.get("procedure", f.stem),
                    "success_rate": data.get("success_rate", "unknown")
                })
            stats["total_size_bytes"] += f.stat().st_size

    return stats


# ─── CLI ─────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Query agent memory (episodic, semantic, procedural)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s search "layer violation"            Search all memory types
  %(prog)s search "auth" --type episodic       Search only episodes
  %(prog)s episodes --since 7d                 Recent episodes (last 7 days)
  %(prog)s episodes --outcome success          Only successful episodes
  %(prog)s procedures --min-success 0.8        High-success procedures
  %(prog)s knowledge                           Show all codebase knowledge
  %(prog)s stats                               Memory store statistics
        """
    )

    subparsers = parser.add_subparsers(dest="command", help="Command to run")

    # search
    search_p = subparsers.add_parser("search", help="Search across all memory types")
    search_p.add_argument("keyword", help="Search keyword")
    search_p.add_argument("--type", choices=["episodic", "semantic", "procedural"],
                          help="Limit to one memory type")
    search_p.add_argument("--json", action="store_true")

    # episodes
    ep_p = subparsers.add_parser("episodes", help="Query episodic memory")
    ep_p.add_argument("--since", help="Time window (e.g., 7d, 24h, 30m)")
    ep_p.add_argument("--keyword", help="Filter by keyword")
    ep_p.add_argument("--outcome", choices=["success", "failure", "partial"],
                      help="Filter by outcome")
    ep_p.add_argument("--json", action="store_true")

    # procedures
    proc_p = subparsers.add_parser("procedures", help="Query procedural memory")
    proc_p.add_argument("--keyword", help="Filter by keyword")
    proc_p.add_argument("--min-success", type=float,
                        help="Minimum success rate (0.0-1.0)")
    proc_p.add_argument("--json", action="store_true")

    # knowledge
    know_p = subparsers.add_parser("knowledge", help="Query semantic memory")
    know_p.add_argument("--keyword", help="Filter by keyword")
    know_p.add_argument("--json", action="store_true")

    # stats
    stats_p = subparsers.add_parser("stats", help="Memory store statistics")
    stats_p.add_argument("--json", action="store_true")

    # Common
    parser.add_argument("--project-root", default=".", help="Project root (default: .)")

    args = parser.parse_args()
    if not args.command:
        parser.print_help()
        sys.exit(1)

    project_root = Path(args.project_root).resolve()

    if args.command == "search":
        results = search_all(project_root, args.keyword, args.type)
        if args.json:
            print(json.dumps(results, indent=2, default=str))
        else:
            total = sum(len(v) for v in results.values())
            if total == 0:
                print(f"No results for '{args.keyword}'")
                return
            print(f"Found {total} result(s) for '{args.keyword}':\n")
            for mem_type, entries in results.items():
                if entries:
                    print(f"  [{mem_type.upper()}] ({len(entries)} match{'es' if len(entries) != 1 else ''})")
                    for e in entries[:5]:
                        if mem_type == "episodic":
                            print(f"    • {e.get('task', 'unknown task')} — {e.get('outcome', '?')}")
                            for lesson in e.get("lessons", [])[:2]:
                                print(f"      └ {lesson}")
                        elif mem_type == "semantic":
                            print(f"    • {e.get('source', '?')} (updated: {e.get('updated', '?')})")
                        elif mem_type == "procedural":
                            print(f"    • {e.get('procedure', '?')} — success: {e.get('success_rate', '?')}")
                    if len(entries) > 5:
                        print(f"    ... and {len(entries) - 5} more")
                    print()

    elif args.command == "episodes":
        results = query_episodes(project_root, args.since, args.keyword, args.outcome)
        if args.json:
            print(json.dumps(results, indent=2, default=str))
        else:
            if not results:
                print("No episodes found")
                return
            print(f"Found {len(results)} episode(s):\n")
            for e in results:
                outcome_icon = {"success": "✓", "failure": "✗", "partial": "◐"}.get(
                    e.get("outcome", ""), "?")
                print(f"  {outcome_icon} {e.get('task', 'unknown')} [{e.get('timestamp', '')[:10]}]")
                for lesson in e.get("lessons", []):
                    print(f"    └ {lesson}")
                for event in e.get("key_events", [])[:3]:
                    print(f"    • {event.get('event', '?')}: {event.get('details', '')[:80]}")

    elif args.command == "procedures":
        results = query_procedures(project_root, args.keyword, args.min_success)
        if args.json:
            print(json.dumps(results, indent=2, default=str))
        else:
            if not results:
                print("No procedures found")
                return
            print(f"Found {len(results)} procedure(s):\n")
            for p in results:
                rate = p.get("success_rate", "?")
                print(f"  📋 {p['procedure']} (success: {rate}, last used: {p.get('last_used', '?')})")
                for step in p.get("steps", [])[:5]:
                    print(f"     {step.get('step', '?')}. {step.get('action', '?')}")
                for strat in p.get("strategies", [])[:3]:
                    sr = strat.get("success_rate", "?")
                    print(f"     → {strat.get('strategy', '?')} ({sr})")

    elif args.command == "knowledge":
        results = query_knowledge(project_root, getattr(args, 'keyword', None))
        if args.json:
            print(json.dumps(results, indent=2, default=str))
        else:
            if not results:
                print("No knowledge entries found")
                return
            print(f"Found {len(results)} knowledge file(s):\n")
            for k in results:
                print(f"  📖 {k['source']} (updated: {k.get('updated', '?')})")
                content = k.get("content", {})
                for top_key in list(content.keys())[:5]:
                    if top_key != "updated":
                        val = content[top_key]
                        if isinstance(val, dict):
                            print(f"     {top_key}: {len(val)} entries")
                        elif isinstance(val, list):
                            print(f"     {top_key}: {len(val)} items")
                        else:
                            print(f"     {top_key}: {str(val)[:60]}")

    elif args.command == "stats":
        stats = memory_stats(project_root)
        if args.json:
            print(json.dumps(stats, indent=2))
        else:
            print("Memory Store Statistics\n")
            ep = stats["episodic"]
            print(f"  📔 Episodic:   {ep['files']} files, {ep['entries']} entries")
            if ep["date_range"]:
                print(f"                 Range: {ep['date_range']}")
            print(f"  📖 Semantic:   {stats['semantic']['files']} knowledge files")
            proc = stats["procedural"]
            print(f"  📋 Procedural: {proc['files']} procedures")
            for p in proc.get("procedures", []):
                print(f"                 • {p['name']} ({p['success_rate']})")
            size_kb = stats["total_size_bytes"] / 1024
            print(f"\n  Total size: {size_kb:.1f} KB")


if __name__ == "__main__":
    main()
