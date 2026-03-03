import { useCallback, useEffect, useRef, useState } from "react";

import { useAcpWebSocket, type WsStatus } from "./useAcpWebSocket";
import {
  useHiCliDispatch,
  useHiCliState,
} from "../context/HiCliSessionContext";
import type {
  AcpRequest,
  AcpResponse,
  AcpNotification,
  InitializeResult,
  SessionNewResult,
  SessionUpdate,
  PermissionRequest,
  Attachment,
  JsonRpcId,
} from "../types/acp";
import { ACP_METHODS } from "../types/acp";
import type { RawMessage } from "../types/log";
import type { AggregatedLogEntry } from "../types/log";
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
import { LogAggregator } from "../lib/utils/logAggregator";
import { buildAcpWsUrl } from "../lib/utils/wsUrl";

// ===== WebSocket URL 构建 =====

function buildHiCliWsUrl(cliId: string, runtime?: string, cliSessionConfig?: string, sandboxMode?: string): string {
  return buildAcpWsUrl({
    token: localStorage.getItem("access_token") || undefined,
    provider: cliId || undefined,
    runtime,
    sandboxMode,
    cliSessionConfig,
  });
}

// ===== Hook 接口 =====

export interface UseHiCliSessionReturn {
  status: WsStatus;
  creatingQuest: boolean;
  connect: () => void;
  disconnect: () => void;
  createQuest: (cwd: string) => Promise<string>;
  switchQuest: (questId: string) => void;
  closeQuest: (questId: string) => void;
  sendPrompt: (text: string, attachments?: Attachment[]) =>
    | { queued: true; queuedPromptId?: string }
    | { queued: false; requestId?: string | number };
  cancelPrompt: () => void;
  setModel: (modelId: string) => void;
  setMode: (modeId: string) => void;
  respondPermission: (requestId: JsonRpcId, optionId: string) => void;
  connectToCli: (cliId: string, runtime?: string, cliSessionConfig?: string) => void;
}

// ===== Hook 实现 =====

