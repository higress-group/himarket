import { useState, useEffect } from "react";
import {
  PlusOutlined, DownOutlined,
  MenuFoldOutlined, MenuUnfoldOutlined,
  EditOutlined,
  DeleteOutlined,
  MoreOutlined,
  CheckOutlined,
  CloseOutlined,
  //  RobotOutlined,
} from "@ant-design/icons";
import { message as antdMessage, Spin, Dropdown, Modal } from "antd";
import type { MenuProps } from "antd";
import "./Sidebar.css";
import APIs, { type ISession } from "../../lib/apis";

interface SidebarProps {
  currentSessionId?: string;
  onNewChat: () => void;
  onSelectSession?: (sessionId: string, productIds: string[]) => void;
  refreshTrigger?: number; // 添加刷新触发器
}

interface ChatSession {
  id: string;
  title: string;
  timestamp: Date;
  productIds: string[];
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

export function Sidebar({ currentSessionId, onNewChat, onSelectSession, refreshTrigger }: SidebarProps) {
  // const [selectedFeature, setSelectedFeature] = useState("language-model");
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [expandedSections, setExpandedSections] = useState({
    today: true,
    last7Days: false,
    last30Days: false,
  });
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [loading, setLoading] = useState(false);
  const [editingSessionId, setEditingSessionId] = useState<string | null>(null);
  const [editingName, setEditingName] = useState("");
  const [originalName, setOriginalName] = useState(""); // 保存原始名称用于取消

  // 获取会话列表
  useEffect(() => {
    const fetchSessions = async () => {
      setLoading(true);
      try {
        const response = await APIs.getSessions({ page: 0, size: 50 });

        if (response.code === "SUCCESS" && response.data?.content) {
          const sessionList: ChatSession[] = response.data.content.map((session: ISession) => ({
            id: session.sessionId,
            title: session.name || "未命名会话",
            timestamp: new Date(session.updateAt || session.createAt),
            productIds: session.products || [],
          }));
          console.log('Loaded sessions:', sessionList);
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
  }, [refreshTrigger]); // 当 refreshTrigger 改变时重新获取

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

  // 开始编辑会话名称
  const handleStartEdit = (sessionId: string, currentName: string) => {
    setEditingSessionId(sessionId);
    setEditingName(currentName);
    setOriginalName(currentName); // 保存原始名称
  };

  // 保存会话名称
  const handleSaveEdit = async (sessionId: string) => {
    const trimmedName = editingName.trim();

    if (!trimmedName) {
      antdMessage.error("会话名称不能为空");
      return;
    }

    // 如果名称没有改变，直接取消编辑
    if (trimmedName === originalName) {
      handleCancelEdit();
      return;
    }

    try {
      const response = await APIs.updateSession(sessionId, { name: trimmedName });
      if (response.code === "SUCCESS") {
        // 更新本地状态
        setSessions(prev => prev.map(session =>
          session.id === sessionId
            ? { ...session, title: trimmedName }
            : session
        ));
        antdMessage.success("重命名成功");
        setEditingSessionId(null);
        setEditingName("");
        setOriginalName("");
      } else {
        throw new Error("重命名失败");
      }
    } catch (error) {
      console.error("Failed to rename session:", error);
      antdMessage.error("重命名失败");
    }
  };

  // 取消编辑
  const handleCancelEdit = () => {
    setEditingSessionId(null);
    setEditingName("");
    setOriginalName("");
  };

  // 处理键盘事件
  const handleEditKeyDown = (e: React.KeyboardEvent, sessionId: string) => {
    if (e.key === "Enter") {
      e.preventDefault();
      handleSaveEdit(sessionId);
    } else if (e.key === "Escape") {
      e.preventDefault();
      handleCancelEdit();
    }
  };

  // 删除会话
  const handleDeleteSession = (sessionId: string, sessionName: string) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除会话 "${sessionName}" 吗？此操作无法撤销。`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          const response = await APIs.deleteSession(sessionId);
          if (response.code === "SUCCESS") {
            // 从本地状态中移除
            setSessions(prev => prev.filter(session => session.id !== sessionId));
            antdMessage.success("删除成功");

            // 如果删除的是当前选中的会话，触发新建会话
            if (currentSessionId === sessionId) {
              onNewChat();
            }
          } else {
            throw new Error("删除失败");
          }
        } catch (error) {
          console.error("Failed to delete session:", error);
          antdMessage.error("删除失败");
        }
      },
    });
  };

  // 渲染会话菜单
  const getSessionMenu = (session: ChatSession): MenuProps => ({
    items: [
      {
        key: 'rename',
        label: '重命名',
        icon: <EditOutlined />,
        onClick: ({ domEvent }) => {
          domEvent.stopPropagation();
          handleStartEdit(session.id, session.title);
        },
      },
      {
        key: 'delete',
        label: '删除',
        icon: <DeleteOutlined />,
        danger: true,
        onClick: ({ domEvent }) => {
          domEvent.stopPropagation();
          handleDeleteSession(session.id, session.title);
        },
      },
    ],
  });

  const renderSessionGroup = (
    title: string,
    sessions: ChatSession[],
    sectionKey: keyof typeof expandedSections
  ) => {
    if (sessions.length === 0) return null;

    return (
      <div className="mb-2">
        <div
          className={`${expandedSections[sectionKey] ? "bg-white" : ""} sticky top-0 z-10 flex items-center justify-between px-3 py-2 text-sm text-subTitle cursor-pointer hover:bg-white/30 rounded-lg transition-all duration-200 hover:scale-[1.02]  backdrop-blur-xl`}
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
            overflow-auto transition-all duration-300 ease-in-out sidebar-level-1
            ${expandedSections[sectionKey] ? "opacity-100 mt-1" : "max-h-0 opacity-0"}
          `}
        >
          <div className="space-y-1">
            {sessions.map((session, index) => (
              <div
                key={session.id}
                className={`
                  px-3 py-2 rounded-lg text-sm
                  transition-all duration-200 ease-in-out
                  hover:scale-[1.02] hover:shadow-sm text-mainTitle
                  ${currentSessionId === session.id
                    ? "bg-colorPrimaryBgHover font-medium"
                    : "text-gray-600 hover:bg-colorPrimaryBgHover hover:text-gray-900"
                  }
                `}
                style={{
                  animationDelay: `${index * 30}ms`,
                  animation: expandedSections[sectionKey] ? 'slideIn 300ms ease-out forwards' : 'none',
                }}
              >
                {editingSessionId === session.id ? (
                  /* 编辑模式 */
                  <div className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
                    <input
                      type="text"
                      value={editingName}
                      onChange={(e) => setEditingName(e.target.value)}
                      onKeyDown={(e) => handleEditKeyDown(e, session.id)}
                      onBlur={() => {
                        handleCancelEdit();
                      }}
                      className="flex-1 max-w-[70%] px-2 py-1 text-sm border border-colorPrimary rounded focus:outline-none focus:ring-1 focus:ring-colorPrimary"
                      autoFocus
                    />
                    <button
                      onMouseDown={(e) => {
                        e.preventDefault(); // 防止触发 input 的 blur
                        e.stopPropagation();
                      }}
                      onClick={(e) => {
                        e.stopPropagation();
                        handleSaveEdit(session.id);
                      }}
                      className="flex-shrink-0 p-1 text-green-600 hover:bg-green-50 rounded transition-colors"
                      title="保存"
                    >
                      <CheckOutlined className="text-sm" />
                    </button>
                    <button
                      onMouseDown={(e) => {
                        e.preventDefault(); // 防止触发 input 的 blur
                        e.stopPropagation();
                      }}
                      onClick={(e) => {
                        e.stopPropagation();
                        handleCancelEdit();
                      }}
                      className="flex-shrink-0 p-1 text-gray-600 hover:bg-gray-100 rounded transition-colors"
                      title="取消"
                    >
                      <CloseOutlined className="text-sm" />
                    </button>
                  </div>
                ) : (
                  /* 正常模式 */
                  <div
                    className="flex items-center gap-2 cursor-pointer group"
                    onClick={() => {
                      console.log('Selected session:', session.id, 'productIds:', session.productIds);
                      onSelectSession?.(session.id, session.productIds);
                    }}
                  >
                    <span className="truncate flex-1">{session.title}</span>
                    <Dropdown
                      menu={getSessionMenu(session)}
                      trigger={['click']}
                      placement="bottomRight"
                      overlayClassName="session-menu-dropdown"
                      popupRender={(menu) => (
                        <div className="bg-white/80 backdrop-blur-xl rounded-xl shadow-lg border border-white/40 overflow-hidden">
                          {menu}
                        </div>
                      )}
                    >
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                        }}
                        className="opacity-0 group-hover:opacity-100 p-1 text-gray-400 hover:text-colorPrimary hover:bg-gray-200 rounded transition-all"
                        title="更多操作"
                      >
                        <MoreOutlined className="text-base" />
                      </button>
                    </Dropdown>
                  </div>
                )}
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
        bg-white/50 backdrop-blur-xl rounded-lg flex flex-col ml-4
        transition-all duration-300 ease-in-out chat-session--sidebar
        ${isCollapsed ? "w-16" : "w-64"}
      `}
    >
      {/* 新增话按钮 */}
      <div className="p-4">
        <button
          onClick={onNewChat}
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
                  {isMac ? '⇧' : 'Shift'}
                </kbd>
                <kbd className="px-1.5 py-0.5 bg-gray-100 rounded text-xs font-sans">
                  {isMac ? '⌘' : 'Ctrl'}
                </kbd>
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
        <div className="flex-1 overflow-y-auto px-4 pb-4 sidebar-content">
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
        <div className="flex-1 overflow-y-auto px-4 pb-4" />
        // <div className="flex-1 overflow-y-auto px-4 pb-4">
        //   <div
        //     className="px-2 py-2 text-center text-gray-600 hover:bg-gray-100 rounded-lg cursor-pointer transition-all duration-200 hover:scale-[1.05] active:scale-95"
        //     title="历史会话"
        //   >
        //     <HistoryOutlined className="text-base transition-transform duration-200 hover:rotate-12" />
        //   </div>
        // </div>
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
