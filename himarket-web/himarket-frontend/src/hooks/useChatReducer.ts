import type { IModelConversation, IMessageChunk, IMcpToolCall, IMcpToolResponse } from "../types";
import type { IAttachment } from "../lib/apis";

// ============ Action Types ============

export type ChatAction =
  | { type: 'RESET' }
  | { type: 'SET_CONVERSATIONS'; payload: IModelConversation[] }
  | { type: 'ADD_CONVERSATION'; payload: {
      modelId: string;
      conversationId: string;
      questionId: string;
      content: string;
      attachments?: IAttachment[];
      selectedModelId?: string;
      sessionId?: string;
    }}
  | { type: 'APPEND_CHUNK'; payload: {
      modelId: string;
      conversationId: string;
      questionId: string;
      chunk: string;
      fullContent: string;
    }}
  | { type: 'ADD_TOOL_CALL'; payload: {
      modelId: string;
      conversationId: string;
      questionId: string;
      toolCall: IMcpToolCall;
    }}
  | { type: 'ADD_TOOL_RESPONSE'; payload: {
      modelId: string;
      conversationId: string;
      questionId: string;
      toolResponse: IMcpToolResponse;
    }}
  | { type: 'COMPLETE'; payload: {
      modelId: string;
      conversationId: string;
      questionId: string;
      fullContent: string;
      usage?: { firstByteTimeout?: number | null; elapsedTime?: number | null; inputTokens?: number; outputTokens?: number };
    }}
  | { type: 'ERROR'; payload: {
      modelId: string;
      conversationId: string;
      questionId: string;
      errorMsg: string;
      fullContent: string;
    }}
  | { type: 'CHANGE_ACTIVE_ANSWER'; payload: {
      modelId: string;
      conversationId: string;
      questionId: string;
      direction: 'prev' | 'next';
    }}
  | { type: 'ADD_MODELS'; payload: {
      modelIds: string[];
      selectedModelId?: string;
      sessionId?: string;
    }}
  | { type: 'CLOSE_MODEL'; payload: { modelId: string } }
  | { type: 'SET_LOADING'; payload: {
      modelId: string;
      conversationId: string;
      loading: boolean;
    }}
  | { type: 'SET_NEW_QUESTION'; payload: {
      modelId: string;
      conversationId: string;
      questionId: string;
    }}
  | { type: 'REGENERATE_CHUNK'; payload: {
      modelId: string;
      conversationId: string;
      questionId: string;
      chunk: string;
      fullContent: string;
      lastIdx: number;
    }}
  | { type: 'SEND_ERROR'; payload: {
      modelId: string;
      conversationId: string;
      questionId: string;
      errorMsg: string;
      fullContent: string;
    }}
  | { type: 'GLOBAL_ERROR'; payload: { errorMsg: string } };

// ============ Helper: update a specific question within the state ============

