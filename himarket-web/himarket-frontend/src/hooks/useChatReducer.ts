import type { IAttachment } from '../lib/apis';
import type { IModelConversation, IMessageChunk, IMcpToolCall, IMcpToolResponse } from '../types';

// ============ Action Types ============

export type ChatAction =
  | { type: 'RESET' }
  | { type: 'SET_CONVERSATIONS'; payload: IModelConversation[] }
  | {
      type: 'ADD_CONVERSATION';
      payload: {
        modelId: string;
        conversationId: string;
        questionId: string;
        content: string;
        attachments?: IAttachment[];
        selectedModelId?: string;
        sessionId?: string;
      };
    }
  | {
      type: 'APPEND_CHUNK';
      payload: {
        modelId: string;
        conversationId: string;
        questionId: string;
        chunk: string;
        fullContent: string;
      };
    }
  | {
      type: 'ADD_TOOL_CALL';
      payload: {
        modelId: string;
        conversationId: string;
        questionId: string;
        toolCall: IMcpToolCall;
      };
    }
  | {
      type: 'ADD_TOOL_RESPONSE';
      payload: {
        modelId: string;
        conversationId: string;
        questionId: string;
        toolResponse: IMcpToolResponse;
      };
    }
  | {
      type: 'COMPLETE';
      payload: {
        modelId: string;
        conversationId: string;
        questionId: string;
        fullContent: string;
        usage?: {
          firstByteTimeout?: number | null;
          elapsedTime?: number | null;
          inputTokens?: number;
          outputTokens?: number;
        };
      };
    }
  | {
      type: 'ERROR';
      payload: {
        modelId: string;
        conversationId: string;
        questionId: string;
        errorMsg: string;
        fullContent: string;
      };
    }
  | {
      type: 'CHANGE_ACTIVE_ANSWER';
      payload: {
        modelId: string;
        conversationId: string;
        questionId: string;
        direction: 'prev' | 'next';
      };
    }
  | {
      type: 'ADD_MODELS';
      payload: {
        modelIds: string[];
        selectedModelId?: string;
        sessionId?: string;
      };
    }
  | { type: 'CLOSE_MODEL'; payload: { modelId: string } }
  | {
      type: 'SET_LOADING';
      payload: {
        modelId: string;
        conversationId: string;
        loading: boolean;
      };
    }
  | {
      type: 'SET_NEW_QUESTION';
      payload: {
        modelId: string;
        conversationId: string;
        questionId: string;
      };
    }
  | {
      type: 'REGENERATE_CHUNK';
      payload: {
        modelId: string;
        conversationId: string;
        questionId: string;
        chunk: string;
        fullContent: string;
        lastIdx: number;
      };
    }
  | {
      type: 'SEND_ERROR';
      payload: {
        modelId: string;
        conversationId: string;
        questionId: string;
        errorMsg: string;
        fullContent: string;
      };
    }
  | { type: 'GLOBAL_ERROR'; payload: { errorMsg: string } };

// ============ Helper: update a specific question within the state ============

function updateQuestion(
  state: IModelConversation[],
  modelId: string,
  conversationId: string,
  questionId: string,
  updater: (
    question: IModelConversation['conversations'][0]['questions'][0],
  ) => IModelConversation['conversations'][0]['questions'][0],
  conversationUpdater?: (
    con: IModelConversation['conversations'][0],
  ) => Partial<IModelConversation['conversations'][0]>,
): IModelConversation[] {
  return state.map((model) => {
    if (model.id !== modelId) return model;
    return {
      ...model,
      conversations: model.conversations.map((con) => {
        if (con.id !== conversationId) return con;
        const extraFields = conversationUpdater ? conversationUpdater(con) : {};
        return {
          ...con,
          ...extraFields,
          questions: con.questions.map((q) => (q.id === questionId ? updater(q) : q)),
        };
      }),
    };
  });
}

// ============ Helper: merge or create text chunk ============

function mergeTextChunk(chunks: IMessageChunk[], textChunk: string): IMessageChunk[] {
  const lastChunk = chunks[chunks.length - 1];
  if (lastChunk && lastChunk.type === 'text') {
    return chunks.map((c, i) =>
      i === chunks.length - 1 ? { ...c, content: (c.content || '') + textChunk } : c,
    );
  }
  return [
    ...chunks,
    {
      content: textChunk,
      id: `chunk-text-${Date.now()}`,
      type: 'text' as const,
    },
  ];
}

