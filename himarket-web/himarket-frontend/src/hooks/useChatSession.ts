import { message as antdMessage } from 'antd';
import { useReducer, useState, useRef, useCallback } from 'react';

import { chatReducer, type ChatAction } from './useChatReducer';
import APIs, {
  type IProductConversations,
  type IProductDetail,
  type IAttachment,
} from '../lib/apis';
import { handleSSEStream } from '../lib/sse';
import { generateConversationId, generateQuestionId } from '../lib/uuid';

import type { SSEOptions } from '../lib/sse';
import type { IModelConversation, IMcpToolCall, IMcpToolResponse } from '../types';

// ============ SSE Callbacks Factory ============

interface SSEContext {
  modelId: string;
  conversationId: string;
  questionId: string;
  fullContentRef: { current: string };
  dispatch: React.Dispatch<ChatAction>;
  setIsMcpExecuting: (v: boolean) => void;
}

function createSSECallbacks(ctx: SSEContext): SSEOptions {
  const { conversationId, dispatch, fullContentRef, modelId, questionId, setIsMcpExecuting } = ctx;
  return {
    onChunk: (chunk: string) => {
      fullContentRef.current += chunk;
      dispatch({
        payload: {
          chunk,
          conversationId,
          fullContent: fullContentRef.current,
          modelId,
          questionId,
        },
        type: 'APPEND_CHUNK',
      });
    },
    onComplete: (_content: string, _chatId: string, usage) => {
      setIsMcpExecuting(false);
      dispatch({
        payload: {
          conversationId,
          fullContent: fullContentRef.current,
          modelId,
          questionId,
          usage,
        },
        type: 'COMPLETE',
      });
    },
    onError: (errorMsg: string) => {
      setIsMcpExecuting(false);
      dispatch({
        payload: {
          conversationId,
          errorMsg,
          fullContent: fullContentRef.current,
          modelId,
          questionId,
        },
        type: 'SEND_ERROR',
      });
    },
    onToolCall: (toolCall: IMcpToolCall) => {
      setIsMcpExecuting(true);
      dispatch({
        payload: { conversationId, modelId, questionId, toolCall },
        type: 'ADD_TOOL_CALL',
      });
    },
    onToolResponse: (toolResponse: IMcpToolResponse) => {
      setIsMcpExecuting(false);
      dispatch({
        payload: { conversationId, modelId, questionId, toolResponse },
        type: 'ADD_TOOL_RESPONSE',
      });
    },
  };
}

// ============ SSE Request Helper ============

async function executeSSERequest(
  _modelId: string,
  messagePayload: Record<string, unknown>,
  abortController: AbortController,
  sseCallbacks: SSEOptions,
) {
  const streamUrl = APIs.getChatMessageStreamUrl();
  const accessToken = localStorage.getItem('access_token');
  await handleSSEStream(
    streamUrl,
    {
      body: JSON.stringify(messagePayload),
      headers: {
        Authorization: accessToken ? `Bearer ${accessToken}` : '',
        'Content-Type': 'application/json',
      },
      method: 'POST',
    },
    sseCallbacks,
    abortController.signal,
  );
}

// ============ Hook ============

