import {
  CopyOutlined,
  ReloadOutlined,
  LeftOutlined,
  RightOutlined,
  DownCircleOutlined,
} from '@ant-design/icons';
import { message } from 'antd';
import { useEffect, useRef, useState } from 'react';

import { ProductIconRenderer } from '../icon/ProductIconRenderer';
import MarkdownRender from '../MarkdownRender';
import { AttachmentPreview, type PreviewAttachment } from './AttachmentPreview';
import { McpToolCallPanel, McpToolCallItem } from './McpToolCallPanel';
import { copyToClipboard } from '../../lib/utils';

import type { IModelConversation } from '../../types';

interface MessageListProps {
  conversations: IModelConversation['conversations'];
  modelName?: string;
  modelIcon?: string; // 添加模型 icon
  onRefresh?: (
    msg: IModelConversation['conversations'][0],
    quest: IModelConversation['conversations'][0]['questions'][0],
    isLast: boolean,
  ) => void;
  onChangeVersion?: (
    conversationId: string,
    questionId: string,
    direction: 'prev' | 'next',
  ) => void;
  autoScrollEnabled?: boolean;
}

export function Messages({
  autoScrollEnabled = true,
  conversations,
  modelIcon,
  modelName = 'AI Assistant',
  onChangeVersion,
  onRefresh,
}: MessageListProps) {
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    if (autoScrollEnabled) {
      scrollToBottom();
    }
  }, [conversations, autoScrollEnabled]);

  return (
    <div className="mx-auto px-6 pb-4">
      <div className="space-y-6">
        {conversations.map((conversation, index) => {
          return conversation.questions.map((question) => {
            const activeAnswer = question.answers[question.activeAnswerIndex];
            return (
              <Message
                activeAnswer={activeAnswer}
                conversation={conversation}
                isLast={index === conversations.length - 1}
                isNewChat={question.isNewQuestion !== false}
                key={question.id}
                modelIcon={modelIcon}
                modelName={modelName}
                onChangeVersion={onChangeVersion}
                onRefresh={onRefresh}
                question={question}
              />
            );
          });
        })}
        <div ref={messagesEndRef} />
      </div>
    </div>
  );
}

