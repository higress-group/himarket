import { useState, useCallback } from "react";
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
import { ToolPanel } from "../components/coding/ToolPanel";
import { CodingInput } from "../components/coding/CodingInput";
import { PermissionDialog } from "../components/coding/PermissionDialog";

const WS_URL = `${window.location.protocol === "https:" ? "wss:" : "ws:"}//${window.location.host}/ws/acp`;

function CodingContent() {
  const session = useAcpSession({ wsUrl: WS_URL });
  const state = useCodingState();
  const activeQuest = useActiveQuest();
  const dispatch = useCodingDispatch();
  const [panelCollapsed, setPanelCollapsed] = useState(false);

  const handleCreateQuest = useCallback(() => {
    session.createQuest(".").catch(err => {
      console.error("Failed to create quest:", err);
    });
  }, [session]);

  const hasToolSelected = activeQuest?.selectedToolCallId != null;

  return (
    <div className="flex h-[calc(100vh-48px)]">
      <CodingSidebar
        onCreateQuest={handleCreateQuest}
        onSwitchQuest={session.switchQuest}
        onCloseQuest={session.closeQuest}
      />
      <div className="flex-1 flex flex-col min-w-0">
        {activeQuest ? (
          <>
            <CodingTopBar
              status={session.status}
              onSetModel={session.setModel}
              onSetMode={session.setMode}
            />
            <div className="flex-1 flex min-h-0">
              <ChatStream
                onSelectToolCall={toolCallId =>
                  dispatch({ type: "SELECT_TOOL_CALL", toolCallId })
                }
              />
              {hasToolSelected && !panelCollapsed && (
                <ToolPanel
                  collapsed={panelCollapsed}
                  onToggleCollapse={() => setPanelCollapsed(p => !p)}
                />
              )}
            </div>
            <CodingInput
              onSend={session.sendPrompt}
              onCancel={session.cancelPrompt}
              isProcessing={activeQuest.isProcessing}
              disabled={!state.initialized}
            />
          </>
        ) : (
          <CodingWelcome
            onCreateQuest={handleCreateQuest}
            disabled={!state.initialized}
          />
        )}
      </div>

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
    <Layout>
      <CodingSessionProvider>
        <CodingContent />
      </CodingSessionProvider>
    </Layout>
  );
}

export default Coding;
