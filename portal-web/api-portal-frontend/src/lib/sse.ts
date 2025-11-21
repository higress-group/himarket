// SSE 流式响应处理

export interface SSEMessage {
  status: 'start' | 'chunk' | 'complete' | 'error';
  chatId?: string;
  content?: string;
  fullContent?: string;
  error?: string;
  code?: string;
}

export interface SSEOptions {
  onStart?: (chatId: string) => void;
  onChunk?: (content: string, chatId: string) => void;
  onComplete?: (fullContent: string, chatId: string) => void;
  onError?: (error: string, code?: string) => void;
}

export async function handleSSEStream(
  url: string,
  options: RequestInit,
  callbacks: SSEOptions
): Promise<void> {
  const response = await fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      'Accept': 'text/event-stream',
    },
  });

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  const reader = response.body?.getReader();
  if (!reader) {
    throw new Error('Response body is null');
  }

  const decoder = new TextDecoder();
  let buffer = '';

  try {
    while (true) {
      const { done, value } = await reader.read();

      if (done) {
        break;
      }

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (line.startsWith('data: ')) {
          const data = line.slice(6);

          // 检查是否是结束标志
          if (data === '[DONE]') {
            break;
          }

          try {
            const message: SSEMessage = JSON.parse(data);

            switch (message.status) {
              case 'start':
                if (message.chatId) {
                  callbacks.onStart?.(message.chatId);
                }
                break;

              case 'chunk':
                if (message.content && message.chatId) {
                  callbacks.onChunk?.(message.content, message.chatId);
                }
                break;

              case 'complete':
                if (message.fullContent && message.chatId) {
                  callbacks.onComplete?.(message.fullContent, message.chatId);
                }
                break;

              case 'error':
                callbacks.onError?.(message.error || 'Unknown error', message.code);
                break;
            }
          } catch (error) {
            console.error('Failed to parse SSE message:', error);
          }
        }
      }
    }
  } finally {
    reader.releaseLock();
  }
}
