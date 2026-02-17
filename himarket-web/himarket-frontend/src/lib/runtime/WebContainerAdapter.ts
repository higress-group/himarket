import { WebContainer, type WebContainerProcess } from '@webcontainer/api';

/**
 * WebContainer 运行时适配器
 *
 * 封装 WebContainer API，在浏览器中运行 Node.js CLI Agent。
 * 通过 stdin/stdout 进行 JSON-RPC 行分割消息通信，
 * 通过内存文件系统 API 进行文件操作。
 *
 * Requirements: 4.1, 4.2, 4.3, 4.4, 4.5
 */
export class WebContainerAdapter {
  private container: WebContainer | null = null;
  private process: WebContainerProcess | null = null;
  private messageCallbacks: ((data: string) => void)[] = [];
  private exitCallbacks: ((exitCode: number) => void)[] = [];
  /** stdout 行缓冲区，用于处理跨 chunk 的不完整行 */
  private lineBuffer = '';
  private reading = false;

  /**
   * 启动 WebContainer 并 spawn CLI 进程。
   *
   * 1. 调用 WebContainer.boot() 启动浏览器端 Node.js 环境 (Req 4.2)
   * 2. 调用 container.spawn() 启动 CLI 进程
   * 3. 启动 stdout 读取循环，按行分割 JSON-RPC 消息 (Req 4.4)
   */
  async start(command: string, args: string[]): Promise<void> {
    this.container = await WebContainer.boot();
    this.process = await this.container.spawn(command, args);
    this.reading = true;
    this.readStdout();

    // 监听进程退出事件 (Req 8.4)
    this.process.exit.then((exitCode: number) => {
      this.reading = false;
      for (const cb of this.exitCallbacks) {
        cb(exitCode);
      }
    });
  }

  /**
   * 从 stdout 流中持续读取数据，按行分割后分发给回调。
   *
   * JSON-RPC 消息以换行符分隔，每行是一个完整的 JSON-RPC 消息。
   * 由于 ReadableStream 的 chunk 边界不一定与行边界对齐，
   * 需要维护 lineBuffer 来正确处理跨 chunk 的部分行。
   */
  private async readStdout(): Promise<void> {
    if (!this.process) return;

    const reader = this.process.output.getReader();
    const decoder = new TextDecoder();

    try {
      while (this.reading) {
        const { done, value } = await reader.read();
        if (done) break;

        const text = decoder.decode(value, { stream: true });
        this.processChunk(text);
      }
    } finally {
      reader.releaseLock();
    }
  }

  /**
   * 处理从 stdout 读取的原始文本 chunk。
   *
   * 将 chunk 与 lineBuffer 中的残余数据拼接后按换行符分割。
   * 完整的行（以 \n 结尾的部分）立即分发给回调，
   * 最后一段不完整的数据保留在 lineBuffer 中等待下一个 chunk。
   */
  processChunk(text: string): void {
    this.lineBuffer += text;
    const lines = this.lineBuffer.split('\n');
    // 最后一个元素可能是不完整的行，保留在 buffer 中
    this.lineBuffer = lines.pop() ?? '';
    for (const line of lines) {
      if (line.length > 0) {
        this.dispatchMessage(line);
      }
    }
  }

  private dispatchMessage(line: string): void {
    for (const cb of this.messageCallbacks) {
      cb(line);
    }
  }

  /**
   * 通过 stdin 发送 JSON-RPC 消息到 CLI 进程 (Req 4.3)。
   * 自动追加换行符作为消息分隔符。
   */
  send(data: string): void {
    if (!this.process) {
      throw new Error('WebContainer not started');
    }
    const encoder = new TextEncoder();
    const writer = this.process.input.getWriter();
    writer.write(encoder.encode(data + '\n'));
    writer.releaseLock();
  }

  /**
   * 注册消息回调，当 stdout 收到完整的一行时触发。
   */
  onMessage(callback: (data: string) => void): void {
    this.messageCallbacks.push(callback);
  }

  /**
   * 注册进程退出回调，当 WebContainer 进程退出时触发 (Req 8.4)。
   * exitCode: 进程退出码，非零表示异常退出。
   */
  onExit(callback: (exitCode: number) => void): void {
    this.exitCallbacks.push(callback);
  }

  /**
   * 读取 WebContainer 内存文件系统中的文件 (Req 4.5)。
   */
  async readFile(path: string): Promise<string> {
    if (!this.container) {
      throw new Error('WebContainer not started');
    }
    return this.container.fs.readFile(path, 'utf-8');
  }

  /**
   * 写入文件到 WebContainer 内存文件系统 (Req 4.5)。
   */
  async writeFile(path: string, content: string): Promise<void> {
    if (!this.container) {
      throw new Error('WebContainer not started');
    }
    await this.container.fs.writeFile(path, content);
  }

  /**
   * 列举 WebContainer 内存文件系统中的目录 (Req 4.5)。
   */
  async listDirectory(path: string): Promise<string[]> {
    if (!this.container) {
      throw new Error('WebContainer not started');
    }
    return this.container.fs.readdir(path);
  }

  /**
   * 关闭 CLI 进程并销毁 WebContainer 实例。
   */
  async close(): Promise<void> {
    this.reading = false;
    // 刷新 lineBuffer 中残余的数据
    if (this.lineBuffer.length > 0) {
      this.dispatchMessage(this.lineBuffer);
      this.lineBuffer = '';
    }
    this.process?.kill();
    await this.container?.teardown();
    this.container = null;
    this.process = null;
    this.messageCallbacks = [];
    this.exitCallbacks = [];
  }
}
