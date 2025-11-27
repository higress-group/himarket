import { CopyOutlined, ReloadOutlined, LeftOutlined, RightOutlined } from "@ant-design/icons";
import { useEffect, useRef, useState } from "react";
import { message } from "antd";
import MarkdownRender from "../MarkdownRender";
import { ProductIconRenderer } from "../icon/ProductIconRenderer";

interface Message {
  id: string;
  role: "user" | "assistant";
  content: string;
  timestamp: Date;
  // AI 返回时的统计信息
  firstTokenTime?: number; // 首字符时间（毫秒）
  totalTime?: number; // 总耗时（毫秒）
  inputTokens?: number; // 输入 token
  outputTokens?: number; // 输出 token
  // 多版本答案支持
  questionId?: string;
  versions?: Array<{
    content: string;
    firstTokenTime?: number;
    totalTime?: number;
    inputTokens?: number;
    outputTokens?: number;
  }>;
  currentVersionIndex?: number;
  // 错误状态
  error?: boolean;
  errorMessage?: string;
}

interface MessageListProps {
  messages: Message[];
  modelName?: string;
  modelIcon?: string; // 添加模型 icon
  onRefresh?: (messageId: string) => void;
  onChangeVersion?: (messageId: string, direction: 'prev' | 'next') => void;
  isLoading?: boolean;
}

export function MessageList({ messages, modelName = "AI Assistant", modelIcon, onRefresh, onChangeVersion, isLoading = false }: MessageListProps) {
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const handleCopy = async (content: string, messageId: string) => {
    try {
      await navigator.clipboard.writeText(content);
      setCopiedId(messageId);
      message.success("已复制到剪贴板");
      setTimeout(() => setCopiedId(null), 2000);
    } catch {
      message.error("复制失败");
    }
  };

  const formatTime = (ms?: number) => {
    if (ms === undefined) return "-";
    if (ms < 1000) return `${ms}ms`;
    return `${(ms / 1000).toFixed(2)}s`;
  };

  return (
    <div className="mx-auto px-6 pb-4">
      <div className="space-y-6">
        {messages.map((msg, idx) => (
          <div key={msg.id + "-" + idx}>
            {msg.role === "user" ? (
              /* 用户消息 - 右对齐，无头像 */
              <div className="flex justify-end">
                <div className="max-w-[80%]">
                  <div className="bg-colorPrimaryBgHover px-4 py-3 rounded-lg">
                    <div className="whitespace-pre-wrap leading-relaxed">
                      {msg.content}
                    </div>
                  </div>
                </div>
              </div>
            ) : (
              /* AI 消息 - 左对齐，带头像和模型名称 */
              <div className="flex flex-col gap-2">
                <div className="flex items-center gap-2">
                  {/* 模型头像 */}
                  <div className="w-8 h-8 rounded-full bg-gradient-to-br from-colorPrimary/20 to-colorPrimary/10 flex items-center justify-center flex-shrink-0">
                    <ProductIconRenderer iconType={modelIcon} className="w-5 h-5" />
                  </div>
                  {/* 模型名称 */}
                  <div className="text-sm text-gray-500 mb-1.5">{modelName}</div>
                </div>

                {/* 消息内容 */}
                <div className="flex-1">
                  <div
                    className="overflow-auto bg-white/80 backdrop-blur-sm px-4 py-3 rounded-lg border border-gray-100">
                    {/* 如果是错误状态，显示错误提示 */}
                    {msg.error ? (
                      <div className="flex items-center gap-2 text-red-500">
                        <span>{msg.errorMessage || '网络异常，请重试'}</span>
                      </div>
                    ) : msg.content === '' && isLoading ? (
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
                        <MarkdownRender content={msg.content} />
                      </div>
                    )}
                  </div>

                  {/* 统计信息和功能按钮 - 只在有内容或错误时显示 */}
                  {(msg.content || msg.error) && (
                    <div className="flex items-center justify-between mt-2 px-1">
                      {/* 左侧：统计信息 */}
                      <div className="flex items-center gap-2 text-xs text-gray-400">
                        <span>首字： {formatTime(msg.firstTokenTime)}</span>
                        <span>耗时： {formatTime(msg.totalTime)}</span>
                        <span>输入 Token： {msg.inputTokens ?? "-"}</span>
                        <span>输出 Token： {msg.outputTokens ?? "-"}</span>
                      </div>

                      {/* 右侧：功能按钮 */}
                      <div className="flex items-center gap-1">
                        <button
                          onClick={() => handleCopy(msg.content, msg.id)}
                          className={`
                            p-1.5 rounded-md transition-colors duration-200
                            ${copiedId === msg.id ? "text-colorPrimary" : "text-gray-400 hover:text-gray-600 hover:bg-gray-100"}
                          `}
                          title="复制"
                        >
                          <CopyOutlined className="text-sm" />
                        </button>
                        <button
                          onClick={() => onRefresh?.(msg.id)}
                          className="p-1.5 rounded-md text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors duration-200"
                          title="重新生成"
                        >
                          <ReloadOutlined className="text-sm" />
                        </button>
                        {/* 版本切换按钮 - 仅在有多个版本时显示 */}
                        {msg.versions && msg.versions.length > 1 && (
                          <div className="flex items-center gap-1 mr-2 px-2 py-1 rounded-md">
                            <button
                              onClick={() => onChangeVersion?.(msg.id, 'prev')}
                              disabled={msg.currentVersionIndex === 0}
                              className={`
                                p-1 rounded transition-colors duration-200
                                ${msg.currentVersionIndex === 0
                                  ? "text-gray-300 cursor-not-allowed"
                                  : "text-gray-500 hover:text-gray-700 hover:bg-gray-200"
                                }
                              `}
                              title="上一个版本"
                            >
                              <LeftOutlined className="text-xs" />
                            </button>
                            <span className="text-xs text-gray-600 font-medium min-w-[40px] text-center">
                              {(msg.currentVersionIndex ?? 0) + 1} / {msg.versions.length}
                            </span>
                            <button
                              onClick={() => onChangeVersion?.(msg.id, 'next')}
                              disabled={msg.currentVersionIndex === msg.versions.length - 1}
                              className={`
                                p-1 rounded transition-colors duration-200
                                ${msg.currentVersionIndex === msg.versions.length - 1
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
            )}
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>
    </div>
  );
}