export function useHiCliSession(): UseHiCliSessionReturn {
  const dispatch = useHiCliDispatch();
  const state = useHiCliState();
  const stateRef = useRef(state);
  const initializedRef = useRef(false);
  const sendRawRef = useRef<(data: string) => void>(() => {});
  const rawMsgIdRef = useRef(0);
  const promptSeqRef = useRef(0);

  // 动态 WebSocket URL 管理
  const [currentWsUrl, setCurrentWsUrl] = useState("");
  // cliSessionConfig 不再通过 URL 传递，改为 WebSocket 连接后通过 session/config 消息发送
  const cliSessionConfigRef = useRef<string | undefined>();

  // 日志聚合器
  const aggregatorRef = useRef<LogAggregator>(new LogAggregator());

  // 设置聚合器回调，将聚合日志 dispatch 到 HiCliSessionContext
  useEffect(() => {
    aggregatorRef.current.onEntry = (entry: AggregatedLogEntry) => {
      dispatch({ type: "AGGREGATED_LOG", entry });
    };
  }, [dispatch]);

  // wsUrl 变化时，重置初始化状态和 pending 请求
  useEffect(() => {
    initializedRef.current = false;
    clearPendingRequests();
    resetNextId();
  }, [currentWsUrl]);

  useEffect(() => {
    stateRef.current = state;
  }, [state]);

  // ===== 原始消息日志记录 =====

  const logRawMessage = useCallback(
    (direction: RawMessage["direction"], data: unknown) => {
      const parsed =
        typeof data === "string"
          ? (() => {
              try {
                return JSON.parse(data);
              } catch {
                return data;
              }
            })()
          : data;

      const msg: RawMessage = {
        id: `raw-${++rawMsgIdRef.current}-${Date.now()}`,
        direction,
        timestamp: Date.now(),
        data: parsed,
        method:
          typeof parsed === "object" && parsed !== null
            ? (parsed as Record<string, unknown>).method as string | undefined
            : undefined,
        rpcId:
          typeof parsed === "object" && parsed !== null
            ? ((parsed as Record<string, unknown>).id as
                | JsonRpcId
                | undefined)
            : undefined,
      };
      dispatch({ type: "RAW_MESSAGE", message: msg });

      // 调用日志聚合器处理消息
      aggregatorRef.current.processMessage(direction, parsed);
    },
    [dispatch]
  );

  // 带日志记录的发送函数
  const sendWithLog = useCallback(
    (data: string) => {
      logRawMessage("client_to_agent", data);
      sendRawRef.current(data);
    },
    [logRawMessage]
  );

  // ===== Prompt 发送（内部） =====

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
      sendWithLog(JSON.stringify(req));
      trackRequest(req.id)
        .then((result) => {
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
    [dispatch, sendWithLog]
  );

  // ===== 消息处理 =====

  const handleMessage = useCallback(
    (data: string) => {
      // 记录原始消息（接收方向）
      logRawMessage("agent_to_client", data);

      let parsed: Record<string, unknown>;
      try {
        parsed = JSON.parse(data);
      } catch {
        return;
      }
      parsed = normalizeIncomingMessage(parsed);

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
            (async () => {
              try {
                const initReq = buildInitialize();
                sendWithLog(JSON.stringify(initReq));

                const timeoutPromise = new Promise<never>((_, reject) =>
                  setTimeout(() => reject(new Error("Initialize timeout")), 120000)
                );
                const initResult = (await Promise.race([
                  trackRequest(initReq.id),
                  timeoutPromise,
                ])) as InitializeResult;

                const initModes = initResult.modes?.availableModes ?? [];
                const initCurrentModeId = initResult.modes?.currentModeId ?? "";

                dispatch({
                  type: "PROTOCOL_INITIALIZED",
                  models: [],
                  modes: initModes,
                  currentModelId: "",
                  currentModeId: initCurrentModeId,
                });

                dispatch({
                  type: "DEBUG_PROTOCOL_INITIALIZED",
                  agentInfo: initResult.agentInfo,
                  authMethods: initResult.authMethods,
                  agentCapabilities: initResult.agentCapabilities,
                  modesSource: initModes.length > 0 ? "initialize" : null,
                });
              } catch (err) {
                console.error("ACP initialization failed:", err);
                dispatch({
                  type: "PROTOCOL_INITIALIZED",
                  models: [],
                  modes: [],
                  currentModelId: "",
                  currentModeId: "",
                });
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
        if (notif.method === "workspace/info") {
          const params = notif.params as { cwd?: string };
          if (params?.cwd) {
            dispatch({ type: "WORKSPACE_INFO", cwd: params.cwd });
          }
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
        }
        return;
      }

      // Request from agent (has both id and method)
      if (hasId && hasMethod) {
        const req = parsed as unknown as AcpRequest;
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
        } else if (req.method === ACP_METHODS.TERMINAL_CREATE) {
          const terminalId =
            (req.params as { terminalId?: string })?.terminalId ||
            `term-${req.id}`;
          const sessionId =
            (req.params as { sessionId?: string })?.sessionId ?? "";
          const resp = buildResponse(req.id, { terminalId });
          sendWithLog(JSON.stringify(resp));
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
          const termData = params?.data ?? params?.output ?? "";
          if (sessionId && terminalId && termData) {
            dispatch({
              type: "TERMINAL_DATA",
              questId: sessionId,
              terminalId,
              data: termData,
            });
          }
          const resp = buildResponse(req.id, {
            output: "",
            truncated: false,
            exitStatus: null,
          });
          sendWithLog(JSON.stringify(resp));
        }
      }
    },
    [dispatch, logRawMessage, sendWithLog]
  );

  // ===== WebSocket 连接 =====

  // Send cliSessionConfig as the first WebSocket message after connection
  const handleConnected = useCallback((wsSend: (data: string) => void) => {
    const configJson = cliSessionConfigRef.current;
    if (configJson) {
      const msg = JSON.stringify({
        jsonrpc: "2.0",
        method: "session/config",
        params: JSON.parse(configJson),
      });
      console.log("[HiCliSession] Sending session/config via WebSocket message");
      wsSend(msg);
    }
  }, []);

  const {
    status,
    send: sendRaw,
    connect,
    disconnect,
  } = useAcpWebSocket({
    url: currentWsUrl,
    onMessage: handleMessage,
    onConnected: handleConnected,
    autoConnect: !!currentWsUrl, // 无 URL 时不自动连接
  });

  useEffect(() => {
    sendRawRef.current = sendRaw;
  }, [sendRaw]);

  // ===== Prompt 队列自动消费 =====

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

  // ===== 自动初始化协议 =====

  useEffect(() => {
    if (status === "connected" && !initializedRef.current) {
      dispatch({ type: "WS_CONNECTED" });

      // K8s 运行时：等待沙箱就绪后再发送 initialize 请求
      // 本地运行时：立即发送 initialize 请求
      const shouldWaitForSandbox = stateRef.current.runtimeType === "k8s";
      const sandboxReady = !stateRef.current.sandboxStatus ||
                          stateRef.current.sandboxStatus.status === "ready";

      if (shouldWaitForSandbox && !sandboxReady) {
        // K8s 模式且沙箱未就绪，等待 sandbox/status: ready 通知
        return;
      }

      // 沙箱已就绪或本地模式，发送 initialize 请求
      initializedRef.current = true;

      (async () => {
        try {
          const initReq = buildInitialize();
          sendWithLog(JSON.stringify(initReq));

          // 给 initialize 请求加 2 分钟超时，CLI 启动可能较慢（如 K8s Pod 拉取镜像）
          const timeoutPromise = new Promise<never>((_, reject) =>
            setTimeout(() => reject(new Error("Initialize timeout")), 120000)
          );
          const initResult = (await Promise.race([
            trackRequest(initReq.id),
            timeoutPromise,
          ])) as InitializeResult;

          // 从 initialize 响应中提取 modes（部分 Agent 如 qwen 在此阶段返回）
          const initModes = initResult.modes?.availableModes ?? [];
          const initCurrentModeId = initResult.modes?.currentModeId ?? "";

          dispatch({
            type: "PROTOCOL_INITIALIZED",
            models: [],
            modes: initModes,
            currentModelId: "",
            currentModeId: initCurrentModeId,
          });

          // HiCli 专用：提取 agentInfo、authMethods、agentCapabilities
          dispatch({
            type: "DEBUG_PROTOCOL_INITIALIZED",
            agentInfo: initResult.agentInfo,
            authMethods: initResult.authMethods,
            agentCapabilities: initResult.agentCapabilities,
            modesSource: initModes.length > 0 ? "initialize" : null,
          });
        } catch (err) {
          console.error("ACP initialization failed:", err);
          // 超时或失败时也标记为已初始化，让用户能看到状态而不是永远卡住
          dispatch({
            type: "PROTOCOL_INITIALIZED",
            models: [],
            modes: [],
            currentModelId: "",
            currentModeId: "",
          });
        }
      })();
    }

    if (status === "disconnected") {
      initializedRef.current = false;
      clearPendingRequests();
      resetNextId();
      dispatch({ type: "WS_DISCONNECTED" });
    }
  }, [status, dispatch, sendWithLog]);

  // ===== 公开方法 =====

  const creatingQuestRef = useRef(false);
  const [creatingQuest, setCreatingQuest] = useState(false);

  const createQuest = useCallback(
    async (cwd: string): Promise<string> => {
      if (creatingQuestRef.current) {
        return Promise.reject(new Error("Quest creation already in progress"));
      }
      creatingQuestRef.current = true;
      setCreatingQuest(true);
      try {
        const sessionReq = buildSessionNew(cwd);
        sendWithLog(JSON.stringify(sessionReq));
        const result = (await trackRequest(sessionReq.id)) as SessionNewResult;

        // session/new 响应中可能包含 modes，如果 initialize 没有返回 modes 则更新来源
        const sessionModes = result.modes?.availableModes ?? [];

        dispatch({
          type: "QUEST_CREATED",
          sessionId: result.sessionId,
          cwd,
          models: result.models?.availableModels,
          modes: sessionModes.length > 0 ? sessionModes : undefined,
          currentModelId: result.models?.currentModelId,
          currentModeId: result.modes?.currentModeId,
        });

        // 如果 session/new 返回了 modes 且之前 initialize 没有返回，更新来源标注
        if (sessionModes.length > 0 && !stateRef.current.modesSource) {
          dispatch({
            type: "DEBUG_PROTOCOL_INITIALIZED",
            modesSource: "session_new" as const,
          });
        }

        return result.sessionId;
      } finally {
        creatingQuestRef.current = false;
        setCreatingQuest(false);
      }
    },
    [dispatch, sendWithLog]
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

      // 无活跃 Quest 时：先创建会话再发送消息
      if (!activeId) {
        const cwd = stateRef.current.cwd || ".";
        // 异步创建会话并发送消息，对调用方透明
        createQuest(cwd)
          .then((newQuestId) => {
            startPrompt(newQuestId, text, attachments);
          })
          .catch((err: unknown) => {
            console.error("Failed to create quest before sending prompt:", err);
            const errObj = err as { message?: string; code?: number };
            const message = errObj?.message || "会话创建失败";
            const isAuthError = message.toLowerCase().includes("authentication") ||
              message.toLowerCase().includes("auth") ||
              errObj?.code === 32000;
            dispatch({
              type: "SANDBOX_STATUS",
              status: "error",
              message: isAuthError ? "该工具需要登录后使用，请在工具设置中配置访问凭据" : message,
            });
          });
        // 同步返回 queued: true，表示消息正在排队处理（创建会话中）
        return { queued: true } as const;
      }

      const quest = stateRef.current.quests[activeId];
      if (!quest) return { queued: false } as const;

      if (quest.isProcessing || quest.inflightPromptId !== null) {
        const item = {
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
    [dispatch, startPrompt, createQuest]
  );

  const cancelPrompt = useCallback(() => {
    if (!state.activeQuestId) return;
    const notif = buildCancel(state.activeQuestId);
    sendWithLog(JSON.stringify(notif));
  }, [state.activeQuestId, sendWithLog]);

  const setModel = useCallback(
    (modelId: string) => {
      if (!state.activeQuestId) return;
      dispatch({ type: "SET_MODEL", modelId });
      const req = buildSetModel(state.activeQuestId, modelId);
      sendWithLog(JSON.stringify(req));
      trackRequest(req.id).catch(() => {});
    },
    [dispatch, state.activeQuestId, sendWithLog]
  );

  const setMode = useCallback(
    (modeId: string) => {
      if (!state.activeQuestId) return;
      dispatch({ type: "SET_MODE", modeId });
      const req = buildSetMode(state.activeQuestId, modeId);
      sendWithLog(JSON.stringify(req));
      trackRequest(req.id).catch(() => {});
    },
    [dispatch, state.activeQuestId, sendWithLog]
  );

  const respondPermission = useCallback(
    (requestId: JsonRpcId, optionId: string) => {
      const resp = buildResponse(requestId, {
        outcome: { outcome: "selected", optionId },
      });
      sendWithLog(JSON.stringify(resp));
      dispatch({ type: "PERMISSION_RESOLVED" });
    },
    [dispatch, sendWithLog]
  );

  // ===== connectToCli：连接到指定 CLI 工具 =====

  const connectToCli = useCallback(
    (cliId: string, runtime?: string, cliSessionConfig?: string) => {
      // 重置状态（清空调试数据和会话）
      dispatch({ type: "RESET_STATE" });
      // 记录选中的 CLI 工具（cwd 由后端决定，连接后通过 workspace/info 通知）
      dispatch({ type: "CLI_SELECTED", cliId, cwd: "", runtime });
      // K8s 运行时：立即显示沙箱创建中状态，让用户有即时反馈
      if (runtime === "k8s") {
        dispatch({
          type: "SANDBOX_STATUS",
          status: "creating",
          message: "正在连接沙箱环境...",
        });
      }
      // 重置初始化状态
      initializedRef.current = false;
      // cliSessionConfig 不再通过 URL 传递，改为 WebSocket 连接后通过 session/config 消息发送
      cliSessionConfigRef.current = cliSessionConfig;
      // 构建新的 WebSocket URL 并触发连接
      // K8s 运行时附加 sandboxMode=user，本地运行时不附加
      const isK8s = runtime === "k8s";
      const newUrl = buildHiCliWsUrl(cliId, runtime, undefined, isK8s ? "user" : undefined);
      setCurrentWsUrl(newUrl);
    },
    [dispatch]
  );

  return {
    status: status as WsStatus,
    creatingQuest,
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
    connectToCli,
  };
}
