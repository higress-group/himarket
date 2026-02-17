import { describe, it, expect, vi, beforeEach } from 'vitest';
import fc from 'fast-check';
import { WebContainerAdapter } from './WebContainerAdapter';

// ===== Mock WebContainer API =====

function createMockProcess(outputChunks: string[] = []) {
  const encoder = new TextEncoder();
  let inputWritten: string[] = [];

  const readableStream = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of outputChunks) {
        controller.enqueue(encoder.encode(chunk));
      }
      controller.close();
    },
  });

  const writableStream = new WritableStream<Uint8Array>({
    write(chunk) {
      inputWritten.push(new TextDecoder().decode(chunk));
    },
  });

  return {
    process: {
      output: readableStream,
      input: writableStream,
      kill: vi.fn(),
    },
    getInputWritten: () => inputWritten,
  };
}

function createMockContainer(
  process: ReturnType<typeof createMockProcess>['process'],
  files: Record<string, string> = {},
) {
  return {
    spawn: vi.fn().mockResolvedValue(process),
    teardown: vi.fn().mockResolvedValue(undefined),
    fs: {
      readFile: vi.fn((path: string) => {
        if (path in files) return Promise.resolve(files[path]);
        return Promise.reject(new Error(`File not found: ${path}`));
      }),
      writeFile: vi.fn((path: string, content: string) => {
        files[path] = content;
        return Promise.resolve();
      }),
      readdir: vi.fn((path: string) => {
        const entries = Object.keys(files)
          .filter((f) => f.startsWith(path === '/' ? '/' : path + '/'))
          .map((f) => f.slice(path === '/' ? 1 : path.length + 1).split('/')[0]);
        return Promise.resolve([...new Set(entries)]);
      }),
    },
  };
}

// ===== 行分割解析单元测试 =====

describe('WebContainerAdapter - processChunk 行分割解析', () => {
  let adapter: WebContainerAdapter;

  beforeEach(() => {
    adapter = new WebContainerAdapter();
  });

  it('应正确分割单行消息', () => {
    const messages: string[] = [];
    adapter.onMessage((msg) => messages.push(msg));

    adapter.processChunk('{"jsonrpc":"2.0","id":1}\n');

    expect(messages).toEqual(['{"jsonrpc":"2.0","id":1}']);
  });

  it('应正确分割多行消息', () => {
    const messages: string[] = [];
    adapter.onMessage((msg) => messages.push(msg));

    adapter.processChunk('line1\nline2\nline3\n');

    expect(messages).toEqual(['line1', 'line2', 'line3']);
  });

  it('应缓冲不完整的行，等待后续 chunk', () => {
    const messages: string[] = [];
    adapter.onMessage((msg) => messages.push(msg));

    adapter.processChunk('{"jsonrpc":"2.0",');
    expect(messages).toEqual([]);

    adapter.processChunk('"id":1}\n');
    expect(messages).toEqual(['{"jsonrpc":"2.0","id":1}']);
  });

  it('应处理跨多个 chunk 的行', () => {
    const messages: string[] = [];
    adapter.onMessage((msg) => messages.push(msg));

    adapter.processChunk('part1');
    adapter.processChunk('part2');
    adapter.processChunk('part3\n');

    expect(messages).toEqual(['part1part2part3']);
  });

  it('应忽略空行', () => {
    const messages: string[] = [];
    adapter.onMessage((msg) => messages.push(msg));

    adapter.processChunk('line1\n\n\nline2\n');

    expect(messages).toEqual(['line1', 'line2']);
  });

  it('应处理混合完整行和不完整行', () => {
    const messages: string[] = [];
    adapter.onMessage((msg) => messages.push(msg));

    adapter.processChunk('complete1\ncomplete2\nincomplete');
    expect(messages).toEqual(['complete1', 'complete2']);

    adapter.processChunk('_rest\n');
    expect(messages).toEqual(['complete1', 'complete2', 'incomplete_rest']);
  });
});

// ===== send 方法单元测试 =====

describe('WebContainerAdapter - send', () => {
  it('未启动时 send 应抛出错误', () => {
    const adapter = new WebContainerAdapter();
    expect(() => adapter.send('test')).toThrow('WebContainer not started');
  });
});

// ===== onExit 进程退出检测单元测试 (Req 8.4) =====

