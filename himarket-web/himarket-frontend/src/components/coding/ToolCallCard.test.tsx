import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ToolCallCard } from "./ToolCallCard";
import type { ToolKind } from "../../types/coding-protocol";

const KINDS: ToolKind[] = [
  "read",
  "edit",
  "delete",
  "move",
  "search",
  "execute",
  "think",
  "fetch",
  "switch_mode",
  "other",
  "skill",
];

describe("ToolCallCard kind mapping", () => {
  it.each(KINDS)("renders kind=%s without falling back incorrectly", kind => {
    const title = kind === "skill" ? "Skill my-skill" : `${kind} title`;
    // file-based kinds: read, edit, delete, move
    render(
      <ToolCallCard
        item={{
          type: "tool_call",
          id: `id-${kind}`,
          toolCallId: `tc-${kind}`,
          title,
          kind,
          status: "completed",
          rawInput:
            kind === "execute"
              ? { command: "npm test" }
              : { path: "/tmp/demo.txt" },
          content:
            kind === "execute"
              ? [{ type: "terminal", terminalId: "term-1" }]
              : undefined,
        }}
        selected={false}
        onClick={() => {}}
      />
    );

    if (kind === "execute") {
      expect(screen.getByText("npm test")).toBeInTheDocument();
    } else if (kind === "skill") {
      expect(screen.getByText("my-skill")).toBeInTheDocument();
    } else if (kind === "read") {
      // Default variant renders "已查看 {fileName}"
      expect(screen.getByText(/demo\.txt/)).toBeInTheDocument();
    } else if (kind === "edit" || kind === "delete" || kind === "move") {
      expect(screen.getByText("demo.txt")).toBeInTheDocument();
    } else {
      // search/think/fetch/switch_mode/other prepend a Chinese label before the title
      const span = document.querySelector("span.truncate");
      expect(span).not.toBeNull();
      expect(span!.textContent).toContain(title);
    }
  });
});
