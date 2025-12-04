import { CopyOutlined, ReloadOutlined, LeftOutlined, RightOutlined, DownCircleOutlined } from "@ant-design/icons";
import { useEffect, useRef, useState } from "react";
import { message } from "antd";
import MarkdownRender from "../MarkdownRender";
import { ProductIconRenderer } from "../icon/ProductIconRenderer";
import { McpToolCallPanel } from "./McpToolCallPanel";
import type { IModelConversation } from "../../types";
import { copyToClipboard } from "../../lib/utils";

interface MessageListProps {
  conversations: IModelConversation['conversations'];
  modelName?: string;
  modelIcon?: string; // 添加模型 icon
  onRefresh?: (msg: IModelConversation['conversations'][0], quest: IModelConversation['conversations'][0]['questions'][0], isLast: boolean) => void;
  onChangeVersion?: (conversationId: string, questionId: string, direction: 'prev' | 'next') => void;
  autoScrollEnabled?: boolean;
}

export function Messages({
  conversations, modelName = "AI Assistant", modelIcon, onRefresh, onChangeVersion,
  autoScrollEnabled = true, 
}: MessageListProps) {
  const messagesEndRef = useRef<HTMLDivElement>(null);


  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
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
                key={question.id}
                conversation={conversation}
                question={question}
                activeAnswer={activeAnswer}
                modelIcon={modelIcon}
                modelName={modelName}
                isNewChat={question.isNewQuestion !== false}
                onChangeVersion={onChangeVersion}
                onRefresh={onRefresh}
                isLast={index === conversations.length - 1}
              />
            )
          })
        })}
        <div ref={messagesEndRef} />
      </div>
    </div >
  );
}

