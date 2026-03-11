import { useState, useCallback, useEffect, useRef } from "react";
import { Code2, Eye, FolderOpen, Folder, Settings, Sparkles, Zap } from "lucide-react";
import { message } from "antd";
import { Header } from "../components/Header";
import TextType from "../components/TextType";
import bgImage from "../assets/bg.png";
import {
  CodingSessionProvider,
  useCodingState,
  useActiveCodingSession,
  useCodingDispatch,
} from "../context/CodingSessionContext";
import { useCodingSession } from "../hooks/useCodingSession";
import { useResizable } from "../hooks/useResizable";
import { useCodingConfig } from "../hooks/useCodingConfig";
import { ConfigSidebar } from "../components/coding/ConfigSidebar";
import { ConversationTopBar } from "../components/coding/ConversationTopBar";
import { FileTree } from "../components/coding/FileTree";
import { EditorArea } from "../components/coding/EditorArea";
import { TerminalPanel } from "../components/coding/TerminalPanel";
import type { TerminalPanelHandle } from "../components/coding/TerminalPanel";
import { PreviewPanel } from "../components/coding/PreviewPanel";
import { ConnectionBanner } from "../components/coding/ConnectionBanner";
import { ChatStream } from "../components/coding/ChatStream";
import { CodingInput } from "../components/coding/CodingInput";
import { PermissionDialog } from "../components/coding/PermissionDialog";
import { PlanDisplay } from "../components/coding/PlanDisplay";
import {
  fetchDirectoryTree,
  fetchArtifactContent,
  fetchWorkspaceChanges,
  getPreviewUrl,
  setDefaultRuntime,
} from "../lib/utils/workspaceApi";
import type { FileNode } from "../types/coding";
import type { ChatItemPlan } from "../types/coding-protocol";
import { buildCodingWsUrl } from "../lib/utils/wsUrl";
import { SandboxInitProgress } from "../components/common/SandboxInitProgress";
import { getMarketModels, getCliProviders } from "../lib/apis/cliProvider";
import { sortCliProviders } from "../lib/utils/cliProviderSort";

const EXT_TO_LANG: Record<string, string> = {
  ts: "typescript", tsx: "typescript", js: "javascript", jsx: "javascript",
  json: "json", html: "html", css: "css", scss: "scss", less: "less",
  md: "markdown", py: "python", java: "java", xml: "xml", yaml: "yaml",
  yml: "yaml", sh: "shell", bash: "shell", sql: "sql", go: "go",
  rs: "rust", toml: "toml", vue: "html", svelte: "html",
};

function inferLanguage(fileName: string): string {
  const ext = fileName.split(".").pop()?.toLowerCase() ?? "";
  return EXT_TO_LANG[ext] ?? "plaintext";
}

function readBool(key: string, fallback: boolean): boolean {
  try {
    const raw = localStorage.getItem(key);
    if (raw === "true") return true;
    if (raw === "false") return false;
  } catch { /* ignore */ }
  return fallback;
}

function writeBool(key: string, value: boolean): void {
  try { localStorage.setItem(key, String(value)); } catch { /* ignore */ }
}

/* ─── Resize handle shared component ─── */
function ResizeHandle({
  direction, isDragging, onMouseDown,
}: {
  direction: "horizontal" | "vertical";
  isDragging: boolean;
  onMouseDown: (e: React.MouseEvent) => void;
}) {
  if (direction === "horizontal") {
    return (
      <div
        className={`w-1 flex-shrink-0 cursor-col-resize group relative
          ${isDragging ? "bg-blue-500/40" : "hover:bg-blue-500/30"}`}
        onMouseDown={onMouseDown}
      >
        <div className="absolute inset-y-0 -left-1 -right-1 z-10" />
      </div>
    );
  }
  return (
    <div
      className={`h-1 flex-shrink-0 cursor-row-resize group relative
        ${isDragging ? "bg-blue-500/40" : "hover:bg-blue-500/30"}`}
      onMouseDown={onMouseDown}
    >
      <div className="absolute inset-x-0 -top-1 -bottom-1 z-10" />
    </div>
  );
}

type RightTab = "preview" | "code";

const READ_ONLY_KINDS = new Set(["read", "search", "think", "fetch", "switch_mode"]);

