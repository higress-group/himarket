import type { WebContainerAdapter } from './WebContainerAdapter';

/**
 * WebContainer 文件同步服务
 *
 * 将浏览器内存文件系统中的文件同步到服务端持久化存储。
 * 支持脏文件标记、定时同步（默认 5 秒）、立即同步、
 * beforeunload 事件触发同步、网络中断重试。
 *
 * Requirements: 9.3, 9.4, 9.6
 */
export class FileSyncService {
  private baseUrl: string;
  private userId: string;
  /** 待同步的脏文件：path → content */
  private pendingFiles: Map<string, string> = new Map();
  private syncTimer: ReturnType<typeof setInterval> | null = null;
  /** 网络中断时记录的未同步文件，用于恢复后重试 (Req 9.6) */
  private failedFiles: Map<string, string> = new Map();
  /** beforeunload 事件处理器引用，用于 stop 时移除 */
  private beforeUnloadHandler: (() => void) | null = null;

  constructor(baseUrl: string, userId: string) {
    this.baseUrl = baseUrl;
    this.userId = userId;
  }

  /**
   * 标记文件为待同步（脏文件）。
   * 每次文件内容变更时调用，将文件路径和最新内容加入待同步队列。
   */
  markDirty(path: string, content: string): void {
    this.pendingFiles.set(path, content);
  }

  /**
   * 启动定期自动同步。
   * 默认每 5 秒执行一次 flush，同时注册 beforeunload 事件
   * 确保浏览器标签页关闭前触发同步 (Req 9.4)。
   */
  startAutoSync(intervalMs = 5000): void {
    if (this.syncTimer) {
      clearInterval(this.syncTimer);
    }
    this.syncTimer = setInterval(() => this.flush(), intervalMs);

    // 注册 beforeunload 事件，标签页关闭前触发同步 (Req 9.4)
    this.beforeUnloadHandler = () => {
      this.flushBeforeUnload();
    };
    window.addEventListener('beforeunload', this.beforeUnloadHandler);
  }

  /**
   * 立即同步所有待同步文件到服务端。
   * 合并 pendingFiles 和 failedFiles（网络中断重试），
   * 通过 POST /api/workspace/sync 批量上传。
   * 网络失败时将文件存入 failedFiles 等待下次重试 (Req 9.6)。
   */
  async flush(): Promise<void> {
    // 合并 failedFiles 到 pendingFiles（重试之前失败的文件）
    for (const [path, content] of this.failedFiles) {
      if (!this.pendingFiles.has(path)) {
        this.pendingFiles.set(path, content);
      }
    }
    this.failedFiles.clear();

    if (this.pendingFiles.size === 0) return;

    const files = Array.from(this.pendingFiles.entries()).map(([path, content]) => ({
      path,
      content,
    }));
    // 先清空 pendingFiles，如果请求失败会移入 failedFiles
    this.pendingFiles.clear();

    try {
      const response = await fetch(`${this.baseUrl}/api/workspace/sync`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: this.userId, files }),
      });
      if (!response.ok) {
        throw new Error(`Sync failed with status ${response.status}`);
      }
    } catch {
      // 网络中断或请求失败，记录未同步文件以便后续重试 (Req 9.6)
      for (const file of files) {
        this.failedFiles.set(file.path, file.content);
      }
    }
  }

  /**
   * beforeunload 时使用 navigator.sendBeacon 发送关键文件。
   * sendBeacon 在页面卸载期间可靠地发送少量数据，
   * 不会阻塞页面关闭流程。
   */
  private flushBeforeUnload(): void {
    // 合并 failedFiles
    for (const [path, content] of this.failedFiles) {
      if (!this.pendingFiles.has(path)) {
        this.pendingFiles.set(path, content);
      }
    }

    if (this.pendingFiles.size === 0) return;

    const files = Array.from(this.pendingFiles.entries()).map(([path, content]) => ({
      path,
      content,
    }));
    this.pendingFiles.clear();
    this.failedFiles.clear();

    // 使用 sendBeacon 确保页面关闭时数据能发送出去
    const payload = JSON.stringify({ userId: this.userId, files });
    navigator.sendBeacon(
      `${this.baseUrl}/api/workspace/sync`,
      new Blob([payload], { type: 'application/json' }),
    );
  }

  /**
   * 从服务端恢复文件到 WebContainerAdapter。
   * 获取用户之前持久化的文件列表，逐个写入 WebContainer 内存文件系统 (Req 9.5)。
   */
  async restore(adapter: WebContainerAdapter): Promise<void> {
    const resp = await fetch(`${this.baseUrl}/api/workspace/files?userId=${this.userId}`);
    if (!resp.ok) {
      throw new Error(`Restore failed with status ${resp.status}`);
    }
    const files: { path: string; content: string }[] = await resp.json();
    for (const file of files) {
      await adapter.writeFile(file.path, file.content);
    }
  }

  /**
   * 停止自动同步，清理定时器和事件监听器。
   */
  stop(): void {
    if (this.syncTimer) {
      clearInterval(this.syncTimer);
      this.syncTimer = null;
    }
    if (this.beforeUnloadHandler) {
      window.removeEventListener('beforeunload', this.beforeUnloadHandler);
      this.beforeUnloadHandler = null;
    }
  }

  /** 获取当前待同步文件数量（用于测试和监控） */
  get pendingCount(): number {
    return this.pendingFiles.size;
  }

  /** 获取当前失败待重试文件数量（用于测试和监控） */
  get failedCount(): number {
    return this.failedFiles.size;
  }
}
