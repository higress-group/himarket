import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ChatStream } from "./ChatStream";

const useActiveCodingSessionMock = vi.fn();

vi.mock("../../context/CodingSessionContext", () => ({
  useActiveCodingSession: () => useActiveCodingSessionMock(),
}));

describe("ChatStream plan rendering", () => {
  it("renders plan/todo card only once for one plan message", () => {
    useActiveCodingSessionMock.mockReturnValue({
      id: "q1",
      messages: [
        {
          type: "plan",
          id: "p1",
          entries: [{ content: "step 1", status: "pending" }],
        },
      ],
      isProcessing: false,
      selectedToolCallId: null,
      lastCompletedAt: null,
      lastStopReason: null,
    });

    render(<ChatStream onSelectToolCall={() => {}} />);

    expect(screen.getAllByText("Todo")).toHaveLength(1);
  });
});
