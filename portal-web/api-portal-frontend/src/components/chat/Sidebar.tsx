import { useState, useEffect } from "react";
import {
  PlusOutlined, DownOutlined,
  MenuFoldOutlined, MenuUnfoldOutlined,
  HistoryOutlined,
  //  RobotOutlined,
} from "@ant-design/icons";
import { message as antdMessage, Spin } from "antd";
import { getSessions, type Session } from "../../lib/api";

interface SidebarProps {
  currentSessionId: string | null;
  onNewChat: () => void;
  onSelectSession?: (sessionId: string) => void;
}

interface ChatSession {
  id: string;
  title: string;
  timestamp: Date;
}

const categorizeSessionsByTime = (sessions: ChatSession[]) => {
  const now = new Date();
  const today: ChatSession[] = [];
  const last7Days: ChatSession[] = [];
  const last30Days: ChatSession[] = [];

  sessions.forEach(session => {
    const diffInDays = Math.floor((now.getTime() - session.timestamp.getTime()) / (1000 * 60 * 60 * 24));

    if (diffInDays === 0) {
      today.push(session);
    } else if (diffInDays <= 7) {
      last7Days.push(session);
    } else if (diffInDays <= 30) {
      last30Days.push(session);
    }
  });

  return { today, last7Days, last30Days };
};

