import {
  CopyOutlined,
  ReloadOutlined,
  LeftOutlined,
  RightOutlined,
  DownCircleOutlined,
  BarChartOutlined,
} from '@ant-design/icons';
import { message, Tooltip } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

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
  variant?: 'default' | 'compare';
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
  variant = 'default',
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
    <div
      className={
        variant === 'compare'
          ? 'w-full px-4 pb-4 pt-3'
          : 'mx-auto w-full max-w-[1040px] px-5 pb-5 pt-4'
      }
    >
      <div className={variant === 'compare' ? 'space-y-5' : 'space-y-6'}>
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
                variant={variant}
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
  variant,
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
  variant: 'default' | 'compare';
}) {
  const { t } = useTranslation('chat');
  const contentRef = useRef<HTMLDivElement>(null);

  const [expandedContent, setExpandedContent] = useState(() => {
    // Initial state will be updated after first render
    return true;
  });
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const isCompare = variant === 'compare';

  const handleCopy = async (content: string, messageId: string) => {
    copyToClipboard(content).then(() => {
      message.success(t('messages.copied'));
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
        <div
          className={`flex flex-col items-end gap-2 ${isCompare ? 'max-w-[86%]' : 'max-w-[78%]'}`}
        >
          {question.attachments && question.attachments.length > 0 && (
            <AttachmentPreview
              attachments={question.attachments as PreviewAttachment[]}
              className="mb-1 justify-end"
            />
          )}
          <div className="rounded-[16px] rounded-tr-md bg-colorPrimary px-4 py-3 text-white shadow-sm">
            <div className="whitespace-pre-wrap text-[15px] leading-relaxed tracking-normal">
              {question.content}
            </div>
          </div>
        </div>
      </div>
      <div className={isCompare ? 'mt-3' : 'mt-4'}>
        {/* 消息内容区域 */}
        <div className="min-w-0">
          <div className="mb-2 flex items-center gap-2">
            <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-[12px] border border-[#DDE5F0] bg-white shadow-sm">
              <ProductIconRenderer className="w-5 h-5" iconType={modelIcon} />
            </div>
            <div className="text-sm font-medium leading-5 text-gray-600">{modelName}</div>
          </div>
          <div
            className={
              isCompare
                ? 'rounded-[14px] border border-[#E6ECF4] bg-white px-4 py-3 shadow-none'
                : 'rounded-[18px] bg-white/55 px-5 py-4 shadow-[inset_0_1px_0_rgba(255,255,255,0.72)] backdrop-blur-[2px]'
            }
          >
            <div
              className={`${!isNewChat && expandedContent ? 'max-h-40 overflow-hidden' : 'overflow-auto'} relative text-[15px] leading-[1.72] text-gray-700`}
              ref={contentRef}
            >
              {!isNewChat && expandedContent && (
                <button
                  className="bottom-mask flex justify-center items-end cursor-pointer absolute -bottom-px h-14 w-full border-0 bg-transparent"
                  onClick={() => setExpandedContent(false)}
                  style={{
                    background:
                      'linear-gradient(rgba(255, 255, 255, .15) 9%, rgba(255, 255, 255, .96) 100%)',
                  }}
                  type="button"
                >
                  <DownCircleOutlined className="text-gray-500 mb-2" />
                </button>
              )}
              {/* 如果是错误状态，显示错误提示 */}
              {activeAnswer?.errorMsg ? (
                <div className="flex items-center gap-2 text-red-500">
                  <span>{activeAnswer?.errorMsg || t('messages.networkError')}</span>
                </div>
              ) : conversation.loading ? (
                /* 如果内容为空且正在加载，显示 loading */
                <div className="space-y-3">
                  {/* 如果有 tool_call，先显示工具调用框 - 从当前活跃 answer 中获取 */}
                  {activeAnswer?.messageChunks?.map((chunk) => {
                    if (chunk.type === 'tool_call' && chunk.toolCall) {
                      const toolResultChunk = activeAnswer.messageChunks?.find(
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
                    return null;
                  })}
                  {/* 显示 loading 动画 */}
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
                </div>
              ) : activeAnswer?.messageChunks && activeAnswer.messageChunks.length > 0 ? (
                /* 新逻辑：按 messageChunks 顺序渲染 */
                <div className="space-y-3">
                  {activeAnswer.messageChunks.map((chunk) => {
                    if (chunk.type === 'text' && chunk.content) {
                      return (
                        <div key={chunk.id}>
                          <MarkdownRender
                            content={chunk.content}
                            imageStyle="card"
                            variant="chat"
                          />
                        </div>
                      );
                    }
                    if (chunk.type === 'tool_call' && chunk.toolCall) {
                      // 查找对应的 tool_result
                      const toolResultChunk = activeAnswer.messageChunks?.find(
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
                /* 旧逻辑：兼容历史数据 - 从当前活跃 answer 中获取 tool calls */
                <>
                  {/* MCP 工具调用面板 */}
                  {activeAnswer?.mcpToolCalls && activeAnswer.mcpToolCalls.length > 0 && (
                    <div className="mb-3">
                      <McpToolCallPanel
                        toolCalls={activeAnswer.mcpToolCalls}
                        toolResponses={activeAnswer.mcpToolResponses}
                      />
                    </div>
                  )}
                  <div>
                    <MarkdownRender
                      content={activeAnswer?.content || ''}
                      imageStyle="card"
                      variant="chat"
                    />
                  </div>
                </>
              )}
            </div>
          </div>

          {/* 统计信息和功能按钮 - 只在有内容或错误时显示 */}
          {
            <div className="mt-2 flex items-center gap-1.5 px-1">
              {/* Token 统计图标 - hover 显示详情 */}
              <Tooltip
                color="#ffffff"
                overlayInnerStyle={{
                  border: '1px solid #e5e7eb',
                  borderRadius: 8,
                  boxShadow: '0 4px 12px rgba(0, 0, 0, 0.1)',
                  color: '#333',
                }}
                overlayStyle={{ maxWidth: 'none' }}
                title={
                  <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-gray-700">
                    <span>
                      {t('messages.firstToken')}: {formatTime(activeAnswer?.firstTokenTime)}
                    </span>
                    <span>
                      {t('messages.totalTime')}: {formatTime(activeAnswer?.totalTime)}
                    </span>
                    <span>
                      {t('messages.inputTokens')}: {activeAnswer?.inputTokens ?? '-'}
                    </span>
                    <span>
                      {t('messages.outputTokens')}: {activeAnswer?.outputTokens ?? '-'}
                    </span>
                  </div>
                }
              >
                <button className="rounded-md p-1.5 text-gray-400 transition-colors duration-200 hover:bg-gray-100 hover:text-gray-600">
                  <BarChartOutlined className="text-sm" />
                </button>
              </Tooltip>

              {/* 复制 */}
              <Tooltip
                color="#ffffff"
                overlayInnerStyle={{
                  border: '1px solid #e5e7eb',
                  borderRadius: 8,
                  boxShadow: '0 4px 12px rgba(0, 0, 0, 0.1)',
                  color: '#333',
                }}
                placement="top"
                title={t('messages.copy')}
              >
                <button
                  aria-label={t('messages.copy')}
                  className={`rounded-md p-1.5 transition-colors duration-200 ${copiedId === question.id ? 'text-colorPrimary' : 'text-gray-400 hover:bg-gray-100 hover:text-gray-600'} `}
                  onClick={() => handleCopy(activeAnswer?.content || '', question.id)}
                >
                  <CopyOutlined className="text-sm" />
                </button>
              </Tooltip>

              {/* 重新生成 */}
              <Tooltip
                color="#ffffff"
                overlayInnerStyle={{
                  border: '1px solid #e5e7eb',
                  borderRadius: 8,
                  boxShadow: '0 4px 12px rgba(0, 0, 0, 0.1)',
                  color: '#333',
                }}
                placement="top"
                title={t('messages.regenerate')}
              >
                <button
                  aria-label={t('messages.regenerate')}
                  className="rounded-md p-1.5 text-gray-400 transition-colors duration-200 hover:bg-gray-100 hover:text-gray-600"
                  onClick={() => {
                    onRefresh?.(conversation, question, isLast);
                  }}
                >
                  <ReloadOutlined className="text-sm" />
                </button>
              </Tooltip>

              {/* 版本切换按钮 - 仅在有多个版本时显示 */}
              {question.answers?.length > 1 && (
                <div className="ml-1 inline-flex items-center gap-0.5 rounded-[8px] px-0.5">
                  <button
                    aria-label={t('messages.previousVersion')}
                    className={`flex h-7 w-7 items-center justify-center rounded-[8px] transition-colors duration-200 ${
                      question.activeAnswerIndex === 0
                        ? 'cursor-not-allowed text-gray-300'
                        : 'text-gray-400 hover:bg-white/70 hover:text-gray-700'
                    } `}
                    disabled={question.activeAnswerIndex === 0}
                    onClick={() => onChangeVersion?.(conversation.id, question.id, 'prev')}
                  >
                    <LeftOutlined className="text-xs" />
                  </button>
                  <span className="min-w-[42px] px-1 text-center text-[13px] font-medium leading-7 tabular-nums text-gray-500">
                    {(question.activeAnswerIndex ?? 0) + 1}
                    <span className="mx-1 text-gray-300">/</span>
                    <span className="text-gray-400">{question.answers.length}</span>
                  </span>
                  <button
                    aria-label={t('messages.nextVersion')}
                    className={`flex h-7 w-7 items-center justify-center rounded-[8px] transition-colors duration-200 ${
                      question.activeAnswerIndex === question.answers.length - 1
                        ? 'cursor-not-allowed text-gray-300'
                        : 'text-gray-400 hover:bg-white/70 hover:text-gray-700'
                    } `}
                    disabled={question.activeAnswerIndex === question.answers.length - 1}
                    onClick={() => onChangeVersion?.(conversation.id, question.id, 'next')}
                  >
                    <RightOutlined className="text-xs" />
                  </button>
                </div>
              )}
            </div>
          }
        </div>
      </div>
    </div>
  );
}
