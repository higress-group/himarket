import { describe, expect, it } from "vitest";
import { buildAcpWsUrl } from "./wsUrl";

const ORIGIN = "wss://example.com";

describe("buildAcpWsUrl", () => {
  it("should include runtime query parameter when provided", () => {
    const url = buildAcpWsUrl(
      { provider: "qodercli", runtime: "local" },
      "/ws/acp",
      ORIGIN,
    );
    const parsed = new URL(url);
    expect(parsed.searchParams.get("runtime")).toBe("local");
    expect(parsed.searchParams.get("provider")).toBe("qodercli");
  });

  it("should include runtime=k8s for K8s runtime", () => {
    const url = buildAcpWsUrl(
      { provider: "kiro-cli", runtime: "k8s" },
      "/ws/acp",
      ORIGIN,
    );
    const parsed = new URL(url);
    expect(parsed.searchParams.get("runtime")).toBe("k8s");
  });

  it("should omit runtime parameter when not provided", () => {
    const url = buildAcpWsUrl(
      { provider: "qodercli" },
      "/ws/acp",
      ORIGIN,
    );
    const parsed = new URL(url);
    expect(parsed.searchParams.has("runtime")).toBe(false);
  });

  it("should include token when provided", () => {
    const url = buildAcpWsUrl(
      { provider: "qodercli", runtime: "local", token: "abc123" },
      "/ws/acp",
      ORIGIN,
    );
    const parsed = new URL(url);
    expect(parsed.searchParams.get("token")).toBe("abc123");
  });

  it("should not include cwd parameter (cwd is determined by backend)", () => {
    const url = buildAcpWsUrl(
      { provider: "kiro-cli", runtime: "local" },
      "/ws/acp",
      ORIGIN,
    );
    const parsed = new URL(url);
    expect(parsed.searchParams.has("cwd")).toBe(false);
  });

  it("should return base URL without query string when no params provided", () => {
    const url = buildAcpWsUrl({}, "/ws/acp", ORIGIN);
    expect(url).toBe("wss://example.com/ws/acp");
  });

  it("should use default basePath /ws/acp", () => {
    const url = buildAcpWsUrl(
      { runtime: "local" },
      undefined,
      ORIGIN,
    );
    expect(url).toContain("/ws/acp");
    const parsed = new URL(url);
    expect(parsed.searchParams.get("runtime")).toBe("local");
  });

  it("should include sandboxMode query parameter when provided", () => {
    const url = buildAcpWsUrl(
      { provider: "kiro-cli", runtime: "k8s", sandboxMode: "user" },
      "/ws/acp",
      ORIGIN,
    );
    const parsed = new URL(url);
    expect(parsed.searchParams.get("sandboxMode")).toBe("user");
  });

  it("should omit sandboxMode parameter when not provided", () => {
    const url = buildAcpWsUrl(
      { provider: "kiro-cli", runtime: "k8s" },
      "/ws/acp",
      ORIGIN,
    );
    const parsed = new URL(url);
    expect(parsed.searchParams.has("sandboxMode")).toBe(false);
  });
});

describe("buildAcpWsUrl - cliSessionConfig", () => {
  it("should include cliSessionConfig query parameter when provided", () => {
    const config = JSON.stringify({ mcpServers: [{ name: "test", url: "http://example.com", transportType: "sse" }] });
    const url = buildAcpWsUrl(
      { provider: "qwen-code", cliSessionConfig: config },
      "/ws/acp",
      ORIGIN,
    );
    const parsed = new URL(url);
    expect(parsed.searchParams.get("cliSessionConfig")).toBe(config);
  });

  it("should omit cliSessionConfig parameter when not provided", () => {
    const url = buildAcpWsUrl(
      { provider: "qwen-code" },
      "/ws/acp",
      ORIGIN,
    );
    const parsed = new URL(url);
    expect(parsed.searchParams.has("cliSessionConfig")).toBe(false);
  });

  it("should include both customModelConfig and cliSessionConfig when both provided", () => {
    const url = buildAcpWsUrl(
      { provider: "qwen-code", customModelConfig: '{"model":"x"}', cliSessionConfig: '{"skills":[]}' },
      "/ws/acp",
      ORIGIN,
    );
    const parsed = new URL(url);
    expect(parsed.searchParams.get("customModelConfig")).toBe('{"model":"x"}');
    expect(parsed.searchParams.get("cliSessionConfig")).toBe('{"skills":[]}');
  });
});

