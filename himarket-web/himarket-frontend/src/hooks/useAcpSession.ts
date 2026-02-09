import { useCallback, useEffect, useRef } from "react";
import { useAcpWebSocket, type WsStatus } from "./useAcpWebSocket";
import {
  useCodingDispatch,
  useCodingState,
} from "../context/CodingSessionContext";
import type {
  AcpRequest,
  AcpResponse,
  AcpNotification,
  SessionNewResult,
  SessionUpdate,
  PermissionRequest,
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

interface UseAcpSessionOptions {
  wsUrl: string;
}

export function useAcpSession({ wsUrl }: UseAcpSessionOptions) {
  const dispatch = useCodingDispatch();
  const state = useCodingState();
  const initializedRef = useRef(false);
  const sendRawRef = useRef<(data: string) => void>(() => {});

  const handleMessage = useCallback(
    (data: string) => {
      let parsed: Record<string, unknown>;
      try {
        parsed = JSON.parse(data);
      } catch {
        return;
      }

      const hasId = typeof parsed.id === "number";
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
            dispatch({
              type: "PERMISSION_REQUEST",
              id: req.id,
              sessionId,
              request: perm as PermissionRequest,
            });
          }
        } else if (req.method === ACP_METHODS.READ_TEXT_FILE) {
          send(JSON.stringify(buildResponse(req.id, { content: "" })));
        } else if (req.method === ACP_METHODS.WRITE_TEXT_FILE) {
          send(JSON.stringify(buildResponse(req.id, { success: true })));
        } else if (req.method === ACP_METHODS.TERMINAL_CREATE) {
          send(
            JSON.stringify(
              buildResponse(req.id, { terminalId: `term-${req.id}` })
            )
          );
        } else if (req.method === ACP_METHODS.TERMINAL_OUTPUT) {
          send(JSON.stringify(buildResponse(req.id, {})));
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
    (text: string) => {
      const activeId = state.activeQuestId;
      if (!activeId) return;
      dispatch({ type: "USER_PROMPT_SENT", text });
      const req = buildPrompt(activeId, text);
      sendRawRef.current(JSON.stringify(req));
      trackRequest(req.id)
        .then(result => {
          const r = result as { stopReason?: string };
          dispatch({
            type: "PROMPT_COMPLETED",
            questId: activeId,
            stopReason: r?.stopReason ?? "unknown",
          });
        })
        .catch(() => {
          dispatch({
            type: "PROMPT_COMPLETED",
            questId: activeId,
            stopReason: "error",
          });
        });
    },
    [dispatch, state.activeQuestId]
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
    (requestId: number, optionId: string) => {
      const resp = buildResponse(requestId, { optionId });
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
    cancelPrompt,
    setModel,
    setMode,
    respondPermission,
  };
}
