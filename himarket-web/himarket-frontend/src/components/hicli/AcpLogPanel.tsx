import { useState, useRef, useEffect } from "react";
import { Search, ChevronDown, ChevronRight } from "lucide-react";
import { useHiCliState } from "../../context/HiCliSessionContext";
import { filterLogs } from "../../lib/utils/logFilter";
import type { AggregatedLogEntry } from "../../types/log";

/** 方法名 → Tailwind 文本色类映射 */
const METHOD_COLOR_MAP: Record<string, string> = {
  initialize: "text-purple-600",
  "session/new": "text-blue-600",
  "session/prompt": "text-emerald-600",
  "session/update": "text-yellow-600",
  "session/set_model": "text-orange-500",
  "session/set_mode": "text-orange-500",
  "session/cancel": "text-red-600",
  "session/request_permission": "text-pink-600",
  "fs/read_text_file": "text-gray-500",
  "fs/write_text_file": "text-gray-500",
  "terminal/create": "text-gray-500",
  "terminal/output": "text-gray-500",
};

function getMethodColorClass(method?: string): string {
  if (!method) return "text-gray-400";
  return METHOD_COLOR_MAP[method] ?? "text-gray-400";
}

/** 格式化时间戳为 HH:MM:SS.mmm */
function formatTime(ts: number): string {
  const d = new Date(ts);
  return (
    d.toLocaleTimeString("en-US", {
      hour12: false,
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    }) +
    "." +
    String(d.getMilliseconds()).padStart(3, "0")
  );
}

export interface AcpLogPanelProps {
  filter: string;
  onFilterChange: (filter: string) => void;
}

export function AcpLogPanel({ filter, onFilterChange }: AcpLogPanelProps) {
  const { aggregatedLogs } = useHiCliState();
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set());
  const scrollRef = useRef<HTMLDivElement>(null);
  const autoScrollRef = useRef(true);

  const filtered = filterLogs(aggregatedLogs, filter);

  // 自动滚动到最新日志
  useEffect(() => {
    if (autoScrollRef.current && scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [filtered.length]);

  const handleScroll = () => {
    if (!scrollRef.current) return;
    const { scrollTop, scrollHeight, clientHeight } = scrollRef.current;
    autoScrollRef.current = scrollHeight - scrollTop - clientHeight < 40;
  };

  const toggleExpand = (id: string) => {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  return (
    <div className="flex flex-col h-full">
      {/* 工具栏：过滤输入 + 计数 */}
      <div className="flex items-center gap-2 px-3 py-2 border-b border-gray-200/60">
        <div className="relative flex-1">
          <Search
            size={14}
            className="absolute left-2 top-1/2 -translate-y-1/2 text-gray-400"
          />
          <input
            type="text"
            className="w-full pl-7 pr-2 py-1.5 text-xs rounded-md border border-gray-200
                       bg-white/60 placeholder-gray-400 focus:outline-none focus:ring-1
                       focus:ring-blue-500/40 focus:border-blue-400 transition-colors"
            placeholder="按 method 或摘要过滤..."
            value={filter}
            onChange={(e) => onFilterChange(e.target.value)}
          />
        </div>
        <span className="text-[11px] text-gray-400 whitespace-nowrap">
          {filtered.length} 条
        </span>
      </div>

      {/* 日志列表 */}
      <div
        className="flex-1 overflow-y-auto"
        ref={scrollRef}
        onScroll={handleScroll}
      >
        {filtered.length === 0 ? (
          <div className="text-xs text-gray-400 text-center mt-12">
            暂无日志
          </div>
        ) : (
          filtered.map((entry) => (
            <LogEntryRow
              key={entry.id}
              entry={entry}
              expanded={expandedIds.has(entry.id)}
              onToggle={() => toggleExpand(entry.id)}
            />
          ))
        )}
      </div>
    </div>
  );
}

/** 单条日志行 */
function LogEntryRow({
  entry,
  expanded,
  onToggle,
}: {
  entry: AggregatedLogEntry;
  expanded: boolean;
  onToggle: () => void;
}) {
  const isSend = entry.direction === "client_to_agent";
  const methodColor = getMethodColorClass(entry.method);

  return (
    <div
      className={`border-b border-gray-100 ${
        isSend ? "bg-white/40" : "bg-blue-50/30"
      }`}
    >
      {/* 日志头部 - 可点击展开 */}
      <div
        className="flex items-center gap-1.5 px-3 py-1.5 cursor-pointer
                   hover:bg-gray-50/80 transition-colors select-none"
        onClick={onToggle}
      >
        {/* 方向箭头 */}
        <span
          className={`text-xs font-mono ${
            isSend ? "text-orange-500" : "text-blue-500"
          }`}
          title={isSend ? "发送" : "接收"}
        >
          {isSend ? "↑" : "↓"}
        </span>

        {/* method 名称（带颜色编码） */}
        <span
          className={`text-xs font-medium truncate max-w-[160px] ${methodColor}`}
        >
          {entry.method ?? entry.summary}
        </span>

        {/* RPC ID */}
        {entry.rpcId !== undefined && (
          <span className="text-[10px] text-gray-400 font-mono">
            id:{entry.rpcId}
          </span>
        )}

        {/* 聚合消息数徽章 */}
        {entry.isAggregated && entry.messageCount > 1 && (
          <span
            className="inline-flex items-center justify-center px-1.5 py-0.5
                       text-[10px] font-medium leading-none rounded-full
                       bg-blue-100 text-blue-600"
          >
            ×{entry.messageCount}
          </span>
        )}

        {/* 弹性间隔 */}
        <span className="flex-1" />

        {/* 时间戳 */}
        <span className="text-[10px] text-gray-400 font-mono whitespace-nowrap">
          {formatTime(entry.timestamp)}
        </span>

        {/* 展开/折叠指示 */}
        {expanded ? (
          <ChevronDown size={12} className="text-gray-400 flex-shrink-0" />
        ) : (
          <ChevronRight size={12} className="text-gray-400 flex-shrink-0" />
        )}
      </div>

      {/* 展开后显示完整 JSON */}
      {expanded && (
        <pre
          className="mx-3 mb-2 p-2 text-[11px] leading-relaxed font-mono
                     bg-gray-50 rounded-md border border-gray-200/60
                     overflow-x-auto text-gray-700 whitespace-pre-wrap break-all"
        >
          {JSON.stringify(entry.data, null, 2)}
        </pre>
      )}
    </div>
  );
}
