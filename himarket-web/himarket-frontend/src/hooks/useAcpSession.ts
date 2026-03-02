import { useCallback, useEffect, useRef, useState } from "react";
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
  clearPendingRequests,
  resetNextId,
} from "../lib/utils/acp";
import { normalizeIncomingMessage } from "../lib/utils/acpNormalize";
import {
  ARTIFACT_SCAN_FALLBACK_ENABLED,
  fetchWorkspaceChanges,
} from "../lib/utils/workspaceApi";

export interface UseAcpSessionOptions {
  wsUrl: string;
  /** If false, WebSocket won't connect until `connect()` or `createQuest()` is called. Default true. */
  autoConnect?: boolean;
  /** JSON string of CliSessionConfig, sent to backend via WebSocket message after connection (not via URL). */
  cliSessionConfig?: string;
}

interface QueuedPromptItemInput {
  id: string;
  text: string;
  attachments?: Attachment[];
  createdAt: number;
}

export function useAcpSession({ wsUrl, autoConnect: autoConnectOpt = true, cliSessionConfig }: UseAcpSessionOptions) {
  const dispatch = useQuestDispatch();
  const state = useQuestState();
  const stateRef = useRef(state);
  const initializedRef = useRef(false);
  const sendRawRef = useRef<(data: string) => void>(() => {});
  const scanTriggeredRef = useRef<Set<string>>(new Set());
  const autoPermissionsRef = useRef<Record<string, "allow" | "reject">>({});
  const promptSeqRef = useRef(0);
  const cliSessionConfigRef = useRef(cliSessionConfig);
  cliSessionConfigRef.current = cliSessionConfig;

  // wsUrl 变化时（CLI provider 切换），重置初始化状态和 pending 请求
  useEffect(() => {
    initializedRef.current = false;
    clearPendingRequests();
    resetNextId();
  }, [wsUrl]);

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
        .catch((err: unknown) => {
          let code = -1;
          let message = "Unknown error";
          let data: Record<string, unknown> | undefined;

          if (
            typeof err === "object" &&
            err !== null &&
            "code" in err &&
            "message" in err &&
            typeof (err as Record<string, unknown>).code === "number" &&
            typeof (err as Record<string, unknown>).message === "string"
          ) {
            code = (err as Record<string, unknown>).code as number;
            message = (err as Record<string, unknown>).message as string;
            const rawData = (err as Record<string, unknown>).data;
            if (
              typeof rawData === "object" &&
              rawData !== null &&
              !Array.isArray(rawData)
            ) {
              data = rawData as Record<string, unknown>;
            }
          }

          dispatch({
            type: "PROMPT_ERROR",
            questId,
            requestId: req.id,
            code,
            message,
            ...(data !== undefined ? { data } : {}),
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
        console.warn("[AcpSession] Failed to parse message:", data.substring(0, 200));
        return;
      }
      parsed = normalizeIncomingMessage(parsed);
      console.log("[AcpSession] Received message:", JSON.stringify(parsed).substring(0, 300));

      // Debug: log all terminal-related messages
      const method = parsed.method as string | undefined;
      if (method && method.startsWith("terminal")) {
        console.log("[ACP Terminal]", method, parsed);
      }

      const hasId =
        typeof parsed.id === "number" || typeof parsed.id === "string";
      const hasMethod = typeof parsed.method === "string";

      // Response (has id, no method)
      if (hasId && !hasMethod) {
        resolveResponse(parsed as unknown as AcpResponse);
        return;
      }

      // Notification (has method, no id)
      if (hasMethod && !hasId) {
        const notif = parsed as unknown as AcpNotification;
        if (notif.method === "sandbox/status") {
          const params = notif.params as { status?: string; message?: string; sandboxHost?: string };
          dispatch({
            type: "SANDBOX_STATUS",
            status: (params?.status ?? "creating") as "creating" | "ready" | "error",
            message: params?.message ?? "",
            sandboxHost: params?.sandboxHost,
          });

          // 沙箱就绪后，如果协议还未初始化，触发 initialize 请求
          // 这样可以避免 initialize 请求在沙箱初始化期间被缓存导致的时序问题
          if (params?.status === "ready" && !initializedRef.current && !stateRef.current.initialized) {
            initializedRef.current = true;
            console.log("[AcpSession] Sandbox ready, starting initialize...");
            (async () => {
              try {
                const send = sendRawRef.current;
                const initReq = buildInitialize();
                console.log("[AcpSession] Sending initialize request:", JSON.stringify(initReq));
                send(JSON.stringify(initReq));

                const timeoutPromise = new Promise<never>((_, reject) =>
                  setTimeout(() => reject(new Error("Initialize timeout")), 120000)
                );
                const result = await Promise.race([trackRequest(initReq.id), timeoutPromise]);
                console.log("[AcpSession] Initialize response received:", result);

                dispatch({
                  type: "PROTOCOL_INITIALIZED",
                  models: [],
                  modes: [],
                  currentModelId: "",
                  currentModeId: "",
                });
                console.log("[AcpSession] PROTOCOL_INITIALIZED dispatched");
              } catch (err) {
                console.error("[AcpSession] ACP initialization failed:", err);
                dispatch({
                  type: "PROTOCOL_INITIALIZED",
                  models: [],
                  modes: [],
                  currentModelId: "",
                  currentModeId: "",
                });
                console.log("[AcpSession] PROTOCOL_INITIALIZED dispatched (fallback after error)");
              }
            })();
          }
          return;
        }
        if (notif.method === "sandbox/init-progress") {
          const params = notif.params as {
            phase?: string;
            status?: "executing" | "completed";
            message?: string;
            progress?: number;
            totalPhases?: number;
            completedPhases?: number;
          };
          dispatch({
            type: "INIT_PROGRESS",
            phase: params?.phase ?? "",
            status: params?.status ?? "executing",
            message: params?.message ?? "",
            progress: params?.progress ?? 0,
            totalPhases: params?.totalPhases ?? 5,
            completedPhases: params?.completedPhases ?? 0,
          });
          return;
        }
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
        } else if (notif.method === ACP_METHODS.TERMINAL_OUTPUT) {
          // terminal/output may arrive as notification (no id) in some agent impls
          const params = notif.params as {
            sessionId?: string;
            terminalId?: string;
            data?: string;
            output?: string;
          };
          const sessionId = params?.sessionId ?? "";
          const terminalId = params?.terminalId ?? "";
          const data = params?.data ?? params?.output ?? "";
          if (sessionId && terminalId && data) {
            dispatch({
              type: "TERMINAL_DATA",
              questId: sessionId,
              terminalId,
              data,
            });

            const portMatch = data.match(
              /(?:https?:\/\/)?(?:localhost|127\.0\.0\.1):(\d{4,5})/
            );
            if (portMatch) {
              const port = parseInt(portMatch[1], 10);
              if (port >= 1024 && port <= 65535) {
                dispatch({
                  type: "PREVIEW_PORT_DETECTED",
                  questId: sessionId,
                  port,
                });
              }
            }
          }
        } else if (notif.method === ACP_METHODS.TERMINAL_CREATE) {
          // terminal/create as notification — auto-register terminal
          const params = notif.params as {
            sessionId?: string;
            terminalId?: string;
          };
          const sessionId = params?.sessionId ?? "";
          const terminalId = params?.terminalId ?? `term-notif-${Date.now()}`;
          if (sessionId) {
            dispatch({
              type: "TERMINAL_CREATED",
              questId: sessionId,
              terminalId,
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
          const params = req.params as {
            sessionId?: string;
            terminalId?: string;
          };
          // Use agent-provided terminalId if present, otherwise generate one
          const terminalId = params?.terminalId || `term-${req.id}`;
          const sessionId = params?.sessionId ?? "";
          send(JSON.stringify(buildResponse(req.id, { terminalId })));
          if (sessionId) {
            dispatch({
              type: "TERMINAL_CREATED",
              questId: sessionId,
              terminalId,
            });
          }
        } else if (req.method === ACP_METHODS.TERMINAL_OUTPUT) {
          const params = req.params as {
            sessionId?: string;
            terminalId?: string;
            data?: string;
            output?: string;
          };
          const sessionId = params?.sessionId ?? "";
          const terminalId = params?.terminalId ?? "";
          const data = params?.data ?? params?.output ?? "";
          if (sessionId && terminalId && data) {
            dispatch({
              type: "TERMINAL_DATA",
              questId: sessionId,
              terminalId,
              data,
            });

            // Detect localhost port from terminal output for preview
            const portMatch = data.match(
              /(?:https?:\/\/)?(?:localhost|127\.0\.0\.1):(\d{4,5})/
            );
            if (portMatch) {
              const port = parseInt(portMatch[1], 10);
              if (port >= 1024 && port <= 65535) {
                dispatch({
                  type: "PREVIEW_PORT_DETECTED",
                  questId: sessionId,
                  port,
                });
              }
            }
          }
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

  // Send cliSessionConfig as the first WebSocket message after connection,
  // instead of passing it via URL query string (which hits Nginx header size limits).
  const handleConnected = useCallback((wsSend: (data: string) => void) => {
    const configJson = cliSessionConfigRef.current;
    if (configJson) {
      const msg = JSON.stringify({
        jsonrpc: "2.0",
        method: "session/config",
        params: JSON.parse(configJson),
      });
      console.log("[AcpSession] Sending session/config via WebSocket message");
      wsSend(msg);
    }
  }, []);

  const {
    status: wsStatus,
    send: sendRaw,
    connect: wsConnect,
    disconnect: wsDisconnect,
  } = useAcpWebSocket({
    url: wsUrl,
    onMessage: handleMessage,
    onConnected: handleConnected,
    autoConnect: autoConnectOpt && !!wsUrl,
  });

  const status = wsStatus;
  const connect = wsConnect;
  const disconnect = wsDisconnect;
  const send = sendRaw;

  useEffect(() => {
    sendRawRef.current = send;
  }, [send]);

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
          "read",
          "search",
          "think",
          "fetch",
          "switch_mode",
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
      console.log("[AcpSession] WS connected");
      dispatch({ type: "WS_CONNECTED" });

      // K8s 运行时：等待沙箱就绪后再发送 initialize 请求
      // 本地运行时：立即发送 initialize 请求
      const sandboxReady = !stateRef.current.sandboxStatus ||
                          stateRef.current.sandboxStatus.status === "ready";

      if (!sandboxReady) {
        // K8s 模式且沙箱未就绪，等待 sandbox/status: ready 通知
        console.log("[AcpSession] Waiting for sandbox ready before initialize");
        return;
      }

      // 沙箱已就绪或本地模式，发送 initialize 请求
      initializedRef.current = true;
      console.log("[AcpSession] Starting initialize...");

      (async () => {
        try {
          const send = sendRawRef.current;
          const initReq = buildInitialize();
          console.log("[AcpSession] Sending initialize request:", JSON.stringify(initReq));
          send(JSON.stringify(initReq));

          // 给 initialize 请求加 2 分钟超时，CLI 启动可能较慢（如 K8s Pod 拉取镜像）
          const timeoutPromise = new Promise<never>((_, reject) =>
            setTimeout(() => reject(new Error("Initialize timeout")), 120000)
          );
          const result = await Promise.race([trackRequest(initReq.id), timeoutPromise]);
          console.log("[AcpSession] Initialize response received:", result);

          dispatch({
            type: "PROTOCOL_INITIALIZED",
            models: [],
            modes: [],
            currentModelId: "",
            currentModeId: "",
          });
          console.log("[AcpSession] PROTOCOL_INITIALIZED dispatched");
        } catch (err) {
          console.error("[AcpSession] ACP initialization failed:", err);
          // 超时或失败时也标记为已初始化，让用户能看到错误而不是永远置灰
          dispatch({
            type: "PROTOCOL_INITIALIZED",
            models: [],
            modes: [],
            currentModelId: "",
            currentModeId: "",
          });
          console.log("[AcpSession] PROTOCOL_INITIALIZED dispatched (fallback after error)");
        }
      })();
    }

    if (status === "disconnected") {
      initializedRef.current = false;
      clearPendingRequests();
      resetNextId();
      dispatch({ type: "WS_DISCONNECTED" });
    }
  }, [status, dispatch]);

  // Default model / mode to apply after quest creation
  const DEFAULT_MODEL_NAME = "Kimi-K2.5";
  const DEFAULT_MODE_NAME = "Bypass Permissions";

  const creatingQuestRef = useRef(false);
  const [creatingQuest, setCreatingQuest] = useState(false);

  // Promise that resolves once ACP protocol is initialized.
  // Used by createQuest to wait for connection + init when autoConnect is off.
  const initPromiseRef = useRef<{ promise: Promise<void>; resolve: () => void } | null>(null);

  const getInitPromise = useCallback(() => {
    if (!initPromiseRef.current) {
      let resolve!: () => void;
      const promise = new Promise<void>(r => { resolve = r; });
      initPromiseRef.current = { promise, resolve };
    }
    return initPromiseRef.current;
  }, []);

  // Resolve the init promise when protocol is initialized
  useEffect(() => {
    if (state.initialized && initPromiseRef.current) {
      initPromiseRef.current.resolve();
      initPromiseRef.current = null;
    }
  }, [state.initialized]);

  const createQuest = useCallback(
    async (cwd: string): Promise<string> => {
      if (creatingQuestRef.current) {
        return Promise.reject(new Error("Quest creation already in progress"));
      }
      creatingQuestRef.current = true;
      setCreatingQuest(true);
      try {
        // If not yet initialized, trigger connection and wait for ACP init
        if (!stateRef.current.initialized) {
          const { promise } = getInitPromise();
          connect();
          await promise;
        }

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
      } finally {
        creatingQuestRef.current = false;
        setCreatingQuest(false);
      }
    },
    [dispatch, connect, getInitPromise]
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
          ...(attachments && attachments.length > 0 ? { attachments } : {}),
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
    creatingQuest,
    runtimeError: null,
    connect,
    disconnect,
    reconnect: connect,
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
