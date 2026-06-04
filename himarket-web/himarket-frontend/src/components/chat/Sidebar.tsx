import {
  PlusOutlined,
  DownOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  EditOutlined,
  DeleteOutlined,
  MoreOutlined,
  CheckOutlined,
  CloseOutlined,
} from '@ant-design/icons';
import { message as antdMessage, Spin, Dropdown, Modal } from 'antd';
import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';

import './Sidebar.css';
import APIs, { type ISession } from '../../lib/apis';
import { FileVideo, Image, MessageSquareQuote } from '../icon';

import type { MenuProps } from 'antd';

interface SidebarProps {
  currentSessionId?: string;
  onNewChat: () => void;
  onSelectSession?: (sessionId: string, productIds: string[]) => void;
  refreshTrigger?: number; // 添加刷新触发器
  selectedType?: 'TEXT' | 'Image';
  onSelectType?: (type: 'TEXT' | 'Image') => void;
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
};

export function Sidebar({
  currentSessionId,
  onNewChat,
  onSelectSession,
  onSelectType,
  refreshTrigger,
  selectedType = 'TEXT',
}: SidebarProps) {
  const { t } = useTranslation('chat');
  // const [selectedFeature, setSelectedFeature] = useState("language-model");
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [expandedSections, setExpandedSections] = useState({
    last30Days: false,
    last7Days: false,
    today: true,
  });
  const [sessions, setSessions] = useState<ChatSession[]>([]);
  const [loading, setLoading] = useState(false);
  const [editingSessionId, setEditingSessionId] = useState<string | null>(null);
  const [editingName, setEditingName] = useState('');
  const [originalName, setOriginalName] = useState(''); // 保存原始名称用于取消

  // 获取会话列表
  useEffect(() => {
    const fetchSessions = async () => {
      setLoading(true);
      try {
        const response = await APIs.getSessions({ page: 1, size: 50 });

        if (response.code === 'SUCCESS' && response.data?.content) {
          const sessionList: ChatSession[] = response.data.content.map((session: ISession) => ({
            id: session.sessionId,
            productIds: session.products || [],
            timestamp: new Date(session.updateAt || session.createAt),
            title: session.name || t('sidebar.untitledSession'),
          }));
          setSessions(sessionList);
        }
      } catch (error) {
        console.error('Failed to fetch sessions:', error);
        antdMessage.error(t('sidebar.fetchSessionsFailed'));
      } finally {
        setLoading(false);
      }
    };

    fetchSessions();
  }, [refreshTrigger, t]); // 当 refreshTrigger 改变时重新获取

  const { last30Days, last7Days, today } = categorizeSessionsByTime(sessions);

  // 检测操作系统
  const isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0;

  // 监听快捷键
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      // Shift + Command/Ctrl + O
      if (
        event.shiftKey &&
        (isMac ? event.metaKey : event.ctrlKey) &&
        event.key.toLowerCase() === 'o'
      ) {
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
    setExpandedSections((prev) => ({
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
      antdMessage.error(t('sidebar.sessionNameRequired'));
      return;
    }

    // 如果名称没有改变，直接取消编辑
    if (trimmedName === originalName) {
      handleCancelEdit();
      return;
    }

    try {
      const response = await APIs.updateSession(sessionId, {
        name: trimmedName,
      });
      if (response.code === 'SUCCESS') {
        // 更新本地状态
        setSessions((prev) =>
          prev.map((session) =>
            session.id === sessionId ? { ...session, title: trimmedName } : session,
          ),
        );
        antdMessage.success(t('sidebar.renameSuccess'));
        setEditingSessionId(null);
        setEditingName('');
        setOriginalName('');
      } else {
        throw new Error(t('sidebar.renameFailed'));
      }
    } catch (error) {
      console.error('Failed to rename session:', error);
      antdMessage.error(t('sidebar.renameFailed'));
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
      cancelText: t('sidebar.cancel'),
      content: t('sidebar.deleteConfirm', { name: sessionName }),
      okText: t('sidebar.delete'),
      okType: 'danger',
      onOk: async () => {
        try {
          const response = await APIs.deleteSession(sessionId);
          if (response.code === 'SUCCESS') {
            // 从本地状态中移除
            setSessions((prev) => prev.filter((session) => session.id !== sessionId));
            antdMessage.success(t('sidebar.deleteSuccess'));

            // 如果删除的是当前选中的会话，触发新会话
            if (currentSessionId === sessionId) {
              onNewChat();
            }
          } else {
            throw new Error(t('sidebar.deleteFailed'));
          }
        } catch (error) {
          console.error('Failed to delete session:', error);
          antdMessage.error(t('sidebar.deleteFailed'));
        }
      },
      title: t('sidebar.deleteTitle'),
    });
  };

  // 渲染会话菜单
  const getSessionMenu = (session: ChatSession): MenuProps => ({
    items: [
      {
        icon: <EditOutlined />,
        key: 'rename',
        label: t('sidebar.rename'),
        onClick: ({ domEvent }) => {
          domEvent.stopPropagation();
          handleStartEdit(session.id, session.title);
        },
      },
      {
        danger: true,
        icon: <DeleteOutlined />,
        key: 'delete',
        label: t('sidebar.delete'),
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
    sectionKey: keyof typeof expandedSections,
  ) => {
    if (sessions.length === 0) return null;

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
            {sessions.map((session, index) => (
              <div
                className={`
                  px-3 py-2 rounded-lg text-sm
                  transition-all duration-200 ease-in-out
                  hover:scale-[1.02] hover:shadow-sm text-mainTitle
                  ${
                    currentSessionId === session.id
                      ? 'bg-colorPrimaryHoverLight font-medium'
                      : 'text-gray-600 hover:bg-colorPrimaryHoverLight hover:text-gray-900'
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
                        e.preventDefault(); // 防止触发 input 的 blur
                        e.stopPropagation();
                      }}
                      title={t('sidebar.save')}
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
                        e.preventDefault(); // 防止触发 input 的 blur
                        e.stopPropagation();
                      }}
                      title={t('sidebar.cancel')}
                    >
                      <CloseOutlined className="text-sm" />
                    </button>
                  </div>
                ) : (
                  /* 正常模式 */
                  <button
                    className="flex items-center gap-2 cursor-pointer group border-0 bg-transparent w-full text-left px-0 py-0"
                    onClick={() => {
                      onSelectSession?.(session.id, session.productIds);
                    }}
                    type="button"
                  >
                    <span className="truncate flex-1">{session.title}</span>
                    <Dropdown
                      classNames={{
                        root: 'session-menu-dropdown',
                      }}
                      menu={getSessionMenu(session)}
                      placement="bottomRight"
                      popupRender={(menu) => (
                        <div className="bg-white/80 backdrop-blur-xl rounded-[10px] shadow-lg border border-white/40 overflow-hidden">
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
                        title={t('sidebar.moreActions')}
                      >
                        <MoreOutlined className="text-base" />
                      </button>
                    </Dropdown>
                  </button>
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
        flex min-h-0 flex-col rounded-[16px] border border-white/70 bg-white/75
        shadow-[0_18px_50px_rgba(37,56,88,0.08)] backdrop-blur-xl
        transition-all duration-300 ease-in-out chat-session--sidebar
        ${isCollapsed ? 'w-[72px]' : 'w-[260px]'}
      `}
    >
      {/* 新增话按钮 */}
      <div className="p-4">
        <button
          className={`
            flex items-center rounded-[12px] border border-[#DDE5F0] bg-white/90
            transition-all duration-200 ease-in-out
            hover:border-colorPrimary/40 hover:shadow-sm active:scale-[0.98] text-nowrap overflow-hidden
            ${isCollapsed ? 'h-10 w-10 justify-center p-0' : 'w-full justify-between px-3 py-2.5'}
          `}
          onClick={onNewChat}
          title={isCollapsed ? t('sidebar.newChat') : ''}
        >
          {isCollapsed ? (
            <PlusOutlined className="transition-transform duration-200 hover:rotate-90" />
          ) : (
            <>
              <div className="flex items-center gap-2">
                <PlusOutlined className="transition-transform duration-200 text-sm" />
                <span className="text-sm font-medium">{t('sidebar.newChat')}</span>
              </div>
              <div className="flex items-center gap-1 text-xs text-gray-400">
                <kbd className="px-1.5 py-0.5 bg-gray-100 rounded text-xs font-sans">
                  {isMac ? '⇧' : 'Shift'}
                </kbd>
                <kbd className="px-1.5 py-0.5 bg-gray-100 rounded text-xs font-sans">
                  {isMac ? '⌘' : 'Ctrl'}
                </kbd>
                <kbd className="px-1.5 py-0.5 bg-gray-100 rounded text-xs font-sans">O</kbd>
              </div>
            </>
          )}
        </button>
      </div>

      {/* 功能列表 */}
      <div className="px-4 mb-4 flex flex-col gap-2">
        <button
          className={`
            px-3 py-2 rounded-lg text-sm cursor-pointer
            transition-all duration-200 ease-in-out overflow-hidden text-nowrap
            border-0 w-full text-left hover:bg-white/80
            ${selectedType === 'TEXT' ? 'bg-colorPrimaryHoverLight text-gray-900 font-medium' : 'text-gray-600 hover:bg-gray-100'}
            ${isCollapsed ? 'px-2 py-2 text-center' : 'px-3 py-2 text-sm'}
          `}
          onClick={() => onSelectType?.('TEXT')}
          title={isCollapsed ? t('sidebar.languageModel') : ''}
          type="button"
        >
          {isCollapsed ? (
            <MessageSquareQuote className="fill-mainTitle text-base transition-transform duration-200 hover:scale-110" />
          ) : (
            <div className="flex items-center gap-2">
              <MessageSquareQuote className="fill-mainTitle text-base transition-transform duration-200 hover:scale-110" />
              {t('sidebar.languageModel')}
            </div>
          )}
        </button>
        <button
          className={`
            px-3 py-2 rounded-lg text-sm overflow-hidden text-nowrap cursor-pointer
            transition-all duration-200 ease-in-out
            border-0 w-full text-left hover:bg-white/80
            ${selectedType === 'Image' ? 'bg-colorPrimaryHoverLight text-gray-900 font-medium' : 'text-gray-600 hover:bg-gray-100'}
            ${isCollapsed ? 'px-2 py-2 text-center' : 'px-3 py-2 text-sm'}
          `}
          onClick={() => onSelectType?.('Image')}
          title={isCollapsed ? t('sidebar.textToImage') : ''}
        >
          {isCollapsed ? (
            <Image className="fill-mainTitle text-base transition-transform duration-200 hover:scale-110" />
          ) : (
            <div className="flex items-center gap-2 justify-between">
              <div className="flex items-center gap-2">
                <Image className="fill-mainTitle text-base transition-transform duration-200 hover:scale-110" />
                {t('sidebar.textToImage')}
              </div>
            </div>
          )}
        </button>
        <div
          className={`
            px-3 py-2 rounded-lg text-sm overflow-hidden text-nowrap
            transition-all duration-200 ease-in-out
            text-gray-900 hover:bg-white/80
            ${isCollapsed ? 'px-2 py-2 text-center' : 'px-3 py-2 text-sm'}
          `}
          title={isCollapsed ? t('sidebar.textToVideo') : ''}
        >
          {isCollapsed ? (
            <FileVideo className="fill-mainTitle text-base transition-transform duration-200 hover:scale-110" />
          ) : (
            <div className="flex items-center gap-2 justify-between">
              <div className="flex items-center gap-2">
                <FileVideo className="fill-mainTitle text-base transition-transform duration-200 hover:scale-110" />
                {t('sidebar.textToVideo')}
              </div>
              <div className="py-1 px-2 rounded-[10px] bg-[#F3F4F6] text-[#99A1AF]">
                {t('sidebar.comingSoon')}
              </div>
            </div>
          )}
        </div>
        {!isCollapsed && <div className="h-[1px] bg-[#e5e5e5] my-2"></div>}
      </div>

      {/* 历史会话列表 */}
      {!isCollapsed ? (
        <div className="flex-1 overflow-y-auto px-4 pb-4 sidebar-content">
          {loading ? (
            <div className="flex items-center justify-center py-8">
              <Spin />
            </div>
          ) : sessions.length === 0 ? (
            <div className="text-center py-8 text-gray-400 text-sm">{t('sidebar.noHistory')}</div>
          ) : (
            <>
              {renderSessionGroup(t('sidebar.today'), today, 'today')}
              {renderSessionGroup(t('sidebar.last7Days'), last7Days, 'last7Days')}
              {renderSessionGroup(t('sidebar.last30Days'), last30Days, 'last30Days')}
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
          className={`
            flex items-center gap-2 text-gray-600 rounded-lg
            transition-all duration-200 ease-in-out overflow-hidden text-nowrap
            hover:bg-white/80 active:scale-[0.98]
            ${isCollapsed ? 'mx-auto h-10 w-10 justify-center p-0' : 'w-full justify-center px-4 py-2'}
          `}
          onClick={() => setIsCollapsed(!isCollapsed)}
          title={isCollapsed ? t('sidebar.expand') : t('sidebar.collapse')}
        >
          {isCollapsed ? (
            <MenuUnfoldOutlined className="transition-transform duration-200 hover:translate-x-1" />
          ) : (
            <>
              <MenuFoldOutlined className="transition-transform duration-200 hover:-translate-x-1" />
              <span className="text-sm">{t('sidebar.collapse')}</span>
            </>
          )}
        </button>
      </div>
    </div>
  );
}
