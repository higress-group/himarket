import { useState, useCallback, useEffect, useRef } from "react";
import { RefreshCw, ExternalLink, Code2, Eye } from "lucide-react";
import { message } from "antd";
import { Header } from "../components/Header";
import {
  QuestSessionProvider,
  useQuestState,
  useActiveQuest,
  useQuestDispatch,
} from "../context/QuestSessionContext";
import { useAcpSession } from "../hooks/useAcpSession";
import { useResizable } from "../hooks/useResizable";
import { CodingTopBar } from "../components/coding/CodingTopBar";
import { FileTree } from "../components/coding/FileTree";
import { EditorArea } from "../components/coding/EditorArea";
import { TerminalPanel } from "../components/coding/TerminalPanel";
import { PreviewPanel } from "../components/coding/PreviewPanel";
import { ChatStream } from "../components/quest/ChatStream";
import { QuestInput } from "../components/quest/QuestInput";
import { PermissionDialog } from "../components/quest/PermissionDialog";
import { PlanDisplay } from "../components/quest/PlanDisplay";
import {
  fetchDirectoryTree,
  fetchArtifactContent,
  fetchWorkspaceChanges,
  getPreviewUrl,
} from "../lib/utils/workspaceApi";
import type { FileNode, OpenFile } from "../types/coding";
import type { ChatItemPlan } from "../types/acp";
import type { ICliProvider } from "../lib/apis/cliProvider";
import { buildAcpWsUrl } from "../lib/utils/wsUrl";
import { WelcomePage } from "../components/common/WelcomePage";
import { SandboxInitProgress } from "../components/hicli/SandboxInitProgress";

const EXT_TO_LANG: Record<string, string> = {
  ts: "typescript",
  tsx: "typescript",
  js: "javascript",
  jsx: "javascript",
  json: "json",
  html: "html",
  css: "css",
  scss: "scss",
  less: "less",
  md: "markdown",
  py: "python",
  java: "java",
  xml: "xml",
  yaml: "yaml",
  yml: "yaml",
  sh: "shell",
  bash: "shell",
  sql: "sql",
  go: "go",
  rs: "rust",
  toml: "toml",
  vue: "html",
  svelte: "html",
};

function inferLanguage(fileName: string): string {
  const ext = fileName.split(".").pop()?.toLowerCase() ?? "";
  return EXT_TO_LANG[ext] ?? "plaintext";
}

// buildWsUrl removed — wsUrl is now built lazily via handleSelectCli

function readBool(key: string, fallback: boolean): boolean {
  try {
    const raw = localStorage.getItem(key);
    if (raw === "true") return true;
    if (raw === "false") return false;
  } catch {
    // ignore
  }
  return fallback;
}

function writeBool(key: string, value: boolean): void {
  try {
    localStorage.setItem(key, String(value));
  } catch {
    // ignore
  }
}

/* ─── Resize handle shared component ─── */
function ResizeHandle({
  direction,
  isDragging,
  onMouseDown,
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
        <div className={`absolute inset-y-0 -left-1 -right-1 z-10`} />
      </div>
    );
  }
  return (
    <div
      className={`h-1 flex-shrink-0 cursor-row-resize group relative
        ${isDragging ? "bg-blue-500/40" : "hover:bg-blue-500/30"}`}
      onMouseDown={onMouseDown}
    >
      <div className={`absolute inset-x-0 -top-1 -bottom-1 z-10`} />
    </div>
  );
}

type RightTab = "preview" | "code";

// Kinds that are read-only and never create/modify/delete files
const READ_ONLY_KINDS = new Set([
  "read",
  "search",
  "think",
  "fetch",
  "switch_mode",
]);

