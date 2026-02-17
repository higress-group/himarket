/**
 * Property 13: WebContainer 文件持久化 Round-Trip
 *
 * 对于任意文件路径和文件内容集合，通过 WebContainerAdapter 写入文件后，
 * 经 FileSyncService 同步到服务端，再通过 FileSyncService.restore 恢复到
 * 新的 WebContainerAdapter 实例，读取的文件内容应该与原始写入内容一致。
 *
 * **Validates: Requirements 9.3, 9.5**
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import fc from 'fast-check';
import { FileSyncService } from './FileSyncService';
import type { WebContainerAdapter } from './WebContainerAdapter';

// ===== 测试辅助 =====

/**
 * 创建 mock WebContainerAdapter，使用内存 Map 模拟文件系统。
 * 返回 adapter 和底层 files Map 以便验证。
 */
function createMockAdapter(): { adapter: WebContainerAdapter; files: Map<string, string> } {
  const files = new Map<string, string>();
  const adapter = {
    writeFile: vi.fn(async (path: string, content: string) => {
      files.set(path, content);
    }),
    readFile: vi.fn(async (path: string) => {
      if (!files.has(path)) throw new Error(`File not found: ${path}`);
      return files.get(path)!;
    }),
  } as unknown as WebContainerAdapter;
  return { adapter, files };
}

/**
 * 创建模拟服务端存储。
 * 拦截 fetch 调用，POST /api/workspace/sync 存储文件，
 * GET /api/workspace/files 返回已存储的文件。
 */