export function useChatSession() {
  const [state, dispatch] = useReducer(chatReducer, []);
  const [generating, setGenerating] = useState(false);
  const [isMcpExecuting, setIsMcpExecuting] = useState(false);
  const [currentSessionId, setCurrentSessionId] = useState<string>();
  const [sidebarRefreshTrigger, setSidebarRefreshTrigger] = useState(0);
  const abortControllersRef = useRef<AbortController[]>([]);

  // 停止生成
  const handleStop = useCallback(() => {
    abortControllersRef.current.forEach((c) => c.abort());
    abortControllersRef.current = [];
    setGenerating(false);
    setIsMcpExecuting(false);
  }, []);

  // 新建会话
  const handleNewChat = useCallback(() => {
    dispatch({ type: 'RESET' });
    setCurrentSessionId(undefined);
  }, []);

  // 发送消息
  const sendMessage = useCallback(
    async (
      content: string,
      mcps: IProductDetail[],
      enableWebSearch: boolean,
      modelMap: Map<string, IProductDetail>,
      selectedModel: IProductDetail,
      attachments: IAttachment[] = [],
    ) => {
      try {
        setGenerating(true);

        // 如果没有会话，先创建
        let sessionId = currentSessionId;
        if (!sessionId) {
          const sessionResponse = await APIs.createSession({
            name: content.length > 20 ? content.substring(0, 20) + '...' : content,
            products: state.length ? state.map((v) => v.id) : [selectedModel.productId],
            talkType: 'MODEL',
          });
          if (sessionResponse.code === 'SUCCESS') {
            sessionId = sessionResponse.data.sessionId;
            setCurrentSessionId(sessionId);
            setSidebarRefreshTrigger((prev) => prev + 1);
          } else {
            setGenerating(false);
            throw new Error('创建会话失败');
          }
        }

        const conversationId = generateConversationId();
        const questionId = generateQuestionId();

        if (!sessionId) throw new Error('会话ID不存在');

        const modelIds = state.length ? state.map((m) => m.id) : [selectedModel.productId];
        abortControllersRef.current = [];

        const requests = modelIds.map(async (modelId) => {
          const abortController = new AbortController();
          abortControllersRef.current.push(abortController);

          const isSupport = modelMap.get(modelId)?.feature?.modelFeature?.webSearch || false;
          const messagePayload = {
            attachments: attachments.map((a) => ({ attachmentId: a.attachmentId })),
            conversationId,
            enableWebSearch: enableWebSearch ? isSupport : false,
            mcpProducts: mcps.map((mcp) => mcp.productId),
            needMemory: true,
            productId: modelId,
            question: content,
            questionId,
            sessionId,
            stream: true,
          };

          // 添加对话到 state
          dispatch({
            payload: {
              attachments,
              content,
              conversationId,
              modelId,
              questionId,
              selectedModelId: selectedModel.productId,
              sessionId: currentSessionId,
            },
            type: 'ADD_CONVERSATION',
          });

          const fullContentRef = { current: '' };
          const sseCallbacks = createSSECallbacks({
            conversationId,
            dispatch,
            fullContentRef,
            modelId,
            questionId,
            setIsMcpExecuting,
          });

          await executeSSERequest(modelId, messagePayload, abortController, sseCallbacks);
        });

        await Promise.allSettled(requests);
        setGenerating(false);
        abortControllersRef.current = [];
      } catch (error) {
        dispatch({ payload: { errorMsg: '网络错误，请重试' }, type: 'GLOBAL_ERROR' });
        setGenerating(false);
        console.error('Failed to send message:', error);
      }
    },
    [currentSessionId, state],
  );

  // 重新生成答案
  const regenerateMessage = useCallback(
    async ({
      attachments = [],
      content,
      conversationId,
      enableWebSearch,
      mcps,
      modelId,
      modelMap,
      questionId,
    }: {
      modelId: string;
      conversationId: string;
      questionId: string;
      content: string;
      mcps: IProductDetail[];
      enableWebSearch: boolean;
      modelMap: Map<string, IProductDetail>;
      attachments?: IAttachment[];
    }) => {
      setGenerating(true);
      const abortController = new AbortController();
      abortControllersRef.current = [abortController];

      const isSupportWebSearch = modelMap.get(modelId)?.feature?.modelFeature?.webSearch || false;
      try {
        const messagePayload = {
          attachments: attachments.map((a) => ({ attachmentId: a.attachmentId })),
          conversationId,
          enableWebSearch: enableWebSearch ? isSupportWebSearch : false,
          mcpProducts: mcps.map((mcp) => mcp.productId),
          needMemory: true,
          productId: modelId,
          question: content,
          questionId,
          sessionId: currentSessionId,
          stream: true,
        };

        // 设置 loading 和 isNewQuestion
        dispatch({ payload: { conversationId, loading: true, modelId }, type: 'SET_LOADING' });
        dispatch({ payload: { conversationId, modelId, questionId }, type: 'SET_NEW_QUESTION' });

        const fullContentRef = { current: '' };
        const lastIdxRef = { current: -1 };

        // 创建 regenerate 专用的 SSE 回调（onChunk 和 onComplete 逻辑不同）
        const sseCallbacks: SSEOptions = {
          ...createSSECallbacks({
            conversationId,
            dispatch,
            fullContentRef,
            modelId,
            questionId,
            setIsMcpExecuting,
          }),
          onChunk: (chunk: string) => {
            fullContentRef.current += chunk;
            // regenerate 时需要追加新 answer 或更新最后一个 answer
            dispatch({
              payload: {
                chunk,
                conversationId,
                fullContent: fullContentRef.current,
                lastIdx: lastIdxRef.current,
                modelId,
                questionId,
              },
              type: 'REGENERATE_CHUNK',
            });
            if (lastIdxRef.current === -1) {
              lastIdxRef.current = 1; // 标记已初始化
            }
          },
          onComplete: (_content: string, _chatId: string, usage) => {
            setIsMcpExecuting(false);
            // regenerate 完成时更新最后一个 answer 的 usage
            dispatch({
              payload: {
                conversationId,
                fullContent: fullContentRef.current,
                modelId,
                questionId,
                usage,
              },
              type: 'COMPLETE',
            });
            setGenerating(false);
          },
          onError: (errorMsg: string) => {
            setIsMcpExecuting(false);
            // regenerate 错误时追加一个错误 answer
            dispatch({
              payload: {
                conversationId,
                errorMsg,
                fullContent: fullContentRef.current,
                modelId,
                questionId,
              },
              type: 'ERROR',
            });
            setGenerating(false);
          },
        };

        await executeSSERequest(modelId, messagePayload, abortController, sseCallbacks);
      } catch (error) {
        setGenerating(false);
        console.error('Failed to generate message:', error);
      }
    },
    [currentSessionId],
  );

  // 选择历史会话
  const handleSelectSession = useCallback(
    async (sessionId: string) => {
      if (currentSessionId === sessionId) return;
      setGenerating(false);

      try {
        setCurrentSessionId(sessionId);
        const response = await APIs.getConversationsV2(sessionId);

        if (response.code === 'SUCCESS' && response.data) {
          const models: IProductConversations[] = response.data;
          const m: IModelConversation[] = models.map((model) => ({
            conversations: model.conversations.map((conversation) => ({
              id: conversation.conversationId,
              loading: false,
              questions: conversation.questions.map((question) => {
                const activeAnswerIndex = question.answers.length - 1;
                const activeAnswer = question.answers[activeAnswerIndex];
                const toolCalls = activeAnswer?.toolCalls || [];

                const mcpToolCalls: IMcpToolCall[] = toolCalls.map((tc) => ({
                  arguments:
                    typeof tc.arguments === 'string' ? tc.arguments : JSON.stringify(tc.arguments),
                  id: tc.id,
                  mcpServerName: tc.mcpServerName,
                  name: tc.name,
                  type: 'function',
                }));

                const mcpToolResponses: IMcpToolResponse[] = toolCalls
                  .filter((tc) => tc.result !== undefined && tc.result !== null)
                  .map((tc) => ({ id: tc.id, name: tc.name, result: tc.result }));

                return {
                  activeAnswerIndex,
                  answers: question.answers.map((answer) => ({
                    content: answer.content,
                    errorMsg: '',
                    firstTokenTime: answer.usage?.firstByteTimeout || 0,
                    inputTokens: answer.usage?.inputTokens || 0,
                    outputTokens: answer.usage?.outputTokens || 0,
                    totalTime: answer.usage?.elapsedTime || 0,
                  })),
                  attachments: question.attachments,
                  content: question.content,
                  createdAt: question.createdAt,
                  id: question.questionId,
                  isNewQuestion: false,
                  mcpToolCalls: mcpToolCalls.length > 0 ? mcpToolCalls : undefined,
                  mcpToolResponses: mcpToolResponses.length > 0 ? mcpToolResponses : undefined,
                };
              }),
            })),
            id: model.productId,
            name: '-',
            sessionId,
          }));
          dispatch({ payload: m, type: 'SET_CONVERSATIONS' });
        }
      } catch (error) {
        console.error('Failed to load conversation:', error);
        antdMessage.error('加载聊天记录失败');
      }
    },
    [currentSessionId],
  );

  // 切换活跃答案
  const onChangeActiveAnswer = useCallback(
    (modelId: string, conversationId: string, questionId: string, direction: 'prev' | 'next') => {
      dispatch({
        payload: { conversationId, direction, modelId, questionId },
        type: 'CHANGE_ACTIVE_ANSWER',
      });
    },
    [],
  );

  // 添加模型
  const addModels = useCallback(
    (modelIds: string[], selectedModelId?: string) => {
      setCurrentSessionId(undefined);
      dispatch({
        payload: { modelIds, selectedModelId, sessionId: currentSessionId },
        type: 'ADD_MODELS',
      });
    },
    [currentSessionId],
  );

  // 关闭模型
  const closeModel = useCallback((modelId: string) => {
    dispatch({ payload: { modelId }, type: 'CLOSE_MODEL' });
  }, []);

  return {
    addModels,
    closeModel,
    currentSessionId,
    dispatch,
    generating,
    handleNewChat,
    handleSelectSession,
    handleStop,
    isMcpExecuting,
    modelConversation: state,
    onChangeActiveAnswer,
    regenerateMessage,
    sendMessage,
    setCurrentSessionId,
    sidebarRefreshTrigger,
  };
}