function CodingContent() {
  // 延迟连接模式：初始 wsUrl 为空，不触发连接
  const [currentWsUrl, setCurrentWsUrl] = useState("");
  const [currentCliSessionConfig, setCurrentCliSessionConfig] = useState<string | undefined>();
  const [, setCurrentProvider] = useState(
    () => localStorage.getItem("hicoding:cliProvider") || ""
  );
  const session = useAcpSession({ wsUrl: currentWsUrl, cliSessionConfig: currentCliSessionConfig });

  const state = useQuestState();
  const activeQuest = useActiveQuest();
  const dispatch = useQuestDispatch();

  const isConnected = session.status === "connected";

  // 跟踪当前运行时类型
  const currentRuntimeRef = useRef<string>("local");

  // CLI 工具选择 → 构建 wsUrl 触发连接
  const handleSelectCli = useCallback(
    (cliId: string, _cwd: string, runtime?: string, _providerObj?: ICliProvider, cliSessionConfig?: string) => {
      localStorage.setItem("hicoding:cliProvider", cliId);
      setCurrentProvider(cliId);
      const isK8s = runtime === "k8s";
      currentRuntimeRef.current = runtime || "local";
      // cliSessionConfig 不再通过 URL 传递，改为 WebSocket 连接后通过 session/config 消息发送
      setCurrentCliSessionConfig(cliSessionConfig);
      const url = buildAcpWsUrl({
        token: localStorage.getItem("access_token") || undefined,
        provider: cliId || undefined,
        runtime: runtime || "local",
        sandboxMode: isK8s ? "user" : undefined,
      });
      if (isK8s) {
        dispatch({ type: "SANDBOX_STATUS", status: "creating", message: "正在连接沙箱环境..." });
      }
      setCurrentWsUrl(url);
    },
    [dispatch]
  );


  const [activeTab, setActiveTab] = useState<RightTab>("code");
  const [tree, setTree] = useState<FileNode[]>([]);
  const [treeLoading, setTreeLoading] = useState(false);
  const autoOpenedRef = useRef<Set<string>>(new Set());

  // File tree visibility (persisted)
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

  // Terminal collapse (persisted)
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

  // Resizable panels
  const leftPanel = useResizable({
    direction: "horizontal",
    defaultSize: 420,
    minSize: 320,
    maxSize: 600,
    storageKey: "hicoding:leftPanelWidth",
  });

  const fileTreePanel = useResizable({
    direction: "horizontal",
    defaultSize: 200,
    minSize: 140,
    maxSize: 400,
    storageKey: "hicoding:fileTreeWidth",
  });

  const terminalPanel = useResizable({
    direction: "vertical",
    defaultSize: 200,
    minSize: 100,
    maxSize: 500,
    storageKey: "hicoding:terminalHeight",
    reverse: true,
  });

  // 自动创建 Quest 的标记
  const autoCreatedRef = useRef(false);

  // 连接成功且初始化完成后，自动创建第一个 Quest
  // K8s 运行时：等待 sandboxStatus.status === "ready" 后再创建
  // 本地运行时：保持现有行为，连接成功后立即创建
  useEffect(() => {
    if (!isConnected || !state.initialized || Object.keys(state.quests).length > 0 || autoCreatedRef.current) {
      // 断开连接时重置标记
      if (!isConnected) {
        autoCreatedRef.current = false;
      }
      return;
    }

    const isK8s = currentRuntimeRef.current === "k8s";

    // K8s 运行时：sandboxStatus 为 creating 时不创建，等待 ready
    if (isK8s && state.sandboxStatus?.status !== "ready") {
      return;
    }

    // Set flag BEFORE creation to prevent re-entry on failure
    autoCreatedRef.current = true;

    // Add debug logging
    console.log("[Coding] Auto-creating quest:", {
      runtime: currentRuntimeRef.current,
      sandboxStatus: state.sandboxStatus?.status,
    });

    session.createQuest(".").catch(err => {
      console.error("[Coding] Auto create quest failed:", err);
      // Keep autoCreatedRef = true to prevent infinite retry
      // Show error to user via sandbox status
      dispatch({
        type: "SANDBOX_STATUS",
        status: "error",
        message: err?.message || "会话创建失败，请重新连接",
      });
    });
  }, [isConnected, state.initialized, state.quests, state.sandboxStatus, session, dispatch]);

  // Load directory tree when quest is created
  useEffect(() => {
    if (!activeQuest?.cwd) return;
    setTreeLoading(true);
    fetchDirectoryTree(activeQuest.cwd, 10, currentRuntimeRef.current).then(nodes => {
      setTree(nodes);
      setTreeLoading(false);
    });
  }, [activeQuest?.cwd]);

  // Refresh tree on tool call completion (file may have been created/modified)
  const messageCount = activeQuest?.messages.length ?? 0;
  useEffect(() => {
    if (!activeQuest?.cwd || messageCount === 0) return;
    const lastMsg = activeQuest.messages[messageCount - 1];
    if (
      lastMsg?.type === "tool_call" &&
      (lastMsg.status === "completed" || lastMsg.status === "failed") &&
      !READ_ONLY_KINDS.has(lastMsg.kind)
    ) {
      fetchDirectoryTree(activeQuest.cwd, 10, currentRuntimeRef.current).then(setTree);
    }
  }, [messageCount, activeQuest?.cwd, activeQuest?.messages]);

  // Poll for external file changes (e.g. user creates files via terminal)
  const lastPollRef = useRef<number>(0);
  const pollingRef = useRef(false);
  useEffect(() => {
    if (!activeQuest?.cwd) return;
    const cwd = activeQuest.cwd;
    lastPollRef.current = Date.now();
    const pollInterval = 10000;
    const interval = setInterval(async () => {
      if (pollingRef.current) return; // 上一次还没回来，跳过
      pollingRef.current = true;
      try {
        const changes = await fetchWorkspaceChanges(cwd, lastPollRef.current, 200, currentRuntimeRef.current);
        if (changes.length > 0) {
          lastPollRef.current = Date.now();
          fetchDirectoryTree(cwd, 10, currentRuntimeRef.current).then(setTree);
        }
      } finally {
        pollingRef.current = false;
      }
    }, pollInterval);
    return () => clearInterval(interval);
  }, [activeQuest?.cwd]);

  // Auto-switch to preview when port is detected
  useEffect(() => {
    if (activeQuest?.previewPort) {
      setActiveTab("preview");
    }
  }, [activeQuest?.previewPort]);

  // Auto-open files when Agent edits them
  useEffect(() => {
    if (!activeQuest || messageCount === 0) return;
    for (const msg of activeQuest.messages) {
      if (msg.type !== "tool_call") continue;
      if (msg.kind !== "edit") continue;
      if (msg.status !== "completed") continue;
      const locations = msg.locations;
      if (!locations || locations.length === 0) continue;
      for (const loc of locations) {
        const key = `${activeQuest.id}:${msg.toolCallId}:${loc.path}`;
        if (autoOpenedRef.current.has(key)) continue;
        autoOpenedRef.current.add(key);
        const filePath = loc.path;
        const fileName = filePath.split("/").pop() ?? filePath;
        fetchArtifactContent(filePath, { raw: true, runtime: currentRuntimeRef.current }).then(result => {
          if (result.content !== null) {
            dispatch({
              type: "FILE_OPENED",
              questId: activeQuest.id,
              file: {
                path: filePath,
                fileName,
                content: result.content,
                language: inferLanguage(fileName),
                encoding: result.encoding ?? "utf-8",
              },
            });
            setActiveTab("code");
          }
        });
      }
    }
  }, [messageCount, activeQuest, dispatch]);

  const handleFileSelect = useCallback(
    async (node: FileNode) => {
      if (node.type !== "file" || !activeQuest) return;
      if (activeQuest.openFiles.some(f => f.path === node.path)) {
        dispatch({
          type: "ACTIVE_FILE_CHANGED",
          questId: activeQuest.id,
          path: node.path,
        });
        setActiveTab("code");
        return;
      }
      const result = await fetchArtifactContent(node.path, { raw: true, runtime: currentRuntimeRef.current });
      if (result.content !== null) {
        const file: OpenFile = {
          path: node.path,
          fileName: node.name,
          content: result.content,
          language: inferLanguage(node.name),
          encoding: result.encoding ?? "utf-8",
        };
        dispatch({ type: "FILE_OPENED", questId: activeQuest.id, file });
        setActiveTab("code");
      } else if (result.error) {
        message.warning(result.error.message);
      }
    },
    [activeQuest, dispatch]
  );

  const handleCloseFile = useCallback(
    (path: string) => {
      if (!activeQuest) return;
      dispatch({ type: "FILE_CLOSED", questId: activeQuest.id, path });
    },
    [activeQuest, dispatch]
  );

  const handleOpenFilePath = useCallback(
    async (path: string) => {
      if (!activeQuest) return;
      // If already open, just switch to it
      if (activeQuest.openFiles.some(f => f.path === path)) {
        dispatch({
          type: "ACTIVE_FILE_CHANGED",
          questId: activeQuest.id,
          path,
        });
        setActiveTab("code");
        return;
      }
      const result = await fetchArtifactContent(path, { raw: true, runtime: currentRuntimeRef.current });
      if (result.content !== null) {
        const fileName = path.split(/[/\\]/).pop() ?? path;
        const file: OpenFile = {
          path,
          fileName,
          content: result.content,
          language: inferLanguage(fileName),
          encoding: result.encoding ?? "utf-8",
        };
        dispatch({ type: "FILE_OPENED", questId: activeQuest.id, file });
        setActiveTab("code");
      } else if (result.error) {
        message.warning(result.error.message);
      }
    },
    [activeQuest, dispatch]
  );

  const handleSelectFile = useCallback(
    (path: string) => {
      if (!activeQuest) return;
      dispatch({
        type: "ACTIVE_FILE_CHANGED",
        questId: activeQuest.id,
        path,
      });
    },
    [activeQuest, dispatch]
  );

  // DEV: hardcode port 3000 for testing preview
  const previewPort = activeQuest?.previewPort ?? 3000;
  const sandboxHost = state.sandboxStatus?.sandboxHost ?? null;

  const handleRefreshPreview = useCallback(() => {
    const iframe = document.querySelector<HTMLIFrameElement>(
      "#coding-preview-iframe"
    );
    if (iframe && previewPort) {
      iframe.src = getPreviewUrl(previewPort, sandboxHost);
    }
  }, [previewPort, sandboxHost]);

  const handleOpenExternal = useCallback(() => {
    if (previewPort) {
      window.open(getPreviewUrl(previewPort, sandboxHost), "_blank");
    }
  }, [previewPort, sandboxHost]);

  const planEntries = activeQuest?.messages.find(
    (m): m is ChatItemPlan => m.type === "plan"
  )?.entries;

  // 未连接时显示欢迎页，连接中显示进度，已连接时显示 IDE 界面
  if (!isConnected) {
    return (
      <div className="flex flex-1 min-h-0 overflow-hidden bg-white/50">
        <WelcomePage
          icon={<Code2 size={48} />}
          title="HiCoding"
          description="选择一个 CLI 工具开始编码"
          isConnected={false}
          disabled={false}
          onSelectCli={handleSelectCli}
          showRuntimeSelector={true}
        />

        {/* Permission dialog */}
        {state.pendingPermission && (
          <PermissionDialog
            permission={state.pendingPermission}
            onRespond={session.respondPermission}
          />
        )}
      </div>
    );
  }

  // 沙箱错误：显示错误信息和重连按钮
  if (state.sandboxStatus?.status === "error") {
    return (
      <div className="flex flex-1 min-h-0 overflow-hidden bg-white/50">
        <div className="flex-1 flex items-center justify-center">
          <div className="text-center">
            <p className="text-red-500 mb-4">{state.sandboxStatus.message}</p>
            <button
              onClick={() => {
                autoCreatedRef.current = false;
                setCurrentWsUrl("");
              }}
              className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
            >
              重新连接
            </button>
          </div>
        </div>

        {/* Permission dialog */}
        {state.pendingPermission && (
          <PermissionDialog
            permission={state.pendingPermission}
            onRespond={session.respondPermission}
          />
        )}
      </div>
    );
  }

  // 已连接但未初始化：显示进度
  if (!state.initialized || !activeQuest) {
    return (
      <div className="flex flex-1 min-h-0 overflow-hidden bg-white/50">
        <div className="flex-1 flex items-center justify-center">
          <SandboxInitProgress />
        </div>

        {/* Permission dialog */}
        {state.pendingPermission && (
          <PermissionDialog
            permission={state.pendingPermission}
            onRespond={session.respondPermission}
          />
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-1 min-h-0 overflow-hidden">
      {/* ===== Left Column: Conversation ===== */}
      <div
        className="flex flex-col border-r border-gray-200/60 bg-white/50 overflow-hidden flex-shrink-0"
        style={{ width: leftPanel.size }}
      >
        <CodingTopBar
          status={session.status}
          onSetModel={session.setModel}
          fileTreeVisible={fileTreeVisible}
          onToggleFileTree={toggleFileTree}
        />

        <ChatStream
          onSelectToolCall={toolCallId =>
            dispatch({ type: "SELECT_TOOL_CALL", toolCallId })
          }
          onOpenFile={handleOpenFilePath}
        />

        {planEntries && planEntries.length > 0 && (
          <div className="max-w-full px-3 pt-1 flex-shrink-0">
            <PlanDisplay entries={planEntries} />
          </div>
        )}

        <div className="flex-shrink-0">
          <QuestInput
            onSend={session.sendPrompt}
            onDropQueuedPrompt={session.dropQueuedPrompt}
            onCancel={session.cancelPrompt}
            isProcessing={activeQuest?.isProcessing ?? false}
            queueSize={activeQuest?.promptQueue.length ?? 0}
            queuedPrompts={activeQuest?.promptQueue ?? []}
            disabled={!state.initialized || !activeQuest}
          />
        </div>
      </div>

      {/* ===== Left-Right Resize Handle ===== */}
      <ResizeHandle
        direction="horizontal"
        isDragging={leftPanel.isDragging}
        onMouseDown={leftPanel.handleMouseDown}
      />

      {/* ===== Right Column: IDE ===== */}
      <div className="flex-1 flex flex-col min-w-0 min-h-0 overflow-hidden">
        {/* Content area + Terminal */}
        <div className="flex-1 flex flex-col min-h-0 overflow-hidden">
          {/* Upper: Content area */}
          <div className="flex-1 flex min-h-0 overflow-hidden">
            {/* File tree sidebar (middle section, collapsible) */}
            {fileTreeVisible && (
              <>
                <div
                  className="border-r border-gray-200/60 bg-white/50 overflow-hidden flex-shrink-0 flex flex-col"
                  style={{ width: fileTreePanel.size }}
                >
                  {/* File tree header */}
                  <div className="flex items-center px-3 py-2 border-b border-gray-200/60 bg-white/30">
                    <span className="text-xs font-medium text-gray-600">
                      文件
                    </span>
                  </div>
                  {/* File tree content */}
                  <div className="flex-1 overflow-hidden">
                    {treeLoading ? (
                      <div className="flex items-center justify-center h-full text-xs text-gray-400">
                        加载中...
                      </div>
                    ) : (
                      <FileTree
                        tree={tree}
                        onFileSelect={handleFileSelect}
                        selectedPath={activeQuest?.activeFilePath}
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

            {/* Right section: Editor + Preview area */}
            <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
              {/* Tab bar for code/preview toggle */}
              <div className="flex items-center border-b border-gray-200/60 bg-white/30 flex-shrink-0">
                <button
                  className={`px-4 py-2 text-sm font-medium transition-colors border-b-2 flex items-center gap-1.5
                    ${
                      activeTab === "code"
                        ? "text-blue-600 border-blue-500 bg-white/50"
                        : "text-gray-500 border-transparent hover:text-gray-700 hover:bg-gray-50/50"
                    }`}
                  onClick={() => setActiveTab("code")}
                >
                  <Code2 size={14} />
                  代码
                </button>
                <button
                  className={`px-4 py-2 text-sm font-medium transition-colors border-b-2 flex items-center gap-1.5
                    ${
                      activeTab === "preview"
                        ? "text-blue-600 border-blue-500 bg-white/50"
                        : "text-gray-500 border-transparent hover:text-gray-700 hover:bg-gray-50/50"
                    }`}
                  onClick={() => setActiveTab("preview")}
                >
                  <Eye size={14} />
                  预览
                </button>

                {/* Spacer */}
                <div className="flex-1" />

                {/* Preview tool buttons (only when preview tab active) */}
                {activeTab === "preview" && previewPort && (
                  <div className="flex items-center gap-0.5 pr-2">
                    <button
                      className="w-7 h-7 flex items-center justify-center rounded text-gray-400
                        hover:text-gray-600 hover:bg-gray-100 transition-colors"
                      onClick={handleRefreshPreview}
                      title="刷新预览"
                    >
                      <RefreshCw size={14} />
                    </button>
                    <button
                      className="w-7 h-7 flex items-center justify-center rounded text-gray-400
                        hover:text-gray-600 hover:bg-gray-100 transition-colors"
                      onClick={handleOpenExternal}
                      title="在新窗口打开预览"
                    >
                      <ExternalLink size={14} />
                    </button>
                  </div>
                )}
              </div>

              {/* Main content */}
              <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
                {activeTab === "preview" ? (
                  <PreviewPanel port={previewPort} sandboxHost={sandboxHost} />
                ) : (
                  <EditorArea
                    openFiles={activeQuest?.openFiles ?? []}
                    activeFilePath={activeQuest?.activeFilePath ?? null}
                    onSelectFile={handleSelectFile}
                    onCloseFile={handleCloseFile}
                  />
                )}
              </div>
            </div>
          </div>

          {/* Terminal resize handle (only when expanded) */}
          {!terminalCollapsed && (
            <ResizeHandle
              direction="vertical"
              isDragging={terminalPanel.isDragging}
              onMouseDown={terminalPanel.handleMouseDown}
            />
          )}

          {/* Lower: Terminal */}
          <div className="flex-shrink-0">
            <TerminalPanel
              height={terminalPanel.size}
              collapsed={terminalCollapsed}
              onToggleCollapse={toggleTerminalCollapse}
              runtime={currentRuntimeRef.current}
            />
          </div>
        </div>
      </div>

      {/* Permission dialog */}
      {state.pendingPermission && (
        <PermissionDialog
          permission={state.pendingPermission}
          onRespond={session.respondPermission}
        />
      )}
    </div>
  );
}

function Coding() {
  return (
    <div className="h-screen flex flex-col overflow-hidden bg-gray-50/30">
      <div className="relative z-10 flex-shrink-0">
        <Header />
      </div>
      <div className="flex-1 min-h-0 relative z-10 flex flex-col">
        <QuestSessionProvider>
          <CodingContent />
        </QuestSessionProvider>
      </div>
    </div>
  );
}

export default Coding;
