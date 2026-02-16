import { useState, useCallback, useMemo } from "react";
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

function buildWsUrl(provider?: string): string {
  const base = `${window.location.protocol === "https:" ? "wss:" : "ws:"}//${window.location.host}/ws/acp`;
  const params = new URLSearchParams();
  const token = localStorage.getItem("access_token");
  if (token) params.set("token", token);
  const p = provider || localStorage.getItem("hicoding:cliProvider") || "";
  if (p) params.set("provider", p);
  const qs = params.toString();
  return qs ? `${base}?${qs}` : base;
}

function QuestContent() {
  // CLI Provider 切换
  const [currentProvider, setCurrentProvider] = useState(
    () => localStorage.getItem("hicoding:cliProvider") || ""
  );
  const [wsUrl, setWsUrl] = useState(() => buildWsUrl(currentProvider));
  const session = useAcpSession({ wsUrl });

  const state = useQuestState();
  const activeQuest = useActiveQuest();
  const dispatch = useQuestDispatch();
  const [panelCollapsed, setPanelCollapsed] = useState(false);

  const handleProviderChange = useCallback((providerKey: string) => {
    localStorage.setItem("hicoding:cliProvider", providerKey);
    setCurrentProvider(providerKey);
    dispatch({ type: "RESET_STATE" });
    setWsUrl(buildWsUrl(providerKey));
  }, [dispatch]);

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
        creatingQuest={session.creatingQuest}
      />
      {activeQuest ? (
        <div className="flex-1 flex min-w-0">
          {/* Middle column: top bar + chat + input */}
          <div className="flex-1 flex flex-col min-w-0">
            <QuestTopBar
              status={session.status}
              onSetModel={session.setModel}
              currentProvider={currentProvider}
              onProviderChange={handleProviderChange}
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
            onCreateQuest={handleCreateQuest}
            disabled={!state.initialized}
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
