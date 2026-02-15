import { useState, useCallback, useEffect, useRef } from "react";
import { X } from "lucide-react";
import { Header } from "../components/Header";
import {
  HiCliSessionProvider,
  useHiCliState,
  useHiCliDispatch,
} from "../context/HiCliSessionContext";
import { useActiveQuest } from "../context/QuestSessionContext";
import { useHiCliSession } from "../hooks/useHiCliSession";
import { HiCliSidebar } from "../components/hicli/HiCliSidebar";
import { HiCliTopBar } from "../components/hicli/HiCliTopBar";
import { HiCliWelcome } from "../components/hicli/HiCliWelcome";
import { AcpLogPanel } from "../components/hicli/AcpLogPanel";
import { AgentInfoCard } from "../components/hicli/AgentInfoCard";
import { ChatStream } from "../components/quest/ChatStream";
import { QuestInput } from "../components/quest/QuestInput";
import { PermissionDialog } from "../components/quest/PermissionDialog";
import { PlanDisplay } from "../components/quest/PlanDisplay";
import type { ChatItemPlan } from "../types/acp";

type DebugTab = "none" | "acplog" | "info";

const DEBUG_TAB_TITLES: Record<Exclude<DebugTab, "none">, string> = {
  acplog: "ACP 日志",
  info: "Agent 信息",
};

