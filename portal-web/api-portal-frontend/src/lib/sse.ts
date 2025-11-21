// SSE 流式响应处理

// 旧格式消息（保留兼容）
export interface SSEMessage {
  status: 'start' | 'chunk' | 'complete' | 'error';
  chatId?: string;
  content?: string;
  fullContent?: string;
  message?: string; // 错误消息
  code?: string;
}

// OpenAI 格式消息
export interface OpenAIChunk {
  id: string;
  object: string;
  created: number;
  model: string;
  choices: Array<{
    delta: {
      content?: string;
    };
    index: number;
    finish_reason?: string;
  }>;
  usage?: {
    prompt_tokens: number;
    completion_tokens: number;
    total_tokens: number;
    prompt_tokens_details?: {
      cached_tokens: number;
    };
  };
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
  let chatId = '';
  let fullContent = '';

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
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim();

          // 检查是否是结束标志
          if (data === '[DONE]') {
            // 流结束，调用 onComplete
            if (fullContent && chatId) {
              callbacks.onComplete?.(fullContent, chatId);
            }
            break;
          }

          try {
            const message = JSON.parse(data);

            // 检查是否是旧格式（带 status 字段）
            if ('status' in message) {
              const oldMessage = message as SSEMessage;

              switch (oldMessage.status) {
                case 'start':
                  if (oldMessage.chatId) {
                    chatId = oldMessage.chatId;
                    callbacks.onStart?.(oldMessage.chatId);
                  }
                  break;

                case 'chunk':
                  if (oldMessage.content && oldMessage.chatId) {
                    fullContent += oldMessage.content;
                    callbacks.onChunk?.(oldMessage.content, oldMessage.chatId);
                  }
                  break;

                case 'complete':
                  if (oldMessage.fullContent && oldMessage.chatId) {
                    callbacks.onComplete?.(oldMessage.fullContent, oldMessage.chatId);
                  }
                  break;

                case 'error':
                  callbacks.onError?.(oldMessage.message || 'Unknown error', oldMessage.code);
                  break;
              }
            }
            // OpenAI 格式
            else if ('object' in message && message.object === 'chat.completion.chunk') {
              const chunk = message as OpenAIChunk;

              // 首次收到时，保存 chatId
              if (!chatId && chunk.id) {
                chatId = chunk.id;
                callbacks.onStart?.(chunk.id);
              }

              // 处理内容块
              if (chunk.choices && chunk.choices.length > 0) {
                const choice = chunk.choices[0];
                if (choice.delta?.content) {
                  fullContent += choice.delta.content;
                  callbacks.onChunk?.(choice.delta.content, chatId || chunk.id);
                }
              }

              // 如果有 usage 信息，说明是完成事件（不需要处理，等待 [DONE]）
              // finish_reason 为 "stop" 也表示这是最后一个内容块
            }
          } catch (error) {
            console.error('Failed to parse SSE message:', error, 'Data:', data);
          }
        }
      }
    }
  } finally {
    reader.releaseLock();
  }
}