function Message({
  conversation, question, activeAnswer, modelIcon, modelName, isNewChat,
  isLast,
  onChangeVersion, onRefresh,
}: {
  conversation: IModelConversation["conversations"][0];
  question: IModelConversation["conversations"][0]['questions'][0],
  activeAnswer: IModelConversation["conversations"][0]['questions'][0]['answers'][0],
  modelIcon?: string; modelName?: string; isNewChat?: boolean;
  isLast: boolean;
  onChangeVersion?: (conversationId: string, questionId: string, direction: 'prev' | 'next') => void;
  onRefresh?: (msg: IModelConversation['conversations'][0], quest: IModelConversation['conversations'][0]['questions'][0], isLast: boolean) => void;
}) {

  const contentRef = useRef<HTMLDivElement>(null);

  const [expandedContent, setExpandedContent] = useState(true);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const handleCopy = async (content: string, messageId: string) => {
    copyToClipboard(content).then(() => {
      message.success("已复制到剪贴板");
      setCopiedId(messageId);
      setTimeout(() => setCopiedId(null), 2000);
    });
  };

  const formatTime = (ms?: number) => {
    if (ms === undefined) return "-";
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(2)}s`;
  };

  useEffect(() => {
    if (contentRef.current) {
      if (contentRef.current.getBoundingClientRect().height < 160) {
        setExpandedContent(false);
      }
    }
  }, [])

  return (
    <div key={question.id}>
      <div className="flex justify-end">
        <div className="max-w-[80%]">
          <div className="bg-colorPrimaryBgHover px-4 py-3 rounded-lg">
            <div className="whitespace-pre-wrap leading-relaxed">
              {question.content}
            </div>
          </div>
        </div>
      </div>
      <div className="flex flex-col gap-2">
        <div className="flex items-center gap-2">
          {/* 模型头像 */}
          <div className="w-8 h-8 rounded-full bg-gradient-to-br from-colorPrimary/20 to-colorPrimary/10 flex items-center justify-center flex-shrink-0">
            <ProductIconRenderer iconType={modelIcon} className="w-5 h-5" />
          </div>
          {/* 模型名称 */}
          <div className="text-sm text-gray-500 mb-1.5">{modelName}</div>
        </div>

        {/* MCP 工具调用面板 */}
        {(question.mcpToolCalls && question.mcpToolCalls.length > 0) && (
          <McpToolCallPanel
            toolCalls={question.mcpToolCalls}
            toolResponses={question.mcpToolResponses}
          />
        )}

        {/* 消息内容 */}
        <div className="flex-1">
          <div
            ref={contentRef}
            className={`${!isNewChat && expandedContent ? "max-h-40 overflow-hidden" : "overflow-auto"} relative  bg-white/80 backdrop-blur-sm px-4 py-3 rounded-lg border border-gray-100`}>
            {
              !isNewChat && expandedContent && (
                <div
                  onClick={() => setExpandedContent(false)}
                  className="bottom-mask flex justify-center items-end cursor-pointer absolute -bottom-px h-14 w-full " style={{ background: "linear-gradient(rgba(255, 255, 255, .4) 9%, rgb(255, 255, 255) 100%)" }}>
                  <DownCircleOutlined className="text-gray-500 mb-2" />
                </div>
              )
            }
            {/* 如果是错误状态，显示错误提示 */}
            {activeAnswer?.errorMsg ? (
              <div className="flex items-center gap-2 text-red-500">
                <span>{activeAnswer?.errorMsg || '网络异常，请重试'}</span>
              </div>
            ) : conversation.loading ? (
              /* 如果内容为空且正在加载，显示 loading */
              <div className="flex items-center gap-2 text-gray-500">
                <div className="flex items-center gap-1">
                  <span className="w-1.5 h-1.5 bg-colorPrimary rounded-full" style={{ animation: 'bounceStrong 1s infinite', animationDelay: '0ms' }}></span>
                  <span className="w-1.5 h-1.5 bg-colorPrimary rounded-full" style={{ animation: 'bounceStrong 1s infinite', animationDelay: '150ms' }}></span>
                  <span className="w-1.5 h-1.5 bg-colorPrimary rounded-full" style={{ animation: 'bounceStrong 1s infinite', animationDelay: '300ms' }}></span>
                </div>
              </div>
            ) : (
              <div className="prose">
                <MarkdownRender content={activeAnswer?.content} />
              </div>
            )}
          </div>

          {/* 统计信息和功能按钮 - 只在有内容或错误时显示 */}
          {(
            <div className="flex items-center justify-between mt-2 px-1">
              {/* 左侧：统计信息 */}
              <div className="flex items-center gap-2 text-xs text-gray-400">
                <span>首字： {formatTime(activeAnswer?.firstTokenTime)}</span>
                <span>耗时： {formatTime(activeAnswer?.totalTime)}</span>
                <span>输入 Token： {activeAnswer?.inputTokens ?? "-"}</span>
                <span>输出 Token： {activeAnswer?.outputTokens ?? "-"}</span>
              </div>

              {/* 右侧：功能按钮 */}
              <div className="flex items-center gap-1">
                <button
                  onClick={() => handleCopy(activeAnswer?.content, question.id)}
                  className={`
                            p-1.5 rounded-md transition-colors duration-200
                            ${copiedId === question.id ? "text-colorPrimary" : "text-gray-400 hover:text-gray-600 hover:bg-gray-100"}
                          `}
                  title="复制"
                >
                  <CopyOutlined className="text-sm" />
                </button>
                <button
                  onClick={() => {
                    onRefresh?.(conversation, question, isLast);
                  }}
                  className="p-1.5 rounded-md text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors duration-200"
                  title={"重新生成"}
                >
                  <ReloadOutlined className="text-sm" />
                </button>
                {/* 版本切换按钮 - 仅在有多个版本时显示 */}
                {question.answers?.length > 1 && (
                  <div className="flex items-center gap-1 mr-2 px-2 py-1 rounded-md">
                    <button
                      onClick={() => onChangeVersion?.(conversation.id, question.id, 'prev')}
                      disabled={question.activeAnswerIndex === 0}
                      className={`
                                p-1 rounded transition-colors duration-200
                                ${question.activeAnswerIndex === 0
                          ? "text-gray-300 cursor-not-allowed"
                          : "text-gray-500 hover:text-gray-700 hover:bg-gray-200"
                        }
                              `}
                      title="上一个版本"
                    >
                      <LeftOutlined className="text-xs" />
                    </button>
                    <span className="text-xs text-gray-600 font-medium min-w-[40px] text-center">
                      {(question.activeAnswerIndex ?? 0) + 1} / {question.answers.length}
                    </span>
                    <button
                      onClick={() => onChangeVersion?.(conversation.id, question.id, 'next')}
                      disabled={question.activeAnswerIndex === question.answers.length - 1}
                      className={`
                                p-1 rounded transition-colors duration-200
                                ${question.activeAnswerIndex === question.answers.length - 1
                          ? "text-gray-300 cursor-not-allowed"
                          : "text-gray-500 hover:text-gray-700 hover:bg-gray-200"
                        }
                              `}
                      title="下一个版本"
                    >
                      <RightOutlined className="text-xs" />
                    </button>
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}