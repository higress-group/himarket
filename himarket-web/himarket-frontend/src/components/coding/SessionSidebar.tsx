import {
  PlusOutlined,
  DownOutlined,
  EditOutlined,
  DeleteOutlined,
  MoreOutlined,
  CheckOutlined,
  CloseOutlined,
} from '@ant-design/icons';
import { message as antdMessage, Spin, Dropdown, Modal } from 'antd';
import { PanelLeftClose, PanelLeftOpen } from 'lucide-react';
import { useState, useEffect, useCallback } from 'react';

import './SessionSidebar.css';
import {
  getCodingSessions,
  deleteCodingSession,
  updateCodingSession,
  type ICodingSession,
} from '../../lib/apis/codingSession';

import type { MenuProps } from 'antd';

export interface SessionSidebarProps {
  activeCliSessionId: string | null;
  agentSupportsLoadSession: boolean;
  onLoadSession: (
    cliSessionId: string,
    cwd: string,
    title: string,
    platformSessionId: string,
    providerKey: string,
  ) => void;
  onNewSession: () => void;
  /** External trigger to refresh the session list (increment to trigger) */
  refreshTrigger?: number;
  /** Whether the sidebar should be collapsed by default. Changes to this prop will sync the collapsed state. */
  defaultCollapsed?: boolean;
}

interface SessionItem {
  id: string;
  sessionId: string;
  cliSessionId: string;
  title: string;
  cwd: string;
  providerKey: string;
  modelName?: string;
  timestamp: Date;
}

function toSessionItems(sessions: ICodingSession[]): SessionItem[] {
  return sessions.map((s) => ({
    cliSessionId: s.cliSessionId,
    cwd: s.cwd,
    id: s.sessionId,
    modelName: s.modelName || undefined,
    providerKey: s.providerKey || '',
    sessionId: s.sessionId,
    timestamp: new Date(s.updatedAt || s.createdAt),
    title: s.title || '未命名会话',
  }));
}

function categorizeByTime(sessions: SessionItem[]) {
  const now = new Date();
  const today: SessionItem[] = [];
  const last7Days: SessionItem[] = [];
  const last30Days: SessionItem[] = [];

  sessions.forEach((session) => {
    const diffInDays = Math.floor(
      (now.getTime() - session.timestamp.getTime()) / (1000 * 60 * 60 * 24),
    );

    if (diffInDays === 0) {
      today.push(session);
    } else if (diffInDays <= 7) {
      last7Days.push(session);
    } else if (diffInDays <= 30) {
      last30Days.push(session);
    }
  });

  return { last30Days, last7Days, today };
}

