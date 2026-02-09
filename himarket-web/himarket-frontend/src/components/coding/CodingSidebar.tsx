import { useState } from "react";
import { Plus } from "lucide-react";
import { useCodingState } from "../../context/CodingSessionContext";
import type { QuestData } from "../../context/CodingSessionContext";

interface CodingSidebarProps {
  onCreateQuest: () => void;
  onSwitchQuest: (questId: string) => void;
  onCloseQuest: (questId: string) => void;
}

export function CodingSidebar({
  onCreateQuest,
  onSwitchQuest,
  onCloseQuest,
}: CodingSidebarProps) {
  const state = useCodingState();
  const [hoveredId, setHoveredId] = useState<string | null>(null);

  const questList = Object.values(state.quests).sort(
    (a, b) => b.createdAt - a.createdAt
  );

  return (
    <div className="w-56 flex-shrink-0 flex flex-col border-r border-gray-200/60 bg-white/40 backdrop-blur-sm">
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200/60">
        <span className="text-sm font-semibold text-gray-700">HiCoding</span>
        <button
          className="w-7 h-7 flex items-center justify-center rounded-md text-gray-500
                     hover:bg-gray-100 hover:text-gray-700 transition-colors disabled:opacity-40"
          onClick={onCreateQuest}
          disabled={!state.initialized}
          title="New Quest"
        >
          <Plus size={16} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto py-1.5">
        {questList.length === 0 ? (
          <div className="text-xs text-gray-400 text-center mt-8 px-4">
            创建一个新的 Quest 开始编程
          </div>
        ) : (
          questList.map(quest => (
            <QuestItem
              key={quest.id}
              quest={quest}
              active={state.activeQuestId === quest.id}
              hovered={hoveredId === quest.id}
              onMouseEnter={() => setHoveredId(quest.id)}
              onMouseLeave={() => setHoveredId(null)}
              onClick={() => onSwitchQuest(quest.id)}
              onClose={e => {
                e.stopPropagation();
                onCloseQuest(quest.id);
              }}
            />
          ))
        )}
      </div>

      <div className="px-4 py-2 border-t border-gray-200/60">
        <div className="flex items-center gap-1.5 text-xs">
          <span
            className={`w-1.5 h-1.5 rounded-full ${state.connected ? "bg-green-500" : "bg-gray-400"}`}
          />
          <span className="text-gray-500">
            {state.connected ? "已连接" : "未连接"}
          </span>
        </div>
      </div>
    </div>
  );
}

interface QuestItemProps {
  quest: QuestData;
  active: boolean;
  hovered: boolean;
  onMouseEnter: () => void;
  onMouseLeave: () => void;
  onClick: () => void;
  onClose: (e: React.MouseEvent) => void;
}

function QuestItem({
  quest,
  active,
  hovered,
  onMouseEnter,
  onMouseLeave,
  onClick,
  onClose,
}: QuestItemProps) {
  const timeStr = formatRelativeTime(quest.createdAt);

  return (
    <div
      className={`
        flex items-center justify-between px-3 py-2 mx-1.5 rounded-lg cursor-pointer
        transition-all duration-200
        ${
          active
            ? "bg-white shadow-sm text-gray-900"
            : "text-gray-600 hover:bg-white/60 hover:text-gray-800"
        }
      `}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      onClick={onClick}
    >
      <div className="flex-1 min-w-0">
        <div className="text-sm truncate">{quest.title}</div>
        <div className="flex items-center gap-1.5 mt-0.5">
          <span className="text-[10px] text-gray-400">{timeStr}</span>
          {quest.isProcessing && (
            <span className="w-1.5 h-1.5 rounded-full bg-blue-500 animate-pulse" />
          )}
        </div>
      </div>
      {hovered && (
        <button
          className="ml-1 w-5 h-5 flex items-center justify-center rounded text-gray-400
                     hover:text-gray-600 hover:bg-gray-100 transition-colors text-xs"
          onClick={onClose}
          title="关闭 Quest"
        >
          &times;
        </button>
      )}
    </div>
  );
}

function formatRelativeTime(ts: number): string {
  const diff = Date.now() - ts;
  const sec = Math.floor(diff / 1000);
  if (sec < 60) return "刚刚";
  const min = Math.floor(sec / 60);
  if (min < 60) return `${min}分钟前`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr}小时前`;
  const day = Math.floor(hr / 24);
  return `${day}天前`;
}