export function Sidebar({ currentSessionId, onNewChat, onSelectSession }: SidebarProps) {
  // const [selectedFeature, setSelectedFeature] = useState("language-model");
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [expandedSections, setExpandedSections] = useState({
    today: true,
    last7Days: true,
    last30Days: true,
  });
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [loading, setLoading] = useState(false);

  // 获取会话列表
  useEffect(() => {
    const fetchSessions = async () => {
      setLoading(true);
      try {
        const response: any = await getSessions({ page: 0, size: 50 });

        if (response.code === "SUCCESS" && response.data?.content) {
          const sessionList: ChatSession[] = response.data.content.map((session: Session) => ({
            id: session.sessionId,
            title: session.name || "未命名会话",
            timestamp: new Date(session.updateAt || session.createAt),
          }));
          setSessions(sessionList);
        }
      } catch (error) {
        console.error("Failed to fetch sessions:", error);
        antdMessage.error("获取会话列表失败");
      } finally {
        setLoading(false);
      }
    };

    fetchSessions();
  }, []);

  const { today, last7Days, last30Days } = categorizeSessionsByTime(sessions);

  // 检测操作系统
  const isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0;

  // 监听快捷键
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      // Shift + Command/Ctrl + O
      if (event.shiftKey && (isMac ? event.metaKey : event.ctrlKey) && event.key.toLowerCase() === 'o') {
        event.preventDefault();
        onNewChat();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isMac, onNewChat]);

  const toggleSection = (section: keyof typeof expandedSections) => {
    setExpandedSections(prev => ({
      ...prev,
      [section]: !prev[section],
    }));
  };

  const renderSessionGroup = (
    title: string,
    sessions: ChatSession[],
    sectionKey: keyof typeof expandedSections
  ) => {
    if (sessions.length === 0) return null;

    return (
      <div className="mb-4">
        <div
          className="flex items-center justify-between px-3 py-2 text-sm text-gray-600 cursor-pointer hover:bg-gray-50 rounded-lg transition-all duration-200 hover:scale-[1.02]"
          onClick={() => toggleSection(sectionKey)}
        >
          <span className="font-medium">{title}</span>
          <span
            className={`
              transition-transform duration-300 ease-in-out
              ${expandedSections[sectionKey] ? "rotate-0" : "-rotate-90"}
            `}
          >
            <DownOutlined className="text-xs" />
          </span>
        </div>
        <div
          className={`
            overflow-hidden transition-all duration-300 ease-in-out
            ${expandedSections[sectionKey] ? "max-h-[500px] opacity-100 mt-1" : "max-h-0 opacity-0"}
          `}
        >
          <div className="space-y-1">
            {sessions.map((session, index) => (
              <div
                key={session.id}
                onClick={() => onSelectSession?.(session.id)}
                className={`
                  px-3 py-2 rounded-lg cursor-pointer text-sm
                  transition-all duration-200 ease-in-out
                  hover:scale-[1.02] hover:shadow-sm
                  ${currentSessionId === session.id
                    ? "bg-gray-100 text-gray-900 font-medium"
                    : "text-gray-600 hover:bg-gray-100 hover:text-gray-900"
                  }
                `}
                style={{
                  animationDelay: `${index * 30}ms`,
                  animation: expandedSections[sectionKey] ? 'slideIn 300ms ease-out forwards' : 'none',
                }}
              >
                <div className="flex items-center gap-2">
                  <span className="truncate">{session.title}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  };

  return (
    <div
      className={`
        bg-white/40 backdrop-blur-xl rounded-lg flex flex-col m-4 mr-0
        transition-all duration-300 ease-in-out
        ${isCollapsed ? "w-16" : "w-64"}
      `}
    >
      {/* 新增会话按钮 */}
      <div className="p-4">
        <button
          onClick={onNewChat}
          className={`
            flex items-center bg-white border border-gray-200 rounded-lg
            transition-all duration-200 ease-in-out
            hover:bg-gray-50 hover:shadow-md hover:scale-[1.02] active:scale-95
            ${isCollapsed ? "w-8 h-8 p-0 justify-center" : "w-full px-3 py-2 justify-between"}
          `}
          title={isCollapsed ? "新增会话" : ""}
        >
          {isCollapsed ? (
            <PlusOutlined className="transition-transform duration-200 hover:rotate-90" />
          ) : (
            <>
              <div className="flex items-center gap-2">
                <PlusOutlined className="transition-transform duration-200 text-sm" />
                <span className="text-sm font-medium">新增会话</span>
              </div>
              <div className="flex items-center gap-0.5 text-xs text-gray-400">
                <kbd className="px-1.5 py-0.5 bg-gray-100 rounded text-xs font-sans">
                  {isMac ? '⇧' : 'Shift'}
                </kbd>
                <span className="text-gray-400">+</span>
                <kbd className="px-1.5 py-0.5 bg-gray-100 rounded text-xs font-sans">
                  {isMac ? '⌘' : 'Ctrl'}
                </kbd>
                <span className="text-gray-400">+</span>
                <kbd className="px-1.5 py-0.5 bg-gray-100 rounded text-xs font-sans">
                  O
                </kbd>
              </div>
            </>
          )}
        </button>
      </div>

      {/* 功能列表 */}
      {/* <div className="px-4 mb-4">
        <div
          onClick={() => setSelectedFeature("language-model")}
          className={`
            rounded-lg cursor-pointer font-medium
            transition-all duration-200 ease-in-out
            hover:scale-[1.02] active:scale-95
            ${
              selectedFeature === "language-model"
                ? "bg-white text-gray-900 shadow-md"
                : "text-gray-600 hover:bg-white hover:shadow-sm"
            }
            ${isCollapsed ? "px-2 py-2 text-center" : "px-3 py-2 text-sm"}
          `}
          title={isCollapsed ? "语言模型" : ""}
        >
          {isCollapsed ? (
            <RobotOutlined className="text-base transition-transform duration-200 hover:scale-110" />
          ) : (
            "语言模型"
          )}
        </div>
      </div> */}

      {/* 历史会话列表 */}
      {!isCollapsed ? (
        <div className="flex-1 overflow-y-auto px-4 pb-4">
          {loading ? (
            <div className="flex items-center justify-center py-8">
              <Spin tip="加载中..." />
            </div>
          ) : sessions.length === 0 ? (
            <div className="text-center py-8 text-gray-400 text-sm">
              暂无历史会话
            </div>
          ) : (
            <>
              {renderSessionGroup("今天", today, "today")}
              {renderSessionGroup("近7天", last7Days, "last7Days")}
              {renderSessionGroup("近30天", last30Days, "last30Days")}
            </>
          )}
        </div>
      ) : (
        <div className="flex-1 overflow-y-auto px-4 pb-4">
          <div
            className="px-2 py-2 text-center text-gray-600 hover:bg-gray-100 rounded-lg cursor-pointer transition-all duration-200 hover:scale-[1.05] active:scale-95"
            title="历史会话"
          >
            <HistoryOutlined className="text-base transition-transform duration-200 hover:rotate-12" />
          </div>
        </div>
      )}

      {/* 收起/展开按钮 */}
      <div className="p-4">
        <button
          onClick={() => setIsCollapsed(!isCollapsed)}
          className={`
            flex items-center gap-2 text-gray-600 rounded-lg
            transition-all duration-200 ease-in-out
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
