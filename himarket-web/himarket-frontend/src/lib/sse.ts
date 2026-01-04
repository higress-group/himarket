// SSE stream response handler

import type { IToolCall, IToolResponse, IChatUsage } from './apis/chat';

// @chat-legacy: Legacy type definitions, can be removed after full migration
/*
// Legacy message format (for backward compatibility)
export interface SSEMessage {
  status: 'start' | 'chunk' | 'complete' | 'error';
  chatId?: string;
  content?: string;
  fullContent?: string;
  message?: string; // Error message
  code?: string;
}

// Legacy message type, use ChatEventType instead
export type SSEMsgType = 'USER' | 'TOOL_CALL' | 'TOOL_RESPONSE' | 'ANSWER' | 'STOP' | 'ERROR';

// Legacy message structure, use ChatEvent instead
export interface SSENewMessage {
  chatId: string;
  msgType: SSEMsgType;
  content: string | IToolCall | IToolResponse | null;
  chatUsage: IChatUsage | null;
  error?: string;      // Error type
  message?: string;    // Error message
}
*/

// Chat Event Type
export type ChatEventType = 
  | 'START'        // Stream started
  | 'ASSISTANT'    // Assistant response
  | 'THINKING'     // Thinking process
  | 'TOOL_CALL'    // Tool call request
  | 'TOOL_RESULT'  // Tool execution result
  | 'DONE'         // Stream completed
  | 'ERROR';       // Error occurred

// Chat Event Structure
export interface ChatEvent {
  chatId: string;
  type: ChatEventType;
  content?: string | IToolCall | IToolResponse | null;
  usage?: IChatUsage;
  error?: string;
  message?: string;
}

// @chat-legacy: OpenAI format type definition, can be removed if not using OpenAI API directly
/*
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
*/

export interface SSEUsage {
  first_byte_timeout?: number;
  prompt_tokens: number;
  completion_tokens: number;
  total_tokens: number;
  elapsed_time?: number;
}

export interface SSEOptions {
  onStart?: (chatId: string) => void;
  onChunk?: (content: string, chatId: string) => void;
  onToolCall?: (toolCall: IToolCall, chatId: string, usage?: IChatUsage) => void;
  onToolResponse?: (toolResponse: IToolResponse, chatId: string, usage?: IChatUsage) => void;
  onComplete?: (fullContent: string, chatId: string, usage?: SSEUsage) => void;
  onError?: (error: string, code?: string, httpStatus?: number) => void;
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
    const status = response.status;

    // Handle 403 error: clear token and redirect to login
    if (status === 403) {
      localStorage.removeItem('access_token');
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
      return;
    }

