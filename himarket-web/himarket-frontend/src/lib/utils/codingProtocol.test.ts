import { describe, expect, it } from "vitest";
import fc from "fast-check";
import {
  trackRequest,
  resolveResponse,

} from "./codingProtocol";
import type { CodingResponse } from "../../types/coding-protocol";

describe("resolveResponse error propagation", () => {
  // Feature: acp-error-response-handling, Property 4: resolveResponse 正确传递错误对象
  // **Validates: Requirements 5.1**
  it("Property 4: resolveResponse 正确传递错误对象 — reject 回调收到完整的 { code, message, data? } 对象", async () => {
    await fc.assert(
      fc.asyncProperty(
        // 生成随机 JsonRpcId（number 或 string）
        fc.oneof(
          fc.integer({ min: 1, max: 100000 }),
          fc.string({ minLength: 1, maxLength: 20 })
        ),
        // 生成随机 error code
        fc.integer(),
        // 生成随机 error message
        fc.string({ minLength: 1 }),
        // 生成可选的 data 字段
        fc.option(fc.dictionary(fc.string({ minLength: 1, maxLength: 10 }), fc.jsonValue()), { nil: undefined }),
        async (id, code, message, data) => {
          // 先注册一个 pending request
          const promise = trackRequest(id);

          // 构造含 error 字段的 CodingResponse
          const errorObj: { code: number; message: string; data?: Record<string, unknown> } = {
            code,
            message,
          };
          if (data !== undefined) {
            errorObj.data = data as Record<string, unknown>;
          }

          const response: CodingResponse = {
            jsonrpc: "2.0",
            id,
            error: errorObj,
          };

          // 调用 resolveResponse
          const resolved = resolveResponse(response);
          expect(resolved).toBe(true);

          // 验证 reject 回调收到完整的错误对象
          try {
            await promise;
            // 不应该走到这里，因为有 error 字段应该 reject
            expect.unreachable("promise should have been rejected");
          } catch (err: unknown) {
            const error = err as { code: number; message: string; data?: Record<string, unknown> };
            expect(error.code).toBe(code);
            expect(error.message).toBe(message);
            if (data !== undefined) {
              expect(error.data).toEqual(data);
            }
          }
        }
      ),
      { numRuns: 100 }
    );
  });
});
