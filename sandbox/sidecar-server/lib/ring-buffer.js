// ---------------------------------------------------------------------------
// RingBuffer - detach 期间缓冲 CLI 输出
// ---------------------------------------------------------------------------

export class RingBuffer {
  constructor(maxBytes) {
    this._maxBytes = maxBytes;
    this._chunks = [];
    this._totalBytes = 0;
    this._droppedBytes = 0;
  }

  push(chunk) {
    const len = Buffer.byteLength(chunk, 'utf-8');
    this._chunks.push(chunk);
    this._totalBytes += len;
    while (this._totalBytes > this._maxBytes && this._chunks.length > 1) {
      const removed = this._chunks.shift();
      const removedLen = Buffer.byteLength(removed, 'utf-8');
      this._totalBytes -= removedLen;
      this._droppedBytes += removedLen;
    }
  }

  drain() {
    const result = { chunks: this._chunks, droppedBytes: this._droppedBytes };
    this._chunks = [];
    this._totalBytes = 0;
    this._droppedBytes = 0;
    return result;
  }

  clear() {
    this._chunks = [];
    this._totalBytes = 0;
    this._droppedBytes = 0;
  }

  get totalBytes() {
    return this._totalBytes;
  }
  get droppedBytes() {
    return this._droppedBytes;
  }
}