    // Handle other HTTP errors via onError callback
    const errorMessage = `HTTP error! status: ${status}`;
    callbacks.onError?.(errorMessage, undefined, status);
    return;
  }

  const reader = response.body?.getReader();
  if (!reader) {
    throw new Error('Response body is null');
  }

  const decoder = new TextDecoder();
  let buffer = '';
  let chatId = '';
  let fullContent = '';
  let usage: SSEUsage | undefined;

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

          // Check if it's the end marker
          if (data === '[DONE]') {
            // Stream ended, call onComplete
            if (fullContent && chatId) {
              callbacks.onComplete?.(fullContent, chatId, usage);
            }
            break;
          }

          try {
            const message = JSON.parse(data);

            // Check if it's the new format (has type field, not msgType)
            if ('type' in message && !('msgType' in message)) {
              const event = message as ChatEvent;

              // Update chatId
              if (event.chatId && !chatId) {
                chatId = event.chatId;
              }

              switch (event.type) {
                case 'START':
                  // Corresponds to legacy USER
                  callbacks.onStart?.(event.chatId);
                  break;

                case 'ASSISTANT':
                  // Corresponds to legacy ANSWER
                  if (typeof event.content === 'string' && event.chatId) {
                    fullContent += event.content;
                    callbacks.onChunk?.(event.content, event.chatId);
                  }
                  break;

                case 'THINKING':
                  // Thinking process - temporarily ignored
                  break;

                case 'TOOL_CALL':
                  // Tool call
                  if (event.content && typeof event.content === 'object') {
                    callbacks.onToolCall?.(
                      event.content as IToolCall,
                      event.chatId,
                      event.usage || undefined
                    );
                  }
                  break;

                case 'TOOL_RESULT':
                  // Corresponds to legacy TOOL_RESPONSE
                  if (event.content && typeof event.content === 'object') {
                    callbacks.onToolResponse?.(
                      event.content as IToolResponse,
                      event.chatId,
                      event.usage || undefined
                    );
                  }
                  break;

                case 'DONE':
                  // Corresponds to legacy STOP
                  if (event.chatId) {
                    // Convert usage fields (use camelCase)
                    const sseUsage: SSEUsage | undefined = event.usage ? {
                      first_byte_timeout: event.usage.firstByteTimeout ?? event.usage.first_byte_timeout ?? undefined,
                      prompt_tokens: event.usage.inputTokens ?? event.usage.prompt_tokens ?? 0,
                      completion_tokens: event.usage.outputTokens ?? event.usage.completion_tokens ?? 0,
                      total_tokens: event.usage.totalTokens ?? event.usage.total_tokens ?? 0,
                      elapsed_time: event.usage.elapsedTime ?? event.usage.elapsed_time ?? undefined,
                    } : undefined;
                    callbacks.onComplete?.(fullContent, event.chatId, sseUsage);
                  }
                  break;

                case 'ERROR':
                  // Error event
                  callbacks.onError?.(
                    event.message || "Network error, please retry",
                    event.error
                  );
                  break;
              }
            }
            // @chat-legacy: Legacy format handler (msgType field), can be removed after backend fully migrates
            /* 
            else if ('msgType' in message) {
              const newMessage = message as SSENewMessage;

              // Update chatId
              if (newMessage.chatId && !chatId) {
                chatId = newMessage.chatId;
              }

              switch (newMessage.msgType) {
                case 'USER':
                  // User message start
                  if (newMessage.chatId) {
                    callbacks.onStart?.(newMessage.chatId);
                  }
                  break;

                case 'TOOL_CALL':
                  // MCP tool call
                  if (newMessage.content && typeof newMessage.content === 'object') {
                    callbacks.onToolCall?.(
                      newMessage.content as IToolCall,
                      newMessage.chatId,
                      newMessage.chatUsage || undefined
                    );
                  }
                  break;

                case 'TOOL_RESPONSE':
                  // MCP tool result
                  if (newMessage.content && typeof newMessage.content === 'object') {
                    callbacks.onToolResponse?.(
                      newMessage.content as IToolResponse,
                      newMessage.chatId,
                      newMessage.chatUsage || undefined
                    );
                  }
                  break;

                case 'ANSWER':
                  // Model answer content stream
                  if (typeof newMessage.content === 'string' && newMessage.chatId) {
                    fullContent += newMessage.content;
                    callbacks.onChunk?.(newMessage.content, newMessage.chatId);
                  }
                  break;

                case 'STOP':
                  // Stream response completed
                  if (newMessage.chatId) {
                    // Convert chatUsage to SSEUsage format
                    const sseUsage: SSEUsage | undefined = newMessage.chatUsage ? {
                      first_byte_timeout: newMessage.chatUsage.first_byte_timeout ?? undefined,
                      prompt_tokens: newMessage.chatUsage.prompt_tokens || 0,
                      completion_tokens: newMessage.chatUsage.completion_tokens || 0,
                      total_tokens: newMessage.chatUsage.total_tokens || 0,
                      elapsed_time: newMessage.chatUsage.elapsed_time ?? undefined,
                    } : undefined;
                    callbacks.onComplete?.(fullContent, newMessage.chatId, sseUsage);
                  }
                  break;

                case 'ERROR':
                  // Error event
                  callbacks.onError?.(
                    newMessage.message || "Network error, please retry",
                    newMessage.error
                  );
                  break;
              }
            }
            */
            // @chat-legacy: Legacy format handler (status field), can be removed after backend fully migrates
            /*
            else if ('status' in message) {
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
                    callbacks.onComplete?.(oldMessage.fullContent, oldMessage.chatId, usage);
                  }
                  break;

                case 'error':
                  callbacks.onError?.(oldMessage.message || 'Unknown error', oldMessage.code);
                  break;
              }
            }
            */
            // @chat-legacy: OpenAI format handler, can be removed if not using OpenAI API directly
            /*
            else if ('object' in message && message.object === 'chat.completion.chunk') {
              const chunk = message as OpenAIChunk;

              // Save chatId on first receipt
              if (!chatId && chunk.id) {
                chatId = chunk.id;
                callbacks.onStart?.(chunk.id);
              }

              // Process content chunk
              if (chunk.choices && chunk.choices.length > 0) {
                const choice = chunk.choices[0];
                if (choice.delta?.content) {
                  fullContent += choice.delta.content;
                  callbacks.onChunk?.(choice.delta.content, chatId || chunk.id);
                }
              }

              // Extract usage info (usually in the last chunk)
              if (chunk.usage) {
                usage = chunk.usage;
              }
            }
            */
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