export function SessionSidebar({
  activeCliSessionId,
  agentSupportsLoadSession,
  defaultCollapsed = true,
  onLoadSession,
  onNewSession,
  refreshTrigger,
}: SessionSidebarProps) {
  const [collapsed, setCollapsed] = useState(defaultCollapsed);

  // Sync collapsed state when the page transitions (e.g. welcome → conversation)
  useEffect(() => {
    setCollapsed(defaultCollapsed);
  }, [defaultCollapsed]);

  const [expandedSections, setExpandedSections] = useState({
    last30Days: false,
    last7Days: false,
    today: true,
  });
  const [sessions, setSessions] = useState<SessionItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [editingSessionId, setEditingSessionId] = useState<string | null>(null);
  const [editingName, setEditingName] = useState('');
  const [originalName, setOriginalName] = useState('');

  // 检测操作系统
  const isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0;

  // 获取会话列表
  const fetchSessions = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getCodingSessions({ page: 0, size: 50 });
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const data = res.data as any;
      const list: ICodingSession[] = Array.isArray(data.content)
        ? data.content
        : Array.isArray(data)
          ? data
          : [];
      setSessions(toSessionItems(list));
    } catch (err) {
      console.error('[SessionSidebar] Failed to fetch sessions:', err);
      antdMessage.error('获取会话列表失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchSessions();
  }, [fetchSessions, refreshTrigger]);

  // 监听快捷键 Shift + Command/Ctrl + O
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (
        event.shiftKey &&
        (isMac ? event.metaKey : event.ctrlKey) &&
        event.key.toLowerCase() === 'o'
      ) {
        event.preventDefault();
        onNewSession();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [isMac, onNewSession]);

  const toggleSection = (section: keyof typeof expandedSections) => {
    setExpandedSections((prev) => ({
      ...prev,
      [section]: !prev[section],
    }));
  };

  // 开始编辑会话名称
  const handleStartEdit = (sessionId: string, currentName: string) => {
    setEditingSessionId(sessionId);
    setEditingName(currentName);
    setOriginalName(currentName);
  };

  // 保存会话名称
  const handleSaveEdit = async (sessionId: string) => {
    const trimmedName = editingName.trim();

    if (!trimmedName) {
      antdMessage.error('会话名称不能为空');
      return;
    }

    if (trimmedName === originalName) {
      handleCancelEdit();
      return;
    }

    try {
      const response = await updateCodingSession(sessionId, {
        title: trimmedName,
      });
      if (response.code === 'SUCCESS') {
        setSessions((prev) =>
          prev.map((session) =>
            session.id === sessionId ? { ...session, title: trimmedName } : session,
          ),
        );
        antdMessage.success('重命名成功');
        setEditingSessionId(null);
        setEditingName('');
        setOriginalName('');
      } else {
        throw new Error('重命名失败');
      }
    } catch (error) {
      console.error('Failed to rename session:', error);
      antdMessage.error('重命名失败');
    }
  };

  // 取消编辑
  const handleCancelEdit = () => {
    setEditingSessionId(null);
    setEditingName('');
    setOriginalName('');
  };

  // 处理键盘事件
  const handleEditKeyDown = (e: React.KeyboardEvent, sessionId: string) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleSaveEdit(sessionId);
    } else if (e.key === 'Escape') {
      e.preventDefault();
      handleCancelEdit();
    }
  };

  // 删除会话
  const handleDeleteSession = (sessionId: string, sessionName: string) => {
    Modal.confirm({
      cancelText: '取消',
      content: `确定要删除会话 "${sessionName}" 吗？此操作无法撤销。`,
      okText: '删除',
      okType: 'danger',
      onOk: async () => {
        try {
          await deleteCodingSession(sessionId);
          setSessions((prev) => prev.filter((session) => session.id !== sessionId));
          antdMessage.success('删除成功');

          // 如果删除的是当前活跃会话，触发新建
          const deletedSession = sessions.find((s) => s.id === sessionId);
          if (deletedSession?.cliSessionId === activeCliSessionId) {
            onNewSession();
          }
        } catch (error) {
          console.error('Failed to delete session:', error);
          antdMessage.error('删除失败');
        }
      },
      title: '确认删除',
    });
  };

  // 渲染会话菜单
  const getSessionMenu = (session: SessionItem): MenuProps => ({
    items: [
      {
        icon: <EditOutlined />,
        key: 'rename',
        label: '重命名',
        onClick: ({ domEvent }) => {
          domEvent.stopPropagation();
          handleStartEdit(session.id, session.title);
        },
      },
      {
        danger: true,
        icon: <DeleteOutlined />,
        key: 'delete',
        label: '删除',
        onClick: ({ domEvent }) => {
          domEvent.stopPropagation();
          handleDeleteSession(session.id, session.title);
        },
      },
    ],
  });

  const { last30Days, last7Days, today } = categorizeByTime(sessions);

  const renderSessionGroup = (
    title: string,
    groupSessions: SessionItem[],
    sectionKey: keyof typeof expandedSections,
  ) => {
    if (groupSessions.length === 0) return null;

    return (
      <div className="mb-2">
        <button
          className={`${expandedSections[sectionKey] ? 'bg-white' : ''} sticky top-0 z-10 flex items-center justify-between px-3 py-2 text-sm text-subTitle cursor-pointer hover:bg-white/30 rounded-lg transition-all duration-200 hover:scale-[1.02] backdrop-blur-xl border-0 w-full text-left`}
          onClick={() => toggleSection(sectionKey)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault();
              toggleSection(sectionKey);
            }
          }}
          type="button"
        >
          <span className="font-medium">{title}</span>
          <span
            className={`
              transition-transform duration-300 ease-in-out
              ${expandedSections[sectionKey] ? 'rotate-0' : '-rotate-90'}
            `}
          >
            <DownOutlined className="text-xs" />
          </span>
        </button>
        <div
          className={`
            overflow-auto transition-all duration-300 ease-in-out sidebar-level-1
            ${expandedSections[sectionKey] ? 'opacity-100 mt-1' : 'max-h-0 opacity-0'}
          `}
        >
          <div className="space-y-1">
            {groupSessions.map((session, index) => {
              const isActive = session.cliSessionId === activeCliSessionId;
              return (
                <div
                  className={`
                    px-3 py-2 rounded-lg text-sm
                    transition-all duration-200 ease-in-out
                    hover:scale-[1.02] hover:shadow-sm text-mainTitle
                    ${
                      isActive
                        ? 'bg-colorPrimaryHoverLight font-medium'
                        : agentSupportsLoadSession || activeCliSessionId === null
                          ? 'text-gray-600 hover:bg-colorPrimaryHoverLight hover:text-gray-900 cursor-pointer'
                          : 'text-gray-600 opacity-60'
                    }
                  `}
                  key={session.id}
                  style={{
                    animation: expandedSections[sectionKey]
                      ? 'slideIn 300ms ease-out forwards'
                      : 'none',
                    animationDelay: `${index * 30}ms`,
                  }}
                >
                  {editingSessionId === session.id ? (
                    /* 编辑模式 */
                    <div
                      className="flex items-center gap-2"
                      onClick={(e) => e.stopPropagation()}
                      role="presentation"
                    >
                      <input
                        autoFocus
                        className="flex-1 max-w-[70%] px-2 py-1 text-sm border border-colorPrimary rounded focus:outline-none focus:ring-1 focus:ring-colorPrimary"
                        onBlur={() => {
                          handleCancelEdit();
                        }}
                        onChange={(e) => setEditingName(e.target.value)}
                        onKeyDown={(e) => handleEditKeyDown(e, session.id)}
                        type="text"
                        value={editingName}
                      />
                      <button
                        className="flex-shrink-0 p-1 text-green-600 hover:bg-green-50 rounded transition-colors"
                        onClick={(e) => {
                          e.stopPropagation();
                          handleSaveEdit(session.id);
                        }}
                        onMouseDown={(e) => {
                          e.preventDefault();
                          e.stopPropagation();
                        }}
                        title="保存"
                      >
                        <CheckOutlined className="text-sm" />
                      </button>
                      <button
                        className="flex-shrink-0 p-1 text-gray-600 hover:bg-gray-100 rounded transition-colors"
                        onClick={(e) => {
                          e.stopPropagation();
                          handleCancelEdit();
                        }}
                        onMouseDown={(e) => {
                          e.preventDefault();
                          e.stopPropagation();
                        }}
                        title="取消"
                      >
                        <CloseOutlined className="text-sm" />
                      </button>
                    </div>
                  ) : (
                    /* 正常模式 */
                    <button
                      className="group border-0 bg-transparent w-full text-left px-0 py-0"
                      onClick={() => {
                        if (activeCliSessionId !== null && !agentSupportsLoadSession) return;
                        onLoadSession(
                          session.cliSessionId,
                          session.cwd,
                          session.title,
                          session.sessionId,
                          session.providerKey,
                        );
                      }}
                      type="button"
                    >
                      <div className="flex items-center gap-2">
                        <span className="truncate flex-1">{session.title}</span>
                        <Dropdown
                          classNames={{
                            root: 'coding-session-menu-dropdown',
                          }}
                          menu={getSessionMenu(session)}
                          placement="bottomRight"
                          popupRender={(menu) => (
                            <div className="bg-white/80 backdrop-blur-xl rounded-xl shadow-lg border border-white/40 overflow-hidden">
                              {menu}
                            </div>
                          )}
                          trigger={['click']}
                        >
                          <button
                            className="opacity-0 group-hover:opacity-100 p-1 text-gray-400 hover:text-colorPrimary hover:bg-gray-200 rounded transition-all"
                            onClick={(e) => {
                              e.stopPropagation();
                            }}
                            title="更多操作"
                          >
                            <MoreOutlined className="text-base" />
                          </button>
                        </Dropdown>
                      </div>
                      {(session.providerKey || session.modelName) && (
                        <div className="text-[11px] text-gray-400 truncate mt-0.5">
                          {[session.providerKey, session.modelName].filter(Boolean).join(' · ')}
                        </div>
                      )}
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </div>
    );
  };

  /* ========== 收起态 ========== */
  if (collapsed) {
    return (
      <div className="flex flex-col items-center py-3 px-1 gap-2 flex-shrink-0 bg-white/50 backdrop-blur-xl transition-all duration-300 ease-in-out w-12">
        {/* 展开按钮 */}
        <button
          className="w-8 h-8 flex items-center justify-center rounded-lg
                     text-gray-400 hover:text-colorPrimary hover:bg-colorPrimaryHoverLight
                     transition-all duration-200"
          onClick={() => setCollapsed(false)}
          title="展开侧栏"
        >
          <PanelLeftOpen size={16} />
        </button>
        {/* 新会话 */}
        <button
          className="w-8 h-8 flex items-center justify-center rounded-lg
                     text-gray-400 hover:text-colorPrimary hover:bg-colorPrimaryHoverLight
                     transition-all duration-200"
          onClick={onNewSession}
          title="新会话"
        >
          <PlusOutlined className="text-sm" />
        </button>
      </div>
    );
  }

  /* ========== 展开态 ========== */
  return (
    <div className="bg-white/50 backdrop-blur-xl flex flex-col flex-shrink-0 transition-all duration-300 ease-in-out w-64 coding-session--sidebar">
      {/* 顶部操作栏：收起 + 新会话 */}
      <div className="flex items-center gap-2 p-3">
        <button
          className="w-8 h-8 flex items-center justify-center rounded-lg flex-shrink-0
                     text-gray-400 hover:text-colorPrimary hover:bg-colorPrimaryHoverLight
                     transition-all duration-200"
          onClick={() => setCollapsed(true)}
          title="收起侧栏"
        >
          <PanelLeftClose size={16} />
        </button>
        <button
          className="flex-1 flex items-center justify-between bg-white rounded-lg
                     border-[3px] border-colorPrimaryBgHover/50 px-3 py-1.5
                     transition-all duration-200 ease-in-out
                     hover:bg-gray-50 hover:shadow-md hover:scale-[1.02] active:scale-95
                     text-nowrap overflow-hidden"
          onClick={onNewSession}
        >
          <div className="flex items-center gap-2">
            <PlusOutlined className="text-sm" />
            <span className="text-sm font-medium">新会话</span>
          </div>
          <div className="flex items-center gap-1 text-xs text-gray-400">
            <kbd className="px-1 py-0.5 bg-gray-100 rounded text-[10px] font-sans">
              {isMac ? '⇧' : 'Shift'}
            </kbd>
            <kbd className="px-1 py-0.5 bg-gray-100 rounded text-[10px] font-sans">
              {isMac ? '⌘' : 'Ctrl'}
            </kbd>
            <kbd className="px-1 py-0.5 bg-gray-100 rounded text-[10px] font-sans">O</kbd>
          </div>
        </button>
      </div>

      {/* 分隔线 */}
      <div className="h-[1px] bg-[#e5e5e5] mx-3 mb-1"></div>

      {/* 历史会话列表 */}
      <div className="flex-1 overflow-y-auto px-3 pb-3 sidebar-content">
        {loading ? (
          <div className="flex items-center justify-center py-8">
            <Spin />
          </div>
        ) : sessions.length === 0 ? (
          <div className="text-center py-8 text-gray-400 text-sm">暂无历史会话</div>
        ) : (
          <>
            {renderSessionGroup('今天', today, 'today')}
            {renderSessionGroup('近7天', last7Days, 'last7Days')}
            {renderSessionGroup('近30天', last30Days, 'last30Days')}
          </>
        )}
      </div>

      {/* 不支持会话恢复的提示 */}
      {!agentSupportsLoadSession && activeCliSessionId !== null && sessions.length > 0 && (
        <div className="px-3 py-1.5 text-[11px] text-gray-400 text-center">
          当前 CLI 不支持会话恢复
        </div>
      )}
    </div>
  );
}
