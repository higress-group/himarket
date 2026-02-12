import { useState, useCallback, useMemo } from "react";
import { Layout } from "../components/Layout";
import {
  CodingSessionProvider,
  useCodingState,
  useActiveQuest,
  useCodingDispatch,
} from "../context/CodingSessionContext";
import { useAcpSession } from "../hooks/useAcpSession";
import { CodingSidebar } from "../components/coding/CodingSidebar";
import { CodingTopBar } from "../components/coding/CodingTopBar";
import { CodingWelcome } from "../components/coding/CodingWelcome";
import { ChatStream } from "../components/coding/ChatStream";
import { RightPanel } from "../components/coding/RightPanel";
import { CodingInput } from "../components/coding/CodingInput";
import { PermissionDialog } from "../components/coding/PermissionDialog";
import { PlanDisplay } from "../components/coding/PlanDisplay";
import type { ChatItemPlan, ChatItemToolCall } from "../types/acp";

function buildWsUrl(): string {
  const base = `${window.location.protocol === "https:" ? "wss:" : "ws:"}//${window.location.host}/ws/acp`;
  const token = localStorage.getItem("access_token");
  return token ? `${base}?token=${encodeURIComponent(token)}` : base;
}

function CodingContent() {
  const [wsUrl] = useState(buildWsUrl);
  const session = useAcpSession({ wsUrl });
  const state = useCodingState();
  const activeQuest = useActiveQuest();
  const dispatch = useCodingDispatch();
  const [panelCollapsed, setPanelCollapsed] = useState(false);

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
      <CodingSidebar
        onCreateQuest={handleCreateQuest}
        onSwitchQuest={session.switchQuest}
        onCloseQuest={session.closeQuest}
      />
      {activeQuest ? (
        <div className="flex-1 flex min-w-0">
          {/* Middle column: top bar + chat + input */}
          <div className="flex-1 flex flex-col min-w-0">
            <CodingTopBar
              status={session.status}
              onSetModel={session.setModel}
              onSetMode={session.setMode}
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
            <CodingInput
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
          <CodingWelcome
            onCreateQuest={handleCreateQuest}
            disabled={!state.initialized}
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

function Coding() {
  return (
    <Layout className="h-screen overflow-hidden">
      <CodingSessionProvider>
        <CodingContent />
      </CodingSessionProvider>
    </Layout>
  );
}

export default Coding;
