import { MessageCircle, ArrowDown } from 'lucide-react';
import { useRef, useEffect, useMemo, useState, useCallback } from 'react';

import { groupMessages } from '@/lib/utils/groupMessages.ts';

import { ActivityGroupCard } from './ActivityGroupCard';
import { AgentMessage } from './AgentMessage';
import { ErrorMessage } from './ErrorMessage';
import { InlineArtifact } from './InlineArtifact';
import { PlanDisplay } from './PlanDisplay';
import { SandboxStatusCard } from './SandboxStatusCard';
import { TerminalOutput } from './TerminalOutput';
import { ThoughtBlock } from './ThoughtBlock';
import { ToolCallCard } from './ToolCallCard';
import { UserMessage } from './UserMessage';
import { useActiveCodingSession } from '../../context/CodingSessionContext';

import type {
  ChatItem,
  ChatItemUser,
  ChatItemToolCall,
  ChatItemPlan,
  ChatItemError,
} from '../../types/coding-protocol';

interface ChatStreamProps {
  onSelectToolCall: (toolCallId: string) => void;
  onOpenFile?: (path: string) => void;
  onPreviewArtifact?: () => void;
  onSandboxRetry?: () => void;
}

const SCROLL_THRESHOLD = 24;

export function ChatStream({
  onOpenFile,
  onPreviewArtifact,
  onSandboxRetry,
  onSelectToolCall,
}: ChatStreamProps) {
  const quest = useActiveCodingSession();
  const bottomRef = useRef<HTMLDivElement>(null);
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const lastScrollTopRef = useRef(0);
  const isUserNearBottom = useRef(true);
  const userInteractingRef = useRef(false);
  const wheelTimerRef = useRef<ReturnType<typeof setTimeout>>();
  const [showScrollButton, setShowScrollButton] = useState(false);
  const [completionToast, setCompletionToast] = useState<string | null>(null);
  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const messages = useMemo(() => quest?.messages ?? [], [quest?.messages]);
  const artifacts = useMemo(() => quest?.artifacts ?? [], [quest?.artifacts]);
  const selectedToolCallId = quest?.selectedToolCallId ?? null;
  const isProcessing = quest?.isProcessing ?? false;
  const lastCompletedAt = quest?.lastCompletedAt ?? null;
  const lastStopReason = quest?.lastStopReason ?? null;
  const renderItems = useMemo(
    () => groupMessages(messages, isProcessing),
    [messages, isProcessing],
  );

  // Extract inline blocks (artifacts, diffs, terminals) from a set of ChatItems
  const getInlineBlocks = useCallback(
    (items: ChatItem[]) => {
      const blocks: React.ReactNode[] = [];
      const seenArtifacts = new Set<string>();

      for (const item of items) {
        if (item.type !== 'tool_call') continue;
        const tc = item as ChatItemToolCall;

        // Artifact: match by toolCallId (compact card, preview in right panel)
        for (const artifact of artifacts) {
          if (artifact.toolCallId === tc.toolCallId && !seenArtifacts.has(artifact.id)) {
            seenArtifacts.add(artifact.id);
            blocks.push(
              <InlineArtifact
                key={`artifact-${artifact.id}`}
                onPreviewClick={onPreviewArtifact}
                title={artifact.fileName}
                type="artifact"
              />,
            );
          }
        }

        // Terminal: from tool_call content
        const terminals = (tc.content ?? []).filter((c) => c.type === 'terminal');
        for (const term of terminals) {
          if (term.type !== 'terminal') continue;
          const terminalId = term.terminalId;
          // Extract text outputs from the same tool_call's content
          const outputs = (tc.content ?? [])
            .filter(
              (c) =>
                c.type === 'content' &&
                c.content?.type === 'text' &&
                typeof c.content.text === 'string' &&
                c.content.text.length > 0,
            )
            .map((c) => (c.type === 'content' && c.content?.type === 'text' ? c.content.text : ''))
            .filter(Boolean);

          blocks.push(
            <InlineArtifact
              defaultExpanded={false}
              key={`terminal-${tc.toolCallId}-${terminalId}`}
              title={tc.title || `Terminal ${terminalId}`}
              type="terminal"
            >
              {outputs.length > 0 ? (
                outputs.map((text, idx) => <TerminalOutput key={idx} text={text} />)
              ) : (
                <div className="text-xs text-gray-400">终端输出暂不可用</div>
              )}
            </InlineArtifact>,
          );
        }
      }

      return blocks;
    },
    [artifacts, onPreviewArtifact],
  );

  // Track scroll position to determine if user is near bottom.
  // Only react to user-initiated scrolls (guarded by userInteractingRef)
  // so that programmatic scrollIntoView calls cannot re-enable auto-follow.
  const handleScroll = useCallback(() => {
    const container = scrollContainerRef.current;
    if (!container) return;
    const { clientHeight, scrollHeight, scrollTop } = container;
    const distanceToBottom = scrollHeight - (scrollTop + clientHeight);
    const atBottom = distanceToBottom <= SCROLL_THRESHOLD;
    const isScrollingUp = scrollTop < lastScrollTopRef.current - 2;
    lastScrollTopRef.current = scrollTop;

    // Only process scroll events that originate from real user interaction
    // (wheel, pointer, touch). Programmatic scrollIntoView never fires these.
    if (!userInteractingRef.current) return;

    if (isScrollingUp && distanceToBottom > 8) {
      isUserNearBottom.current = false;
      setShowScrollButton(true);
      return;
    }

    isUserNearBottom.current = atBottom;
    setShowScrollButton(!atBottom);
  }, []);

  // Attach scroll listener
  useEffect(() => {
    const container = scrollContainerRef.current;
    if (!container) return;
    lastScrollTopRef.current = container.scrollTop;
    container.addEventListener('scroll', handleScroll, { passive: true });
    return () => container.removeEventListener('scroll', handleScroll);
  }, [handleScroll]);

  // Detect real user interaction with the scroll container.
  // pointer/touch events only fire from user actions, never from programmatic
  // scrollIntoView, so this reliably distinguishes user vs auto scrolls.
  useEffect(() => {
    const container = scrollContainerRef.current;
    if (!container) return;

    const onPointerDown = () => {
      userInteractingRef.current = true;
    };
    const onPointerUp = () => {
      userInteractingRef.current = false;
    };
    const onWheel = () => {
      userInteractingRef.current = true;
      clearTimeout(wheelTimerRef.current);
      wheelTimerRef.current = setTimeout(() => {
        userInteractingRef.current = false;
      }, 150);
    };

    container.addEventListener('pointerdown', onPointerDown);
    window.addEventListener('pointerup', onPointerUp);
    container.addEventListener('wheel', onWheel, { passive: true });

    return () => {
      container.removeEventListener('pointerdown', onPointerDown);
      window.removeEventListener('pointerup', onPointerUp);
      container.removeEventListener('wheel', onWheel);
      clearTimeout(wheelTimerRef.current);
    };
  }, []);

  // Auto-scroll only when user is near bottom
  useEffect(() => {
    if (isUserNearBottom.current) {
      bottomRef.current?.scrollIntoView({
        behavior: isProcessing ? 'auto' : 'smooth',
      });
    }
  }, [messages, isProcessing]);

  // Reset to bottom when switching quests
  const questId = quest?.id;
  useEffect(() => {
    isUserNearBottom.current = true;
    setShowScrollButton(false);
    bottomRef.current?.scrollIntoView({ behavior: 'auto' });
    const container = scrollContainerRef.current;
    if (container) {
      lastScrollTopRef.current = container.scrollTop;
    }
  }, [questId]);

  const scrollToBottom = useCallback(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    isUserNearBottom.current = true;
    setShowScrollButton(false);
    setCompletionToast(null);
  }, []);

  useEffect(() => {
    if (!lastCompletedAt) return;

    if (!isUserNearBottom.current) {
      const text =
        lastStopReason === 'cancelled'
          ? '任务已停止'
          : lastStopReason === 'error'
            ? '任务执行失败'
            : '任务已完成';
      setCompletionToast(text);
      if (toastTimerRef.current) {
        clearTimeout(toastTimerRef.current);
      }
      toastTimerRef.current = setTimeout(() => {
        setCompletionToast(null);
      }, 5000);
    }

    if (typeof window !== 'undefined' && typeof Notification !== 'undefined') {
      if (document.hidden) {
        if (Notification.permission === 'granted') {
          void new Notification('HiWork', {
            body: lastStopReason === 'error' ? '任务执行失败，请返回查看详情。' : '任务已完成。',
          });
        } else if (Notification.permission === 'default') {
          void Notification.requestPermission();
        }
      }
    }
  }, [lastCompletedAt, lastStopReason]);

  useEffect(() => {
    return () => {
      if (toastTimerRef.current) {
        clearTimeout(toastTimerRef.current);
      }
      clearTimeout(wheelTimerRef.current);
    };
  }, []);

  const isLoading = quest?.isLoading ?? false;

  // Check if original last message is an agent message (for streaming indicator)
  const lastMsg = messages.length > 0 ? messages[messages.length - 1] : null;

  return (
    <div className="flex-1 overflow-y-auto relative" ref={scrollContainerRef}>
      <div className="max-w-3xl mx-auto px-4 py-4 space-y-3">
        {/* 沙箱状态卡片 - 始终在对话流顶部 */}
        <SandboxStatusCard onRetry={onSandboxRetry} />

        {messages.length === 0 ? (
          /* 空态占位 */
          <div className="flex items-center justify-center py-16">
            <div className="text-center text-gray-400">
              {isLoading ? (
                <>
                  <div className="mx-auto mb-3 w-6 h-6 border-2 border-blue-400 border-t-transparent rounded-full animate-spin" />
                  <span className="text-sm">正在恢复会话历史...</span>
                </>
              ) : (
                <>
                  <MessageCircle className="mx-auto mb-2 opacity-40" size={32} />
                  <span className="text-sm">在下方输入消息开始对话</span>
                </>
              )}
            </div>
          </div>
        ) : (
          renderItems.map((ri) => {
            if (ri.type === 'activity_group') {
              const { group } = ri;

              // Special: thinking-only groups with ≤2 blocks → render as singles
              if (group.isThinkingOnly && group.blocks.length <= 2) {
                return (
                  <div key={group.id}>
                    {group.blocks.map((blk) => {
                      if (blk.type === 'thought') {
                        return (
                          <ThoughtBlock
                            key={blk.id}
                            streaming={blk === lastMsg && isProcessing}
                            text={blk.text}
                          />
                        );
                      }
                      if (blk.type === 'agent') {
                        return (
                          <AgentMessage
                            key={blk.id}
                            onOpenFile={onOpenFile}
                            streaming={blk === lastMsg && isProcessing && !blk.complete}
                            text={blk.text}
                          />
                        );
                      }
                      return null;
                    })}
                  </div>
                );
              }

              // Special: single-tool group without errors → render as standalone tool
              if (group.blocks.length === 1 && !group.hasErrorTool) {
                const singleItem = group.blocks[0];
                if (singleItem && singleItem.type === 'tool_call') {
                  const tc = singleItem as ChatItemToolCall;
                  const inlineBlocks = getInlineBlocks([singleItem]);
                  return (
                    <div key={group.id}>
                      <ToolCallCard
                        item={tc}
                        onClick={() => onSelectToolCall(tc.toolCallId)}
                        selected={selectedToolCallId === tc.toolCallId}
                      />
                      {inlineBlocks.length > 0 && (
                        <div className="mt-1 space-y-1">{inlineBlocks}</div>
                      )}
                    </div>
                  );
                }
              }

              // Default: full ActivityGroupCard with inline blocks
              const inlineBlocks = getInlineBlocks(group.blocks);
              return (
                <div key={group.id}>
                  <ActivityGroupCard
                    group={group}
                    onOpenFile={onOpenFile}
                    onSelectToolCall={onSelectToolCall}
                    selectedToolCallId={selectedToolCallId}
                  />
                  {inlineBlocks.length > 0 && <div className="mt-1 space-y-1">{inlineBlocks}</div>}
                </div>
              );
            }
            const item = ri.item;
            switch (item.type) {
              case 'user':
                return (
                  <UserMessage
                    attachments={(item as ChatItemUser).attachments}
                    key={item.id}
                    text={item.text}
                  />
                );
              case 'agent': {
                const isLast = item === lastMsg;
                return (
                  <AgentMessage
                    key={item.id}
                    onOpenFile={onOpenFile}
                    streaming={isLast && isProcessing && !item.complete}
                    text={item.text}
                  />
                );
              }
              case 'thought': {
                const isLastThought = item === lastMsg;
                return (
                  <ThoughtBlock
                    key={item.id}
                    streaming={isLastThought && isProcessing}
                    text={item.text}
                  />
                );
              }
              case 'tool_call': {
                const inlineBlocks = getInlineBlocks([item]);
                return (
                  <div key={item.id}>
                    <ToolCallCard
                      item={item}
                      onClick={() => onSelectToolCall(item.toolCallId)}
                      selected={selectedToolCallId === item.toolCallId}
                    />
                    {inlineBlocks.length > 0 && (
                      <div className="mt-1 space-y-1">{inlineBlocks}</div>
                    )}
                  </div>
                );
              }
              case 'plan':
                return (
                  <PlanDisplay
                    entries={(item as ChatItemPlan).entries}
                    key={item.id}
                    variant="inline"
                  />
                );
              case 'error': {
                const err = item as ChatItemError;
                return (
                  <ErrorMessage
                    code={err.code}
                    data={err.data}
                    key={err.id}
                    message={err.message}
                  />
                );
              }
              default:
                return null;
            }
          })
        )}
        <div ref={bottomRef} />
      </div>

      {/* Scroll to bottom button */}
      {completionToast && (
        <button
          className="absolute bottom-16 right-4 rounded-lg border border-blue-200 bg-blue-50/95 px-3 py-1.5
                     text-xs text-blue-700 shadow-sm hover:bg-blue-100 transition-colors"
          onClick={scrollToBottom}
        >
          {completionToast}，点击查看
        </button>
      )}
      {showScrollButton && (
        <button
          aria-label="滚动到底部"
          className="absolute bottom-4 right-4 bg-gray-800/80 text-white rounded-full p-2 shadow-lg
                     hover:bg-gray-800 transition-all duration-200 backdrop-blur-sm"
          onClick={scrollToBottom}
        >
          <ArrowDown size={16} />
        </button>
      )}
    </div>
  );
}
