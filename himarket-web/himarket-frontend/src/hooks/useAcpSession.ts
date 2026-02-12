import { useCallback, useEffect, useRef } from "react";
import { useAcpWebSocket, type WsStatus } from "./useAcpWebSocket";
import {
  useQuestDispatch,
  useQuestState,
} from "../context/QuestSessionContext";
import type {
  AcpRequest,
  AcpResponse,
  AcpNotification,
  SessionNewResult,
  SessionUpdate,
  PermissionRequest,
  Attachment,
  ChatItemToolCall,
  JsonRpcId,
} from "../types/acp";
import { ACP_METHODS } from "../types/acp";
import {
  buildInitialize,
  buildSessionNew,
  buildPrompt,
  buildCancel,
  buildSetModel,
  buildSetMode,
  buildResponse,
  resolveResponse,
  trackRequest,
  extractSessionUpdate,
  extractPermissionRequest,
} from "../lib/utils/acp";
import { normalizeIncomingMessage } from "../lib/utils/acpNormalize";
import {
  ARTIFACT_SCAN_FALLBACK_ENABLED,
  fetchWorkspaceChanges,
} from "../lib/utils/workspaceApi";

interface UseAcpSessionOptions {
  wsUrl: string;
}

interface QueuedPromptItemInput {
  id: string;
  text: string;
  attachments?: Attachment[];
  createdAt: number;
}

