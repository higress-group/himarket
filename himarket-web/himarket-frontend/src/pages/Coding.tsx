import { message } from 'antd';
import { Code2, Eye, FolderOpen, Folder } from 'lucide-react';
import { useState, useCallback, useEffect, useRef } from 'react';

import bgImage from '../assets/bg.png';
import { ChatStream } from '../components/coding/ChatStream';
import { CodingInput } from '../components/coding/CodingInput';
import { ConfigDropdowns, ModelSelector } from '../components/coding/ConfigDropdowns';
import { ConnectionBanner } from '../components/coding/ConnectionBanner';
import { ConversationTopBar } from '../components/coding/ConversationTopBar';
import { EditorArea } from '../components/coding/EditorArea';
import { FileTree } from '../components/coding/FileTree';
import { PermissionDialog } from '../components/coding/PermissionDialog';
import { PlanDisplay } from '../components/coding/PlanDisplay';
import { PreviewPanel } from '../components/coding/PreviewPanel';
import { SessionSidebar } from '../components/coding/SessionSidebar';
import { TerminalPanel } from '../components/coding/TerminalPanel';
import { Header } from '../components/Header';
import TextType from '../components/TextType';
import { WelcomeView } from '../components/WelcomeView';
import {
  CodingSessionProvider,
  useCodingState,
  useActiveCodingSession,
  useCodingDispatch,
} from '../context/CodingSessionContext';
import { useAuth } from '../hooks/useAuth';
import { useCodingConfig } from '../hooks/useCodingConfig';
import { useCodingSession } from '../hooks/useCodingSession';
import { useResizable } from '../hooks/useResizable';
import {
  getMarketModels,
  getCliProviders,
  getCodingFeatures,
  type ICliProvider,
} from '../lib/apis/cliProvider';
import { createCodingSession, updateCodingSession } from '../lib/apis/codingSession';
import { sortCliProviders } from '../lib/utils/cliProviderSort';
import {
  fetchDirectoryTree,
  fetchArtifactContent,
  fetchWorkspaceChanges,
  setDefaultRuntime,
} from '../lib/utils/workspaceApi';
import { buildCodingWsUrl } from '../lib/utils/wsUrl';

import type { TerminalPanelHandle } from '../components/coding/TerminalPanel';
import type { FileNode } from '../types/coding';
import type { ChatItemPlan } from '../types/coding-protocol';

const EXT_TO_LANG: Record<string, string> = {
  bash: 'shell',
  css: 'css',
  go: 'go',
  html: 'html',
  java: 'java',
  js: 'javascript',
  json: 'json',
  jsx: 'javascript',
  less: 'less',
  md: 'markdown',
  py: 'python',
  rs: 'rust',
  scss: 'scss',
  sh: 'shell',
  sql: 'sql',
  svelte: 'html',
  toml: 'toml',
  ts: 'typescript',
  tsx: 'typescript',
  vue: 'html',
  xml: 'xml',
  yaml: 'yaml',
  yml: 'yaml',
};

function inferLanguage(fileName: string): string {
  const ext = fileName.split('.').pop()?.toLowerCase() ?? '';
  return EXT_TO_LANG[ext] ?? 'plaintext';
}

function readBool(key: string, fallback: boolean): boolean {
  try {
    const raw = localStorage.getItem(key);
    if (raw === 'true') return true;
    if (raw === 'false') return false;
  } catch {
    /* ignore */
  }
  return fallback;
}

function writeBool(key: string, value: boolean): void {
  try {
    localStorage.setItem(key, String(value));
  } catch {
    /* ignore */
  }
}

/* ─── Resize handle shared component ─── */
function ResizeHandle({
  direction,
  isDragging,
  onMouseDown,
}: {
  direction: 'horizontal' | 'vertical';
  isDragging: boolean;
  onMouseDown: (e: React.MouseEvent) => void;
}) {
  if (direction === 'horizontal') {
    return (
      <div
        aria-hidden="true"
        className={`w-1 flex-shrink-0 cursor-col-resize group relative
          ${isDragging ? 'bg-blue-500/40' : 'hover:bg-blue-500/30'}`}
        onMouseDown={onMouseDown}
        role="separator"
      >
        <div className="absolute inset-y-0 -left-1 -right-1 z-10" />
      </div>
    );
  }
  return (
    <div
      aria-hidden="true"
      className={`h-1 flex-shrink-0 cursor-row-resize group relative
        ${isDragging ? 'bg-blue-500/40' : 'hover:bg-blue-500/30'}`}
      onMouseDown={onMouseDown}
      role="separator"
    >
      <div className="absolute inset-x-0 -top-1 -bottom-1 z-10" />
    </div>
  );
}

type RightTab = 'preview' | 'code';

