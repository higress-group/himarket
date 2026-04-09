#!/usr/bin/env python3
"""
Action Verifier - Pre-execution validation based on AutoHarness methodology.

Implements the "Harness-as-Action-Verifier" pattern from the AutoHarness paper
(arXiv:2603.03329). Instead of only validating after execution, this script
validates proposed actions BEFORE execution to prevent invalid operations.

Key insight from AutoHarness: "78% of Gemini-2.5-Flash losses were attributed
to illegal moves." Pre-validation catches errors before they waste context.

Usage:
    python3 scripts/verify_action.py --action "create file internal/types/foo.go"
    python3 scripts/verify_action.py --action "import internal/core from internal/types"
    python3 scripts/verify_action.py --action "modify file docs/ARCHITECTURE.md"
    python3 scripts/verify_action.py --json  # Machine-readable output
"""

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Dict, List, Optional, Tuple


@dataclass
class VerificationResult:
    """Result of action verification."""
    valid: bool
    action: str
    action_type: str
    rejection_reason: Optional[str] = None
    fix_suggestions: List[str] = None
    context: Dict = None

    def __post_init__(self):
        if self.fix_suggestions is None:
            self.fix_suggestions = []
        if self.context is None:
            self.context = {}


class ActionVerifier:
    """
    Verifies proposed actions against harness rules before execution.

    This is a "learned rejection sampler" - the harness acts as a control
    loop that rejects unacceptable actions, where the definition of
    "acceptable" is derived from the project's architecture and rules.
    """

    def __init__(self, project_root: Path):
        self.project_root = project_root
        self.layer_map = self._load_layer_map()
        self.protected_files = self._load_protected_files()
        self.naming_rules = self._load_naming_rules()

    def _load_layer_map(self) -> Dict[str, int]:
        """
        Load layer hierarchy from ARCHITECTURE.md or lint-deps configuration.

        Layer numbers: lower = more foundational (L0 cannot import L1+)
        """
        layer_map = {}

        # Try to parse from ARCHITECTURE.md
        arch_file = self.project_root / "docs" / "ARCHITECTURE.md"
        if arch_file.exists():
            content = arch_file.read_text()
            # Look for layer definitions like "| L0 | internal/types |"
            layer_pattern = r'\|\s*L(\d+)\s*\|\s*([^|]+)\|'
            for match in re.finditer(layer_pattern, content):
                layer_num = int(match.group(1))
                packages = match.group(2).strip()
                for pkg in packages.split(','):
                    pkg = pkg.strip().strip('`').strip('/')
                    if pkg:
                        layer_map[pkg] = layer_num

        # Also check lint-deps.go for Go projects
        lint_deps = self.project_root / "scripts" / "lint-deps.go"
        if lint_deps.exists():
            content = lint_deps.read_text()
            # Look for var layers = map[string]int{...}
            layers_pattern = r'"([^"]+)":\s*(\d+)'
            for match in re.finditer(layers_pattern, content):
                pkg = match.group(1)
                layer = int(match.group(2))
                layer_map[pkg] = layer

        # Default layers if none found
        if not layer_map:
            layer_map = {
                "internal/types": 0,
                "types": 0,
                "internal/utils": 1,
                "utils": 1,
                "internal/core": 2,
                "core": 2,
                "internal/service": 2,
                "service": 2,
                "internal/handler": 3,
                "handler": 3,
                "api": 3,
                "cmd": 4,
            }

        return layer_map

    def _load_protected_files(self) -> List[str]:
        """Load list of protected files that require special handling."""
        protected = [
            ".git/",
            ".env",
            ".env.local",
            "credentials.json",
            "secrets.yaml",
            "*.key",
            "*.pem",
        ]

        # Check AGENTS.md for additional protected files
        agents_md = self.project_root / "AGENTS.md"
        if agents_md.exists():
            content = agents_md.read_text()
            # Look for protected file patterns
            if "protected" in content.lower() or "do not modify" in content.lower():
                # Simple heuristic: files mentioned after "protected" or "do not modify"
                protected_pattern = r'`([^`]+)`.*(?:protected|do not modify)'
                for match in re.finditer(protected_pattern, content, re.IGNORECASE):
                    protected.append(match.group(1))

        return protected

    def _load_naming_rules(self) -> Dict[str, str]:
        """Load naming conventions from project configuration."""
        rules = {
            "go_file": r"^[a-z][a-z0-9_]*\.go$",
            "go_test": r"^[a-z][a-z0-9_]*_test\.go$",
            "ts_file": r"^[a-zA-Z][a-zA-Z0-9-]*\.(ts|tsx)$",
            "py_file": r"^[a-z][a-z0-9_]*\.py$",
        }
        return rules

    def get_layer(self, path: str) -> Optional[int]:
        """Get the layer number for a given file path."""
        # Normalize path
        path = path.replace("\\", "/").strip("/")

        # Try exact match first, then prefix match
        for pkg, layer in sorted(self.layer_map.items(), key=lambda x: -len(x[0])):
            if path.startswith(pkg):
                return layer
        return None

    def verify(self, action: str) -> VerificationResult:
        """
        Verify if an action is legal given the current harness rules.

        This is the core "is_legal_action()" function from AutoHarness.
        """
        action_lower = action.lower()

        # Parse action type
        if any(word in action_lower for word in ["create file", "create", "new file", "add file"]):
            return self._verify_create_file(action)
        elif any(word in action_lower for word in ["modify", "edit", "change", "update file"]):
            return self._verify_modify_file(action)
        elif any(word in action_lower for word in ["delete", "remove file"]):
            return self._verify_delete_file(action)
        elif any(word in action_lower for word in ["import", "from"]):
            return self._verify_import(action)
        elif any(word in action_lower for word in ["rename", "move"]):
            return self._verify_rename(action)
        else:
            # Unknown action type - allow but warn
            return VerificationResult(
                valid=True,
                action=action,
                action_type="unknown",
                context={"warning": "Unknown action type - proceeding with caution"}
            )

    def _verify_create_file(self, action: str) -> VerificationResult:
        """Verify file creation is allowed."""
        # Extract file path from action
        path_match = re.search(r'(?:file\s+)?([^\s]+\.[a-z]+)', action, re.IGNORECASE)
        if not path_match:
            return VerificationResult(
                valid=False,
                action=action,
                action_type="create_file",
                rejection_reason="Could not parse file path from action",
                fix_suggestions=["Specify the full file path, e.g., 'create file internal/types/user.go'"]
            )

        file_path = path_match.group(1)

        # Check if file is protected
        for protected in self.protected_files:
            if protected.endswith("/"):
                if file_path.startswith(protected):
                    return VerificationResult(
                        valid=False,
                        action=action,
                        action_type="create_file",
                        rejection_reason=f"Cannot create files in protected directory: {protected}",
                        fix_suggestions=[f"Create file outside of {protected}"]
                    )
            elif "*" in protected:
                pattern = protected.replace("*", ".*")
                if re.match(pattern, file_path):
                    return VerificationResult(
                        valid=False,
                        action=action,
                        action_type="create_file",
                        rejection_reason=f"File matches protected pattern: {protected}",
                        fix_suggestions=["Use a different file extension or name"]
                    )

        # Check naming convention
        filename = os.path.basename(file_path)
        ext = os.path.splitext(filename)[1]

        if ext == ".go":
            if not re.match(self.naming_rules["go_file"], filename):
                return VerificationResult(
                    valid=False,
                    action=action,
                    action_type="create_file",
                    rejection_reason=f"Go file name '{filename}' doesn't follow convention (lowercase, underscores)",
                    fix_suggestions=[
                        "Use lowercase letters and underscores",
                        f"Example: {filename.lower().replace('-', '_')}"
                    ]
                )

        # Check layer placement (for source files)
        if "internal/" in file_path or "pkg/" in file_path or "src/" in file_path:
            layer = self.get_layer(file_path)
            if layer is None:
                return VerificationResult(
                    valid=True,
                    action=action,
                    action_type="create_file",
                    context={
                        "warning": f"File path '{file_path}' is not in layer map",
                        "suggestion": "Consider adding this package to ARCHITECTURE.md layer definitions"
                    }
                )

        return VerificationResult(
            valid=True,
            action=action,
            action_type="create_file",
            context={"file_path": file_path}
        )

    def _verify_modify_file(self, action: str) -> VerificationResult:
        """Verify file modification is allowed."""
        path_match = re.search(r'(?:file\s+)?([^\s]+\.[a-z]+)', action, re.IGNORECASE)
        if not path_match:
            return VerificationResult(
                valid=True,  # Allow if we can't parse - be permissive for modify
                action=action,
                action_type="modify_file",
                context={"warning": "Could not parse file path"}
            )

        file_path = path_match.group(1)

        # Check if file is protected
        for protected in self.protected_files:
            if file_path == protected or file_path.endswith(protected):
                return VerificationResult(
                    valid=False,
                    action=action,
                    action_type="modify_file",
                    rejection_reason=f"File '{file_path}' is protected",
                    fix_suggestions=[
                        "This file requires special handling",
                        "Check AGENTS.md for modification guidelines",
                        "Consider creating a new file instead"
                    ]
                )

        return VerificationResult(
            valid=True,
            action=action,
            action_type="modify_file",
            context={"file_path": file_path}
        )

    def _verify_delete_file(self, action: str) -> VerificationResult:
        """Verify file deletion is allowed - be conservative."""
        path_match = re.search(r'(?:file\s+)?([^\s]+\.[a-z]+)', action, re.IGNORECASE)
        if not path_match:
            return VerificationResult(
                valid=False,
                action=action,
                action_type="delete_file",
                rejection_reason="Could not parse file path for deletion",
                fix_suggestions=["Specify the exact file path to delete"]
            )

        file_path = path_match.group(1)

        # Check if file is protected
        for protected in self.protected_files:
            if file_path == protected or file_path.endswith(protected):
                return VerificationResult(
                    valid=False,
                    action=action,
                    action_type="delete_file",
                    rejection_reason=f"Cannot delete protected file: {file_path}",
                    fix_suggestions=["This file is protected and should not be deleted"]
                )

        # Warn about deleting test files
        if "_test." in file_path or ".test." in file_path or "test_" in file_path:
            return VerificationResult(
                valid=True,
                action=action,
                action_type="delete_file",
                context={
                    "warning": "Deleting a test file - ensure this is intentional",
                    "file_path": file_path
                }
            )

        return VerificationResult(
            valid=True,
            action=action,
            action_type="delete_file",
            context={"file_path": file_path}
        )

    def _verify_import(self, action: str) -> VerificationResult:
        """
        Verify that an import doesn't violate layer hierarchy.

        This is the core architectural enforcement - lower layers
        cannot import from higher layers.
        """
        # Parse "import X from Y" or "Y imports X"
        import_match = re.search(
            r'import\s+([^\s]+)\s+(?:from|in)\s+([^\s]+)',
            action, re.IGNORECASE
        )
        if not import_match:
            import_match = re.search(
                r'([^\s]+)\s+imports?\s+([^\s]+)',
                action, re.IGNORECASE
            )

        if not import_match:
            return VerificationResult(
                valid=True,
                action=action,
                action_type="import",
                context={"warning": "Could not parse import statement"}
            )

        # Determine source and target
        pkg1 = import_match.group(1).strip().strip('"\'')
        pkg2 = import_match.group(2).strip().strip('"\'')

        # For "import X from Y", X is imported, Y is the importer
        # For "Y imports X", Y is the importer, X is imported
        if "from" in action.lower():
            imported_pkg = pkg1
            importer_pkg = pkg2
        else:
            importer_pkg = pkg1
            imported_pkg = pkg2

        importer_layer = self.get_layer(importer_pkg)
        imported_layer = self.get_layer(imported_pkg)

        if importer_layer is None or imported_layer is None:
            return VerificationResult(
                valid=True,
                action=action,
                action_type="import",
                context={
                    "warning": "One or both packages not in layer map",
                    "importer": importer_pkg,
                    "imported": imported_pkg
                }
            )

        # Layer violation: lower layer importing from higher layer
        if importer_layer < imported_layer:
            return VerificationResult(
                valid=False,
                action=action,
                action_type="import",
                rejection_reason=(
                    f"Layer violation: L{importer_layer} ({importer_pkg}) "
                    f"cannot import L{imported_layer} ({imported_pkg}). "
                    f"Lower layers must not depend on higher layers."
                ),
                fix_suggestions=[
                    f"1. Move the needed functionality down to L{importer_layer} or lower",
                    f"2. Pass the dependency as a parameter (dependency injection)",
                    f"3. Define an interface in L{importer_layer} that L{imported_layer} implements",
                    f"4. Reconsider the design - perhaps the code belongs in a different layer"
                ],
                context={
                    "importer_pkg": importer_pkg,
                    "importer_layer": importer_layer,
                    "imported_pkg": imported_pkg,
                    "imported_layer": imported_layer
                }
            )

        return VerificationResult(
            valid=True,
            action=action,
            action_type="import",
            context={
                "importer_pkg": importer_pkg,
                "importer_layer": importer_layer,
                "imported_pkg": imported_pkg,
                "imported_layer": imported_layer
            }
        )

    def _verify_rename(self, action: str) -> VerificationResult:
        """Verify file rename/move is allowed."""
        # Parse "rename X to Y" or "move X to Y"
        rename_match = re.search(
            r'(?:rename|move)\s+([^\s]+)\s+to\s+([^\s]+)',
            action, re.IGNORECASE
        )

        if not rename_match:
            return VerificationResult(
                valid=True,
                action=action,
                action_type="rename",
                context={"warning": "Could not parse rename/move action"}
            )

        source = rename_match.group(1)
        target = rename_match.group(2)

        # Verify source isn't protected
        for protected in self.protected_files:
            if source == protected or source.endswith(protected):
                return VerificationResult(
                    valid=False,
                    action=action,
                    action_type="rename",
                    rejection_reason=f"Cannot rename protected file: {source}",
                    fix_suggestions=["This file is protected"]
                )

        # Verify target location
        target_result = self._verify_create_file(f"create file {target}")
        if not target_result.valid:
            return VerificationResult(
                valid=False,
                action=action,
                action_type="rename",
                rejection_reason=f"Target location invalid: {target_result.rejection_reason}",
                fix_suggestions=target_result.fix_suggestions
            )

        return VerificationResult(
            valid=True,
            action=action,
            action_type="rename",
            context={"source": source, "target": target}
        )

    def propose_valid_actions(self, intent: str, count: int = 3) -> List[str]:
        """
        Given an intent, propose valid actions that achieve similar goals.

        This implements the "propose_action()" concept from AutoHarness -
        generating a set of legal moves for the agent to choose from.
        """
        suggestions = []
        intent_lower = intent.lower()

        # If intent involves creating a file in a specific layer
        if "create" in intent_lower and "internal" in intent_lower:
            # Suggest appropriate layer placements
            for pkg, layer in sorted(self.layer_map.items(), key=lambda x: x[1]):
                if layer >= 0:  # Start from foundational layers
                    suggestions.append(f"Create file in {pkg}/ (Layer {layer})")
                    if len(suggestions) >= count:
                        break

        # If intent involves imports
        if "import" in intent_lower:
            suggestions.append("Use dependency injection instead of direct import")
            suggestions.append("Define an interface in the lower layer")
            suggestions.append("Move shared code to a common package")

        return suggestions[:count]


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(
        description="Verify proposed actions against harness rules (AutoHarness pattern)"
    )
    parser.add_argument(
        "--action", "-a",
        type=str,
        required=True,
        help="The action to verify (e.g., 'create file internal/types/foo.go')"
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
        "--suggest", "-s",
        action="store_true",
        help="Also suggest valid alternatives if action is invalid"
    )

    args = parser.parse_args()
    project_root = Path(args.path).resolve()

    if not project_root.is_dir():
        print(f"Error: {project_root} is not a directory", file=sys.stderr)
        sys.exit(1)

    verifier = ActionVerifier(project_root)
    result = verifier.verify(args.action)

    # Add suggestions if requested and action is invalid
    if args.suggest and not result.valid:
        alternatives = verifier.propose_valid_actions(args.action)
        result.context["alternatives"] = alternatives

    if args.json:
        print(json.dumps(asdict(result), indent=2))
    else:
        if result.valid:
            print(f"✅ VALID: {result.action}")
            print(f"   Type: {result.action_type}")
            if result.context:
                if "warning" in result.context:
                    print(f"   ⚠️  Warning: {result.context['warning']}")
                if "suggestion" in result.context:
                    print(f"   💡 Suggestion: {result.context['suggestion']}")
        else:
            print(f"❌ INVALID: {result.action}")
            print(f"   Type: {result.action_type}")
            print(f"   Reason: {result.rejection_reason}")
            if result.fix_suggestions:
                print("   Fix options:")
                for i, suggestion in enumerate(result.fix_suggestions, 1):
                    if suggestion.startswith(f"{i}."):
                        print(f"   {suggestion}")
                    else:
                        print(f"   {i}. {suggestion}")
            if args.suggest and result.context.get("alternatives"):
                print("   Valid alternatives:")
                for alt in result.context["alternatives"]:
                    print(f"   → {alt}")

    sys.exit(0 if result.valid else 1)


if __name__ == "__main__":
    main()