// ============ Reducer ============

export function chatReducer(state: IModelConversation[], action: ChatAction): IModelConversation[] {
  switch (action.type) {
    case 'RESET':
      return [];

    case 'SET_CONVERSATIONS':
      return action.payload;

    case 'ADD_CONVERSATION': {
      const {
        attachments,
        content,
        conversationId,
        modelId,
        questionId,
        selectedModelId,
        sessionId,
      } = action.payload;
      const newConversation = {
        id: conversationId,
        loading: true,
        questions: [
          {
            activeAnswerIndex: 0,
            answers: [
              {
                content: '',
                errorMsg: '',
                firstTokenTime: 0,
                inputTokens: 0,
                outputTokens: 0,
                totalTime: 0,
              },
            ],
            attachments,
            content,
            createdAt: new Date().toDateString(),
            id: questionId,
          },
        ],
      };

      if (state.length === 0) {
        return [
          {
            conversations: [newConversation],
            id: selectedModelId || modelId,
            name: '-',
            sessionId: sessionId || '',
          },
        ];
      }

      return state.map((model) => {
        if (model.id !== modelId) return model;
        return {
          ...model,
          conversations: [...model.conversations, newConversation],
        };
      });
    }

    case 'APPEND_CHUNK': {
      const { chunk, conversationId, fullContent, modelId, questionId } = action.payload;
      return updateQuestion(
        state,
        modelId,
        conversationId,
        questionId,
        (question) => ({
          ...question,
          answers: [
            {
              content: fullContent,
              errorMsg: '',
              firstTokenTime: 0,
              inputTokens: 0,
              outputTokens: 0,
              totalTime: 0,
            },
          ],
          messageChunks: mergeTextChunk(question.messageChunks || [], chunk),
        }),
        () => ({ loading: false }),
      );
    }

    case 'ADD_TOOL_CALL': {
      const { conversationId, modelId, questionId, toolCall } = action.payload;
      const toolCallChunk: IMessageChunk = {
        id: `chunk-tc-${toolCall.id}`,
        toolCall,
        type: 'tool_call',
      };
      return updateQuestion(state, modelId, conversationId, questionId, (question) => ({
        ...question,
        mcpToolCalls: [...(question.mcpToolCalls || []), toolCall],
        messageChunks: [...(question.messageChunks || []), toolCallChunk],
      }));
    }

    case 'ADD_TOOL_RESPONSE': {
      const { conversationId, modelId, questionId, toolResponse } = action.payload;
      const toolResultChunk: IMessageChunk = {
        id: `chunk-tr-${toolResponse.id}`,
        toolResult: toolResponse,
        type: 'tool_result',
      };
      return updateQuestion(state, modelId, conversationId, questionId, (question) => ({
        ...question,
        mcpToolResponses: [...(question.mcpToolResponses || []), toolResponse],
        messageChunks: [...(question.messageChunks || []), toolResultChunk],
      }));
    }

    case 'COMPLETE': {
      const { conversationId, fullContent, modelId, questionId, usage } = action.payload;
      return updateQuestion(
        state,
        modelId,
        conversationId,
        questionId,
        (question) => ({
          ...question,
          activeAnswerIndex: question.answers.length - 1,
          answers: question.answers.map((answer, idx) => {
            if (idx === question.answers.length - 1) {
              return {
                content: fullContent,
                errorMsg: answer.errorMsg || '',
                firstTokenTime: usage?.firstByteTimeout || 0,
                inputTokens: usage?.inputTokens || 0,
                outputTokens: usage?.outputTokens || 0,
                totalTime: usage?.elapsedTime || 0,
              };
            }
            return answer;
          }),
        }),
        () => ({ loading: false }),
      );
    }

    case 'ERROR': {
      const { conversationId, errorMsg, fullContent, modelId, questionId } = action.payload;
      return updateQuestion(
        state,
        modelId,
        conversationId,
        questionId,
        (question) => ({
          ...question,
          answers: [
            ...question.answers,
            {
              content: fullContent,
              errorMsg,
              firstTokenTime: 0,
              inputTokens: 0,
              outputTokens: 0,
              totalTime: 0,
            },
          ],
        }),
        () => ({ loading: false }),
      );
    }

    case 'CHANGE_ACTIVE_ANSWER': {
      const { conversationId, direction, modelId, questionId } = action.payload;
      return updateQuestion(state, modelId, conversationId, questionId, (question) => {
        let newIndex = question.activeAnswerIndex;
        if (direction === 'prev' && newIndex > 0) {
          newIndex -= 1;
        } else if (direction === 'next' && newIndex < question.answers.length - 1) {
          newIndex += 1;
        }
        return { ...question, activeAnswerIndex: newIndex };
      });
    }

    case 'ADD_MODELS': {
      const { modelIds, selectedModelId, sessionId } = action.payload;
      if (state.length === 0) {
        return [
          { conversations: [], id: selectedModelId || '', name: '', sessionId: sessionId || '' },
          ...modelIds.map((id) => ({
            conversations: [],
            id,
            name: '',
            sessionId: sessionId || '',
          })),
        ];
      }
      return [
        ...state.map((model) => ({
          ...model,
          conversations: [] as IModelConversation['conversations'],
          sessionId: sessionId || '',
        })),
        ...modelIds.map((id) => ({
          conversations: [] as IModelConversation['conversations'],
          id,
          name: '',
          sessionId: sessionId || '',
        })),
      ];
    }

    case 'CLOSE_MODEL': {
      return state.filter((model) => model.id !== action.payload.modelId);
    }

    case 'SET_LOADING': {
      const { conversationId, loading, modelId } = action.payload;
      return state.map((model) => {
        if (model.id !== modelId) return model;
        return {
          ...model,
          conversations: model.conversations.map((con) => ({
            ...con,
            loading: con.id === conversationId ? loading : con.loading,
          })),
        };
      });
    }

    case 'SET_NEW_QUESTION': {
      const { conversationId, modelId, questionId } = action.payload;
      return updateQuestion(state, modelId, conversationId, questionId, (question) => ({
        ...question,
        isNewQuestion: true,
      }));
    }

    case 'REGENERATE_CHUNK': {
      const { chunk, conversationId, fullContent, lastIdx, modelId, questionId } = action.payload;
      return updateQuestion(
        state,
        modelId,
        conversationId,
        questionId,
        (question) => {
          const ans =
            lastIdx !== -1
              ? question.answers.map((answer, idx) =>
                  idx !== question.answers.length - 1
                    ? answer
                    : { ...answer, content: fullContent },
                )
              : [
                  ...question.answers,
                  {
                    content: fullContent,
                    errorMsg: '',
                    firstTokenTime: 0,
                    inputTokens: 0,
                    outputTokens: 0,
                    totalTime: 0,
                  },
                ];
          return {
            ...question,
            activeAnswerIndex: ans.length - 1,
            answers: ans,
            messageChunks: mergeTextChunk(question.messageChunks || [], chunk),
          };
        },
        () => ({ loading: false }),
      );
    }

    case 'SEND_ERROR': {
      const { conversationId, errorMsg, fullContent, modelId, questionId } = action.payload;
      return updateQuestion(
        state,
        modelId,
        conversationId,
        questionId,
        (question) => ({
          ...question,
          answers: [
            {
              content: fullContent,
              errorMsg,
              firstTokenTime: 0,
              inputTokens: 0,
              outputTokens: 0,
              totalTime: 0,
            },
          ],
        }),
        () => ({ loading: false }),
      );
    }

    case 'GLOBAL_ERROR': {
      return state.map((model) => ({
        ...model,
        conversations: model.conversations.map((con) => ({
          ...con,
          loading: false,
          questions: con.questions.map((question, idx) => {
            if (idx === con.questions.length - 1) {
              return {
                ...question,
                answers: [
                  {
                    content: '',
                    errorMsg: action.payload.errorMsg,
                    firstTokenTime: 0,
                    inputTokens: 0,
                    outputTokens: 0,
                    totalTime: 0,
                  },
                ],
              };
            }
            return question;
          }),
        })),
      }));
    }

    default:
      return state;
  }
}
