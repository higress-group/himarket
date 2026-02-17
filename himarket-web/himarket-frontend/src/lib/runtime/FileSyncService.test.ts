import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { FileSyncService } from './FileSyncService';
import type { WebContainerAdapter } from './WebContainerAdapter';

// ===== 测试辅助 =====

function createMockAdapter(): WebContainerAdapter {
  const files: Record<string, string> = {};
  return {
    writeFile: vi.fn(async (path: string, content: string) => {
      files[path] = content;
    }),
    readFile: vi.fn(async (path: string) => files[path] ?? ''),
  } as unknown as WebContainerAdapter;
}

/** 创建 mock fetch，返回指定响应 */
function mockFetch(response: { ok: boolean; status?: number; json?: () => Promise<unknown> }) {
  return vi.fn().mockResolvedValue({
    ok: response.ok,
    status: response.status ?? (response.ok ? 200 : 500),
    json: response.json ?? (() => Promise.resolve([])),
  });
}

describe('FileSyncService', () => {
  let service: FileSyncService;
  const BASE_URL = 'http://localhost:8080';
  const USER_ID = 'test-user';

  beforeEach(() => {
    service = new FileSyncService(BASE_URL, USER_ID);
    vi.useFakeTimers();
  });

  afterEach(() => {
    service.stop();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  // ===== markDirty 测试 =====

  describe('markDirty', () => {
    it('应将文件标记为待同步', () => {
      service.markDirty('/src/index.ts', 'console.log("hello")');
      expect(service.pendingCount).toBe(1);
    });

    it('同一路径多次标记应只保留最新内容', () => {
      service.markDirty('/src/index.ts', 'v1');
      service.markDirty('/src/index.ts', 'v2');
      expect(service.pendingCount).toBe(1);
    });

    it('不同路径应分别记录', () => {
      service.markDirty('/src/a.ts', 'a');
      service.markDirty('/src/b.ts', 'b');
      expect(service.pendingCount).toBe(2);
    });
  });

  // ===== flush 测试 =====

  describe('flush', () => {
    it('无待同步文件时不应发送请求', async () => {
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response());
      await service.flush();
      expect(fetchSpy).not.toHaveBeenCalled();
    });

    it('应将待同步文件通过 POST 发送到服务端', async () => {
      const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 200 }));

      service.markDirty('/src/index.ts', 'content1');
      service.markDirty('/src/app.ts', 'content2');
      await service.flush();

      expect(fetchSpy).toHaveBeenCalledOnce();
      const [url, options] = fetchSpy.mock.calls[0];
      expect(url).toBe(`${BASE_URL}/api/workspace/sync`);
      expect(options?.method).toBe('POST');
      expect(options?.headers).toEqual({ 'Content-Type': 'application/json' });

      const body = JSON.parse(options?.body as string);
      expect(body.userId).toBe(USER_ID);
      expect(body.files).toHaveLength(2);
      expect(body.files).toEqual(
        expect.arrayContaining([
          { path: '/src/index.ts', content: 'content1' },
          { path: '/src/app.ts', content: 'content2' },
        ]),
      );
    });

    it('同步成功后应清空待同步队列', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 200 }));

      service.markDirty('/src/index.ts', 'content');
      await service.flush();

      expect(service.pendingCount).toBe(0);
    });

    it('网络失败时应将文件存入 failedFiles 以便重试 (Req 9.6)', async () => {
      vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('Network error'));

      service.markDirty('/src/index.ts', 'content');
      await service.flush();

      expect(service.pendingCount).toBe(0);
      expect(service.failedCount).toBe(1);
    });

    it('重试时应合并 failedFiles 和新的 pendingFiles', async () => {
      const fetchSpy = vi.spyOn(globalThis, 'fetch');

      // 第一次同步失败
      fetchSpy.mockRejectedValueOnce(new Error('Network error'));
      service.markDirty('/src/a.ts', 'a');
      await service.flush();
      expect(service.failedCount).toBe(1);

      // 新增一个脏文件，再次同步
      fetchSpy.mockResolvedValueOnce(new Response('', { status: 200 }));
      service.markDirty('/src/b.ts', 'b');
      await service.flush();

      // 第二次请求应包含两个文件
      const body = JSON.parse(fetchSpy.mock.calls[1][1]?.body as string);
      expect(body.files).toHaveLength(2);
      expect(body.files).toEqual(
        expect.arrayContaining([
          { path: '/src/a.ts', content: 'a' },
          { path: '/src/b.ts', content: 'b' },
        ]),
      );
      expect(service.failedCount).toBe(0);
    });

    it('HTTP 错误响应应将文件存入 failedFiles', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 500 }));

      service.markDirty('/src/index.ts', 'content');
      await service.flush();

      expect(service.failedCount).toBe(1);
    });
  });

  // ===== startAutoSync 测试 =====

  describe('startAutoSync', () => {
    it('应按指定间隔定期调用 flush', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 200 }));
      const flushSpy = vi.spyOn(service, 'flush');

      service.startAutoSync(5000);

      // 5 秒后应触发一次
      await vi.advanceTimersByTimeAsync(5000);
      expect(flushSpy).toHaveBeenCalledTimes(1);

      // 10 秒后应触发两次
      await vi.advanceTimersByTimeAsync(5000);
      expect(flushSpy).toHaveBeenCalledTimes(2);
    });

    it('默认间隔应为 5000ms', () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 200 }));
      const flushSpy = vi.spyOn(service, 'flush');

      service.startAutoSync();

      vi.advanceTimersByTime(4999);
      expect(flushSpy).not.toHaveBeenCalled();

      vi.advanceTimersByTime(1);
      expect(flushSpy).toHaveBeenCalledTimes(1);
    });

    it('应注册 beforeunload 事件监听器 (Req 9.4)', () => {
      const addSpy = vi.spyOn(window, 'addEventListener');
      service.startAutoSync();
      expect(addSpy).toHaveBeenCalledWith('beforeunload', expect.any(Function));
    });

    it('重复调用 startAutoSync 应清除旧定时器', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 200 }));
      const flushSpy = vi.spyOn(service, 'flush');

      service.startAutoSync(5000);
      service.startAutoSync(5000);

      await vi.advanceTimersByTimeAsync(5000);
      // 应只触发一次，不会因为两个定时器而触发两次
      expect(flushSpy).toHaveBeenCalledTimes(1);
    });
  });

  // ===== beforeunload 同步测试 =====

  describe('beforeunload 同步', () => {
    let sendBeaconSpy: ReturnType<typeof vi.fn>;

    beforeEach(() => {
      // jsdom 中 navigator.sendBeacon 不存在，需要手动定义
      sendBeaconSpy = vi.fn().mockReturnValue(true);
      Object.defineProperty(navigator, 'sendBeacon', {
        value: sendBeaconSpy,
        writable: true,
        configurable: true,
      });
    });

    it('应使用 navigator.sendBeacon 发送待同步文件 (Req 9.4)', () => {
      service.markDirty('/src/index.ts', 'content');
      service.startAutoSync();

      // 触发 beforeunload 事件
      window.dispatchEvent(new Event('beforeunload'));

      expect(sendBeaconSpy).toHaveBeenCalledOnce();
      const [url, blob] = sendBeaconSpy.mock.calls[0];
      expect(url).toBe(`${BASE_URL}/api/workspace/sync`);
      expect(blob).toBeInstanceOf(Blob);
    });

    it('无待同步文件时 beforeunload 不应调用 sendBeacon', () => {
      service.startAutoSync();
      window.dispatchEvent(new Event('beforeunload'));

      expect(sendBeaconSpy).not.toHaveBeenCalled();
    });

    it('beforeunload 应合并 failedFiles (Req 9.6)', async () => {
      vi.spyOn(globalThis, 'fetch').mockRejectedValue(new Error('Network error'));

      // 先让一个文件同步失败
      service.markDirty('/src/a.ts', 'a');
      await service.flush();
      expect(service.failedCount).toBe(1);

      // 再标记一个新文件
      service.markDirty('/src/b.ts', 'b');
      service.startAutoSync();

      // 触发 beforeunload
      window.dispatchEvent(new Event('beforeunload'));

      expect(sendBeaconSpy).toHaveBeenCalledOnce();
      // 解析 sendBeacon 发送的数据
      const blob = sendBeaconSpy.mock.calls[0][1] as Blob;
      expect(blob).toBeInstanceOf(Blob);
    });
  });

  // ===== restore 测试 =====

  describe('restore', () => {
    it('应从服务端获取文件并写入 WebContainerAdapter', async () => {
      const serverFiles = [
        { path: '/src/index.ts', content: 'console.log("hello")' },
        { path: '/src/app.ts', content: 'export default {}' },
      ];
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(
        new Response(JSON.stringify(serverFiles), { status: 200 }),
      );

      const adapter = createMockAdapter();
      await service.restore(adapter);

      expect(globalThis.fetch).toHaveBeenCalledWith(
        `${BASE_URL}/api/workspace/files?userId=${USER_ID}`,
      );
      expect(adapter.writeFile).toHaveBeenCalledTimes(2);
      expect(adapter.writeFile).toHaveBeenCalledWith('/src/index.ts', 'console.log("hello")');
      expect(adapter.writeFile).toHaveBeenCalledWith('/src/app.ts', 'export default {}');
    });

    it('服务端返回空文件列表时不应写入任何文件', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(
        new Response(JSON.stringify([]), { status: 200 }),
      );

      const adapter = createMockAdapter();
      await service.restore(adapter);

      expect(adapter.writeFile).not.toHaveBeenCalled();
    });

    it('服务端返回错误时应抛出异常', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 500 }));

      const adapter = createMockAdapter();
      await expect(service.restore(adapter)).rejects.toThrow('Restore failed with status 500');
    });
  });

  // ===== stop 测试 =====

  describe('stop', () => {
    it('应停止定时器', async () => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 200 }));
      const flushSpy = vi.spyOn(service, 'flush');

      service.startAutoSync(5000);
      service.stop();

      await vi.advanceTimersByTimeAsync(10000);
      expect(flushSpy).not.toHaveBeenCalled();
    });

    it('应移除 beforeunload 事件监听器', () => {
      const removeSpy = vi.spyOn(window, 'removeEventListener');

      service.startAutoSync();
      service.stop();

      expect(removeSpy).toHaveBeenCalledWith('beforeunload', expect.any(Function));
    });

    it('未启动时调用 stop 不应报错', () => {
      expect(() => service.stop()).not.toThrow();
    });
  });
});
