import { useState, useEffect, useRef, useMemo } from "react";
import {
  ChevronLeft,
  ChevronRight,
  GitCompareArrows,
  Globe,
  FileText,
  FileCode,
  Image as ImageIcon,
  FileBox,
  FileSpreadsheet,
} from "lucide-react";
import {
  useActiveQuest,
  useQuestDispatch,
} from "../../context/QuestSessionContext";
import { ChangesView } from "./ChangesView";
import { ArtifactPreview } from "./ArtifactPreview";
import { TerminalView } from "./TerminalView";
import type { Artifact, ArtifactType } from "../../types/artifact";
import type { ChatItemToolCall } from "../../types/acp";

// ===== Tab types =====

type TabId = "changes" | `artifact-${string}` | `terminal-${string}`;

interface TabDef {
  id: TabId;
  label: string;
  icon: typeof FileText;
  closable: boolean;
}

const artifactTypeIcons: Record<ArtifactType, typeof FileText> = {
  html: Globe,
  markdown: FileText,
  svg: FileCode,
  image: ImageIcon,
  pdf: FileSpreadsheet,
  file: FileBox,
};

const TerminalIcon = FileCode;

// ===== Component =====

interface RightPanelProps {
  collapsed: boolean;
  onToggleCollapse: () => void;
}

export function RightPanel({ collapsed, onToggleCollapse }: RightPanelProps) {
  const quest = useActiveQuest();
  const dispatch = useQuestDispatch();
  const questArtifacts = quest?.artifacts;
  const questMessages = quest?.messages;
  const artifacts = useMemo(() => questArtifacts ?? [], [questArtifacts]);

  const hasDiffs = useMemo(() => {
    if (!questMessages) return false;
    return questMessages.some(
      m =>
        m.type === "tool_call" &&
        (m as ChatItemToolCall).content?.some(c => c.type === "diff")
    );
  }, [questMessages]);

  const terminals = useMemo(() => {
    if (!questMessages) return [] as Array<{ terminalId: string; toolCall: ChatItemToolCall }>;
    const byId = new Map<string, ChatItemToolCall>();
    for (const m of questMessages) {
      if (m.type !== "tool_call") continue;
      const tc = m as ChatItemToolCall;
      for (const c of tc.content ?? []) {
        if (c.type === "terminal" && c.terminalId) {
          byId.set(c.terminalId, tc);
        }
      }
    }
    return Array.from(byId.entries()).map(([terminalId, toolCall]) => ({
      terminalId,
      toolCall,
    }));
  }, [questMessages]);

  // Build tab list
  const tabs = useMemo<TabDef[]>(() => {
    const result: TabDef[] = [];
    if (hasDiffs) {
      result.push({
        id: "changes",
        label: "Changes",
        icon: GitCompareArrows,
        closable: false,
      });
    }
    for (const a of artifacts) {
      result.push({
        id: `artifact-${a.id}`,
        label: a.fileName,
        icon: artifactTypeIcons[a.type] ?? FileText,
        closable: false,
      });
    }
    for (const terminal of terminals) {
      result.push({
        id: `terminal-${terminal.terminalId}`,
        label: `Terminal ${terminal.terminalId}`,
        icon: TerminalIcon,
        closable: false,
      });
    }
    return result;
  }, [hasDiffs, artifacts, terminals]);

  // Active tab state
  const [activeTabId, setActiveTabId] = useState<TabId>(
    tabs[0]?.id ?? "changes"
  );

  // Auto-switch when a new artifact appears
  const prevArtifactIdRef = useRef(quest?.activeArtifactId);
  useEffect(() => {
    if (
      quest?.activeArtifactId &&
      quest.activeArtifactId !== prevArtifactIdRef.current
    ) {
      setActiveTabId(`artifact-${quest.activeArtifactId}`);
    }
    prevArtifactIdRef.current = quest?.activeArtifactId ?? null;
  }, [quest?.activeArtifactId]);

  // Auto-switch to changes when first diff appears and no artifacts yet
  const prevHasDiffsRef = useRef(hasDiffs);
  useEffect(() => {
    if (hasDiffs && !prevHasDiffsRef.current && artifacts.length === 0) {
      setActiveTabId("changes");
    }
    prevHasDiffsRef.current = hasDiffs;
  }, [hasDiffs, artifacts.length]);

  // Keep activeTabId valid
  useEffect(() => {
    if (tabs.length > 0 && !tabs.some(t => t.id === activeTabId)) {
      setActiveTabId(tabs[tabs.length - 1].id);
    }
  }, [tabs, activeTabId]);

  if (collapsed) {
    return (
      <div className="flex-shrink-0 border-l border-gray-200/60 bg-white/30 backdrop-blur-sm flex flex-col items-center py-2">
        <button
          className="w-8 h-8 flex items-center justify-center rounded
                     text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors"
          onClick={onToggleCollapse}
          title="Expand panel"
        >
          <ChevronLeft size={16} />
        </button>
      </div>
    );
  }

  // Resolve what to render
  const activeArtifact: Artifact | null = activeTabId.startsWith("artifact-")
    ? (artifacts.find(a => `artifact-${a.id}` === activeTabId) ?? null)
    : null;
  const activeTerminal = activeTabId.startsWith("terminal-")
    ? terminals.find(t => `terminal-${t.terminalId}` === activeTabId) ?? null
    : null;

  return (
    <div className="w-[520px] flex-shrink-0 border-l border-gray-200/60 bg-white/30 backdrop-blur-sm flex flex-col">
      {/* Tab bar */}
      <div className="flex items-center border-b border-gray-200/60 bg-gray-50/50">
        <div className="flex-1 flex overflow-x-auto scrollbar-none">
          {tabs.map(tab => {
            const Icon = tab.icon;
            const isActive = activeTabId === tab.id;
            return (
              <button
                key={tab.id}
                className={`
                  group flex items-center gap-1.5 px-3 py-2 text-xs font-medium
                  whitespace-nowrap transition-colors flex-shrink-0
                  border-b-2 -mb-[1px]
                  ${
                    isActive
                      ? "border-blue-500 text-blue-600 bg-white/80"
                      : "border-transparent text-gray-500 hover:text-gray-700 hover:bg-white/50"
                  }
                `}
                onClick={() => {
                  setActiveTabId(tab.id);
                  // Sync artifact selection
                  if (tab.id.startsWith("artifact-")) {
                    const aId = tab.id.replace("artifact-", "");
                    dispatch({ type: "SELECT_ARTIFACT", artifactId: aId });
                  }
                }}
              >
                <Icon size={13} className="flex-shrink-0" />
                <span className="truncate max-w-[120px]">{tab.label}</span>
              </button>
            );
          })}
        </div>
        <button
          className="w-7 h-7 mx-1 flex-shrink-0 flex items-center justify-center rounded
                     text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors"
          onClick={onToggleCollapse}
          title="Collapse panel"
        >
          <ChevronRight size={16} />
        </button>
      </div>

      {/* Content */}
      <div className="flex-1 min-h-0 overflow-y-auto">
        {activeTabId === "changes" ? (
          <ChangesView />
        ) : activeTerminal ? (
          <TerminalView
            terminalId={activeTerminal.terminalId}
            toolCall={activeTerminal.toolCall}
          />
        ) : activeArtifact ? (
          <ArtifactPreview artifact={activeArtifact} />
        ) : (
          <div className="flex items-center justify-center h-full text-gray-400 text-sm">
            Select a tab to view content
          </div>
        )}
      </div>
    </div>
  );
}
