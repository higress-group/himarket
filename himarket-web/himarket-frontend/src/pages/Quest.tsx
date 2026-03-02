import { useState, useCallback, useMemo, useEffect, useRef } from "react";
import { Layout } from "../components/Layout";
import {
  QuestSessionProvider,
  useQuestState,
  useActiveQuest,
  useQuestDispatch,
} from "../context/QuestSessionContext";
import { useAcpSession } from "../hooks/useAcpSession";
import { QuestSidebar } from "../components/quest/QuestSidebar";
import { QuestTopBar } from "../components/quest/QuestTopBar";
import { QuestWelcome } from "../components/quest/QuestWelcome";
import { ChatStream } from "../components/quest/ChatStream";
import { RightPanel } from "../components/quest/RightPanel";
import { QuestInput } from "../components/quest/QuestInput";
import { PermissionDialog } from "../components/quest/PermissionDialog";
import { PlanDisplay } from "../components/quest/PlanDisplay";
import { SandboxInitProgress } from "../components/hicli/SandboxInitProgress";
import type { ChatItemPlan, ChatItemToolCall } from "../types/acp";
import type { ICliProvider } from "../lib/apis/cliProvider";
import { buildAcpWsUrl } from "../lib/utils/wsUrl";

function QuestContent() {
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
  const [panelCollapsed, setPanelCollapsed] = useState(false);

  const isConnected = session.status === "connected";

  // 自动创建 Quest 的标记
  const autoCreatedRef = useRef(false);

  // 跟踪当前运行时类型
  const currentRuntimeRef = useRef<string>("local");

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
    console.log("[Quest] Auto-creating quest:", {
      runtime: currentRuntimeRef.current,
      sandboxStatus: state.sandboxStatus?.status,
    });

    session.createQuest(".").catch((err) => {
      console.error("[Quest] Auto create quest failed:", err);
      // Keep autoCreatedRef = true to prevent infinite retry
      // Show error to user via sandbox status
      dispatch({
        type: "SANDBOX_STATUS",
        status: "error",
        message: err?.message || "会话创建失败，请重新连接",
      });
    });
  }, [isConnected, state.initialized, state.quests, state.sandboxStatus, session, dispatch]);

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

  // 切换工具：断开连接 → RESET_STATE → 清空 wsUrl → 返回欢迎页
  const handleSwitchTool = useCallback(() => {
    session.disconnect();
    dispatch({ type: "RESET_STATE" });
    setCurrentWsUrl("");
  }, [session, dispatch]);

  const handleCreateQuest = useCallback(() => {
    session.createQuest(".").catch(err => {
      console.error("Failed to create quest:", err);
      dispatch({
        type: "SANDBOX_STATUS",
        status: "error",
        message: err?.message || "会话创建失败",
      });
    });
  }, [session, dispatch]);

  const hasArtifacts = (activeQuest?.artifacts.length ?? 0) > 0;
  const activeQuestMessages = activeQuest?.messages;

  const hasDiffs = useMemo(() => {
    if (!activeQuestMessages) return false;
    return activeQuestMessages.some(
      m =>
        m.type === "tool_call" &&
        (m as ChatItemToolCall).content?.some(c => c.type === "diff")
    );
  }, [activeQuestMessages]);

  const hasTerminals = useMemo(() => {
    if (!activeQuestMessages) return false;
    return activeQuestMessages.some(
      m =>
        m.type === "tool_call" &&
        (m as ChatItemToolCall).content?.some(c => c.type === "terminal")
    );
  }, [activeQuestMessages]);

  const showRightPanel = hasDiffs || hasArtifacts || hasTerminals;

  const planEntries = useMemo(() => {
    const plan = activeQuestMessages?.find(
      (m): m is ChatItemPlan => m.type === "plan"
    );
    return plan?.entries;
  }, [activeQuestMessages]);

  return (
    <div className="flex h-full">
      <QuestSidebar
        onCreateQuest={handleCreateQuest}
        onSwitchQuest={session.switchQuest}
        onCloseQuest={session.closeQuest}
        onSwitchTool={handleSwitchTool}
        status={session.status}
        creatingQuest={session.creatingQuest}
      />

      {activeQuest ? (
        <div className="flex-1 flex min-w-0">
          {/* Middle column: top bar + chat + input */}
          <div className="flex-1 flex flex-col min-w-0">
            <QuestTopBar
              status={session.status}
              onSetModel={session.setModel}
            />
            <ChatStream
              onSelectToolCall={toolCallId =>
                dispatch({ type: "SELECT_TOOL_CALL", toolCallId })
              }
            />
            {planEntries && planEntries.length > 0 && (
              <div className="max-w-3xl mx-auto w-full px-4 pt-2">
                <PlanDisplay entries={planEntries} />
              </div>
            )}
            <QuestInput
              onSend={session.sendPrompt}
              onDropQueuedPrompt={session.dropQueuedPrompt}
              onCancel={session.cancelPrompt}
              isProcessing={activeQuest.isProcessing}
              queueSize={activeQuest.promptQueue.length}
              queuedPrompts={activeQuest.promptQueue}
              disabled={!state.initialized}
            />
          </div>
          {/* Right column: artifacts & changes */}
          {showRightPanel && (
            <RightPanel
              collapsed={panelCollapsed}
              onToggleCollapse={() => setPanelCollapsed(p => !p)}
            />
          )}
        </div>
      ) : !isConnected ? (
        /* 未连接：显示 CLI 选择器 */
        <div className="flex-1 flex flex-col min-w-0">
          <QuestWelcome
            onSelectCli={handleSelectCli}
            onCreateQuest={handleCreateQuest}
            isConnected={false}
            disabled={false}
            creatingQuest={false}
          />
        </div>
      ) : isConnected && !state.initialized ? (
        /* 已连接但未初始化：显示进度 */
        <div className="flex-1 flex items-center justify-center">
          <SandboxInitProgress />
        </div>
      ) : state.sandboxStatus?.status === "error" ? (
        /* 沙箱错误：显示错误信息和重连按钮 */
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
      ) : (
        <div className="flex-1 flex flex-col min-w-0">
          <QuestWelcome
            onSelectCli={handleSelectCli}
            onCreateQuest={handleCreateQuest}
            isConnected={isConnected && state.initialized}
            disabled={false}
            creatingQuest={session.creatingQuest}
          />
        </div>
      )}

      {state.pendingPermission && (
        <PermissionDialog
          permission={state.pendingPermission}
          onRespond={session.respondPermission}
        />
      )}
    </div>
  );
}

function Quest() {
  return (
    <Layout className="h-screen overflow-hidden">
      <QuestSessionProvider>
        <QuestContent />
      </QuestSessionProvider>
    </Layout>
  );
}

export default Quest;