function HiCliContent() {
  const state = useHiCliState();
  const dispatch = useHiCliDispatch();
  const activeQuest = useActiveQuest();
  const session = useHiCliSession();

  const [debugTab, setDebugTab] = useState<DebugTab>("none");
  const [logFilter, setLogFilter] = useState("");

  const isConnected = session.status === "connected";
  const hasActiveQuest = !!activeQuest;

  // 连接成功且初始化完成后，自动创建第一个 Quest（获取 models/modes）
  const autoCreatedRef = useRef(false);
  useEffect(() => {
    if (
      isConnected &&
      state.initialized &&
      Object.keys(state.quests).length === 0 &&
      !autoCreatedRef.current
    ) {
      autoCreatedRef.current = true;
      session.createQuest(state.cwd || ".").catch((err) => {
        console.error("Auto create quest failed:", err);
      });
    }
    // 断开连接时重置标记
    if (!isConnected) {
      autoCreatedRef.current = false;
    }
  }, [isConnected, state.initialized, state.quests, state.cwd, session]);

  // 调试面板切换：再次点击已激活标签则关闭
  const handleToggleDebugTab = useCallback((tab: DebugTab) => {
    setDebugTab(tab);
  }, []);

  // CLI 工具选择 → 连接
  const handleSelectCli = useCallback(
    (cliId: string, cwd: string) => {
      session.connectToCli(cliId, cwd);
    },
    [session]
  );

  // 新建 Quest
  const handleCreateQuest = useCallback(() => {
    session.createQuest(state.cwd || ".").catch((err) => {
      console.error("Failed to create quest:", err);
    });
  }, [session, state.cwd]);

  // 切换工具：断开连接并返回 CLI 选择界面
  const handleSwitchTool = useCallback(() => {
    session.disconnect();
    dispatch({ type: "RESET_STATE" });
  }, [session, dispatch]);

  // 移除队列中的 prompt
  const handleDropQueuedPrompt = useCallback(
    (promptId: string) => {
      if (!state.activeQuestId) return;
      dispatch({
        type: "PROMPT_DEQUEUED",
        questId: state.activeQuestId,
        promptId,
      });
    },
    [dispatch, state.activeQuestId]
  );

  // 执行计划条目
  const planEntries = activeQuest?.messages.find(
    (m): m is ChatItemPlan => m.type === "plan"
  )?.entries;

  const showDebugPanel = debugTab !== "none";

  return (
    <div className="flex flex-1 min-h-0 overflow-hidden">
      {/* ===== 左侧边栏：Quest 列表 ===== */}
      <HiCliSidebar
        quests={state.quests}
        activeQuestId={state.activeQuestId}
        onCreateQuest={handleCreateQuest}
        onSwitchQuest={session.switchQuest}
        onCloseQuest={session.closeQuest}
        onSwitchTool={handleSwitchTool}
        status={session.status}
      />

      {/* ===== 主内容区 ===== */}
      <div className="flex-1 flex flex-col min-w-0 min-h-0 overflow-hidden">
        {/* 顶部工具栏 */}
        <HiCliTopBar
          status={session.status}
          onSetModel={session.setModel}
          onSetMode={session.setMode}
          currentProvider={state.selectedCliId ?? ""}
          onProviderChange={() => {}}
          debugTab={debugTab}
          onToggleDebugTab={handleToggleDebugTab}
        />

        {/* 内容区：聊天 + 调试面板 */}
        <div className="flex-1 flex min-h-0 overflow-hidden">
          {/* 左侧聊天区 */}
          <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
            {!isConnected || !state.initialized ? (
              /* 未连接：展示欢迎页 + CLI 选择器 */
              <HiCliWelcome
                onSelectCli={handleSelectCli}
                onCreateQuest={handleCreateQuest}
                isConnected={false}
                disabled={false}
              />
            ) : !hasActiveQuest ? (
              /* 已连接但无活跃 Quest：展示新建 Quest 引导 */
              <HiCliWelcome
                onSelectCli={handleSelectCli}
                onCreateQuest={handleCreateQuest}
                isConnected={true}
                disabled={false}
              />
            ) : (
              /* 已连接且有活跃 Quest：展示聊天流 + 输入框 */
              <>
                <ChatStream
                  onSelectToolCall={(toolCallId) =>
                    dispatch({ type: "SELECT_TOOL_CALL", toolCallId })
                  }
                />

                {planEntries && planEntries.length > 0 && (
                  <div className="max-w-full px-3 pt-1 flex-shrink-0">
                    <PlanDisplay entries={planEntries} />
                  </div>
                )}

                <div className="flex-shrink-0">
                  <QuestInput
                    onSend={session.sendPrompt}
                    onDropQueuedPrompt={handleDropQueuedPrompt}
                    onCancel={session.cancelPrompt}
                    isProcessing={activeQuest.isProcessing}
                    queueSize={activeQuest.promptQueue.length}
                    queuedPrompts={activeQuest.promptQueue}
                    disabled={!state.initialized || !activeQuest}
                  />
                </div>
              </>
            )}
          </div>

          {/* 右侧调试面板 */}
          {showDebugPanel && (
            <div className="w-[400px] flex-shrink-0 flex flex-col border-l border-gray-200/60 bg-white/50">
              {/* 调试面板标题栏 */}
              <div className="flex items-center justify-between px-3 py-2 border-b border-gray-200/60 bg-white/30">
                <span className="text-xs font-semibold text-gray-600">
                  {DEBUG_TAB_TITLES[debugTab as Exclude<DebugTab, "none">]}
                </span>
                <button
                  className="w-6 h-6 flex items-center justify-center rounded text-gray-400
                             hover:text-gray-600 hover:bg-gray-100 transition-colors"
                  onClick={() => setDebugTab("none")}
                  title="关闭面板"
                >
                  <X size={14} />
                </button>
              </div>

              {/* 调试面板内容 */}
              <div className="flex-1 min-h-0 overflow-hidden">
                {debugTab === "acplog" && (
                  <AcpLogPanel
                    filter={logFilter}
                    onFilterChange={setLogFilter}
                  />
                )}
                {debugTab === "info" && <AgentInfoCard />}
              </div>
            </div>
          )}
        </div>
      </div>

      {/* 权限对话框 */}
      {state.pendingPermission && (
        <PermissionDialog
          permission={state.pendingPermission}
          onRespond={session.respondPermission}
        />
      )}
    </div>
  );
}

function HiCli() {
  return (
    <div className="h-screen flex flex-col overflow-hidden bg-gray-50/30">
      <div className="relative z-10 flex-shrink-0">
        <Header />
      </div>
      <div className="flex-1 min-h-0 relative z-10 flex flex-col">
        <HiCliSessionProvider>
          <HiCliContent />
        </HiCliSessionProvider>
      </div>
    </div>
  );
}

export default HiCli;
