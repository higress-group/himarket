import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ChangesView } from "./ChangesView";

const useActiveQuestMock = vi.fn();

vi.mock("../../context/QuestSessionContext", () => ({
  useActiveQuest: () => useActiveQuestMock(),
}));

describe("ChangesView", () => {
  it("includes delete-style diff where newText is empty string", () => {
    useActiveQuestMock.mockReturnValue({
      id: "q1",
      messages: [
        {
          type: "tool_call",
          id: "tc-item-1",
          toolCallId: "tc-1",
          title: "Delete line",
          kind: "edit",
          status: "completed",
          content: [
            {
              type: "diff",
              path: "/tmp/delete.txt",
              oldText: "a",
              newText: "",
            },
          ],
        },
      ],
    });

    render(<ChangesView />);

    expect(screen.getByText("1 file changed")).toBeInTheDocument();
    expect(screen.getByText("/tmp/delete.txt")).toBeInTheDocument();
  });
});
