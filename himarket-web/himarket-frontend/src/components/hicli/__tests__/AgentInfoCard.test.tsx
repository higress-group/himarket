// Feature: hicli-module, Property 6: Agent 信息缺失字段占位
// **Validates: Requirements 6.8**

import { render, screen, cleanup } from "@testing-library/react";
import { describe, it, expect, vi, afterEach } from "vitest";
import * as fc from "fast-check";
import type { AgentInfo, AuthMethod, AgentCapabilities } from "../../../types/acp";
import type { HiCliState } from "../../../context/HiCliSessionContext";

// ===== Mock useHiCliState =====

let mockState: Partial<HiCliState> = {};

vi.mock("../../../context/HiCliSessionContext", () => ({
  useHiCliState: () => ({
    agentInfo: null,
    authMethods: [],
    agentCapabilities: null,
    modesSource: null,
    modes: [],
    models: [],
    commands: [],
    ...mockState,
  }),
}));

// 延迟导入，确保 mock 先生效
const { AgentInfoCard } = await import("../AgentInfoCard");

// ===== fast-check 生成器 =====

/** 生成可选字符串字段（undefined 或非空字符串，排除可能与标签冲突的值） */
const arbOptionalString = fc.option(
  fc.stringMatching(/^[a-zA-Z0-9._-]{1,30}$/).filter(
    (s) => !["name", "title", "version", "未提供"].includes(s),
  ),
  { nil: undefined },
);

/** 生成 AgentInfo，至少有一个字段为 undefined */
const arbAgentInfoWithMissing: fc.Arbitrary<AgentInfo> = fc
  .record({
    name: arbOptionalString,
    title: arbOptionalString,
    version: arbOptionalString,
  })
  .filter(
    (info) =>
      info.name === undefined ||
      info.title === undefined ||
      info.version === undefined,
  );

/** 生成完全随机的 AgentInfo（字段可能全部存在或全部缺失） */
const arbAgentInfo: fc.Arbitrary<AgentInfo> = fc.record({
  name: arbOptionalString,
  title: arbOptionalString,
  version: arbOptionalString,
});

// ===== 每次测试后清理 DOM =====
afterEach(() => cleanup());

// ===== 属性测试 =====

describe("AgentInfoCard 属性测试", () => {
  // Feature: hicli-module, Property 6: Agent 信息缺失字段占位
  // **Validates: Requirements 6.8**
  describe("Property 6: Agent 信息缺失字段占位", () => {
    it("AgentInfo 字段为 undefined 时渲染占位文本「未提供」", () => {
      fc.assert(
        fc.property(arbAgentInfoWithMissing, (agentInfo) => {
          mockState = { agentInfo };
          const { unmount } = render(<AgentInfoCard />);

          const fields: (keyof AgentInfo)[] = ["name", "title", "version"];
          for (const field of fields) {
            if (agentInfo[field] === undefined) {
              // 该字段缺失时，应渲染"未提供"
              const allPlaceholders = screen.getAllByText("未提供");
              expect(allPlaceholders.length).toBeGreaterThan(0);
            }
          }

          unmount();
          cleanup();
        }),
        { numRuns: 100 },
      );
    });

    it("AgentInfo 为 null 时，Agent 信息区域渲染占位文本「未提供」", () => {
      fc.assert(
        fc.property(fc.constant(null), () => {
          mockState = { agentInfo: null };
          const { unmount } = render(<AgentInfoCard />);

          // agentInfo 为 null 时，Agent 信息区域使用 <Empty /> 渲染"未提供"
          const allPlaceholders = screen.getAllByText("未提供");
          expect(allPlaceholders.length).toBeGreaterThan(0);

          unmount();
          cleanup();
        }),
        { numRuns: 100 },
      );
    });

    it("authMethods 为空数组时，认证方式区域渲染占位文本「未提供」", () => {
      fc.assert(
        fc.property(arbAgentInfo, (agentInfo) => {
          mockState = { agentInfo, authMethods: [] };
          const { unmount } = render(<AgentInfoCard />);

          // authMethods 为空时，认证方式区域使用 <Empty /> 渲染"未提供"
          const allPlaceholders = screen.getAllByText("未提供");
          expect(allPlaceholders.length).toBeGreaterThan(0);

          unmount();
          cleanup();
        }),
        { numRuns: 100 },
      );
    });

    it("agentCapabilities 为 null 时，能力配置区域渲染占位文本「未提供」", () => {
      fc.assert(
        fc.property(arbAgentInfo, (agentInfo) => {
          mockState = { agentInfo, agentCapabilities: null };
          const { unmount } = render(<AgentInfoCard />);

          // agentCapabilities 为 null 时，能力配置区域使用 <Empty /> 渲染"未提供"
          const allPlaceholders = screen.getAllByText("未提供");
          expect(allPlaceholders.length).toBeGreaterThan(0);

          unmount();
          cleanup();
        }),
        { numRuns: 100 },
      );
    });

    it("AgentInfo 存在的字段不渲染占位文本，缺失的字段渲染占位文本", () => {
      fc.assert(
        fc.property(arbAgentInfoWithMissing, (agentInfo) => {
          // 确保其他区域不干扰：提供非空 authMethods 和 agentCapabilities
          mockState = {
            agentInfo,
            authMethods: [{ id: "a1", name: "TestAuth", type: "oauth" }],
            agentCapabilities: { loadSession: true },
            modes: [{ id: "m1", name: "TestMode" }],
            models: [{ modelId: "md1", name: "TestModel" }],
            commands: [{ name: "test", description: "test cmd" }],
          };
          const { unmount, container } = render(<AgentInfoCard />);

          const fields: (keyof AgentInfo)[] = ["name", "title", "version"];
          const missingCount = fields.filter(
            (f) => agentInfo[f] === undefined,
          ).length;
          const presentFields = fields.filter(
            (f) => agentInfo[f] !== undefined,
          );

          // 在 Agent 信息区域的 grid 内查找占位文本
          const gridEl = container.querySelector(".grid");
          expect(gridEl).not.toBeNull();
          const valueSpans = gridEl!.querySelectorAll("span.text-gray-700");
          const placeholderCount = Array.from(valueSpans).filter(
            (el) => el.textContent === "未提供",
          ).length;
          expect(placeholderCount).toBe(missingCount);

          // 存在的字段值应出现在 grid 内
          const valueTexts = Array.from(valueSpans).map((el) => el.textContent);
          for (const field of presentFields) {
            expect(valueTexts).toContain(agentInfo[field]);
          }

          unmount();
          cleanup();
        }),
        { numRuns: 100 },
      );
    });
  });
});
