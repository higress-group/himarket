import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { Mcp } from '../icon';

import type { IMcpToolCall, IMcpToolResponse } from '../../types';

interface McpToolCallItemProps {
  toolCall: IMcpToolCall;
  toolResponse?: IMcpToolResponse;
}

// 单个工具调用组件 - 用于内联展示
export function McpToolCallItem({ toolCall, toolResponse }: McpToolCallItemProps) {
  const { t } = useTranslation('chat');
  const [expanded, setExpanded] = useState(false);

  const mcpServerName = toolCall.mcpServerName;
  const toolName = toolCall.name;
  const isExecuting = !toolResponse;

  let parsedInput: unknown = null;
  let parsedResponse: unknown = null;
  try {
    parsedInput = JSON.parse(toolCall.arguments || '{}');
  } catch {
    parsedInput = toolCall.arguments;
  }
  try {
    const resultString =
      typeof toolResponse?.result === 'string'
        ? toolResponse.result
        : JSON.stringify(toolResponse?.result || '{}');
    parsedResponse = JSON.parse(resultString || '{}');
  } catch {
    parsedResponse = toolResponse?.result;
  }

  return (
    <div className="overflow-hidden rounded-[14px] border border-[#DDE5F0] bg-[#F8FAFE] transition-all duration-200 hover:border-[#CCD8EA]">
      {/* Header */}
      <button
        className="flex w-full cursor-pointer items-center justify-between border-0 bg-transparent px-3.5 py-3 text-left transition-colors hover:bg-white/55"
        onClick={() => setExpanded(!expanded)}
        type="button"
      >
        <div className="flex items-center gap-3">
          {/* Icon 容器 - 白色背景 + 边框 + 阴影 */}
          <div className="flex h-9 w-9 items-center justify-center rounded-[10px] border border-[#DDE5F0] bg-white shadow-[0_4px_12px_rgba(37,56,88,0.05)]">
            <Mcp className="h-[18px] w-[18px] fill-colorPrimary" />
          </div>
          <div className="flex flex-col gap-0.5">
            <span className="text-[15px] font-semibold text-gray-800">{toolName}</span>
            <span className="text-xs text-gray-500">{mcpServerName}</span>
          </div>
        </div>
        <div className="flex items-center gap-3">
          {/* 状态标签 - 白色背景 + 边框 */}
          <div
            className={`flex items-center gap-1.5 rounded-full border bg-white px-2.5 py-1 text-xs font-medium shadow-[0_1px_2px_rgba(0,0,0,0.03)] ${isExecuting ? 'border-amber-100 text-amber-600' : 'border-emerald-100 text-emerald-600'}`}
          >
            <span
              className={`h-1.5 w-1.5 rounded-full ${isExecuting ? 'bg-amber-500' : 'bg-emerald-500'}`}
            />
            <span>{isExecuting ? t('toolCall.executing') : t('toolCall.completed')}</span>
          </div>
          {/* 展开图标 */}
          <svg
            className={`h-5 w-5 text-gray-400 transition-transform duration-200 ${expanded ? 'rotate-180' : ''}`}
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            viewBox="0 0 24 24"
          >
            <path d="M6 9l6 6 6-6" />
          </svg>
        </div>
      </button>

      {/* Content */}
      {expanded && (
        <div className="border-t border-[#DDE5F0] px-3.5 pb-3.5">
          <div className="space-y-3 pt-3">
            {/* Parameters */}
            <div>
              <div className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-gray-500">
                {t('toolCall.parameters')}
              </div>
              <div className="overflow-x-auto rounded-lg border border-gray-100 bg-white p-3">
                <pre className="whitespace-pre-wrap font-mono text-xs text-gray-700">
                  {typeof parsedInput === 'object'
                    ? JSON.stringify(parsedInput, null, 2)
                    : String(parsedInput)}
                </pre>
              </div>
            </div>

            {/* Results */}
            {toolResponse && (
              <div>
                <div className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-gray-500">
                  {t('toolCall.results')}
                </div>
                <div className="overflow-x-auto rounded-lg border border-gray-100 bg-white p-3">
                  <pre className="whitespace-pre-wrap font-mono text-xs text-gray-700">
                    {typeof parsedResponse === 'object'
                      ? JSON.stringify(parsedResponse, null, 2)
                      : String(parsedResponse)}
                  </pre>
                </div>
              </div>
            )}

            {/* 等待状态 */}
            {!toolResponse && (
              <div className="flex items-center gap-2 py-2 text-sm text-gray-500">
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-gray-200 border-t-colorPrimary" />
                <span>{t('toolCall.waiting')}</span>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

interface McpToolCallPanelProps {
  toolCalls?: IMcpToolCall[];
  toolResponses?: IMcpToolResponse[];
}

export function McpToolCallPanel({ toolCalls = [], toolResponses = [] }: McpToolCallPanelProps) {
  if (toolCalls.length === 0) {
    return null;
  }

  // 合并 toolCall 和 toolResponse（通过 id 匹配）
  const toolItems = toolCalls.map((toolCall) => {
    const toolResponse = toolResponses?.find((resp) => resp.id === toolCall.id);
    return { toolCall, toolResponse };
  });

  return (
    <div className="space-y-2">
      {toolItems.map(({ toolCall, toolResponse }, index) => (
        <McpToolCallItem
          key={`mcp-tool-${index}`}
          toolCall={toolCall}
          toolResponse={toolResponse}
        />
      ))}
    </div>
  );
}
