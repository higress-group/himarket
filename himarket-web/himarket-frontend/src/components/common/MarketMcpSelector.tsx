import { useState, useEffect, useCallback, useMemo } from "react";
import { Alert, Spin, Button } from "antd";
import { RefreshCw } from "lucide-react";
import {
  getMarketMcps,
  type MarketMcpInfo,
  type McpServerEntry,
} from "../../lib/apis/cliProvider";
import { SelectableCard } from "../common/SelectableCard";
import { SearchFilterInput } from "../common/SearchFilterInput";
import { filterByKeyword } from "../../lib/utils/filterUtils";

// ============ 类型定义 ============

export interface MarketMcpSelectorProps {
  /** 选择 MCP Server 后回调，null 表示未选择 */
  onChange: (mcpServers: McpServerEntry[] | null) => void;
}

// 搜索过滤的阈值：列表超过此数量时显示搜索框
const SEARCH_THRESHOLD = 4;

// ============ 组件 ============

export function MarketMcpSelector({ onChange }: MarketMcpSelectorProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [mcpServers, setMcpServers] = useState<MarketMcpInfo[]>([]);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [searchKeyword, setSearchKeyword] = useState("");

  const fetchMcps = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await getMarketMcps();
      const data = res.data;
      setMcpServers(data.mcpServers ?? []);
    } catch (err: any) {
      if (err?.response?.status === 401) {
        setError("请先登录以使用市场 MCP Server");
      } else {
        setError(
          err instanceof Error ? err.message : "获取市场 MCP Server 列表失败"
        );
      }
    } finally {
      setLoading(false);
    }
  }, []);

  // 组件挂载时获取数据
  useEffect(() => {
    setSelectedIds([]);
    onChange(null);
    fetchMcps();
  }, [fetchMcps]);

  // 根据关键词过滤 MCP 列表（按名称和描述匹配）
  const filteredServers = useMemo(
    () => filterByKeyword(mcpServers, searchKeyword, ["name", "description"]),
    [mcpServers, searchKeyword]
  );

  // 切换卡片选中状态
  const handleToggle = useCallback(
    (productId: string) => {
      setSelectedIds((prev) => {
        const next = prev.includes(productId)
          ? prev.filter((id) => id !== productId)
          : [...prev, productId];

        // 组装 McpServerEntry 列表并回调（仅传递标识符）
        if (next.length === 0) {
          onChange(null);
        } else {
          const entries: McpServerEntry[] = next
            .map((id) => {
              const mcp = mcpServers.find((m) => m.productId === id);
              if (!mcp) return null;
              return { productId: mcp.productId, name: mcp.name };
            })
            .filter((e): e is McpServerEntry => e !== null);
          onChange(entries.length > 0 ? entries : null);
        }

        return next;
      });
    },
    [mcpServers, onChange]
  );

  // 加载中
  if (loading) {
    return (
      <div className="flex justify-center py-4">
        <Spin size="small" />
      </div>
    );
  }

  // 错误状态
  if (error) {
    return (
      <div className="flex flex-col items-center gap-2 w-full">
        <Alert message={error} type="error" showIcon className="w-full" />
        {error !== "请先登录以使用市场 MCP Server" && (
          <Button
            size="small"
            icon={<RefreshCw size={14} />}
            onClick={fetchMcps}
          >
            重试
          </Button>
        )}
      </div>
    );
  }

  // MCP 列表为空
  if (mcpServers.length === 0) {
    return (
      <Alert
        message="暂无已订阅的 MCP Server，请先在市场中订阅"
        type="info"
        showIcon
        className="w-full"
      />
    );
  }

  // MCP 列表非空，展示卡片网格
  return (
    <div className="flex flex-col gap-3 w-full">
      {/* 列表超过 4 项时显示搜索框 */}
      {mcpServers.length > SEARCH_THRESHOLD && (
        <SearchFilterInput
          value={searchKeyword}
          onChange={setSearchKeyword}
          placeholder="搜索 MCP Server..."
        />
      )}

      {/* 过滤后无匹配结果 */}
      {filteredServers.length === 0 ? (
        <div className="text-center text-sm text-gray-400 py-4">
          无匹配结果
        </div>
      ) : (
        /* 卡片网格布局 */
        <div className="grid grid-cols-2 gap-2 max-h-[280px] overflow-y-auto pr-1">
          {filteredServers.map((mcp) => (
            <SelectableCard
              key={mcp.productId}
              selected={selectedIds.includes(mcp.productId)}
              onClick={() => handleToggle(mcp.productId)}
            >
              <div className="flex flex-col gap-1">
                <span className="text-sm font-medium text-gray-800">
                  {mcp.name}
                </span>
                {mcp.description && (
                  <span className="text-xs text-gray-400 line-clamp-2">
                    {mcp.description}
                  </span>
                )}
              </div>
            </SelectableCard>
          ))}
        </div>
      )}
    </div>
  );
}
