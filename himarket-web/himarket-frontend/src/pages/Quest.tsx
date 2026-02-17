import { useState, useCallback, useMemo } from "react";
import { Layout } from "../components/Layout";
import {
  QuestSessionProvider,
  useQuestState,
  useActiveQuest,
  useQuestDispatch,
} from "../context/QuestSessionContext";
import { useAcpSession } from "../hooks/useAcpSession";
import { useRuntimeSelection } from "../hooks/useRuntimeSelection";
import type { RuntimeType } from "../types/runtime";
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

function buildWsUrl(provider?: string, runtime?: string): string {
  const p = provider || localStorage.getItem("hicoding:cliProvider") || "";
  return buildAcpWsUrl({
    token: localStorage.getItem("access_token") || undefined,
    provider: p || undefined,
    runtime,
  });
}

function QuestContent() {
  // CLI Provider 切换
  const [currentProvider, setCurrentProvider] = useState(
    () => localStorage.getItem("hicoding:cliProvider") || ""
  );
  // 当前选中的 provider 对象（用于运行时选择）
  const [currentProviderObj, setCurrentProviderObj] = useState<ICliProvider | null>(null);
  const { selectedRuntime, compatibleRuntimes, selectRuntime } = useRuntimeSelection({
    provider: currentProviderObj,
  });
  const [wsUrl, setWsUrl] = useState(() => buildWsUrl(currentProvider, selectedRuntime));
  const session = useAcpSession({ wsUrl, runtimeType: selectedRuntime as RuntimeType });

  const state = useQuestState();
  const activeQuest = useActiveQuest();
  const dispatch = useQuestDispatch();
  const [panelCollapsed, setPanelCollapsed] = useState(false);

  const handleProviderChange = useCallback((providerKey: string, providerObj?: ICliProvider) => {
    localStorage.setItem("hicoding:cliProvider", providerKey);
    setCurrentProvider(providerKey);
    if (providerObj) setCurrentProviderObj(providerObj);
    dispatch({ type: "RESET_STATE" });
    setWsUrl(buildWsUrl(providerKey, selectedRuntime));
  }, [dispatch, selectedRuntime]);

  const handleRuntimeChange = useCallback((runtimeType: string) => {
    selectRuntime(runtimeType);
    dispatch({ type: "RESET_STATE" });
    setWsUrl(buildWsUrl(currentProvider, runtimeType));
  }, [selectRuntime, dispatch, currentProvider]);

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
              selectedRuntime={selectedRuntime}
              compatibleRuntimes={compatibleRuntimes}
              onRuntimeChange={handleRuntimeChange}
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