function updateQuestion(
  state: IModelConversation[],
  modelId: string,
  conversationId: string,
  questionId: string,
  updater: (question: IModelConversation['conversations'][0]['questions'][0]) => IModelConversation['conversations'][0]['questions'][0],
  conversationUpdater?: (con: IModelConversation['conversations'][0]) => Partial<IModelConversation['conversations'][0]>,
): IModelConversation[] {
  return state.map(model => {
    if (model.id !== modelId) return model;
    return {
      ...model,
      conversations: model.conversations.map(con => {
        if (con.id !== conversationId) return con;
        const extraFields = conversationUpdater ? conversationUpdater(con) : {};
        return {
          ...con,
          ...extraFields,
          questions: con.questions.map(q => q.id === questionId ? updater(q) : q),
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
      i === chunks.length - 1
        ? { ...c, content: (c.content || '') + textChunk }
        : c
    );
  }
  return [...chunks, {
    id: `chunk-text-${Date.now()}`,
    type: 'text' as const,
    content: textChunk,
  }];
}

// ============ Reducer ============

export function chatReducer(state: IModelConversation[], action: ChatAction): IModelConversation[] {
  switch (action.type) {
    case 'RESET':
      return [];

    case 'SET_CONVERSATIONS':
      return action.payload;

    case 'ADD_CONVERSATION': {
      const { modelId, conversationId, questionId, content, attachments, selectedModelId, sessionId } = action.payload;
      const newConversation = {
        id: conversationId,
        loading: true,
        questions: [{
          id: questionId,
          content,
          createdAt: new Date().toDateString(),
          activeAnswerIndex: 0,
          attachments,
          answers: [{
            errorMsg: "",
            content: "",
            firstTokenTime: 0,
            totalTime: 0,
            inputTokens: 0,
            outputTokens: 0,
          }],
        }],
      };

      if (state.length === 0) {
        return [{
          id: selectedModelId || modelId,
          sessionId: sessionId || "",
          name: "-",
          conversations: [newConversation],
        }];
      }

      return state.map(model => {
        if (model.id !== modelId) return model;
        return {
          ...model,
          conversations: [...model.conversations, newConversation],
        };
      });
    }

    case 'APPEND_CHUNK': {
      const { modelId, conversationId, questionId, chunk, fullContent } = action.payload;
      return updateQuestion(state, modelId, conversationId, questionId,
        (question) => ({
          ...question,
          messageChunks: mergeTextChunk(question.messageChunks || [], chunk),
          answers: [{
            errorMsg: "",
            content: fullContent,
            firstTokenTime: 0,
            totalTime: 0,
            inputTokens: 0,
            outputTokens: 0,
          }],
        }),
        () => ({ loading: false }),
      );
    }

    case 'ADD_TOOL_CALL': {
      const { modelId, conversationId, questionId, toolCall } = action.payload;
      const toolCallChunk: IMessageChunk = {
        id: `chunk-tc-${toolCall.id}`,
        type: 'tool_call',
        toolCall,
      };
      return updateQuestion(state, modelId, conversationId, questionId,
        (question) => ({
          ...question,
          mcpToolCalls: [...(question.mcpToolCalls || []), toolCall],
          messageChunks: [...(question.messageChunks || []), toolCallChunk],
        }),
      );
    }

    case 'ADD_TOOL_RESPONSE': {
      const { modelId, conversationId, questionId, toolResponse } = action.payload;
      const toolResultChunk: IMessageChunk = {
        id: `chunk-tr-${toolResponse.id}`,
        type: 'tool_result',
        toolResult: toolResponse,
      };
      return updateQuestion(state, modelId, conversationId, questionId,
        (question) => ({
          ...question,
          mcpToolResponses: [...(question.mcpToolResponses || []), toolResponse],
          messageChunks: [...(question.messageChunks || []), toolResultChunk],
        }),
      );
    }

    case 'COMPLETE': {
      const { modelId, conversationId, questionId, fullContent, usage } = action.payload;
      return updateQuestion(state, modelId, conversationId, questionId,
        (question) => ({
          ...question,
          activeAnswerIndex: question.answers.length - 1,
          answers: question.answers.map((answer, idx) => {
            if (idx === question.answers.length - 1) {
              return {
                errorMsg: answer.errorMsg || "",
                content: fullContent,
                firstTokenTime: usage?.firstByteTimeout || 0,
                totalTime: usage?.elapsedTime || 0,
                inputTokens: usage?.inputTokens || 0,
                outputTokens: usage?.outputTokens || 0,
              };
            }
            return answer;
          }),
        }),
        () => ({ loading: false }),
      );
    }

    case 'ERROR': {
      const { modelId, conversationId, questionId, errorMsg, fullContent } = action.payload;
      return updateQuestion(state, modelId, conversationId, questionId,
        (question) => ({
          ...question,
          answers: [
            ...question.answers,
            {
              errorMsg,
              content: fullContent,
              firstTokenTime: 0,
              totalTime: 0,
              inputTokens: 0,
              outputTokens: 0,
            },
          ],
        }),
        () => ({ loading: false }),
      );
    }

    case 'CHANGE_ACTIVE_ANSWER': {
      const { modelId, conversationId, questionId, direction } = action.payload;
      return updateQuestion(state, modelId, conversationId, questionId,
        (question) => {
          let newIndex = question.activeAnswerIndex;
          if (direction === 'prev' && newIndex > 0) {
            newIndex -= 1;
          } else if (direction === 'next' && newIndex < question.answers.length - 1) {
            newIndex += 1;
          }
          return { ...question, activeAnswerIndex: newIndex };
        },
      );
    }

    case 'ADD_MODELS': {
      const { modelIds, selectedModelId, sessionId } = action.payload;
      if (state.length === 0) {
        return [
          { id: selectedModelId || "", name: "", conversations: [], sessionId: sessionId || "" },
          ...modelIds.map(id => ({ sessionId: sessionId || "", id, name: "", conversations: [] })),
        ];
      }
      return [
        ...state.map(model => ({ ...model, sessionId: sessionId || "", conversations: [] as IModelConversation['conversations'] })),
        ...modelIds.map(id => ({ sessionId: sessionId || "", id, name: "", conversations: [] as IModelConversation['conversations'] })),
      ];
    }

    case 'CLOSE_MODEL': {
      return state.filter(model => model.id !== action.payload.modelId);
    }

    case 'SET_LOADING': {
      const { modelId, conversationId, loading } = action.payload;
      return state.map(model => {
        if (model.id !== modelId) return model;
        return {
          ...model,
          conversations: model.conversations.map(con => ({
            ...con,
            loading: con.id === conversationId ? loading : con.loading,
          })),
        };
      });
    }

    case 'SET_NEW_QUESTION': {
      const { modelId, conversationId, questionId } = action.payload;
      return updateQuestion(state, modelId, conversationId, questionId,
        (question) => ({ ...question, isNewQuestion: true }),
      );
    }

    case 'REGENERATE_CHUNK': {
      const { modelId, conversationId, questionId, chunk, fullContent, lastIdx } = action.payload;
      return updateQuestion(state, modelId, conversationId, questionId,
        (question) => {
          const ans = lastIdx !== -1
            ? question.answers.map((answer, idx) =>
                idx !== question.answers.length - 1 ? answer : { ...answer, content: fullContent }
              )
            : [
                ...question.answers,
                { errorMsg: "", content: fullContent, firstTokenTime: 0, totalTime: 0, inputTokens: 0, outputTokens: 0 },
              ];
          return {
            ...question,
            messageChunks: mergeTextChunk(question.messageChunks || [], chunk),
            activeAnswerIndex: ans.length - 1,
            answers: ans,
          };
        },
        () => ({ loading: false }),
      );
    }

    case 'SEND_ERROR': {
      const { modelId, conversationId, questionId, errorMsg, fullContent } = action.payload;
      return updateQuestion(state, modelId, conversationId, questionId,
        (question) => ({
          ...question,
          answers: [{
            errorMsg,
            content: fullContent,
            firstTokenTime: 0,
            totalTime: 0,
            inputTokens: 0,
            outputTokens: 0,
          }],
        }),
        () => ({ loading: false }),
      );
    }

    case 'GLOBAL_ERROR': {
      return state.map(model => ({
        ...model,
        conversations: model.conversations.map(con => ({
          ...con,
          loading: false,
          questions: con.questions.map((question, idx) => {
            if (idx === con.questions.length - 1) {
              return {
                ...question,
                answers: [{
                  errorMsg: action.payload.errorMsg,
                  content: "",
                  firstTokenTime: 0,
                  totalTime: 0,
                  inputTokens: 0,
                  outputTokens: 0,
                }],
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
