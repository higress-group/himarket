import { describe, it, expect } from "vitest";
import fc from "fast-check";
import { buildAcpWsUrl } from "./wsUrl";

const ORIGIN = "wss://example.com";

// ===== fast-check 生成器 =====

/** 生成合法的 provider 字符串（非空字母数字+连字符） */
const arbProvider = fc.stringMatching(/^[a-z][a-z0-9-]{0,19}$/);

/** 生成合法的 runtime 类型 */
const arbRuntime = fc.constantFrom("local", "k8s");

/** 生成非空的 sandboxMode 字符串（模拟合法模式值） */
const arbSandboxMode = fc.stringMatching(/^[a-z]{1,20}$/);

// Feature: k8s-pod-reuse, Property 1: WebSocket URL 构建包含 sandboxMode 参数
// **Validates: Requirements 1.6**
describe("Property 1: WebSocket URL 构建包含 sandboxMode 参数", () => {
  it("对于任意 provider、runtime 和非空 sandboxMode 的组合，构建的 URL 查询字符串中应包含 sandboxMode 参数且值与输入一致", () => {
    fc.assert(
      fc.property(
        arbProvider,
        arbRuntime,
        arbSandboxMode,
        (provider, runtime, sandboxMode) => {
          const url = buildAcpWsUrl(
            { provider, runtime, sandboxMode },
            "/ws/acp",
            ORIGIN,
          );

          const parsed = new URL(url);

          // sandboxMode 参数应存在且值与输入一致
          expect(parsed.searchParams.has("sandboxMode")).toBe(true);
          expect(parsed.searchParams.get("sandboxMode")).toBe(sandboxMode);
        },
      ),
      { numRuns: 200 },
    );
  });

  it("当 sandboxMode 为空字符串或 undefined 时，URL 不应包含 sandboxMode 参数", () => {
    fc.assert(
      fc.property(
        arbProvider,
        arbRuntime,
        (provider, runtime) => {
          // sandboxMode 为 undefined
          const url1 = buildAcpWsUrl(
            { provider, runtime },
            "/ws/acp",
            ORIGIN,
          );
          const parsed1 = new URL(url1);
          expect(parsed1.searchParams.has("sandboxMode")).toBe(false);

          // sandboxMode 为空字符串
          const url2 = buildAcpWsUrl(
            { provider, runtime, sandboxMode: "" },
            "/ws/acp",
            ORIGIN,
          );
          const parsed2 = new URL(url2);
          expect(parsed2.searchParams.has("sandboxMode")).toBe(false);
        },
      ),
      { numRuns: 100 },
    );
  });
});
