import { useState, useEffect, useCallback } from "react";
import {
  PlusOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  CloseOutlined,
  MoreOutlined,
} from "@ant-design/icons";
import { Dropdown } from "antd";
import type { MenuProps } from "antd";
import { useQuestState } from "../../context/QuestSessionContext";
import "../chat/Sidebar.css";
import type { QuestData } from "../../context/QuestSessionContext";

interface SessionSidebarProps {
  onSwitchQuest: (questId: string) => void;
  onCloseQuest: (questId: string) => void;
  onNewQuest?: () => void;
}

export function SessionSidebar({
  onSwitchQuest,
  onCloseQuest,
  onNewQuest,
}: SessionSidebarProps) {
  const state = useQuestState();
  const [isCollapsed, setIsCollapsed] = useState(false);

  const questList = Object.values(state.quests).sort(
    (a, b) => b.createdAt - a.createdAt,
  );

  const isMac = navigator.platform.toUpperCase().indexOf("MAC") >= 0;

  const handleNewQuest = useCallback(() => {
    onNewQuest?.();
  }, [onNewQuest]);

  // 快捷键: Shift + Cmd/Ctrl + O
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (
        event.shiftKey &&
        (isMac ? event.metaKey : event.ctrlKey) &&
        event.key.toLowerCase() === "o"
      ) {
        event.preventDefault();
        handleNewQuest();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isMac, handleNewQuest]);

  const getSessionMenu = (quest: QuestData): MenuProps => ({
    items: [
      {
        key: "close",
        label: "关闭",
        icon: <CloseOutlined />,
        danger: true,
        onClick: ({ domEvent }) => {
          domEvent.stopPropagation();
          onCloseQuest(quest.id);
        },
      },
    ],
  });

  return (
    <div
      className={`
        bg-white/50 backdrop-blur-xl rounded-lg flex flex-col ml-4
        transition-all duration-300 ease-in-out chat-session--sidebar
        ${isCollapsed ? "w-16" : "w-64"}
      `}
    >
      {/* 新会话按钮 */}
      <div className="p-4">
        <button
          onClick={handleNewQuest}
          className={`
            flex items-center bg-white rounded-lg
            border-[4px] border-colorPrimaryBgHover/50
            transition-all duration-200 ease-in-out
            hover:bg-gray-50 hover:shadow-md hover:scale-[1.02] active:scale-95 text-nowrap overflow-hidden
            ${isCollapsed ? "w-8 h-8 p-0 justify-center" : "w-full px-3 py-2 justify-between"}
          `}
          title={isCollapsed ? "新会话" : ""}
        >
          {isCollapsed ? (
            <PlusOutlined className="transition-transform duration-200 hover:rotate-90" />
          ) : (
            <>
              <div className="flex items-center gap-2">
                <PlusOutlined className="transition-transform duration-200 text-sm" />
                <span className="text-sm font-medium">新会话</span>
              </div>
              <div className="flex items-center gap-1 text-xs text-gray-400">
                <kbd className="px-1.5 py-0.5 bg-gray-100 rounded text-xs font-sans">
                  {isMac ? "⇧" : "Shift"}
                </kbd>
                <kbd className="px-1.5 py-0.5 bg-gray-100 rounded text-xs font-sans">
                  {isMac ? "⌘" : "Ctrl"}
                </kbd>
                <kbd className="px-1.5 py-0.5 bg-gray-100 rounded text-xs font-sans">
                  O
                </kbd>
              </div>
            </>
          )}
        </button>
      </div>

      {/* 会话列表 */}
      {!isCollapsed ? (
        <div className="flex-1 overflow-y-auto px-4 pb-4 sidebar-content">
          {questList.length === 0 ? (
            <div className="text-center py-8 text-gray-400 text-sm">
              暂无历史会话
            </div>
          ) : (
            <div className="space-y-1">
              {questList.map((quest) => (
                <SessionItem
                  key={quest.id}
                  quest={quest}
                  active={state.activeQuestId === quest.id}
                  onClick={() => onSwitchQuest(quest.id)}
                  menuItems={getSessionMenu(quest)}
                />
              ))}
            </div>
          )}
        </div>
      ) : (
        <div className="flex-1" />
      )}

      {/* 底部：收起/展开 */}
      <div className="p-4">
        <button
          onClick={() => setIsCollapsed(!isCollapsed)}
          className={`
            flex items-center gap-2 text-gray-600 rounded-lg
            transition-all duration-200 ease-in-out overflow-hidden text-nowrap
            hover:bg-gray-100 hover:scale-[1.02] active:scale-95
            ${isCollapsed ? "w-8 h-8 p-0 justify-center mx-auto" : "w-full px-4 py-2 justify-center"}
          `}
          title={isCollapsed ? "展开" : "收起"}
        >
          {isCollapsed ? (
            <MenuUnfoldOutlined className="transition-transform duration-200 hover:translate-x-1" />
          ) : (
            <>
              <MenuFoldOutlined className="transition-transform duration-200 hover:-translate-x-1" />
              <span className="text-sm">收起</span>
            </>
          )}
        </button>
      </div>
    </div>
  );
}

/* ─── Session Item ─── */

interface SessionItemProps {
  quest: QuestData;
  active: boolean;
  onClick: () => void;
  menuItems: MenuProps;
}

function SessionItem({ quest, active, onClick, menuItems }: SessionItemProps) {
  return (
    <div
      className={`
        px-3 py-2 rounded-lg text-sm cursor-pointer
        transition-all duration-200 ease-in-out
        hover:scale-[1.02] hover:shadow-sm text-mainTitle
        ${
          active
            ? "bg-colorPrimaryHoverLight font-medium"
            : "text-gray-600 hover:bg-colorPrimaryHoverLight hover:text-gray-900"
        }
      `}
      onClick={onClick}
    >
      <div className="flex items-center gap-2 group">
        <span className="truncate flex-1 min-w-0">{quest.title}</span>
        {quest.isProcessing && (
          <span className="w-1.5 h-1.5 rounded-full bg-blue-500 animate-pulse flex-shrink-0" />
        )}
        <Dropdown
          menu={menuItems}
          trigger={["click"]}
          placement="bottomRight"
          classNames={{ root: "session-menu-dropdown" }}
          popupRender={(menu) => (
            <div className="bg-white/80 backdrop-blur-xl rounded-xl shadow-lg border border-white/40 overflow-hidden">
              {menu}
            </div>
          )}
        >
          <button
            onClick={(e) => e.stopPropagation()}
            className="opacity-0 group-hover:opacity-100 p-1 text-gray-400 hover:text-colorPrimary hover:bg-gray-200 rounded transition-all flex-shrink-0"
            title="更多操作"
          >
            <MoreOutlined className="text-base" />
          </button>
        </Dropdown>
      </div>
    </div>
  );
}
