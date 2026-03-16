import { describe, expect, it } from "vitest";
import { buildCodingWsUrl } from "./wsUrl";

const ORIGIN = "wss://example.com";

describe("buildCodingWsUrl", () => {
  it("should include runtime query parameter when provided", () => {
    const url = buildCodingWsUrl(
      { provider: "qodercli", runtime: "remote" },
      "/ws/acp",
      ORIGIN,
    );
    const parsed = new URL(url);
    expect(parsed.searchParams.get("runtime")).toBe("remote");
    expect(parsed.searchParams.get("provider")).toBe("qodercli");
  });

  it("should include runtime=remote for remote sandbox", () => {
    const url = buildCodingWsUrl(
      { provider: "kiro-cli", runtime: "remote" },
      "/ws/acp",
      ORIGIN,
    );
    const parsed = new URL(url);
    expect(parsed.searchParams.get("runtime")).toBe("remote");
  });

  it("should omit runtime parameter when not provided", () => {
    const url = buildCodingWsUrl(
      { provider: "qodercli" },
      "/ws/acp",
      ORIGIN,
    );
    const parsed = new URL(url);
    expect(parsed.searchParams.has("runtime")).toBe(false);
  });

  it("should include token when provided", () => {
    const url = buildCodingWsUrl(
      { provider: "qodercli", runtime: "remote", token: "abc123" },
      "/ws/acp",
      ORIGIN,
    );
    const parsed = new URL(url);
    expect(parsed.searchParams.get("token")).toBe("abc123");
  });

  it("should not include cwd parameter (cwd is determined by backend)", () => {
    const url = buildCodingWsUrl(
      { provider: "kiro-cli", runtime: "remote" },
      "/ws/acp",
      ORIGIN,
    );
    const parsed = new URL(url);
    expect(parsed.searchParams.has("cwd")).toBe(false);
  });

  it("should return base URL without query string when no params provided", () => {
    const url = buildCodingWsUrl({}, "/ws/acp", ORIGIN);
    expect(url).toBe("wss://example.com/ws/acp");
  });

  it("should use default basePath /ws/acp", () => {
    const url = buildCodingWsUrl(
      { runtime: "remote" },
      undefined,
      ORIGIN,
    );
    expect(url).toContain("/ws/acp");
    const parsed = new URL(url);
    expect(parsed.searchParams.get("runtime")).toBe("remote");
  });

});

describe("buildCodingWsUrl - cliSessionConfig no longer in URL", () => {
  it("should NOT include cliSessionConfig in URL even when provided (now sent via WebSocket message)", () => {
    const config = JSON.stringify({ mcpServers: [{ name: "test", url: "http://example.com", transportType: "sse" }] });
    const url = buildCodingWsUrl(
      { provider: "qwen-code", cliSessionConfig: config } as any,
      "/ws/acp",
      ORIGIN,
    );
    const parsed = new URL(url);
    expect(parsed.searchParams.has("cliSessionConfig")).toBe(false);
  });

  it("should omit cliSessionConfig parameter when not provided", () => {
    const url = buildCodingWsUrl(
      { provider: "qwen-code" },
      "/ws/acp",
      ORIGIN,
    );
    const parsed = new URL(url);
    expect(parsed.searchParams.has("cliSessionConfig")).toBe(false);
  });

  it("should include customModelConfig but not cliSessionConfig when both provided", () => {
    const url = buildCodingWsUrl(
      { provider: "qwen-code", customModelConfig: '{"model":"x"}', cliSessionConfig: '{"skills":[]}' } as any,
      "/ws/acp",
      ORIGIN,
    );
    const parsed = new URL(url);
    expect(parsed.searchParams.get("customModelConfig")).toBe('{"model":"x"}');
    expect(parsed.searchParams.has("cliSessionConfig")).toBe(false);
  });
});