export function useAcpSession({ wsUrl }: UseAcpSessionOptions) {
  const dispatch = useQuestDispatch();
  const state = useQuestState();
  const stateRef = useRef(state);
  const initializedRef = useRef(false);
  const sendRawRef = useRef<(data: string) => void>(() => {});
  const scanTriggeredRef = useRef<Set<string>>(new Set());
  const autoPermissionsRef = useRef<Record<string, "allow" | "reject">>({});
  const promptSeqRef = useRef(0);

  useEffect(() => {
    stateRef.current = state;
  }, [state]);

  const startPrompt = useCallback(
    (
      questId: string,
      text: string,
      attachments?: Attachment[],
      promptId?: string
    ): JsonRpcId => {
      const req = buildPrompt(questId, text, attachments);
      dispatch({
        type: "PROMPT_STARTED",
        questId,
        requestId: req.id,
        text,
        attachments,
        promptId,
      });
      sendRawRef.current(JSON.stringify(req));
      trackRequest(req.id)
        .then(result => {
          const r = result as { stopReason?: string };
          dispatch({
            type: "PROMPT_COMPLETED",
            questId,
            requestId: req.id,
            stopReason: r?.stopReason ?? "unknown",
          });
        })
        .catch(() => {
          dispatch({
            type: "PROMPT_COMPLETED",
            questId,
            requestId: req.id,
            stopReason: "error",
          });
        });
      return req.id;
    },
    [dispatch]
  );

  const handleMessage = useCallback(
    (data: string) => {
      let parsed: Record<string, unknown>;
      try {
        parsed = JSON.parse(data);
      } catch {
        return;
      }
      parsed = normalizeIncomingMessage(parsed);

      const hasId = typeof parsed.id === "number" || typeof parsed.id === "string";
      const hasMethod = typeof parsed.method === "string";

      // Response (has id, no method)
      if (hasId && !hasMethod) {
        resolveResponse(parsed as unknown as AcpResponse);
        return;
      }

      // Notification (has method, no id)
      if (hasMethod && !hasId) {
        const notif = parsed as unknown as AcpNotification;
        if (notif.method === ACP_METHODS.SESSION_UPDATE) {
          const update = extractSessionUpdate(notif);
          if (update) {
            const sessionId =
              (notif.params as { sessionId?: string })?.sessionId ?? "";
            dispatch({
              type: "SESSION_UPDATE",
              sessionId,
              update: update as SessionUpdate,
            });
          }
        }
        return;
      }

      // Request from agent (has both id and method)
      if (hasId && hasMethod) {
        const req = parsed as unknown as AcpRequest;
        const send = sendRawRef.current;
        if (req.method === ACP_METHODS.REQUEST_PERMISSION) {
          const perm = extractPermissionRequest(req);
          if (perm) {
            const sessionId =
              (req.params as { sessionId?: string })?.sessionId ?? "";
            const autoDecision = autoPermissionsRef.current[sessionId];
            if (autoDecision) {
              // Find a matching option to auto-respond with
              const targetKind =
                autoDecision === "allow" ? "allow_once" : "reject_once";
              const option =
                perm.options.find(o => o.kind === targetKind) ??
                perm.options.find(o => o.kind.startsWith(autoDecision));
              if (option) {
                send(
                  JSON.stringify(
                    buildResponse(req.id, {
                      outcome: {
                        outcome: "selected",
                        optionId: option.optionId,
                      },
                    })
                  )
                );
                return;
              }
            }
            dispatch({
              type: "PERMISSION_REQUEST",
              id: req.id,
              sessionId,
              request: perm as PermissionRequest,
            });
          }
        } else if (req.method === ACP_METHODS.TERMINAL_CREATE) {
          send(
            JSON.stringify(
              buildResponse(req.id, { terminalId: `term-${req.id}` })
            )
          );
        } else if (req.method === ACP_METHODS.TERMINAL_OUTPUT) {
          send(
            JSON.stringify(
              buildResponse(req.id, {
                output: "",
                truncated: false,
                exitStatus: null,
              })
            )
          );
        }
      }
    },
    [dispatch]
  );

  const {
    status,
    send: sendRaw,
    connect,
    disconnect,
  } = useAcpWebSocket({
    url: wsUrl,
    onMessage: handleMessage,
  });

  useEffect(() => {
    sendRawRef.current = sendRaw;
  }, [sendRaw]);

  useEffect(() => {
    if (!state.initialized) return;
    const quests = Object.values(state.quests);
    for (const quest of quests) {
      if (quest.isProcessing || quest.inflightPromptId !== null) continue;
      const next = quest.promptQueue[0];
      if (!next) continue;
      startPrompt(quest.id, next.text, next.attachments, next.id);
    }
  }, [state.initialized, state.quests, startPrompt]);

  useEffect(() => {
    if (!ARTIFACT_SCAN_FALLBACK_ENABLED) return;

    const quests = Object.values(state.quests);
    for (const quest of quests) {
      for (const item of quest.messages) {
        if (item.type !== "tool_call") continue;
        const tc = item as ChatItemToolCall;
        const SCAN_SKIP_KINDS: ReadonlySet<string> = new Set([
          "read", "search", "think", "fetch", "switch_mode",
        ]);
        if (SCAN_SKIP_KINDS.has(tc.kind)) continue;
        if (tc.status !== "completed" && tc.status !== "failed") continue;

        const scanKey = `${quest.id}:${tc.toolCallId}:${tc.status}`;
        if (scanTriggeredRef.current.has(scanKey)) continue;
        scanTriggeredRef.current.add(scanKey);

        const since = quest.lastArtifactScanAt ?? quest.createdAt;
        const now = Date.now();

        fetchWorkspaceChanges(quest.cwd, since, 200)
          .then(changes => {
            const paths = changes.map(c => c.path);
            if (paths.length > 0) {
              dispatch({
                type: "UPSERT_ARTIFACTS_FROM_PATHS",
                questId: quest.id,
                toolCallId: tc.toolCallId,
                paths,
              });
            }
            dispatch({
              type: "SET_ARTIFACT_SCAN_CURSOR",
              questId: quest.id,
              cursor: Math.max(0, now - 2000),
            });
          })
          .catch(() => {
            scanTriggeredRef.current.delete(scanKey);
          });
      }
    }
  }, [state.quests, dispatch]);

  // Auto-initialize protocol when connected
  useEffect(() => {
    if (status === "connected" && !initializedRef.current) {
      initializedRef.current = true;
      dispatch({ type: "WS_CONNECTED" });

      (async () => {
        try {
          const send = sendRawRef.current;
          const initReq = buildInitialize();
          send(JSON.stringify(initReq));
          await trackRequest(initReq.id);

          dispatch({
            type: "PROTOCOL_INITIALIZED",
            models: [],
            modes: [],
            currentModelId: "",
            currentModeId: "",
          });
        } catch (err) {
          console.error("ACP initialization failed:", err);
        }
      })();
    }

    if (status === "disconnected") {
      initializedRef.current = false;
      dispatch({ type: "WS_DISCONNECTED" });
    }
  }, [status, dispatch]);

  // Default model / mode to apply after quest creation
  const DEFAULT_MODEL_NAME = "Kimi-K2.5";
  const DEFAULT_MODE_NAME = "Bypass Permissions";

  const createQuest = useCallback(
    async (cwd: string): Promise<string> => {
      const send = sendRawRef.current;
      const sessionReq = buildSessionNew(cwd);
      send(JSON.stringify(sessionReq));
      const result = (await trackRequest(sessionReq.id)) as SessionNewResult;

      dispatch({
        type: "QUEST_CREATED",
        sessionId: result.sessionId,
        cwd,
        models: result.models?.availableModels,
        modes: result.modes?.availableModes,
        currentModelId: result.models?.currentModelId,
        currentModeId: result.modes?.currentModeId,
      });

      // Auto-set default model if available
      const targetModel = result.models?.availableModels?.find(
        m => m.name === DEFAULT_MODEL_NAME
      );
      if (
        targetModel &&
        targetModel.modelId !== result.models?.currentModelId
      ) {
        dispatch({ type: "SET_MODEL", modelId: targetModel.modelId });
        const req = buildSetModel(result.sessionId, targetModel.modelId);
        send(JSON.stringify(req));
        trackRequest(req.id).catch(() => {});
      }

      // Auto-set default mode if available
      const targetMode = result.modes?.availableModes?.find(
        m => m.name === DEFAULT_MODE_NAME
      );
      if (targetMode && targetMode.id !== result.modes?.currentModeId) {
        dispatch({ type: "SET_MODE", modeId: targetMode.id });
        const req = buildSetMode(result.sessionId, targetMode.id);
        send(JSON.stringify(req));
        trackRequest(req.id).catch(() => {});
      }

      return result.sessionId;
    },
    [dispatch]
  );

  const switchQuest = useCallback(
    (questId: string) => {
      dispatch({ type: "QUEST_SWITCHED", questId });
    },
    [dispatch]
  );

  const closeQuest = useCallback(
    (questId: string) => {
      dispatch({ type: "QUEST_CLOSED", questId });
    },
    [dispatch]
  );

  const sendPrompt = useCallback(
    (text: string, attachments?: Attachment[]) => {
      const activeId = stateRef.current.activeQuestId;
      if (!activeId) return { queued: false } as const;
      const quest = stateRef.current.quests[activeId];
      if (!quest) return { queued: false } as const;

      if (quest.isProcessing || quest.inflightPromptId !== null) {
        const item: QueuedPromptItemInput = {
          id: `qp-${Date.now()}-${++promptSeqRef.current}`,
          text,
          ...(attachments && attachments.length > 0
            ? { attachments }
            : {}),
          createdAt: Date.now(),
        };
        dispatch({ type: "PROMPT_ENQUEUED", questId: activeId, item });
        return { queued: true, queuedPromptId: item.id } as const;
      }

      const requestId = startPrompt(activeId, text, attachments);
      return { queued: false, requestId } as const;
    },
    [dispatch, startPrompt]
  );

  const dropQueuedPrompt = useCallback(
    (promptId: string) => {
      const activeId = stateRef.current.activeQuestId;
      if (!activeId) return;
      dispatch({ type: "PROMPT_DEQUEUED", questId: activeId, promptId });
    },
    [dispatch]
  );

  const cancelPrompt = useCallback(() => {
    if (!state.activeQuestId) return;
    const notif = buildCancel(state.activeQuestId);
    sendRawRef.current(JSON.stringify(notif));
  }, [state.activeQuestId]);

  const setModel = useCallback(
    (modelId: string) => {
      if (!state.activeQuestId) return;
      dispatch({ type: "SET_MODEL", modelId });
      const req = buildSetModel(state.activeQuestId, modelId);
      sendRawRef.current(JSON.stringify(req));
      trackRequest(req.id).catch(() => {});
    },
    [dispatch, state.activeQuestId]
  );

  const setMode = useCallback(
    (modeId: string) => {
      if (!state.activeQuestId) return;
      dispatch({ type: "SET_MODE", modeId });
      const req = buildSetMode(state.activeQuestId, modeId);
      sendRawRef.current(JSON.stringify(req));
      trackRequest(req.id).catch(() => {});
    },
    [dispatch, state.activeQuestId]
  );

  const respondPermission = useCallback(
    (requestId: JsonRpcId, optionId: string) => {
      // Look up the selected option's kind from the pending permission
      const pending = stateRef.current.pendingPermission;
      const option = pending?.request.options.find(
        o => o.optionId === optionId
      );

      // Store auto-permission if it's an "always" kind
      if (option?.kind === "allow_always" && pending?.sessionId) {
        autoPermissionsRef.current[pending.sessionId] = "allow";
      } else if (option?.kind === "reject_always" && pending?.sessionId) {
        autoPermissionsRef.current[pending.sessionId] = "reject";
      }

      const resp = buildResponse(requestId, {
        outcome: { outcome: "selected", optionId },
      });
      sendRawRef.current(JSON.stringify(resp));
      dispatch({ type: "PERMISSION_RESOLVED" });
    },
    [dispatch]
  );

  return {
    status: status as WsStatus,
    connect,
    disconnect,
    createQuest,
    switchQuest,
    closeQuest,
    sendPrompt,
    dropQueuedPrompt,
    cancelPrompt,
    setModel,
    setMode,
    respondPermission,
  };
}