describe('WebContainerAdapter - onExit 进程退出检测', () => {
  it('应支持注册 onExit 回调', () => {
    const adapter = new WebContainerAdapter();
    const calls: number[] = [];
    adapter.onExit((code) => calls.push(code));

    // 验证回调已注册到内部数组
    expect((adapter as unknown as { exitCallbacks: unknown[] }).exitCallbacks.length).toBe(1);
  });

  it('应支持注册多个 onExit 回调', () => {
    const adapter = new WebContainerAdapter();
    adapter.onExit(() => {});
    adapter.onExit(() => {});

    expect((adapter as unknown as { exitCallbacks: unknown[] }).exitCallbacks.length).toBe(2);
  });

  it('close 后应清除 exitCallbacks', async () => {
    const adapter = new WebContainerAdapter();
    adapter.onExit(() => {});
    adapter.onExit(() => {});

    expect((adapter as unknown as { exitCallbacks: unknown[] }).exitCallbacks.length).toBe(2);

    await adapter.close();

    expect((adapter as unknown as { exitCallbacks: unknown[] }).exitCallbacks.length).toBe(0);
  });
});

// ===== 文件操作单元测试 =====

describe('WebContainerAdapter - 文件操作', () => {
  it('未启动时 readFile 应抛出错误', async () => {
    const adapter = new WebContainerAdapter();
    await expect(adapter.readFile('/test.txt')).rejects.toThrow('WebContainer not started');
  });

  it('未启动时 writeFile 应抛出错误', async () => {
    const adapter = new WebContainerAdapter();
    await expect(adapter.writeFile('/test.txt', 'content')).rejects.toThrow(
      'WebContainer not started',
    );
  });

  it('未启动时 listDirectory 应抛出错误', async () => {
    const adapter = new WebContainerAdapter();
    await expect(adapter.listDirectory('/')).rejects.toThrow('WebContainer not started');
  });
});

// ===== 属性测试：行分割解析 =====

describe('WebContainerAdapter - 行分割属性测试', () => {
  /**
   * Property: 对于任意非空行列表，将它们用 \n 连接后传入 processChunk，
   * 收到的消息应与原始行列表完全一致。
   *
   * **Validates: Requirements 4.4**
   */
  it('单次 chunk 包含完整行时，消息应与原始行一一对应', () => {
    // 生成不含换行符的非空字符串作为行内容
    const lineArb = fc.stringMatching(/^[^\n\r]+$/, { minLength: 1, maxLength: 200 });
    fc.assert(
      fc.property(
        fc.array(lineArb, { minLength: 1, maxLength: 50 }),
        (lines) => {
          const adapter = new WebContainerAdapter();
          const received: string[] = [];
          adapter.onMessage((msg) => received.push(msg));

          const chunk = lines.join('\n') + '\n';
          adapter.processChunk(chunk);

          expect(received).toEqual(lines);
        },
      ),
      { numRuns: 100 },
    );
  });

  /**
   * Property: 对于任意非空行列表，无论将拼接后的文本在任意位置切分为多个 chunk，
   * 最终收到的消息应与原始行列表完全一致。
   * 这验证了行缓冲机制在任意 chunk 边界下的正确性。
   *
   * **Validates: Requirements 4.4**
   */
  it('任意 chunk 切分方式下，消息应与原始行一一对应', () => {
    const lineArb = fc.stringMatching(/^[^\n\r]+$/, { minLength: 1, maxLength: 100 });
    fc.assert(
      fc.property(
        fc.array(lineArb, { minLength: 1, maxLength: 20 }),
        // 生成切分点数量
        fc.integer({ min: 0, max: 10 }),
        fc.infiniteStream(fc.double({ min: 0, max: 1, noNaN: true })),
        (lines, numSplits, splitPositions) => {
          const fullText = lines.join('\n') + '\n';
          if (fullText.length === 0) return;

          // 生成随机切分点
          const splits: number[] = [];
          for (let i = 0; i < numSplits && i < fullText.length; i++) {
            const pos = Math.floor(splitPositions.next().value * fullText.length);
            splits.push(pos);
          }
          splits.sort((a, b) => a - b);

          // 按切分点将文本分成多个 chunk
          const chunks: string[] = [];
          let prev = 0;
          for (const pos of splits) {
            if (pos > prev) {
              chunks.push(fullText.slice(prev, pos));
              prev = pos;
            }
          }
          if (prev < fullText.length) {
            chunks.push(fullText.slice(prev));
          }

          const adapter = new WebContainerAdapter();
          const received: string[] = [];
          adapter.onMessage((msg) => received.push(msg));

          for (const chunk of chunks) {
            adapter.processChunk(chunk);
          }

          expect(received).toEqual(lines);
        },
      ),
      { numRuns: 100 },
    );
  });
});
