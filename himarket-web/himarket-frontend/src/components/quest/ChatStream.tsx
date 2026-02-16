import { useRef, useEffect, useMemo, useState, useCallback } from "react";
import { MessageCircle, ArrowDown } from "lucide-react";
import { useActiveQuest } from "../../context/QuestSessionContext";
import { groupMessages } from "../../lib/utils/groupMessages";
import { UserMessage } from "./UserMessage";
import { AgentMessage } from "./AgentMessage";
import { ThoughtBlock } from "./ThoughtBlock";
import { ToolCallCard } from "./ToolCallCard";
import { WorkUnitCard } from "./WorkUnitCard";
import { PlanDisplay } from "./PlanDisplay";
import type { ChatItemUser, ChatItemPlan, ChatItemError } from "../../types/acp";
import { ErrorMessage } from "./ErrorMessage";

interface ChatStreamProps {
  onSelectToolCall: (toolCallId: string) => void;
  onOpenFile?: (path: string) => void;
}

const SCROLL_THRESHOLD = 24;

export function ChatStream({ onSelectToolCall, onOpenFile }: ChatStreamProps) {
  const quest = useActiveQuest();
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
  const selectedToolCallId = quest?.selectedToolCallId ?? null;
  const isProcessing = quest?.isProcessing ?? false;
  const lastCompletedAt = quest?.lastCompletedAt ?? null;
  const lastStopReason = quest?.lastStopReason ?? null;
  const renderItems = useMemo(() => groupMessages(messages), [messages]);

  // Track scroll position to determine if user is near bottom.
  // Only react to user-initiated scrolls (guarded by userInteractingRef)
  // so that programmatic scrollIntoView calls cannot re-enable auto-follow.
  const handleScroll = useCallback(() => {
    const container = scrollContainerRef.current;
    if (!container) return;
    const { scrollTop, clientHeight, scrollHeight } = container;
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
    container.addEventListener("scroll", handleScroll, { passive: true });
    return () => container.removeEventListener("scroll", handleScroll);
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

    container.addEventListener("pointerdown", onPointerDown);
    window.addEventListener("pointerup", onPointerUp);
    container.addEventListener("wheel", onWheel, { passive: true });

    return () => {
      container.removeEventListener("pointerdown", onPointerDown);
      window.removeEventListener("pointerup", onPointerUp);
      container.removeEventListener("wheel", onWheel);
      clearTimeout(wheelTimerRef.current);
    };
  }, []);

  // Auto-scroll only when user is near bottom
  useEffect(() => {
    if (isUserNearBottom.current) {
      bottomRef.current?.scrollIntoView({
        behavior: isProcessing ? "auto" : "smooth",
      });
    }
  }, [messages, isProcessing]);

  // Reset to bottom when switching quests
  const questId = quest?.id;
  useEffect(() => {
    isUserNearBottom.current = true;
    setShowScrollButton(false);
    bottomRef.current?.scrollIntoView({ behavior: "auto" });
    const container = scrollContainerRef.current;
    if (container) {
      lastScrollTopRef.current = container.scrollTop;
    }
  }, [questId]);

  const scrollToBottom = useCallback(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
    isUserNearBottom.current = true;
    setShowScrollButton(false);
    setCompletionToast(null);
  }, []);

  useEffect(() => {
    if (!lastCompletedAt) return;

    if (!isUserNearBottom.current) {
      const text =
        lastStopReason === "cancelled"
          ? "任务已停止"
          : lastStopReason === "error"
            ? "任务执行失败"
            : "任务已完成";
      setCompletionToast(text);
      if (toastTimerRef.current) {
        clearTimeout(toastTimerRef.current);
      }
      toastTimerRef.current = setTimeout(() => {
        setCompletionToast(null);
      }, 5000);
    }

    if (typeof window !== "undefined" && typeof Notification !== "undefined") {
      if (document.hidden) {
        if (Notification.permission === "granted") {
          void new Notification("HiWork", {
            body:
              lastStopReason === "error"
                ? "任务执行失败，请返回查看详情。"
                : "任务已完成。",
          });
        } else if (Notification.permission === "default") {
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

  if (messages.length === 0) {
    return (
      <div className="flex-1 flex items-center justify-center">
        <div className="text-center text-gray-400">
          <MessageCircle size={32} className="mx-auto mb-2 opacity-40" />
          <span className="text-sm">在下方输入消息开始对话</span>
        </div>
      </div>
    );
  }

  // Check if original last message is an agent message (for streaming indicator)
  const lastMsg = messages[messages.length - 1];
  const lastRenderItem = renderItems[renderItems.length - 1];

  return (
    <div className="flex-1 overflow-y-auto relative" ref={scrollContainerRef}>
      <div className="max-w-3xl mx-auto px-4 py-4 space-y-3">
        {renderItems.map(ri => {
          if (ri.type === "work_unit") {
            const isLast = ri === lastRenderItem;
            return (
              <WorkUnitCard
                key={ri.id}
                items={ri.items}
                meta={ri.meta}
                selectedToolCallId={selectedToolCallId}
                onSelectToolCall={onSelectToolCall}
                onOpenFile={onOpenFile}
                isLastUnit={isLast}
                isProcessing={isProcessing}
              />
            );
          }
          const item = ri.item;
          switch (item.type) {
            case "user":
              return (
                <UserMessage
                  key={item.id}
                  text={item.text}
                  attachments={(item as ChatItemUser).attachments}
                />
              );
            case "agent": {
              const isLast = item === lastMsg;
              return (
                <AgentMessage
                  key={item.id}
                  text={item.text}
                  streaming={isLast && isProcessing && !item.complete}
                  onOpenFile={onOpenFile}
                />
              );
            }
            case "thought": {
              const isLastThought = item === lastMsg;
              return (
                <ThoughtBlock
                  key={item.id}
                  text={item.text}
                  streaming={isLastThought && isProcessing}
                />
              );
            }
            case "tool_call":
              return (
                <ToolCallCard
                  key={item.id}
                  item={item}
                  selected={selectedToolCallId === item.toolCallId}
                  onClick={() => onSelectToolCall(item.toolCallId)}
                />
              );
            case "plan":
              return (
                <PlanDisplay
                  key={item.id}
                  entries={(item as ChatItemPlan).entries}
                  variant="inline"
                />
              );
            case "error": {
              const err = item as ChatItemError;
              return (
                <ErrorMessage
                  key={err.id}
                  code={err.code}
                  message={err.message}
                  data={err.data}
                />
              );
            }
            default:
              return null;
          }
        })}
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
          className="absolute bottom-4 right-4 bg-gray-800/80 text-white rounded-full p-2 shadow-lg
                     hover:bg-gray-800 transition-all duration-200 backdrop-blur-sm"
          onClick={scrollToBottom}
          aria-label="滚动到底部"
        >
          <ArrowDown size={16} />
        </button>
      )}
    </div>
  );
}