function createMockServer(baseUrl: string) {
  const storage = new Map<string, { path: string; content: string }[]>();

  const fetchMock = vi.fn(async (url: string | URL | Request, init?: RequestInit) => {
    const urlStr = typeof url === 'string' ? url : url instanceof URL ? url.toString() : url.url;

    // POST /api/workspace/sync - 存储文件
    if (urlStr === `${baseUrl}/api/workspace/sync` && init?.method === 'POST') {
      const body = JSON.parse(init.body as string);
      const userId: string = body.userId;
      const files: { path: string; content: string }[] = body.files;

      // 合并存储：相同路径覆盖，新路径追加
      const existing = storage.get(userId) ?? [];
      for (const file of files) {
        const idx = existing.findIndex((f) => f.path === file.path);
        if (idx >= 0) {
          existing[idx] = file;
        } else {
          existing.push(file);
        }
      }
      storage.set(userId, existing);

      return new Response('', { status: 200 });
    }

    // GET /api/workspace/files?userId=xxx - 返回文件列表
    if (urlStr.startsWith(`${baseUrl}/api/workspace/files`)) {
      const parsedUrl = new URL(urlStr);
      const userId = parsedUrl.searchParams.get('userId') ?? '';
      const files = storage.get(userId) ?? [];
      return new Response(JSON.stringify(files), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }

    return new Response('Not Found', { status: 404 });
  });

  return { fetchMock, storage };
}

// ===== Generators =====

/**
 * 生成合法的相对文件路径。
 * 规则：
 * - 由 1~4 个路径段组成
 * - 每段是合法的文件名（字母、数字、连字符、下划线）
 * - 最后一段带文件扩展名
 * - 不包含路径遍历模式（../）
 */
const filePathArb = fc
  .tuple(
    fc.array(
      fc.stringMatching(/^[a-zA-Z][a-zA-Z0-9_-]{0,11}$/),
      { minLength: 0, maxLength: 3 },
    ),
    fc.stringMatching(/^[a-zA-Z][a-zA-Z0-9_-]{0,11}$/),
    fc.constantFrom('.ts', '.js', '.json', '.txt', '.md', '.css'),
  )
  .map(([dirs, name, ext]) => {
    const segments = [...dirs, `${name}${ext}`];
    return segments.join('/');
  });

/**
 * 生成随机文件内容。
 * 包含各种字符：ASCII、Unicode、空字符串、多行文本等。
 */
const fileContentArb = fc.oneof(
  fc.string({ minLength: 0, maxLength: 500 }),
  fc.lorem({ maxCount: 5, mode: 'sentences' }),
  fc.constant(''),
);

/**
 * 生成文件路径→内容的映射（1~10 个文件）。
 * 使用 uniqueBy 确保路径唯一。
 */
const fileSetArb = fc
  .array(fc.tuple(filePathArb, fileContentArb), { minLength: 1, maxLength: 10 })
  .map((entries) => {
    // 去重：相同路径只保留最后一个
    const map = new Map<string, string>();
    for (const [path, content] of entries) {
      map.set(path, content);
    }
    return Array.from(map.entries());
  })
  .filter((entries) => entries.length > 0);

// ===== Property Tests =====

describe('Property 13: WebContainer 文件持久化 Round-Trip', () => {
  const BASE_URL = 'http://localhost:8080';
  const USER_ID = 'test-user';

  let originalFetch: typeof globalThis.fetch;

  beforeEach(() => {
    originalFetch = globalThis.fetch;
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  /**
   * 属性 13a: 对于任意文件路径和内容集合，
   * 写入 → markDirty → flush(同步) → restore(恢复) → 读取
   * 恢复后读取的内容应与原始写入内容完全一致。
   *
   * **Validates: Requirements 9.3, 9.5**
   */
  it('写入→同步→恢复→读取的文件内容应与原始一致', async () => {
    await fc.assert(
      fc.asyncProperty(fileSetArb, async (fileEntries) => {
        // 1. 创建 mock 服务端
        const { fetchMock } = createMockServer(BASE_URL);
        globalThis.fetch = fetchMock;

        // 2. 创建源 WebContainerAdapter 并写入文件
        const { adapter: sourceAdapter } = createMockAdapter();
        for (const [path, content] of fileEntries) {
          await sourceAdapter.writeFile(path, content);
        }

        // 3. 通过 FileSyncService 标记脏文件并同步到服务端
        const syncService = new FileSyncService(BASE_URL, USER_ID);
        for (const [path, content] of fileEntries) {
          syncService.markDirty(path, content);
        }
        await syncService.flush();

        // 4. 创建新的 WebContainerAdapter，通过 restore 恢复文件
        const { adapter: targetAdapter } = createMockAdapter();
        const restoreService = new FileSyncService(BASE_URL, USER_ID);
        await restoreService.restore(targetAdapter);

        // 5. 验证恢复后的文件内容与原始一致
        for (const [path, content] of fileEntries) {
          const restored = await targetAdapter.readFile(path);
          expect(restored).toBe(content);
        }
      }),
      { numRuns: 100 },
    );
  });

  /**
   * 属性 13b: 对于任意单个文件路径和内容，
   * round-trip 后内容应完全保持不变（单文件简化验证）。
   *
   * **Validates: Requirements 9.3, 9.5**
   */
  it('单文件 round-trip 内容应保持不变', async () => {
    await fc.assert(
      fc.asyncProperty(filePathArb, fileContentArb, async (filePath, fileContent) => {
        const { fetchMock } = createMockServer(BASE_URL);
        globalThis.fetch = fetchMock;

        // 写入
        const { adapter: source } = createMockAdapter();
        await source.writeFile(filePath, fileContent);

        // 同步
        const sync = new FileSyncService(BASE_URL, USER_ID);
        sync.markDirty(filePath, fileContent);
        await sync.flush();

        // 恢复
        const { adapter: target } = createMockAdapter();
        const restore = new FileSyncService(BASE_URL, USER_ID);
        await restore.restore(target);

        // 验证
        const result = await target.readFile(filePath);
        expect(result).toBe(fileContent);
      }),
      { numRuns: 100 },
    );
  });

  /**
   * 属性 13c: 对于任意文件集合，多次同步（增量更新）后恢复，
   * 最终内容应反映最后一次写入的值。
   *
   * **Validates: Requirements 9.3, 9.5**
   */
  it('多次增量同步后恢复应反映最新内容', async () => {
    await fc.assert(
      fc.asyncProperty(
        filePathArb,
        fileContentArb,
        fileContentArb,
        async (filePath, contentV1, contentV2) => {
          const { fetchMock } = createMockServer(BASE_URL);
          globalThis.fetch = fetchMock;

          const { adapter: source } = createMockAdapter();

          // 第一次写入并同步
          await source.writeFile(filePath, contentV1);
          const sync = new FileSyncService(BASE_URL, USER_ID);
          sync.markDirty(filePath, contentV1);
          await sync.flush();

          // 第二次写入并同步（覆盖）
          await source.writeFile(filePath, contentV2);
          sync.markDirty(filePath, contentV2);
          await sync.flush();

          // 恢复到新实例
          const { adapter: target } = createMockAdapter();
          const restore = new FileSyncService(BASE_URL, USER_ID);
          await restore.restore(target);

          // 应该是最新版本
          const result = await target.readFile(filePath);
          expect(result).toBe(contentV2);
        },
      ),
      { numRuns: 100 },
    );
  });
});
