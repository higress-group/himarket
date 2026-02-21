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
import type { ChatItemPlan, ChatItemToolCall } from "../types/acp";
import type { ICliProvider } from "../lib/apis/cliProvider";
import { buildAcpWsUrl } from "../lib/utils/wsUrl";

function QuestContent() {
  // 延迟连接模式：初始 wsUrl 为空，不触发连接
  const [currentWsUrl, setCurrentWsUrl] = useState("");
  const [, setCurrentProvider] = useState(
    () => localStorage.getItem("hicoding:cliProvider") || ""
  );
  const session = useAcpSession({ wsUrl: currentWsUrl });

  const state = useQuestState();
  const activeQuest = useActiveQuest();
  const dispatch = useQuestDispatch();
  const [panelCollapsed, setPanelCollapsed] = useState(false);

  const isConnected = session.status === "connected";

  // 自动创建 Quest 的标记
  const autoCreatedRef = useRef(false);

  // 连接成功且初始化完成后，自动创建第一个 Quest
  useEffect(() => {
    if (
      isConnected &&
      state.initialized &&
      Object.keys(state.quests).length === 0 &&
      !autoCreatedRef.current
    ) {
      autoCreatedRef.current = true;
      session.createQuest(".").catch((err) => {
        console.error("Auto create quest failed:", err);
      });
    }
    // 断开连接时重置标记
    if (!isConnected) {
      autoCreatedRef.current = false;
    }
  }, [isConnected, state.initialized, state.quests, session]);

  // CLI 工具选择 → 构建 wsUrl 触发连接
  const handleSelectCli = useCallback(
    (cliId: string, _cwd: string, _runtime?: string, _providerObj?: ICliProvider) => {
      localStorage.setItem("hicoding:cliProvider", cliId);
      setCurrentProvider(cliId);
      const url = buildAcpWsUrl({
        token: localStorage.getItem("access_token") || undefined,
        provider: cliId || undefined,
        runtime: "local",
      });
      setCurrentWsUrl(url);
    },
    []
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
    });
  }, [session]);

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
