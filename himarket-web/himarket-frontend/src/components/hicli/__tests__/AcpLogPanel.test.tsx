// Feature: hicli-module, Property 7: 日志条目渲染完整性
// **Validates: Requirements 5.5**

import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import * as fc from "fast-check";
import { AcpLogPanel } from "../AcpLogPanel";
import type { AggregatedLogEntry, RawMessageDirection } from "../../../types/log";

// ===== Mock useHiCliState =====

let mockAggregatedLogs: AggregatedLogEntry[] = [];

vi.mock("../../../context/HiCliSessionContext", () => ({
  useHiCliState: () => ({
    aggregatedLogs: mockAggregatedLogs,
  }),
}));

// ===== fast-check 生成器 =====

const arbDirection: fc.Arbitrary<RawMessageDirection> = fc.constantFrom(
  "client_to_agent" as const,
  "agent_to_client" as const,
);

const arbMethod = fc.constantFrom(
  "initialize",
  "session/new",
  "session/prompt",
  "session/update",
  "session/cancel",
  "session/set_model",
  "session/set_mode",
  "session/request_permission",
);

/** 生成非空摘要文本（至少包含一个非空格字符，避免 testing-library normalize 导致匹配失败） */
const arbSummary = fc.stringMatching(/^[a-zA-Z0-9]{1,50}$/);

/** 生成时间戳（合理范围内的毫秒时间戳） */
const arbTimestamp = fc.integer({ min: 1_700_000_000_000, max: 1_800_000_000_000 });

/** 生成非聚合日志条目（isAggregated=false, messageCount=1） */
const arbNonAggregatedEntry: fc.Arbitrary<AggregatedLogEntry> = fc.record({
  id: fc.uuid(),
  direction: arbDirection,
  timestamp: arbTimestamp,
  endTimestamp: arbTimestamp,
  method: fc.option(arbMethod, { nil: undefined }),
  rpcId: fc.option(fc.integer({ min: 1, max: 99999 }), { nil: undefined }),
  summary: arbSummary,
  data: fc.constant({}),
  messageCount: fc.constant(1),
  isAggregated: fc.constant(false),
});

/** 生成聚合日志条目（isAggregated=true, messageCount>1） */
const arbAggregatedEntry: fc.Arbitrary<AggregatedLogEntry> = fc.record({
  id: fc.uuid(),
  direction: arbDirection,
  timestamp: arbTimestamp,
  endTimestamp: arbTimestamp,
  method: fc.option(arbMethod, { nil: undefined }),
  rpcId: fc.option(fc.integer({ min: 1, max: 99999 }), { nil: undefined }),
  summary: arbSummary,
  data: fc.constant({}),
  messageCount: fc.integer({ min: 2, max: 100 }),
  isAggregated: fc.constant(true),
});

/** 生成任意日志条目（聚合或非聚合） */
const arbEntry: fc.Arbitrary<AggregatedLogEntry> = fc.oneof(
  arbNonAggregatedEntry,
  arbAggregatedEntry,
);

// ===== 辅助函数 =====

/** 格式化时间戳为 HH:MM:SS.mmm（与组件逻辑一致） */
function formatTime(ts: number): string {
  const d = new Date(ts);
  return (
    d.toLocaleTimeString("en-US", {
      hour12: false,
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    }) +
    "." +
    String(d.getMilliseconds()).padStart(3, "0")
  );
}

// ===== 属性测试 =====

describe("AcpLogPanel 属性测试", () => {
  // Feature: hicli-module, Property 7: 日志条目渲染完整性
  // **Validates: Requirements 5.5**
  describe("Property 7: 日志条目渲染完整性", () => {
    it("每条日志条目渲染包含方向指示（↑ 发送 / ↓ 接收）", () => {
      fc.assert(
        fc.property(arbEntry, (entry) => {
          mockAggregatedLogs = [entry];
          const { unmount } = render(
            <AcpLogPanel filter="" onFilterChange={() => {}} />,
          );

          const expectedArrow = entry.direction === "client_to_agent" ? "↑" : "↓";
          expect(screen.getByText(expectedArrow)).toBeInTheDocument();

          unmount();
        }),
        { numRuns: 100 },
      );
    });

    it("每条日志条目渲染包含 method 或 summary 文本", () => {
      fc.assert(
        fc.property(arbEntry, (entry) => {
          mockAggregatedLogs = [entry];
          const { unmount } = render(
            <AcpLogPanel filter="" onFilterChange={() => {}} />,
          );

          // 组件优先显示 method，无 method 时显示 summary
          const displayText = entry.method ?? entry.summary;
          expect(screen.getByText(displayText)).toBeInTheDocument();

          unmount();
        }),
        { numRuns: 100 },
      );
    });

    it("每条日志条目渲染包含时间戳", () => {
      fc.assert(
        fc.property(arbEntry, (entry) => {
          mockAggregatedLogs = [entry];
          const { unmount } = render(
            <AcpLogPanel filter="" onFilterChange={() => {}} />,
          );

          const expectedTime = formatTime(entry.timestamp);
          expect(screen.getByText(expectedTime)).toBeInTheDocument();

          unmount();
        }),
        { numRuns: 100 },
      );
    });

    it("聚合条目（isAggregated=true, messageCount>1）渲染消息数徽章", () => {
      fc.assert(
        fc.property(arbAggregatedEntry, (entry) => {
          mockAggregatedLogs = [entry];
          const { unmount } = render(
            <AcpLogPanel filter="" onFilterChange={() => {}} />,
          );

          const badgeText = `×${entry.messageCount}`;
          expect(screen.getByText(badgeText)).toBeInTheDocument();

          unmount();
        }),
        { numRuns: 100 },
      );
    });

    it("非聚合条目（isAggregated=false）不渲染消息数徽章", () => {
      fc.assert(
        fc.property(arbNonAggregatedEntry, (entry) => {
          mockAggregatedLogs = [entry];
          const { container, unmount } = render(
            <AcpLogPanel filter="" onFilterChange={() => {}} />,
          );

          // 非聚合条目不应包含 ×N 徽章
          const badges = container.querySelectorAll(".rounded-full");
          expect(badges.length).toBe(0);

          unmount();
        }),
        { numRuns: 100 },
      );
    });
  });
});
