import { useState } from "react";
import { Collapse } from "antd";
import { Mcp } from "../icon";
import type { IMcpToolCall, IMcpToolResponse } from "../../types";

interface McpToolCallPanelProps {
  toolCalls?: IMcpToolCall[];
  toolResponses?: IMcpToolResponse[];
}

export function McpToolCallPanel({ toolCalls = [], toolResponses = [] }: McpToolCallPanelProps) {
  const [activeKey, setActiveKey] = useState<string | string[]>([]);

  if (toolCalls.length === 0) {
    return null;
  }

  // 合并 toolCall 和 toolResponse（通过 id 匹配）
  const toolItems = toolCalls.map((toolCall) => {
    const toolResponse = toolResponses?.find((resp) => resp.id === toolCall.id);
    return { toolCall, toolResponse };
  });

  return (
    <div className="my-4">
      {toolItems.map(({ toolCall, toolResponse }, index) => {
        const panelKey = `mcp-tool-${index}`;
        const mcpServerName = toolCall.toolMeta.mcpNameCn || toolCall.toolMeta.mcpName;
        const toolName = toolCall.toolMeta.toolNameCn || toolCall.toolMeta.toolName;

        // 解析 JSON 字符串
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        let parsedInput: any = null;
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        let parsedResponse: any = null;
        try {
          parsedInput = JSON.parse(toolCall.input || toolCall.arguments || "{}");
        } catch {
          parsedInput = toolCall.input || toolCall.arguments;
        }
        try {
          parsedResponse = JSON.parse(toolResponse?.responseData || toolResponse?.output || "{}");
        } catch {
          parsedResponse = toolResponse?.responseData || toolResponse?.output;
        }

        return (
          <Collapse
            key={panelKey}
            activeKey={activeKey}
            onChange={setActiveKey}
            items={[
              {
                key: panelKey,
                label: (
                  <div className="flex items-center gap-2">
                    <Mcp className="w-4 h-4 fill-colorPrimary" />
                    <span className="font-medium text-colorPrimary">MCP 工具执行中</span>
                    <span className="text-gray-500">· {mcpServerName}</span>
                  </div>
                ),
                children: (
                  <div className="space-y-4">
                    {/* MCP Server 名称 */}
                    <div>
                      <div className="text-xs font-medium text-gray-500 mb-1">MCP Server</div>
                      <div className="text-sm text-gray-900">{mcpServerName}</div>
                    </div>

                    {/* Tools 列表 */}
                    <div>
                      <div className="text-xs font-medium text-gray-500 mb-1">Tool</div>
                      <div className="text-sm text-gray-900">{toolName}</div>
                    </div>

                    {/* Parameters */}
                    <div>
                      <div className="text-xs font-medium text-gray-500 mb-1">Parameters</div>
                      <div className="bg-gray-50 rounded-lg p-3 overflow-x-auto">
                        <pre className="text-xs text-gray-700 whitespace-pre-wrap">
                          {typeof parsedInput === "object"
                            ? JSON.stringify(parsedInput, null, 2)
                            : String(parsedInput)}
                        </pre>
                      </div>
                    </div>

                    {/* Results */}
                    {toolResponse && (
                      <div>
                        <div className="text-xs font-medium text-gray-500 mb-1">Results</div>
                        <div className="bg-green-50 rounded-lg p-3 overflow-x-auto border border-green-100">
                          <pre className="text-xs text-gray-700 whitespace-pre-wrap">
                            {typeof parsedResponse === "object"
                              ? JSON.stringify(parsedResponse, null, 2)
                              : String(parsedResponse)}
                          </pre>
                        </div>
                      </div>
                    )}

                    {/* 如果还没有响应，显示等待状态 */}
                    {!toolResponse && (
                      <div className="text-sm text-gray-400 italic">等待工具响应...</div>
                    )}
                  </div>
                ),
              },
            ]}
            className="bg-blue-50/50 border border-blue-100"
          />
        );
      })}
    </div>
  );
}