function Message({
  activeAnswer,
  conversation,
  isLast,
  isNewChat,
  modelIcon,
  modelName,
  onChangeVersion,
  onRefresh,
  question,
}: {
  conversation: IModelConversation['conversations'][0];
  question: IModelConversation['conversations'][0]['questions'][0];
  activeAnswer?: IModelConversation['conversations'][0]['questions'][0]['answers'][0];
  modelIcon?: string;
  modelName?: string;
  isNewChat?: boolean;
  isLast: boolean;
  onChangeVersion?: (
    conversationId: string,
    questionId: string,
    direction: 'prev' | 'next',
  ) => void;
  onRefresh?: (
    msg: IModelConversation['conversations'][0],
    quest: IModelConversation['conversations'][0]['questions'][0],
    isLast: boolean,
  ) => void;
}) {
  const contentRef = useRef<HTMLDivElement>(null);

  const [expandedContent, setExpandedContent] = useState(() => {
    // Initial state will be updated after first render
    return true;
  });
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const handleCopy = async (content: string, messageId: string) => {
    copyToClipboard(content).then(() => {
      message.success('已复制到剪贴板');
      setCopiedId(messageId);
      setTimeout(() => setCopiedId(null), 2000);
    });
  };

  const formatTime = (ms?: number) => {
    if (ms === undefined) return '-';
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(2)}s`;
  };

  useEffect(() => {
    // Check content height after render and update expanded state if needed
    if (contentRef.current) {
      const height = contentRef.current.getBoundingClientRect().height;
      if (height < 160) {
        setExpandedContent(false);
      }
    }
  }, [activeAnswer?.content]);

  return (
    <div key={question.id}>
      <div className="flex justify-end">
        <div className="max-w-[80%] flex flex-col items-end gap-2">
          {question.attachments && question.attachments.length > 0 && (
            <AttachmentPreview
              attachments={question.attachments as PreviewAttachment[]}
              className="mb-1 justify-end"
            />
          )}
          <div className="bg-colorPrimaryBgHover px-4 py-3 rounded-lg">
            <div className="whitespace-pre-wrap leading-relaxed text-[15px] tracking-[-0.01em]">
              {question.content}
            </div>
          </div>
        </div>
      </div>
      <div className="flex flex-col gap-2">
        <div className="flex items-center gap-2">
          {/* 模型头像 */}
          <div className="w-8 h-8 rounded-full bg-gradient-to-br from-colorPrimary/20 to-colorPrimary/10 flex items-center justify-center flex-shrink-0">
            <ProductIconRenderer className="w-5 h-5" iconType={modelIcon} />
          </div>
          {/* 模型名称 */}
          <div className="text-sm text-gray-500 mb-1.5">{modelName}</div>
        </div>

        {/* 消息内容区域 */}
        <div className="flex-1">
          <div
            className={`${!isNewChat && expandedContent ? 'max-h-40 overflow-hidden' : 'overflow-auto'} relative  bg-white/80 backdrop-blur-sm px-4 py-3 rounded-lg border border-gray-100`}
            ref={contentRef}
          >
            {!isNewChat && expandedContent && (
              <button
                className="bottom-mask flex justify-center items-end cursor-pointer absolute -bottom-px h-14 w-full border-0 bg-transparent"
                onClick={() => setExpandedContent(false)}
                style={{
                  background:
                    'linear-gradient(rgba(255, 255, 255, .4) 9%, rgb(255, 255, 255) 100%)',
                }}
                type="button"
              >
                <DownCircleOutlined className="text-gray-500 mb-2" />
              </button>
            )}
            {/* 如果是错误状态，显示错误提示 */}
            {activeAnswer?.errorMsg ? (
              <div className="flex items-center gap-2 text-red-500">
                <span>{activeAnswer?.errorMsg || '网络异常，请重试'}</span>
              </div>
            ) : conversation.loading ? (
              /* 如果内容为空且正在加载，显示 loading */
              <div className="flex items-center gap-2 text-gray-500">
                <div className="flex items-center gap-1">
                  <span
                    className="w-1.5 h-1.5 bg-colorPrimary rounded-full"
                    style={{ animation: 'bounceStrong 1s infinite', animationDelay: '0ms' }}
                  ></span>
                  <span
                    className="w-1.5 h-1.5 bg-colorPrimary rounded-full"
                    style={{ animation: 'bounceStrong 1s infinite', animationDelay: '150ms' }}
                  ></span>
                  <span
                    className="w-1.5 h-1.5 bg-colorPrimary rounded-full"
                    style={{ animation: 'bounceStrong 1s infinite', animationDelay: '300ms' }}
                  ></span>
                </div>
              </div>
            ) : question.messageChunks && question.messageChunks.length > 0 ? (
              /* 新逻辑：按 messageChunks 顺序渲染 */
              <div className="space-y-3">
                {question.messageChunks.map((chunk) => {
                  if (chunk.type === 'text' && chunk.content) {
                    return (
                      <div className="prose" key={chunk.id}>
                        <MarkdownRender content={chunk.content} />
                      </div>
                    );
                  }
                  if (chunk.type === 'tool_call' && chunk.toolCall) {
                    // 查找对应的 tool_result
                    const toolResultChunk = question.messageChunks?.find(
                      (c) => c.type === 'tool_result' && c.toolResult?.id === chunk.toolCall?.id,
                    );
                    return (
                      <McpToolCallItem
                        key={chunk.id}
                        toolCall={chunk.toolCall}
                        toolResponse={toolResultChunk?.toolResult}
                      />
                    );
                  }
                  // tool_result 已在 tool_call 中处理，跳过
                  return null;
                })}
              </div>
            ) : (
              /* 旧逻辑：兼容历史数据 */
              <>
                {/* MCP 工具调用面板 */}
                {question.mcpToolCalls && question.mcpToolCalls.length > 0 && (
                  <div className="mb-3">
                    <McpToolCallPanel
                      toolCalls={question.mcpToolCalls}
                      toolResponses={question.mcpToolResponses}
                    />
                  </div>
                )}
                <div className="prose">
                  <MarkdownRender content={activeAnswer?.content || ''} />
                </div>
              </>
            )}
          </div>

          {/* 统计信息和功能按钮 - 只在有内容或错误时显示 */}
          {
            <div className="flex items-center justify-between mt-2 px-1">
              {/* 左侧：统计信息 */}
              <div className="flex items-center gap-3 text-xs text-gray-400 tabular-nums">
                <span>首字： {formatTime(activeAnswer?.firstTokenTime)}</span>
                <span>耗时： {formatTime(activeAnswer?.totalTime)}</span>
                <span>输入 Token： {activeAnswer?.inputTokens ?? '-'}</span>
                <span>输出 Token： {activeAnswer?.outputTokens ?? '-'}</span>
              </div>

              {/* 右侧：功能按钮 */}
              <div className="flex items-center gap-1">
                <button
                  className={`
                            p-1.5 rounded-md transition-colors duration-200
                            ${copiedId === question.id ? 'text-colorPrimary' : 'text-gray-400 hover:text-gray-600 hover:bg-gray-100'}
                          `}
                  onClick={() => handleCopy(activeAnswer?.content || '', question.id)}
                  title="复制"
                >
                  <CopyOutlined className="text-sm" />
                </button>
                <button
                  className="p-1.5 rounded-md text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors duration-200"
                  onClick={() => {
                    onRefresh?.(conversation, question, isLast);
                  }}
                  title={'重新生成'}
                >
                  <ReloadOutlined className="text-sm" />
                </button>
                {/* 版本切换按钮 - 仅在有多个版本时显示 */}
                {question.answers?.length > 1 && (
                  <div className="flex items-center gap-1 mr-2 px-2 py-1 rounded-md">
                    <button
                      className={`
                                p-1 rounded transition-colors duration-200
                                ${
                                  question.activeAnswerIndex === 0
                                    ? 'text-gray-300 cursor-not-allowed'
                                    : 'text-gray-500 hover:text-gray-700 hover:bg-gray-200'
                                }
                              `}
                      disabled={question.activeAnswerIndex === 0}
                      onClick={() => onChangeVersion?.(conversation.id, question.id, 'prev')}
                      title="上一个版本"
                    >
                      <LeftOutlined className="text-xs" />
                    </button>
                    <span className="text-xs text-gray-600 font-medium min-w-[40px] text-center">
                      {(question.activeAnswerIndex ?? 0) + 1} / {question.answers.length}
                    </span>
                    <button
                      className={`
                                p-1 rounded transition-colors duration-200
                                ${
                                  question.activeAnswerIndex === question.answers.length - 1
                                    ? 'text-gray-300 cursor-not-allowed'
                                    : 'text-gray-500 hover:text-gray-700 hover:bg-gray-200'
                                }
                              `}
                      disabled={question.activeAnswerIndex === question.answers.length - 1}
                      onClick={() => onChangeVersion?.(conversation.id, question.id, 'next')}
                      title="下一个版本"
                    >
                      <RightOutlined className="text-xs" />
                    </button>
                  </div>
                )}
              </div>
            </div>
          }
        </div>
      </div>
    </div>
  );
}