function CodingContent() {
  // ===== 配置持久化 =====
  const { config, setConfig, isFirstTime, isComplete } = useCodingConfig();
  const [configOpen, setConfigOpen] = useState(false);

  // ===== 延迟连接模式：初始 wsUrl 为空，不触发连接 =====
  const [currentWsUrl, setCurrentWsUrl] = useState("");
  const [currentCliSessionConfig, setCurrentCliSessionConfig] = useState<string | undefined>();
  const session = useCodingSession({ wsUrl: currentWsUrl, cliSessionConfig: currentCliSessionConfig });

  const state = useCodingState();
  const activeSession = useActiveCodingSession();
  const dispatch = useCodingDispatch();

  const isConnected = session.status === "connected";
  // reconnecting 时仍视为"活跃"，保持 IDE 视图不跳回欢迎页
  const isActiveSession = session.status === "connected" || session.status === "reconnecting";

  // ===== 终端重连联动 =====
  const terminalPanelRef = useRef<TerminalPanelHandle>(null);
  const prevAcpStatusRef = useRef(session.status);

  useEffect(() => {
    const prevStatus = prevAcpStatusRef.current;
    prevAcpStatusRef.current = session.status;

    if (session.status === "connected" && prevStatus === "reconnecting") {
      // ACP 重连成功 — 后端已销毁旧 session，需要重新走完整流程
      console.log("[Coding] ACP reconnected, resetting state for re-initialization");

      // 清空旧 sessions（后端 session 已不存在），重置自动创建标记
      autoCreatedRef.current = false;
      dispatch({ type: "RESET_STATE" });
      // RESET_STATE 会清空 initialized/sessions/sandboxStatus，
      // useCodingSession 的 status=connected + !initializedRef 会重新触发 initialize，
      // 后端会重新发 sandbox/status: ready → 自动创建 Session → 终端也会跟着重建

      // 触发终端重连
      terminalPanelRef.current?.reconnect();
    }
  }, [session.status, dispatch]);

  // 跟踪当前运行时类型（HiCoding 仅支持沙箱模式）
  const currentRuntimeRef = useRef<string>(config.cliRuntime);

  // 设置全局默认 runtime，让 ArtifactPreview / FileRenderer 等组件
  // 调用 workspace API 时自动带上 runtime 参数
  useEffect(() => {
    currentRuntimeRef.current = config.cliRuntime;
    setDefaultRuntime(config.cliRuntime);
    return () => setDefaultRuntime(undefined);
  }, [config.cliRuntime]);

  // ===== 补全旧配置中缺失的名称字段 =====
  useEffect(() => {
    const needModelName = config.modelProductId && !config.modelName;
    const needCliName = config.cliProviderId && !config.cliProviderName;
    if (!needModelName && !needCliName) return;

    let cancelled = false;
    const patch: Partial<typeof config> = {};

    const resolve = async () => {
      if (needModelName) {
        try {
          const res = await getMarketModels();
          const models = res.data.models ?? [];
          const found = models.find((m) => m.productId === config.modelProductId);
          if (found) patch.modelName = found.name;
        } catch { /* ignore */ }
      }
      if (needCliName) {
        try {
          const res = await getCliProviders();
          const list: any[] = Array.isArray(res.data) ? res.data : (res as any).data?.data ?? [];
          const sorted = sortCliProviders(list);
          const found = sorted.find((p) => p.key === config.cliProviderId);
          if (found) patch.cliProviderName = found.displayName;
        } catch { /* ignore */ }
      }
      if (!cancelled && Object.keys(patch).length > 0) {
        setConfig({ ...config, ...patch });
      }
    };

    resolve();
    return () => { cancelled = true; };
  }, [config.modelProductId, config.cliProviderId]);

  // 暂存首条消息，等连接 + 会话就绪后自动发送
  const pendingPromptRef = useRef<string | null>(null);

  // ConfigSidebar 确认配置并连接
  const handleConnect = useCallback(
    (cfg: typeof config) => {
      const cliId = cfg.cliProviderId ?? "";
      currentRuntimeRef.current = cfg.cliRuntime;

      setCurrentCliSessionConfig(cfg.cliSessionConfig);

      const url = buildCodingWsUrl({
        token: localStorage.getItem("access_token") || undefined,
        provider: cliId || undefined,
        runtime: cfg.cliRuntime,
      });

      dispatch({ type: "SANDBOX_STATUS", status: "creating", message: "正在连接沙箱环境..." });

      setCurrentWsUrl(url);
      setConfigOpen(false);
    },
    [dispatch]
  );

  // 模型配置由 ConfigInjectionPhase 注入，createSession 中会自动设置 CLI 模型
  // 不需要额外的 setModel 调用（config.modelProductId 是平台产品 ID，CLI 不认识）

  // ===== 自动创建 Session =====
  const autoCreatedRef = useRef(false);

  useEffect(() => {
    if (!isConnected || !state.initialized || Object.keys(state.sessions).length > 0 || autoCreatedRef.current) {
      if (!isConnected) {
        autoCreatedRef.current = false;
      }
      return;
    }

    // 沙箱模式：等待 sandbox ready 后再创建会话
    if (state.sandboxStatus?.status !== "ready") {
      return;
    }

    // 等待 workspace/info 通知推送实际 cwd（如 /workspace/{userId}）
    if (!state.workspaceCwd) {
      return;
    }

    autoCreatedRef.current = true;
    session.createSession(state.workspaceCwd).catch(err => {
      console.error("[Coding] Auto create session failed:", err);
      dispatch({
        type: "SANDBOX_STATUS",
        status: "error",
        message: err?.message || "会话创建失败，请重新连接",
      });
    });
  }, [isConnected, state.initialized, state.sessions, state.sandboxStatus, state.workspaceCwd, session, dispatch]);

  // 会话就绪后自动发送暂存的首条消息
  useEffect(() => {
    if (activeSession && pendingPromptRef.current) {
      const prompt = pendingPromptRef.current;
      pendingPromptRef.current = null;
      session.sendPrompt(prompt);
    }
  }, [activeSession, session]);

  // 新建会话：断开连接，回到欢迎页
  const handleNewSession = useCallback(() => {
    autoCreatedRef.current = false;
    pendingPromptRef.current = null;
    dispatch({ type: "RESET_STATE" });
    setCurrentWsUrl("");
    setCurrentCliSessionConfig(undefined);
  }, [dispatch]);

  // 欢迎页发送消息：如果未连接则先触发连接，暂存消息
  const handleWelcomeSend = useCallback(
    (text: string) => {
      if (!isComplete) {
        message.warning("请先完成配置");
        setConfigOpen(true);
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
    [isComplete, isConnected, activeSession, config, handleConnect, session]
  );

  // ===== IDE 面板状态 =====
  const [activeTab, setActiveTab] = useState<RightTab>("preview");
  const [tree, setTree] = useState<FileNode[]>([]);
  const [treeLoading, setTreeLoading] = useState(false);
  const autoOpenedRef = useRef<Set<string>>(new Set());

  const [fileTreeVisible, setFileTreeVisible] = useState(() =>
    readBool("hicoding:fileTreeVisible", true)
  );
  const toggleFileTree = useCallback(() => {
    setFileTreeVisible(prev => {
      const next = !prev;
      writeBool("hicoding:fileTreeVisible", next);
      return next;
    });
  }, []);

  const [terminalCollapsed, setTerminalCollapsed] = useState(() =>
    readBool("hicoding:terminalCollapsed", false)
  );
  const toggleTerminalCollapse = useCallback(() => {
    setTerminalCollapsed(prev => {
      const next = !prev;
      writeBool("hicoding:terminalCollapsed", next);
      return next;
    });
  }, []);

  // ===== Resizable panels =====
  const conversationPanel = useResizable({
    direction: "horizontal", defaultSize: 420, minSize: 320, maxSize: 600,
    storageKey: "hicoding:conversationWidth",
  });
  const fileTreePanel = useResizable({
    direction: "horizontal", defaultSize: 200, minSize: 140, maxSize: 400,
    storageKey: "hicoding:fileTreeWidth",
  });
  const terminalPanel = useResizable({
    direction: "vertical", defaultSize: 200, minSize: 100, maxSize: 500,
    storageKey: "hicoding:terminalHeight", reverse: true,
  });

  // ===== 文件树加载 =====
  useEffect(() => {
    if (!activeSession?.cwd) return;
    setTreeLoading(true);
    fetchDirectoryTree(activeSession.cwd, 10, currentRuntimeRef.current).then(nodes => {
      if (nodes !== null) setTree(nodes);
      setTreeLoading(false);
    });
  }, [activeSession?.cwd]);

  const messageCount = activeSession?.messages.length ?? 0;
  useEffect(() => {
    if (!activeSession?.cwd || messageCount === 0) return;
    const lastMsg = activeSession.messages[messageCount - 1];
    if (
      lastMsg?.type === "tool_call" &&
      (lastMsg.status === "completed" || lastMsg.status === "failed") &&
      !READ_ONLY_KINDS.has(lastMsg.kind)
    ) {
      fetchDirectoryTree(activeSession.cwd, 10, currentRuntimeRef.current).then(nodes => {
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
        const changes = await fetchWorkspaceChanges(cwd, lastPollRef.current, 200, currentRuntimeRef.current);
        if (changes.length > 0) {
          lastPollRef.current = Date.now();
          fetchDirectoryTree(cwd, 10, currentRuntimeRef.current).then(nodes => {
            if (nodes !== null) setTree(nodes);
          });
        }
      } finally { pollingRef.current = false; }
    }, 10000);
    return () => clearInterval(interval);
  }, [activeSession?.cwd]);

  useEffect(() => {
    if (activeSession?.previewPorts.selectedPort) setActiveTab("preview");
  }, [activeSession?.previewPorts.selectedPort]);

  // Auto-open files when Agent edits them
  useEffect(() => {
    if (!activeSession || messageCount === 0) return;
    for (const msg of activeSession.messages) {
      if (msg.type !== "tool_call" || msg.kind !== "edit" || msg.status !== "completed") continue;
      const locations = msg.locations;
      if (!locations || locations.length === 0) continue;
      for (const loc of locations) {
        const key = `${activeSession.id}:${msg.toolCallId}:${loc.path}`;
        if (autoOpenedRef.current.has(key)) continue;
        autoOpenedRef.current.add(key);
        const filePath = loc.path;
        const fileName = filePath.split("/").pop() ?? filePath;
        fetchArtifactContent(filePath, { raw: true, runtime: currentRuntimeRef.current }).then(result => {
          if (result.content !== null) {
            dispatch({
              type: "FILE_OPENED",
              sessionId: activeSession.id,
              file: { path: filePath, fileName, content: result.content, language: inferLanguage(fileName), encoding: result.encoding ?? "utf-8" },
            });
            setActiveTab("code");
          }
        });
      }
    }
  }, [messageCount, activeSession, dispatch]);

  // ===== 文件操作回调 =====
  const handleFileSelect = useCallback(
    async (node: FileNode) => {
      if (node.type !== "file" || !activeSession) return;
      if (activeSession.openFiles.some(f => f.path === node.path)) {
        dispatch({ type: "ACTIVE_FILE_CHANGED", sessionId: activeSession.id, path: node.path });
        setActiveTab("code");
        return;
      }
      const result = await fetchArtifactContent(node.path, { raw: true, runtime: currentRuntimeRef.current });
      if (result.content !== null) {
        dispatch({ type: "FILE_OPENED", sessionId: activeSession.id, file: { path: node.path, fileName: node.name, content: result.content, language: inferLanguage(node.name), encoding: result.encoding ?? "utf-8" } });
        setActiveTab("code");
      } else if (result.error) {
        message.warning(result.error.message);
      }
    },
    [activeSession, dispatch]
  );

  const handleCloseFile = useCallback(
    (path: string) => {
      if (!activeSession) return;
      dispatch({ type: "FILE_CLOSED", sessionId: activeSession.id, path });
    },
    [activeSession, dispatch]
  );

  const handleOpenFilePath = useCallback(
    async (path: string) => {
      if (!activeSession) return;
      if (activeSession.openFiles.some(f => f.path === path)) {
        dispatch({ type: "ACTIVE_FILE_CHANGED", sessionId: activeSession.id, path });
        setActiveTab("code");
        return;
      }
      const result = await fetchArtifactContent(path, { raw: true, runtime: currentRuntimeRef.current });
      if (result.content !== null) {
        const fileName = path.split(/[/\\]/).pop() ?? path;
        dispatch({ type: "FILE_OPENED", sessionId: activeSession.id, file: { path, fileName, content: result.content, language: inferLanguage(fileName), encoding: result.encoding ?? "utf-8" } });
        setActiveTab("code");
      } else if (result.error) {
        message.warning(result.error.message);
      }
    },
    [activeSession, dispatch]
  );

  const handleSelectFile = useCallback(
    (path: string) => {
      if (!activeSession) return;
      dispatch({ type: "ACTIVE_FILE_CHANGED", sessionId: activeSession.id, path });
    },
    [activeSession, dispatch]
  );

  const previewPort = activeSession?.previewPorts.selectedPort ?? null;

  const handleRefreshPreview = useCallback(() => {
    const iframe = document.querySelector<HTMLIFrameElement>("#coding-preview-iframe");
    if (iframe && previewPort) iframe.src = getPreviewUrl(previewPort);
  }, [previewPort]);

  const handleOpenExternal = useCallback(() => {
    if (previewPort) window.open(getPreviewUrl(previewPort), "_blank");
  }, [previewPort]);

  const planEntries = activeSession?.messages.find(
    (m): m is ChatItemPlan => m.type === "plan"
  )?.entries;

  // ===== 渲染状态判断 =====
  const showSandboxError = isActiveSession && state.sandboxStatus?.status === "error";
  const showInitProgress = isActiveSession && (!state.initialized || !activeSession) && !showSandboxError;
  const showIDE = isActiveSession && state.initialized && activeSession && !showSandboxError;
  // 欢迎页：未连接（reconnecting 不算），或者连接中但还没有活跃会话（且没有暂存消息正在等待）
  const showWelcome = !isActiveSession && !pendingPromptRef.current;

  // ===== 欢迎页：使用 HiChat 风格布局 =====
  if (showWelcome) {
    return (
      <>
        <div className="flex flex-1 min-h-0 overflow-hidden">
          {/* 居中欢迎页 */}
          <div className="flex-1 flex flex-col">
            <div className="flex-1 flex flex-col items-center justify-center px-4">
              <div className="max-w-2xl w-full">
                {/* 欢迎标题 */}
                <div className="text-center mb-8">
                  <div
                    className="mx-auto mb-4 w-20 h-20 rounded-2xl flex items-center justify-center shadow-lg"
                    style={{
                      background: "linear-gradient(135deg, rgba(99,102,241,1) 0%, rgba(139,92,246,1) 100%)"
                    }}
                  >
                    <Code2 size={40} className="text-white" />
                  </div>
                  <h1 className="text-2xl font-medium text-gray-900 mb-2">
                    欢迎使用{" "}
                    <span className="text-blue-500">
                      <TextType
                        text={["HiCoding"]}
                        loop={false}
                        typingSpeed={80}
                        showCursor={true}
                        cursorCharacter="_"
                      />
                    </span>
                  </h1>
                  <p className="text-sm text-gray-400">
                    AI 驱动的编程助手，输入你的需求开始编程
                  </p>
                </div>

                {/* 输入框 - 渐变边框包裹 */}
                <div className="mb-4">
                  <div
                    className="p-[2px] rounded-2xl shadow-md"
                    style={{
                      background: "linear-gradient(256deg, rgba(234, 228, 248, 1) 36%, rgba(215, 229, 243, 1) 100%)"
                    }}
                  >
                    <div className="rounded-2xl overflow-hidden bg-white/95">
                      <CodingInput
                        variant="welcome"
                        onSend={handleWelcomeSend}
                        onDropQueuedPrompt={() => {}}
                        onCancel={() => {}}
                        isProcessing={false}
                        queueSize={0}
                        queuedPrompts={[]}
                        disabled={!isComplete}
                        toolbarExtra={
                          !isComplete ? (
                            <button
                              onClick={() => setConfigOpen(true)}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full
                                         border border-amber-300 bg-amber-50 text-amber-700
                                         text-xs font-medium cursor-pointer
                                         hover:border-amber-400 hover:bg-amber-100 transition-all"
                            >
                              <Settings size={12} />
                              请先完成配置
                            </button>
                          ) : (
                            <>
                              <button
                                onClick={() => setConfigOpen(true)}
                                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full
                                           border border-gray-200 bg-white text-gray-600
                                           text-xs font-medium cursor-pointer
                                           hover:border-blue-300 hover:text-blue-600 hover:bg-blue-50/50 transition-all"
                              >
                                <Sparkles size={12} className="text-blue-500" />
                                <span className="text-gray-400">Model:</span>
                                <span>{config.modelName || config.modelProductId}</span>
                              </button>
                              <button
                                onClick={() => setConfigOpen(true)}
                                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full
                                           border border-gray-200 bg-white text-gray-600
                                           text-xs font-medium cursor-pointer
                                           hover:border-violet-300 hover:text-violet-600 hover:bg-violet-50/50 transition-all"
                              >
                                <Zap size={12} className="text-violet-500" />
                                <span className="text-gray-400">CLI:</span>
                                <span>{config.cliProviderName || config.cliProviderId}</span>
                              </button>
                            </>
                          )
                        }
                      />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* ConfigSidebar 在欢迎页也需要 */}
        <ConfigSidebar
          open={configOpen}
          onClose={() => setConfigOpen(false)}
          config={config}
          onConfigChange={setConfig}
          isFirstTime={isFirstTime}
        />
      </>
    );
  }

  // ===== 非欢迎页：IDE 全屏布局 =====
  return (
    <div className="flex flex-col flex-1 min-h-0 overflow-hidden">
      {/* 断连提示横幅 — 固定在顶部，推挤布局 */}
      <ConnectionBanner
        acpStatus={session.status}
        reconnectAttempt={session.reconnectAttempt}
        onManualReconnect={session.manualReconnect}
      />
      <div className="flex flex-1 min-h-0 overflow-hidden">
      {showSandboxError ? (
        <div className="flex-1 flex items-center justify-center bg-white/50">
          <div className="text-center">
            <p className="text-red-500 mb-4">{state.sandboxStatus?.message}</p>
            <button
              onClick={handleNewSession}
              className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
            >
              重新连接
            </button>
          </div>
        </div>
      ) : showInitProgress ? (
        <div className="flex-1 flex items-center justify-center bg-white/50">
          <SandboxInitProgress />
        </div>
      ) : showIDE ? (
        /* ===== 三栏布局主体：Conversation_Panel + ResizeHandle + IDE_Panel ===== */
        <>
          {/* Conversation_Panel */}
          <div
            className="flex flex-col border-r border-gray-200/60 bg-white/50 overflow-hidden flex-shrink-0"
            style={{ width: conversationPanel.size }}
          >
            <ConversationTopBar
              status={session.status}
              sessionTitle={activeSession?.title ?? ""}
              usage={state.usage ?? undefined}
            />
            <ChatStream
              onSelectToolCall={toolCallId => dispatch({ type: "SELECT_TOOL_CALL", toolCallId })}
              onOpenFile={handleOpenFilePath}
              onPreviewArtifact={() => setActiveTab("preview")}
            />
            {planEntries && planEntries.length > 0 && (
              <div className="max-w-full px-3 pt-1 flex-shrink-0">
                <PlanDisplay entries={planEntries} />
              </div>
            )}
            <div className="flex-shrink-0">
              <CodingInput
                onSend={session.sendPrompt}
                onDropQueuedPrompt={session.dropQueuedPrompt}
                onCancel={session.cancelPrompt}
                isProcessing={activeSession?.isProcessing ?? false}
                queueSize={activeSession?.promptQueue.length ?? 0}
                queuedPrompts={activeSession?.promptQueue ?? []}
                disabled={!state.initialized || !activeSession}
              />
            </div>
          </div>

          <ResizeHandle direction="horizontal" isDragging={conversationPanel.isDragging} onMouseDown={conversationPanel.handleMouseDown} />

          {/* IDE_Panel */}
          <div className="flex-1 flex flex-col min-w-0 min-h-0 overflow-hidden">
            <div className="flex-1 flex flex-col min-h-0 overflow-hidden">
              <div className="flex-1 flex min-h-0 overflow-hidden">
                {fileTreeVisible && (
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
                          <div className="flex items-center justify-center h-full text-xs text-gray-400">加载中...</div>
                        ) : (
                          <FileTree tree={tree} onFileSelect={handleFileSelect} selectedPath={activeSession?.activeFilePath} />
                        )}
                      </div>
                    </div>
                    <ResizeHandle direction="horizontal" isDragging={fileTreePanel.isDragging} onMouseDown={fileTreePanel.handleMouseDown} />
                  </>
                )}

                <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
                  <div className="flex items-center border-b border-gray-200/60 bg-white/30 flex-shrink-0">
                    <button
                      className={`px-4 py-2 text-sm font-medium transition-colors border-b-2 flex items-center gap-1.5
                        ${activeTab === "code" ? "text-blue-600 border-blue-500 bg-white/50" : "text-gray-500 border-transparent hover:text-gray-700 hover:bg-gray-50/50"}`}
                      onClick={() => setActiveTab("code")}
                    >
                      <Code2 size={14} /> 代码
                    </button>
                    <button
                      className={`px-4 py-2 text-sm font-medium transition-colors border-b-2 flex items-center gap-1.5
                        ${activeTab === "preview" ? "text-blue-600 border-blue-500 bg-white/50" : "text-gray-500 border-transparent hover:text-gray-700 hover:bg-gray-50/50"}`}
                      onClick={() => setActiveTab("preview")}
                    >
                      <Eye size={14} /> 预览
                    </button>
                    <div className="flex-1" />
                    <button
                      className={`w-7 h-7 flex items-center justify-center rounded transition-colors mr-2
                        ${fileTreeVisible ? "text-blue-600 bg-blue-50" : "text-gray-400 hover:text-gray-600 hover:bg-gray-100"}`}
                      onClick={toggleFileTree}
                      title={fileTreeVisible ? "隐藏文件" : "显示文件"}
                    >
                      {fileTreeVisible ? <FolderOpen size={16} /> : <Folder size={16} />}
                    </button>
                  </div>
                  <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                    {activeTab === "preview" ? (
                      <PreviewPanel
                        sessionId={activeSession?.id ?? null}
                        previewPorts={activeSession?.previewPorts ?? { ports: [], selectedPort: null }}
                        artifacts={activeSession?.artifacts ?? []}
                        activeArtifactId={activeSession?.activeArtifactId ?? null}
                        onPortSelect={(port) => activeSession && dispatch({ type: "PREVIEW_PORT_SELECTED", sessionId: activeSession.id, port })}
                        onAddPort={(port) => activeSession && dispatch({ type: "PREVIEW_PORT_ADDED", sessionId: activeSession.id, port })}
                        onSelectArtifact={(artifactId) => dispatch({ type: "SELECT_ARTIFACT", artifactId })}
                        onRefresh={handleRefreshPreview}
                        onOpenExternal={handleOpenExternal}
                      />
                    ) : (
                      <EditorArea
                        openFiles={activeSession?.openFiles ?? []}
                        activeFilePath={activeSession?.activeFilePath ?? null}
                        onSelectFile={handleSelectFile}
                        onCloseFile={handleCloseFile}
                      />
                    )}
                  </div>
                </div>
              </div>

              {!terminalCollapsed && (
                <ResizeHandle direction="vertical" isDragging={terminalPanel.isDragging} onMouseDown={terminalPanel.handleMouseDown} />
              )}
              <div className="flex-shrink-0">
                <TerminalPanel
                  ref={terminalPanelRef}
                  height={terminalPanel.size}
                  collapsed={terminalCollapsed}
                  onToggleCollapse={toggleTerminalCollapse}
                  runtime={currentRuntimeRef.current}
                />
              </div>
            </div>
          </div>
        </>
      ) : null}

      {/* ===== ConfigSidebar ===== */}
      <ConfigSidebar
        open={configOpen}
        onClose={() => setConfigOpen(false)}
        config={config}
        onConfigChange={setConfig}
        isFirstTime={isFirstTime}
      />

      {/* ===== Permission dialog ===== */}
      {state.pendingPermission && (
        <PermissionDialog
          permission={state.pendingPermission}
          onRespond={session.respondPermission}
        />
      )}
      </div>
    </div>
  );
}

function Coding() {
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
    <div className={`flex flex-col overflow-hidden ${isWelcomePhase ? "min-h-screen" : "h-screen bg-gray-50/30"}`}>
      {/* 背景层：仅欢迎页显示 */}
      {isWelcomePhase && (
        <>
          <div
            className="fixed w-full h-full z-[1]"
            style={{
              backgroundImage: `url(${bgImage})`,
              backgroundSize: "cover",
              backgroundPosition: "center",
              backgroundRepeat: "no-repeat",
              backgroundAttachment: "fixed",
            }}
          />
          <div
            className="fixed w-full h-full z-[2]"
            style={{ backdropFilter: "blur(204px)" }}
          />
        </>
      )}
      <div className="relative z-10 flex-shrink-0">
        <Header />
      </div>
      <div className={`flex-1 min-h-0 relative z-10 flex flex-col ${isWelcomePhase ? "px-8" : ""}`}>
        <CodingContent />
      </div>
    </div>
  );
}

export default Coding;