const READ_ONLY_KINDS = new Set(['read', 'search', 'think', 'fetch', 'switch_mode']);

function CodingContent() {
  // ===== 功能开关 =====
  const [terminalEnabled, setTerminalEnabled] = useState(true);
  useEffect(() => {
    getCodingFeatures()
      .then((res) => {
        const data = (res as unknown as Record<string, unknown>)?.data as
          | Record<string, unknown>
          | undefined;
        if (typeof data?.terminalEnabled === 'boolean') {
          setTerminalEnabled(data.terminalEnabled);
        }
      })
      .catch(() => {
        /* 获取失败时保持默认值 true */
      });
  }, []);

  // ===== 配置（纯内存，不持久化） =====
  const { config, isComplete, setConfig } = useCodingConfig();
  const [sessionRefreshTrigger, setSessionRefreshTrigger] = useState(0);

  // ===== 延迟连接模式：初始 wsUrl 为空，不触发连接 =====
  const [currentWsUrl, setCurrentWsUrl] = useState('');
  const [currentCliSessionConfig, setCurrentCliSessionConfig] = useState<string | undefined>();
  const session = useCodingSession({
    cliSessionConfig: currentCliSessionConfig,
    wsUrl: currentWsUrl,
  });

  const state = useCodingState();
  const activeSession = useActiveCodingSession();
  const dispatch = useCodingDispatch();

  const isConnected = session.status === 'connected';
  // reconnecting 时仍视为"活跃"，保持 IDE 视图不跳回欢迎页
  const isActiveSession = session.status === 'connected' || session.status === 'reconnecting';

  // ===== 终端重连联动 =====
  const terminalPanelRef = useRef<TerminalPanelHandle>(null);
  const prevAcpStatusRef = useRef(session.status);

  useEffect(() => {
    const prevStatus = prevAcpStatusRef.current;
    prevAcpStatusRef.current = session.status;

    if (session.status === 'connected' && prevStatus === 'reconnecting') {
      // ACP 重连成功 — 后端已销毁旧 session，需要重新走完整流程
      console.warn('[Coding] ACP reconnected, resetting state for re-initialization');

      // 清空旧 sessions（后端 session 已不存在），重置自动创建标记。
      // 不恢复旧会话：重连后直接创建新的空会话，避免 CLI 回放完整对话历史。
      autoCreatedRef.current = false;
      dispatch({ type: 'RESET_STATE' });

      // 触发终端重连
      terminalPanelRef.current?.reconnect();
    }
  }, [session.status, dispatch, activeSession]);

  // 跟踪当前运行时类型（HiCoding 仅支持沙箱模式）
  const currentRuntimeRef = useRef<string>(config.cliRuntime);

  // 设置全局默认 runtime，让 ArtifactPreview / FileRenderer 等组件
  // 调用 workspace API 时自动带上 runtime 参数
  useEffect(() => {
    currentRuntimeRef.current = config.cliRuntime;
    setDefaultRuntime(config.cliRuntime);
    return () => setDefaultRuntime(undefined);
  }, [config.cliRuntime]);

  // ===== 页面加载时自动选择默认 CLI + 第一个模型 =====
  useEffect(() => {
    let cancelled = false;

    const autoSelect = async () => {
      const patch: Partial<typeof config> = {};

      // 自动选择第一个可用的 CLI Provider
      if (!config.cliProviderId) {
        try {
          const res = await getCliProviders();
          const list = Array.isArray(res.data)
            ? res.data
            : ((((res as unknown as Record<string, unknown>)?.data as Record<string, unknown>)
                ?.data ?? []) as ICliProvider[]);
          const sorted = sortCliProviders(list);
          const first = sorted.find((p) => p.available);
          if (first) {
            patch.cliProviderId = first.key;
            patch.cliProviderName = first.displayName;
          }
        } catch {
          /* ignore */
        }
      }

      // 自动选择第一个模型
      if (!config.modelProductId) {
        try {
          const res = await getMarketModels();
          const models = res.data?.models ?? [];
          if (models.length > 0) {
            const firstModel = models[0];
            if (firstModel) {
              patch.modelProductId = firstModel.productId;
              patch.modelName = firstModel.name;
            }
          }
        } catch {
          /* ignore */
        }
      }

      if (!cancelled && Object.keys(patch).length > 0) {
        setConfig({ ...config, ...patch });
      }
    };

    autoSelect();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [config.modelProductId, config.cliProviderId]);

  // 暂存首条消息，等连接 + 会话就绪后自动发送
  const pendingPromptRef = useRef<string | null>(null);

  // 暂存从欢迎页点击的历史会话，等连接 + 初始化就绪后自动加载
  const pendingLoadSessionRef = useRef<{
    cliSessionId: string;
    cwd: string;
    title: string;
    platformSessionId: string;
    providerKey: string;
  } | null>(null);

  // 跟踪当前 WebSocket 连接使用的 provider，用于判断加载历史会话时是否需要重连
  const connectedProviderRef = useRef<string>('');

  // 确认配置并连接
  const handleConnect = useCallback(
    (cfg: typeof config) => {
      const cliId = cfg.cliProviderId ?? '';
      currentRuntimeRef.current = cfg.cliRuntime;
      connectedProviderRef.current = cliId;

      // 如果 cliSessionConfig 为空（用户未通过下拉菜单选择），
      // 就地构建最小的 session config，确保后端至少能收到 modelProductId
      let sessionConfig = cfg.cliSessionConfig;
      if (!sessionConfig && cfg.modelProductId) {
        sessionConfig = JSON.stringify({ modelProductId: cfg.modelProductId });
      }
      setCurrentCliSessionConfig(sessionConfig);

      const url = buildCodingWsUrl({
        provider: cliId || undefined,
        runtime: cfg.cliRuntime,
        token: localStorage.getItem('access_token') || undefined,
      });

      dispatch({ message: '正在连接沙箱环境...', status: 'creating', type: 'SANDBOX_STATUS' });

      setCurrentWsUrl(url);
    },
    [dispatch],
  );

  // 模型配置由 ConfigInjectionPhase 注入，createSession 中会自动设置 CLI 模型
  // 不需要额外的 setModel 调用（config.modelProductId 是平台产品 ID，CLI 不认识）

  // ===== 连接就绪后自动加载暂存的历史会话 =====
  useEffect(() => {
    if (!isConnected || !state.initialized || !pendingLoadSessionRef.current) return;
    if (state.sandboxStatus?.status !== 'ready') return;

    const { cliSessionId, cwd, platformSessionId, title } = pendingLoadSessionRef.current;
    pendingLoadSessionRef.current = null;
    autoCreatedRef.current = true; // 防止自动创建新会话

    session.loadSession(cliSessionId, cwd, title, platformSessionId).catch((err) => {
      console.error('[Coding] Load pending session failed:', err);
      message.warning('会话已过期，请选择其他会话或创建新会话');
    });
  }, [isConnected, state.initialized, state.sandboxStatus, session]);

  // ===== 自动创建 Session =====
  const autoCreatedRef = useRef(false);

  useEffect(() => {
    if (
      !isConnected ||
      !state.initialized ||
      Object.keys(state.sessions).length > 0 ||
      autoCreatedRef.current
    ) {
      if (!isConnected) {
        autoCreatedRef.current = false;
      }
      return;
    }

    // 沙箱模式：等待 sandbox ready 后再创建会话
    if (state.sandboxStatus?.status !== 'ready') {
      return;
    }

    // 等待 workspace/info 通知推送实际 cwd（如 /workspace/{userId}）
    if (!state.workspaceCwd) {
      return;
    }

    // 有暂存的历史会话加载请求时，不自动创建新会话
    if (pendingLoadSessionRef.current) {
      return;
    }

    autoCreatedRef.current = true;
    session
      .createSession(state.workspaceCwd)
      .then((cliSessionId) => {
        // Persist session to backend
        createCodingSession({
          cliSessionId,
          cwd: state.workspaceCwd ?? '',
          modelName: config.modelName ?? undefined,
          modelProductId: config.modelProductId ?? undefined,
          providerKey: config.cliProviderId ?? '',
        })
          .then((res) => {
            const platformId = res.data?.sessionId;
            if (platformId) {
              dispatch({
                platformSessionId: platformId,
                sessionId: cliSessionId,
                type: 'SET_PLATFORM_SESSION_ID',
              });
            }
            setSessionRefreshTrigger((n) => n + 1);
          })
          .catch((err) => console.warn('[Coding] Failed to persist session:', err));
      })
      .catch((err) => {
        console.error('[Coding] Auto create session failed:', err);
        dispatch({
          message: err?.message || '会话创建失败，请重新连接',
          status: 'error',
          type: 'SANDBOX_STATUS',
        });
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    isConnected,
    state.initialized,
    state.sessions,
    state.sandboxStatus,
    state.workspaceCwd,
    session,
    dispatch,
  ]);

  // 会话就绪后自动发送暂存的首条消息
  useEffect(() => {
    if (activeSession && pendingPromptRef.current) {
      const prompt = pendingPromptRef.current;
      pendingPromptRef.current = null;
      session.sendPrompt(prompt);
    }
  }, [activeSession, session]);

  // Persist session title to backend when CLI updates it
  const lastPersistedTitleRef = useRef<string>('');
  useEffect(() => {
    if (!activeSession || !activeSession.title) return;
    if (!activeSession.platformSessionId) return;
    const title = activeSession.title;
    if (title === lastPersistedTitleRef.current) return;
    if (title === 'Loading...') return;
    lastPersistedTitleRef.current = title;
    updateCodingSession(activeSession.platformSessionId, { title }).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeSession?.id, activeSession?.title, activeSession?.platformSessionId]);

  // Handle loading a historical session from sidebar
  const handleLoadSession = useCallback(
    (
      cliSessionId: string,
      cwd: string,
      title: string,
      platformSessionId: string,
      providerKey: string,
    ) => {
      // 使用历史会话的 providerKey；若旧数据为空则降级为当前选择
      const effectiveProvider = providerKey || config.cliProviderId || '';

      if (!isConnected) {
        // 未连接时暂存会话信息，用历史 provider 建连
        pendingLoadSessionRef.current = {
          cliSessionId,
          cwd,
          platformSessionId,
          providerKey: effectiveProvider,
          title,
        };
        const overriddenConfig = { ...config, cliProviderId: effectiveProvider };
        handleConnect(overriddenConfig);
        return;
      }

      // 已连接但 provider 不匹配 — 需要断开重连到正确的 provider
      if (effectiveProvider && effectiveProvider !== connectedProviderRef.current) {
        pendingLoadSessionRef.current = {
          cliSessionId,
          cwd,
          platformSessionId,
          providerKey: effectiveProvider,
          title,
        };
        autoCreatedRef.current = false;
        dispatch({ type: 'RESET_STATE' });
        setCurrentWsUrl('');
        setCurrentCliSessionConfig(undefined);
        // 延迟一帧让状态清理完成后再重新连接
        setTimeout(() => {
          const overriddenConfig = { ...config, cliProviderId: effectiveProvider };
          handleConnect(overriddenConfig);
        }, 0);
        return;
      }

      // 已连接且 provider 匹配 — 直接加载
      session.loadSession(cliSessionId, cwd, title, platformSessionId).catch((err) => {
        console.error('[Coding] Load session failed:', err);
        message.warning('会话已过期，请选择其他会话或创建新会话');
      });
    },
    [isConnected, config, handleConnect, session, dispatch],
  );

  // 新会话：断开连接，回到欢迎页
  const handleNewSession = useCallback(() => {
    autoCreatedRef.current = false;
    pendingPromptRef.current = null;
    pendingLoadSessionRef.current = null;
    dispatch({ type: 'RESET_STATE' });
    setCurrentWsUrl('');
    setCurrentCliSessionConfig(undefined);
  }, [dispatch]);

  // 欢迎页发送消息：如果未连接则先触发连接，暂存消息
  const handleWelcomeSend = useCallback(
    (text: string) => {
      if (!isComplete) {
        message.warning('请先完成配置');
        return { queued: false as const };
      }

      if (!isConnected) {
        // 暂存消息，触发连接
        pendingPromptRef.current = text;
        handleConnect(config);
        return { queued: true as const };
      }

      if (activeSession) {
        return session.sendPrompt(text);
      }

      // 已连接但会话还没创建好，暂存
      pendingPromptRef.current = text;
      return { queued: true as const };
    },
    [isComplete, isConnected, activeSession, config, handleConnect, session],
  );

  // ===== IDE 面板状态 =====
  const [activeTab, setActiveTab] = useState<RightTab>('preview');
  const isPreviewMode = activeTab === 'preview';
  const [tree, setTree] = useState<FileNode[]>([]);
  const [treeLoading, setTreeLoading] = useState(false);
  const autoOpenedRef = useRef<Set<string>>(new Set());

  const [fileTreeVisible, setFileTreeVisible] = useState(() =>
    readBool('hicoding:fileTreeVisible', true),
  );
  const toggleFileTree = useCallback(() => {
    setFileTreeVisible((prev) => {
      const next = !prev;
      writeBool('hicoding:fileTreeVisible', next);
      return next;
    });
  }, []);

  const [terminalCollapsed, setTerminalCollapsed] = useState(() =>
    readBool('hicoding:terminalCollapsed', false),
  );
  const toggleTerminalCollapse = useCallback(() => {
    setTerminalCollapsed((prev) => {
      const next = !prev;
      writeBool('hicoding:terminalCollapsed', next);
      return next;
    });
  }, []);

  // Derived visibility: hide file tree and terminal in preview mode; hide terminal when disabled
  const showFileTree = fileTreeVisible && !isPreviewMode;
  const showTerminal = !isPreviewMode && terminalEnabled;

  // ===== Resizable panels =====
  const conversationPanel = useResizable({
    defaultSize: 420,
    direction: 'horizontal',
    maxSize: 600,
    minSize: 320,
    storageKey: 'hicoding:conversationWidth',
  });
  const fileTreePanel = useResizable({
    defaultSize: 200,
    direction: 'horizontal',
    maxSize: 400,
    minSize: 140,
    storageKey: 'hicoding:fileTreeWidth',
  });
  const terminalPanel = useResizable({
    defaultSize: 200,
    direction: 'vertical',
    maxSize: 500,
    minSize: 100,
    reverse: true,
    storageKey: 'hicoding:terminalHeight',
  });

  // ===== 文件树加载 =====
  useEffect(() => {
    if (!activeSession?.cwd) return;
    setTreeLoading(true);
    fetchDirectoryTree(activeSession.cwd, 10, currentRuntimeRef.current).then((nodes) => {
      if (nodes !== null) setTree(nodes);
      setTreeLoading(false);
    });
  }, [activeSession?.cwd]);

  const messageCount = activeSession?.messages.length ?? 0;
  useEffect(() => {
    if (!activeSession?.cwd || messageCount === 0) return;
    const lastMsg = activeSession.messages[messageCount - 1];
    if (
      lastMsg?.type === 'tool_call' &&
      (lastMsg.status === 'completed' || lastMsg.status === 'failed') &&
      !READ_ONLY_KINDS.has(lastMsg.kind)
    ) {
      fetchDirectoryTree(activeSession.cwd, 10, currentRuntimeRef.current).then((nodes) => {
        if (nodes !== null) setTree(nodes);
      });
    }
  }, [messageCount, activeSession?.cwd, activeSession?.messages]);

  const lastPollRef = useRef<number>(0);
  const pollingRef = useRef(false);
  useEffect(() => {
    if (!activeSession?.cwd) return;
    const cwd = activeSession.cwd;
    lastPollRef.current = Date.now();
    const interval = setInterval(async () => {
      if (pollingRef.current) return;
      pollingRef.current = true;
      try {
        const changes = await fetchWorkspaceChanges(
          cwd,
          lastPollRef.current,
          200,
          currentRuntimeRef.current,
        );
        if (changes.length > 0) {
          lastPollRef.current = Date.now();
          fetchDirectoryTree(cwd, 10, currentRuntimeRef.current).then((nodes) => {
            if (nodes !== null) setTree(nodes);
          });
        }
      } finally {
        pollingRef.current = false;
      }
    }, 10000);
    return () => clearInterval(interval);
  }, [activeSession?.cwd]);

  // Auto-open files when Agent edits them
  useEffect(() => {
    if (!activeSession || messageCount === 0) return;
    for (const msg of activeSession.messages) {
      if (msg.type !== 'tool_call' || msg.kind !== 'edit' || msg.status !== 'completed') continue;
      const locations = msg.locations;
      if (!locations || locations.length === 0) continue;
      for (const loc of locations) {
        const key = `${activeSession.id}:${msg.toolCallId}:${loc.path}`;
        if (autoOpenedRef.current.has(key)) continue;
        autoOpenedRef.current.add(key);
        const filePath = loc.path;
        const fileName = filePath.split('/').pop() ?? filePath;
        fetchArtifactContent(filePath, { raw: true, runtime: currentRuntimeRef.current }).then(
          (result) => {
            if (result.content !== null) {
              dispatch({
                file: {
                  content: result.content,
                  encoding: result.encoding ?? 'utf-8',
                  fileName,
                  language: inferLanguage(fileName),
                  path: filePath,
                },
                sessionId: activeSession.id,
                type: 'FILE_OPENED',
              });
              setActiveTab('code');
            }
          },
        );
      }
    }
  }, [messageCount, activeSession, dispatch]);

  // ===== 文件操作回调 =====
  const handleFileSelect = useCallback(
    async (node: FileNode) => {
      if (node.type !== 'file' || !activeSession) return;
      if (activeSession.openFiles.some((f) => f.path === node.path)) {
        dispatch({ path: node.path, sessionId: activeSession.id, type: 'ACTIVE_FILE_CHANGED' });
        setActiveTab('code');
        return;
      }
      const result = await fetchArtifactContent(node.path, {
        raw: true,
        runtime: currentRuntimeRef.current,
      });
      if (result.content !== null) {
        dispatch({
          file: {
            content: result.content,
            encoding: result.encoding ?? 'utf-8',
            fileName: node.name,
            language: inferLanguage(node.name),
            path: node.path,
          },
          sessionId: activeSession.id,
          type: 'FILE_OPENED',
        });
        setActiveTab('code');
      } else if (result.error) {
        message.warning(result.error.message);
      }
    },
    [activeSession, dispatch],
  );

  const handleCloseFile = useCallback(
    (path: string) => {
      if (!activeSession) return;
      dispatch({ path, sessionId: activeSession.id, type: 'FILE_CLOSED' });
    },
    [activeSession, dispatch],
  );

  const handleOpenFilePath = useCallback(
    async (path: string) => {
      if (!activeSession) return;
      if (activeSession.openFiles.some((f) => f.path === path)) {
        dispatch({ path, sessionId: activeSession.id, type: 'ACTIVE_FILE_CHANGED' });
        setActiveTab('code');
        return;
      }
      const result = await fetchArtifactContent(path, {
        raw: true,
        runtime: currentRuntimeRef.current,
      });
      if (result.content !== null) {
        const fileName = path.split(/[/\\]/).pop() ?? path;
        dispatch({
          file: {
            content: result.content,
            encoding: result.encoding ?? 'utf-8',
            fileName,
            language: inferLanguage(fileName),
            path,
          },
          sessionId: activeSession.id,
          type: 'FILE_OPENED',
        });
        setActiveTab('code');
      } else if (result.error) {
        message.warning(result.error.message);
      }
    },
    [activeSession, dispatch],
  );

  const handleSelectFile = useCallback(
    (path: string) => {
      if (!activeSession) return;
      dispatch({ path, sessionId: activeSession.id, type: 'ACTIVE_FILE_CHANGED' });
    },
    [activeSession, dispatch],
  );

  const handleRefreshFile = useCallback(
    async (path: string) => {
      if (!activeSession) return;
      const result = await fetchArtifactContent(path, {
        raw: true,
        runtime: currentRuntimeRef.current,
      });
      if (result.content !== null) {
        const fileName = path.split(/[/\\]/).pop() ?? path;
        dispatch({
          file: {
            content: result.content,
            encoding: result.encoding ?? 'utf-8',
            fileName,
            language: inferLanguage(fileName),
            path,
          },
          sessionId: activeSession.id,
          type: 'FILE_OPENED',
        });
      }
    },
    [activeSession, dispatch],
  );

  const planEntries = activeSession?.messages.find(
    (m): m is ChatItemPlan => m.type === 'plan',
  )?.entries;

  // ===== 渲染状态判断 =====
  // 欢迎页：未连接（reconnecting 不算），或者连接中但还没有活跃会话（且没有暂存消息/会话正在等待）
  const showWelcome =
    !isActiveSession && !pendingPromptRef.current && !pendingLoadSessionRef.current;
  // IDE 布局：一旦触发连接就立即显示，沙箱状态在对话流中以卡片形式展示
  const showIDELayout = !showWelcome;

  // ===== 常驻侧栏 =====
  const sessionSidebar = (
    <SessionSidebar
      activeCliSessionId={activeSession?.id ?? null}
      agentSupportsLoadSession={state.agentSupportsLoadSession}
      defaultCollapsed={!showWelcome}
      onLoadSession={handleLoadSession}
      onNewSession={handleNewSession}
      refreshTrigger={sessionRefreshTrigger}
    />
  );

  // ===== 欢迎页：使用 HiChat 风格布局 =====
  if (showWelcome) {
    return (
      <>
        <div className="flex flex-1 min-h-0 overflow-hidden">
          {/* 左侧常驻会话侧栏 */}
          {sessionSidebar}

          {/* 居中欢迎页 */}
          <div className="flex-1 flex flex-col">
            <div className="flex-1 flex flex-col items-center justify-center px-4">
              <div className="max-w-2xl w-full">
                {/* 欢迎标题 */}
                <div className="text-center mb-8">
                  <div
                    className="mx-auto mb-4 w-20 h-20 rounded-2xl flex items-center justify-center shadow-lg"
                    style={{
                      background:
                        'linear-gradient(135deg, rgba(99,102,241,1) 0%, rgba(139,92,246,1) 100%)',
                    }}
                  >
                    <Code2 className="text-white" size={40} />
                  </div>
                  <h1 className="text-2xl font-medium text-gray-900 mb-2">
                    欢迎使用{' '}
                    <span className="text-blue-500">
                      <TextType
                        cursorCharacter="_"
                        loop={false}
                        showCursor={true}
                        text={['HiCoding']}
                        typingSpeed={80}
                      />
                    </span>
                  </h1>
                  <p className="text-sm text-gray-400">AI 驱动的编程助手，输入你的需求开始编程</p>
                </div>

                {/* 输入框 - 渐变边框包裹 */}
                <div className="mb-4">
                  <div
                    className="p-[2px] rounded-2xl shadow-md"
                    style={{
                      background:
                        'linear-gradient(256deg, rgba(234, 228, 248, 1) 36%, rgba(215, 229, 243, 1) 100%)',
                    }}
                  >
                    <div className="rounded-2xl overflow-hidden bg-white/95">
                      {/* 模型选择器 - 对话框左上角 */}
                      <div className="px-4 pt-3 pb-0">
                        <ModelSelector config={config} onConfigChange={setConfig} />
                      </div>
                      <CodingInput
                        disabled={!isComplete}
                        isProcessing={false}
                        onCancel={() => {}}
                        onDropQueuedPrompt={() => {}}
                        onSend={handleWelcomeSend}
                        queuedPrompts={[]}
                        queueSize={0}
                        toolbarExtra={
                          <ConfigDropdowns config={config} hideModel onConfigChange={setConfig} />
                        }
                        variant="welcome"
                      />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </>
    );
  }

  // ===== 非欢迎页：IDE 全屏布局 =====
  return (
    <div className="flex flex-col flex-1 min-h-0 overflow-hidden">
      {/* 断连提示横幅 — 固定在顶部，推挤布局 */}
      <ConnectionBanner
        acpStatus={session.status}
        onManualReconnect={session.manualReconnect}
        reconnectAttempt={session.reconnectAttempt}
      />
      <div className="flex flex-1 min-h-0 overflow-hidden">
        {/* 左侧常驻会话侧栏 */}
        {sessionSidebar}

        {showIDELayout ? (
          /* ===== 三栏布局主体：Conversation_Panel + ResizeHandle + IDE_Panel ===== */
          <>
            {/* Conversation_Panel */}
            <div
              className="flex flex-col border-r border-gray-200/60 bg-white/50 overflow-hidden flex-shrink-0"
              style={{ width: conversationPanel.size }}
            >
              <ConversationTopBar
                sessionTitle={activeSession?.title ?? ''}
                usage={state.usage ?? undefined}
              />
              <ChatStream
                onOpenFile={handleOpenFilePath}
                onPreviewArtifact={() => setActiveTab('preview')}
                onSandboxRetry={handleNewSession}
                onSelectToolCall={(toolCallId) =>
                  dispatch({ toolCallId, type: 'SELECT_TOOL_CALL' })
                }
              />
              {planEntries && planEntries.length > 0 && (
                <div className="max-w-full px-3 pt-1 flex-shrink-0">
                  <PlanDisplay entries={planEntries} />
                </div>
              )}
              <div className="flex-shrink-0">
                <CodingInput
                  disabled={!state.initialized || !activeSession}
                  isProcessing={activeSession?.isProcessing ?? false}
                  onCancel={session.cancelPrompt}
                  onDropQueuedPrompt={session.dropQueuedPrompt}
                  onSend={session.sendPrompt}
                  queuedPrompts={activeSession?.promptQueue ?? []}
                  queueSize={activeSession?.promptQueue.length ?? 0}
                />
              </div>
            </div>

            <ResizeHandle
              direction="horizontal"
              isDragging={conversationPanel.isDragging}
              onMouseDown={conversationPanel.handleMouseDown}
            />

            {/* IDE_Panel */}
            <div className="flex-1 flex flex-col min-w-0 min-h-0 overflow-hidden">
              <div className="flex-1 flex flex-col min-h-0 overflow-hidden">
                <div className="flex-1 flex min-h-0 overflow-hidden">
                  {showFileTree && (
                    <>
                      <div
                        className="border-r border-gray-200/60 bg-white/50 overflow-hidden flex-shrink-0 flex flex-col"
                        style={{ width: fileTreePanel.size }}
                      >
                        <div className="flex items-center px-3 py-2 border-b border-gray-200/60 bg-white/30">
                          <span className="text-xs font-medium text-gray-600">文件</span>
                        </div>
                        <div className="flex-1 overflow-hidden">
                          {treeLoading ? (
                            <div className="flex items-center justify-center h-full text-xs text-gray-400">
                              加载中...
                            </div>
                          ) : (
                            <FileTree
                              onFileSelect={handleFileSelect}
                              selectedPath={activeSession?.activeFilePath}
                              tree={tree}
                            />
                          )}
                        </div>
                      </div>
                      <ResizeHandle
                        direction="horizontal"
                        isDragging={fileTreePanel.isDragging}
                        onMouseDown={fileTreePanel.handleMouseDown}
                      />
                    </>
                  )}

                  <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
                    <div className="flex items-center border-b border-gray-200/60 bg-white/30 flex-shrink-0">
                      <button
                        className={`px-4 py-2 text-sm font-medium transition-colors border-b-2 flex items-center gap-1.5
                        ${activeTab === 'code' ? 'text-blue-600 border-blue-500 bg-white/50' : 'text-gray-500 border-transparent hover:text-gray-700 hover:bg-gray-50/50'}`}
                        onClick={() => setActiveTab('code')}
                      >
                        <Code2 size={14} /> 代码
                      </button>
                      <button
                        className={`px-4 py-2 text-sm font-medium transition-colors border-b-2 flex items-center gap-1.5
                        ${activeTab === 'preview' ? 'text-blue-600 border-blue-500 bg-white/50' : 'text-gray-500 border-transparent hover:text-gray-700 hover:bg-gray-50/50'}`}
                        onClick={() => setActiveTab('preview')}
                      >
                        <Eye size={14} /> 预览
                      </button>
                      <div className="flex-1" />
                      {!isPreviewMode && (
                        <button
                          className={`w-7 h-7 flex items-center justify-center rounded transition-colors mr-2
                          ${fileTreeVisible ? 'text-blue-600 bg-blue-50' : 'text-gray-400 hover:text-gray-600 hover:bg-gray-100'}`}
                          onClick={toggleFileTree}
                          title={fileTreeVisible ? '隐藏文件' : '显示文件'}
                        >
                          {fileTreeVisible ? <FolderOpen size={16} /> : <Folder size={16} />}
                        </button>
                      )}
                    </div>
                    <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                      {activeTab === 'preview' ? (
                        <PreviewPanel
                          activeArtifactId={activeSession?.activeArtifactId ?? null}
                          artifacts={activeSession?.artifacts ?? []}
                          onSelectArtifact={(artifactId) =>
                            dispatch({ artifactId, type: 'SELECT_ARTIFACT' })
                          }
                        />
                      ) : (
                        <EditorArea
                          activeFilePath={activeSession?.activeFilePath ?? null}
                          onCloseFile={handleCloseFile}
                          onRefreshFile={handleRefreshFile}
                          onSelectFile={handleSelectFile}
                          openFiles={activeSession?.openFiles ?? []}
                        />
                      )}
                    </div>
                  </div>
                </div>

                {!terminalCollapsed && showTerminal && (
                  <ResizeHandle
                    direction="vertical"
                    isDragging={terminalPanel.isDragging}
                    onMouseDown={terminalPanel.handleMouseDown}
                  />
                )}
                {terminalEnabled && (
                  <div
                    className="flex-shrink-0"
                    style={{ display: showTerminal ? undefined : 'none' }}
                  >
                    <TerminalPanel
                      collapsed={terminalCollapsed}
                      height={terminalPanel.size}
                      onToggleCollapse={toggleTerminalCollapse}
                      ref={terminalPanelRef}
                      runtime={currentRuntimeRef.current}
                    />
                  </div>
                )}
              </div>
            </div>
          </>
        ) : null}

        {/* ===== Permission dialog ===== */}
        {state.pendingPermission && (
          <PermissionDialog
            onRespond={session.respondPermission}
            permission={state.pendingPermission}
          />
        )}
      </div>
    </div>
  );
}

function Coding() {
  const { isLoggedIn } = useAuth();

  if (!isLoggedIn) {
    return (
      <div className="flex flex-col min-h-screen">
        <div
          className="fixed w-full h-full z-[1]"
          style={{
            backgroundAttachment: 'fixed',
            backgroundImage: `url(${bgImage})`,
            backgroundPosition: 'center',
            backgroundRepeat: 'no-repeat',
            backgroundSize: 'cover',
          }}
        />
        <div className="fixed w-full h-full z-[2]" style={{ backdropFilter: 'blur(204px)' }} />
        <div className="relative z-10 flex-shrink-0">
          <Header />
        </div>
        <div className="flex-1 relative z-10 px-8">
          <WelcomeView type="coding" />
        </div>
      </div>
    );
  }

  return (
    <CodingSessionProvider>
      <CodingShell />
    </CodingSessionProvider>
  );
}

/** 根据连接状态选择不同的外壳布局。
 *  关键：CodingContent 必须始终在同一个 React 树位置渲染，
 *  否则布局切换（欢迎页 → IDE）时组件会被卸载重建，
 *  导致 WebSocket 连接、currentWsUrl 等内部状态丢失。
 *  因此这里用同一个 DOM 结构 + CSS 切换来实现两种布局。
 */
function CodingShell() {
  const state = useCodingState();
  const activeSession = useActiveCodingSession();

  const isWelcomePhase = Object.keys(state.sessions).length === 0 && !activeSession;

  return (
    <div
      className={`flex flex-col overflow-hidden ${isWelcomePhase ? 'min-h-screen' : 'h-screen'}`}
    >
      {/* 背景层：始终显示，保持统一视觉效果 */}
      <div
        className="fixed w-full h-full z-[1]"
        style={{
          backgroundAttachment: 'fixed',
          backgroundImage: `url(${bgImage})`,
          backgroundPosition: 'center',
          backgroundRepeat: 'no-repeat',
          backgroundSize: 'cover',
        }}
      />
      <div className="fixed w-full h-full z-[2]" style={{ backdropFilter: 'blur(204px)' }} />
      <div className="relative z-10 flex-shrink-0">
        <Header />
      </div>
      <div className={`flex-1 min-h-0 relative z-10 flex flex-col ${isWelcomePhase ? 'px-8' : ''}`}>
        <CodingContent />
      </div>
    </div>
  );
}

export default Coding;
