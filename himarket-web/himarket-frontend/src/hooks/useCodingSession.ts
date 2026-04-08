import { useCallback, useEffect, useRef, useState } from 'react';

import { useCodingWebSocket, type WsStatus } from './useCodingWebSocket';
import { useCodingDispatch, useCodingState } from '../context/CodingSessionContext';
import { normalizeIncomingMessage } from '../lib/utils/codingNormalize';
import {
  buildInitialize,
  buildSessionNew,
  buildSessionLoad,
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
} from '../lib/utils/codingProtocol';
import { ARTIFACT_SCAN_FALLBACK_ENABLED, fetchWorkspaceChanges } from '../lib/utils/workspaceApi';
import { CODING_METHODS } from '../types/coding-protocol';

import type {
  CodingRequest,
  CodingResponse,
  CodingNotification,
  SessionNewResult,
  InitializeResult,
  SessionUpdate,
  PermissionRequest,
  Attachment,
  ChatItem,
  ChatItemToolCall,
  JsonRpcId,
} from '../types/coding-protocol';

export interface UseCodingSessionOptions {
  wsUrl: string;
  /** If false, WebSocket won't connect until `connect()` or `createSession()` is called. Default true. */
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

/** 最大注入历史字符数，避免 prompt 过长 */
const MAX_HISTORY_CHARS = 8000;

/**
 * 将会话中已有的消息列表序列化为纯文本摘要，
 * 用于在 CLI 无法原生恢复 session 时注入到首条 prompt 中，
 * 使 LLM 保留先前对话的上下文。
 */
function buildConversationHistory(messages: ChatItem[]): string {
  const lines: string[] = [];
  for (const msg of messages) {
    if (msg.type === 'user') {
      lines.push(`Human: ${msg.text}`);
    } else if (msg.type === 'agent' && msg.text) {
      lines.push(`Assistant: ${msg.text}`);
    } else if (msg.type === 'tool_call') {
      const tc = msg as ChatItemToolCall;
      lines.push(`[Tool ${tc.kind}: ${tc.title} → ${tc.status}]`);
    }
    // Skip thought / plan / error for brevity
  }
  if (lines.length === 0) return '';

  let body = lines.join('\n');
  if (body.length > MAX_HISTORY_CHARS) {
    body = body.slice(body.length - MAX_HISTORY_CHARS);
    // 截断到第一个完整行
    const idx = body.indexOf('\n');
    if (idx > 0) body = body.slice(idx + 1);
    body = '...(earlier messages truncated)\n' + body;
  }
  return (
    '<conversation_history>\n' +
    body +
    '\n</conversation_history>\n' +
    '[Above is the history of our previous conversation in this session. ' +
    'Please take it into account when responding to the following message.]'
  );
}

export function useCodingSession({
  autoConnect: autoConnectOpt = true,
  cliSessionConfig,
  wsUrl,
}: UseCodingSessionOptions) {
  const dispatch = useCodingDispatch();
  const state = useCodingState();
  const stateRef = useRef(state);
  const initializedRef = useRef(false);
  const sendRawRef = useRef<(data: string) => void>(() => {});
  const scanTriggeredRef = useRef<Set<string>>(new Set());
  const autoPermissionsRef = useRef<Record<string, 'allow' | 'reject'>>({});
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
      sessionId: string,
      text: string,
      attachments?: Attachment[],
      promptId?: string,
      /** If provided, this text is sent to the CLI instead of `text`.
       *  `text` is still used for the UI message display. */
      textForCli?: string,
    ): JsonRpcId => {
      const req = buildPrompt(sessionId, textForCli ?? text, attachments);
      dispatch({
        attachments,
        promptId,
        requestId: req.id,
        sessionId,
        text,
        type: 'PROMPT_STARTED',
      });
      sendRawRef.current(JSON.stringify(req));
      trackRequest(req.id)
        .then((result) => {
          const r = result as { stopReason?: string };
          dispatch({
            requestId: req.id,
            sessionId,
            stopReason: r?.stopReason ?? 'unknown',
            type: 'PROMPT_COMPLETED',
          });
        })
        .catch((err: unknown) => {
          let code = -1;
          let message = 'Unknown error';
          let data: Record<string, unknown> | undefined;

          if (
            typeof err === 'object' &&
            err !== null &&
            'code' in err &&
            'message' in err &&
            typeof (err as Record<string, unknown>).code === 'number' &&
            typeof (err as Record<string, unknown>).message === 'string'
          ) {
            code = (err as Record<string, unknown>).code as number;
            message = (err as Record<string, unknown>).message as string;
            const rawData = (err as Record<string, unknown>).data;
            if (typeof rawData === 'object' && rawData !== null && !Array.isArray(rawData)) {
              data = rawData as Record<string, unknown>;
            }
          }

          dispatch({
            code,
            message,
            requestId: req.id,
            sessionId,
            type: 'PROMPT_ERROR',
            ...(data !== undefined ? { data } : {}),
          });
        });
      return req.id;
    },
    [dispatch],
  );

  const handleMessage = useCallback(
    (data: string) => {
      let parsed: Record<string, unknown>;
      try {
        parsed = JSON.parse(data);
      } catch {
        console.warn('[CodingSession] Failed to parse message (len=' + data.length + '):', data);
        return;
      }
      parsed = normalizeIncomingMessage(parsed);
      console.warn('[CodingSession] Received message:', JSON.stringify(parsed));

      // Debug: log all terminal-related messages
      const method = parsed.method as string | undefined;
      if (method && method.startsWith('terminal')) {
        console.warn('[Coding Terminal]', method, parsed);
      }

      const hasId = typeof parsed.id === 'number' || typeof parsed.id === 'string';
      const hasMethod = typeof parsed.method === 'string';

      // Response (has id, no method)
      if (hasId && !hasMethod) {
        resolveResponse(parsed as unknown as CodingResponse);
        return;
      }

      // Notification (has method, no id)
      if (hasMethod && !hasId) {
        const notif = parsed as unknown as CodingNotification;
        if (notif.method === 'sandbox/status') {
          const params = notif.params as {
            status?: string;
            message?: string;
            sandboxHost?: string;
          };
          dispatch({
            message: params?.message ?? '',
            sandboxHost: params?.sandboxHost,
            status: (params?.status ?? 'creating') as 'creating' | 'ready' | 'error',
            type: 'SANDBOX_STATUS',
          });

          // 沙箱就绪后，如果协议还未初始化，触发 initialize 请求
          // 这样可以避免 initialize 请求在沙箱初始化期间被缓存导致的时序问题
          if (
            params?.status === 'ready' &&
            !initializedRef.current &&
            !stateRef.current.initialized
          ) {
            initializedRef.current = true;
            console.warn('[CodingSession] Sandbox ready, starting initialize...');
            (async () => {
              try {
                const send = sendRawRef.current;
                const initReq = buildInitialize();
                console.warn(
                  '[CodingSession] Sending initialize request:',
                  JSON.stringify(initReq),
                );
                send(JSON.stringify(initReq));

                const timeoutPromise = new Promise<never>((_, reject) =>
                  setTimeout(() => reject(new Error('Initialize timeout')), 120000),
                );
                const result = await Promise.race([trackRequest(initReq.id), timeoutPromise]);
                console.warn('[CodingSession] Initialize response received:', result);

                const initResult = result as InitializeResult | undefined;
                const supportsLoad = initResult?.agentCapabilities?.loadSession === true;

                dispatch({
                  agentSupportsLoadSession: supportsLoad,
                  currentModeId: '',
                  currentModelId: '',
                  models: [],
                  modes: [],
                  type: 'PROTOCOL_INITIALIZED',
                });
                console.warn('[CodingSession] PROTOCOL_INITIALIZED dispatched');
              } catch (err) {
                console.error('[CodingSession] Protocol initialization failed:', err);
                dispatch({
                  currentModeId: '',
                  currentModelId: '',
                  models: [],
                  modes: [],
                  type: 'PROTOCOL_INITIALIZED',
                });
                console.warn(
                  '[CodingSession] PROTOCOL_INITIALIZED dispatched (fallback after error)',
                );
              }
            })();
          }
          return;
        }
        if (notif.method === 'sandbox/init-progress') {
          const params = notif.params as {
            phase?: string;
            status?: 'executing' | 'completed';
            message?: string;
            progress?: number;
            totalPhases?: number;
            completedPhases?: number;
          };
          dispatch({
            completedPhases: params?.completedPhases ?? 0,
            message: params?.message ?? '',
            phase: params?.phase ?? '',
            progress: params?.progress ?? 0,
            status: params?.status ?? 'executing',
            totalPhases: params?.totalPhases ?? 5,
            type: 'INIT_PROGRESS',
          });
          return;
        }
        if (notif.method === 'workspace/info') {
          const params = notif.params as { cwd?: string };
          if (params?.cwd) {
            dispatch({ cwd: params.cwd, type: 'WORKSPACE_INFO' });
          }
          return;
        }
        if (notif.method === 'sandbox/reattached') {
          const params = notif.params as { sidecarSessionId?: string; reattached?: boolean };
          console.warn(
            '[CodingSession] Session reattached to existing sidecar session:',
            params?.sidecarSessionId,
          );
          return;
        }
        if (notif.method === CODING_METHODS.SESSION_UPDATE) {
          const update = extractSessionUpdate(notif);
          if (update) {
            const sessionId = (notif.params as { sessionId?: string })?.sessionId ?? '';
            const variant =
              (update as { update?: { sessionUpdate?: string } }).update?.sessionUpdate ??
              'unknown';
            const sessionExists = !!stateRef.current.sessions[sessionId];
            const isLoading = stateRef.current.sessions[sessionId]?.isLoading ?? false;
            console.warn(
              '[CodingSession] session/update:',
              variant,
              '| sessionId:',
              sessionId.substring(0, 8) + '...',
              '| sessionExists:',
              sessionExists,
              '| isLoading:',
              isLoading,
            );
            dispatch({
              sessionId,
              type: 'SESSION_UPDATE',
              update: update as SessionUpdate,
            });
          }
        } else if (notif.method === CODING_METHODS.TERMINAL_OUTPUT) {
          // terminal/output may arrive as notification (no id) in some agent impls
          const params = notif.params as {
            sessionId?: string;
            terminalId?: string;
            data?: string;
            output?: string;
          };
          const sessionId = params?.sessionId ?? '';
          const terminalId = params?.terminalId ?? '';
          const data = params?.data ?? params?.output ?? '';
          if (sessionId && terminalId && data) {
            dispatch({
              data,
              sessionId,
              terminalId,
              type: 'TERMINAL_DATA',
            });
          }
        } else if (notif.method === CODING_METHODS.TERMINAL_CREATE) {
          // terminal/create as notification — auto-register terminal
          const params = notif.params as {
            sessionId?: string;
            terminalId?: string;
          };
          const sessionId = params?.sessionId ?? '';
          const terminalId = params?.terminalId ?? `term-notif-${Date.now()}`;
          if (sessionId) {
            dispatch({
              sessionId,
              terminalId,
              type: 'TERMINAL_CREATED',
            });
          }
        }
        return;
      }

      // Request from agent (has both id and method)
      if (hasId && hasMethod) {
        const req = parsed as unknown as CodingRequest;
        const send = sendRawRef.current;
        if (req.method === CODING_METHODS.REQUEST_PERMISSION) {
          const perm = extractPermissionRequest(req);
          if (perm) {
            const sessionId = (req.params as { sessionId?: string })?.sessionId ?? '';
            const autoDecision = autoPermissionsRef.current[sessionId];
            if (autoDecision) {
              // Find a matching option to auto-respond with
              const targetKind = autoDecision === 'allow' ? 'allow_once' : 'reject_once';
              const option =
                perm.options.find((o) => o.kind === targetKind) ??
                perm.options.find((o) => o.kind.startsWith(autoDecision));
              if (option) {
                send(
                  JSON.stringify(
                    buildResponse(req.id, {
                      outcome: {
                        optionId: option.optionId,
                        outcome: 'selected',
                      },
                    }),
                  ),
                );
                return;
              }
            }
            dispatch({
              id: req.id,
              request: perm as PermissionRequest,
              sessionId,
              type: 'PERMISSION_REQUEST',
            });
          }
        } else if (req.method === CODING_METHODS.TERMINAL_CREATE) {
          const params = req.params as {
            sessionId?: string;
            terminalId?: string;
          };
          // Use agent-provided terminalId if present, otherwise generate one
          const terminalId = params?.terminalId || `term-${req.id}`;
          const sessionId = params?.sessionId ?? '';
          send(JSON.stringify(buildResponse(req.id, { terminalId })));
          if (sessionId) {
            dispatch({
              sessionId,
              terminalId,
              type: 'TERMINAL_CREATED',
            });
          }
        } else if (req.method === CODING_METHODS.TERMINAL_OUTPUT) {
          const params = req.params as {
            sessionId?: string;
            terminalId?: string;
            data?: string;
            output?: string;
          };
          const sessionId = params?.sessionId ?? '';
          const terminalId = params?.terminalId ?? '';
          const data = params?.data ?? params?.output ?? '';
          if (sessionId && terminalId && data) {
            dispatch({
              data,
              sessionId,
              terminalId,
              type: 'TERMINAL_DATA',
            });
          }
          send(
            JSON.stringify(
              buildResponse(req.id, {
                exitStatus: null,
                output: '',
                truncated: false,
              }),
            ),
          );
        }
      }
    },
    [dispatch],
  );

  // Send cliSessionConfig as the first WebSocket message after connection,
  // instead of passing it via URL query string (which hits Nginx header size limits).
  const handleConnected = useCallback((wsSend: (data: string) => void) => {
    const configJson = cliSessionConfigRef.current;
    if (configJson) {
      const msg = JSON.stringify({
        jsonrpc: '2.0',
        method: 'session/config',
        params: JSON.parse(configJson),
      });
      console.warn('[CodingSession] Sending session/config via WebSocket message');
      wsSend(msg);
    }
  }, []);

  const {
    connect: wsConnect,
    disconnect: wsDisconnect,
    manualReconnect,
    reconnectAttempt,
    send: sendRaw,
    status: wsStatus,
  } = useCodingWebSocket({
    autoConnect: autoConnectOpt && !!wsUrl,
    onConnected: handleConnected,
    onMessage: handleMessage,
    url: wsUrl,
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
    const sessions = Object.values(state.sessions);
    for (const session of sessions) {
      if (session.isProcessing || session.inflightPromptId !== null) continue;
      const next = session.promptQueue[0];
      if (!next) continue;
      startPrompt(session.id, next.text, next.attachments, next.id);
    }
  }, [state.initialized, state.sessions, startPrompt]);

  useEffect(() => {
    if (!ARTIFACT_SCAN_FALLBACK_ENABLED) return;

    const sessions = Object.values(state.sessions);
    for (const session of sessions) {
      for (const item of session.messages) {
        if (item.type !== 'tool_call') continue;
        const tc = item as ChatItemToolCall;
        const SCAN_SKIP_KINDS: ReadonlySet<string> = new Set([
          'read',
          'search',
          'think',
          'fetch',
          'switch_mode',
        ]);
        if (SCAN_SKIP_KINDS.has(tc.kind)) continue;
        if (tc.status !== 'completed' && tc.status !== 'failed') continue;

        const scanKey = `${session.id}:${tc.toolCallId}:${tc.status}`;
        if (scanTriggeredRef.current.has(scanKey)) continue;
        scanTriggeredRef.current.add(scanKey);

        const since = session.lastArtifactScanAt ?? session.createdAt;
        const now = Date.now();

        fetchWorkspaceChanges(session.cwd, since, 200)
          .then((changes) => {
            const paths = changes.map((c) => c.path);
            if (paths.length > 0) {
              dispatch({
                paths,
                sessionId: session.id,
                toolCallId: tc.toolCallId,
                type: 'UPSERT_ARTIFACTS_FROM_PATHS',
              });
            }
            dispatch({
              cursor: Math.max(0, now - 2000),
              sessionId: session.id,
              type: 'SET_ARTIFACT_SCAN_CURSOR',
            });
          })
          .catch(() => {
            scanTriggeredRef.current.delete(scanKey);
          });
      }
    }
  }, [state.sessions, dispatch]);

  // Auto-initialize protocol when connected
  useEffect(() => {
    if (status === 'connected') {
      console.warn('[CodingSession] WS connected');
      dispatch({ type: 'WS_CONNECTED' });
      // initialize 请求统一由 handleMessage 收到 sandbox/status: ready 后触发，
      // 避免双路径竞争。
    }

    // 重连中：后端在 WebSocket 关闭时已销毁 runtime/sandbox 资源，
    // 需要重置前端初始化状态，让重连成功后重新走完整的 initialize → session/new 流程。
    if (status === 'reconnecting') {
      console.warn(
        '[CodingSession] Reconnecting — resetting init state for full re-initialization',
      );
      initializedRef.current = false;
      clearPendingRequests();
      resetNextId();
    }

    if (status === 'disconnected') {
      initializedRef.current = false;
      clearPendingRequests();
      resetNextId();
      dispatch({ type: 'WS_DISCONNECTED' });
    }
  }, [status, dispatch]);

  const creatingSessionRef = useRef(false);
  const [creatingSession, setCreatingSession] = useState(false);

  // Promise that resolves once protocol is initialized.
  // Used by createSession to wait for connection + init when autoConnect is off.
  const initPromiseRef = useRef<{ promise: Promise<void>; resolve: () => void } | null>(null);

  const getInitPromise = useCallback(() => {
    if (!initPromiseRef.current) {
      let resolve!: () => void;
      const promise = new Promise<void>((r) => {
        resolve = r;
      });
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

  const createSession = useCallback(
    async (cwd: string): Promise<string> => {
      if (creatingSessionRef.current) {
        return Promise.reject(new Error('Session creation already in progress'));
      }
      creatingSessionRef.current = true;
      setCreatingSession(true);
      try {
        // If not yet initialized, trigger connection and wait for init
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
          currentModeId: result.modes?.currentModeId,
          currentModelId: result.models?.currentModelId,
          cwd,
          models: result.models?.availableModels,
          modes: result.modes?.availableModes,
          sessionId: result.sessionId,
          type: 'SESSION_CREATED',
        });

        return result.sessionId;
      } finally {
        creatingSessionRef.current = false;
        setCreatingSession(false);
      }
    },
    [dispatch, connect, getInitPromise],
  );

  const loadingSessionRef = useRef(false);

  const loadSession = useCallback(
    async (
      cliSessionId: string,
      cwd: string,
      title?: string,
      platformSessionId?: string,
    ): Promise<string> => {
      if (loadingSessionRef.current) {
        return Promise.reject(new Error('Session loading already in progress'));
      }
      loadingSessionRef.current = true;
      try {
        // If not yet initialized, trigger connection and wait for init
        if (!stateRef.current.initialized) {
          const { promise } = getInitPromise();
          connect();
          await promise;
        }

        // Dispatch SESSION_LOADING to create UI placeholder
        dispatch({
          cwd,
          platformSessionId,
          sessionId: cliSessionId,
          title,
          type: 'SESSION_LOADING',
        });

        const send = sendRawRef.current;

        // session/load: ACP 协议规定返回 null 即为成功（会话上下文已恢复），
        // 原始 cliSessionId 仍然有效，无需回退到 session/new。
        const loadReq = buildSessionLoad(cliSessionId, cwd);
        send(JSON.stringify(loadReq));
        await trackRequest(loadReq.id);

        // Mark session as fully loaded
        dispatch({
          sessionId: cliSessionId,
          type: 'SESSION_LOADED',
        });

        return cliSessionId;
      } catch (err) {
        // If loading fails, remove the placeholder session
        dispatch({ sessionId: cliSessionId, type: 'SESSION_CLOSED' });
        throw err;
      } finally {
        loadingSessionRef.current = false;
      }
    },
    [dispatch, connect, getInitPromise],
  );

  const switchSession = useCallback(
    (sessionId: string) => {
      dispatch({ sessionId, type: 'SESSION_SWITCHED' });
    },
    [dispatch],
  );

  const closeSession = useCallback(
    (sessionId: string) => {
      dispatch({ sessionId, type: 'SESSION_CLOSED' });
    },
    [dispatch],
  );

  const sendPrompt = useCallback(
    (text: string, attachments?: Attachment[]) => {
      const activeId = stateRef.current.activeSessionId;
      if (!activeId) return { queued: false } as const;
      const session = stateRef.current.sessions[activeId];
      if (!session) return { queued: false } as const;

      if (session.isProcessing || session.inflightPromptId !== null) {
        const item: QueuedPromptItemInput = {
          id: `qp-${Date.now()}-${++promptSeqRef.current}`,
          text,
          ...(attachments && attachments.length > 0 ? { attachments } : {}),
          createdAt: Date.now(),
        };
        dispatch({ item, sessionId: activeId, type: 'PROMPT_ENQUEUED' });
        return { queued: true, queuedPromptId: item.id } as const;
      }

      // When the session was restored via fallback (CLI couldn't load the
      // original session), inject the replayed conversation history into
      // the first prompt so the LLM retains prior context.
      let textForCli: string | undefined;
      if (session.needsHistoryInjection && session.messages.length > 0) {
        const historyContext = buildConversationHistory(session.messages);
        if (historyContext) {
          textForCli = historyContext + '\n\n' + text;
        }
        dispatch({ sessionId: activeId, type: 'HISTORY_INJECTED' });
      }

      const requestId = startPrompt(activeId, text, attachments, undefined, textForCli);
      return { queued: false, requestId } as const;
    },
    [dispatch, startPrompt],
  );

  const dropQueuedPrompt = useCallback(
    (promptId: string) => {
      const activeId = stateRef.current.activeSessionId;
      if (!activeId) return;
      dispatch({ promptId, sessionId: activeId, type: 'PROMPT_DEQUEUED' });
    },
    [dispatch],
  );

  const cancelPrompt = useCallback(() => {
    if (!state.activeSessionId) return;
    const notif = buildCancel(state.activeSessionId);
    sendRawRef.current(JSON.stringify(notif));
  }, [state.activeSessionId]);

  const setModel = useCallback(
    (modelId: string) => {
      if (!state.activeSessionId) return;
      dispatch({ modelId, type: 'SET_MODEL' });
      const req = buildSetModel(state.activeSessionId, modelId);
      sendRawRef.current(JSON.stringify(req));
      trackRequest(req.id).catch(() => {});
    },
    [dispatch, state.activeSessionId],
  );

  const setMode = useCallback(
    (modeId: string) => {
      if (!state.activeSessionId) return;
      dispatch({ modeId, type: 'SET_MODE' });
      const req = buildSetMode(state.activeSessionId, modeId);
      sendRawRef.current(JSON.stringify(req));
      trackRequest(req.id).catch(() => {});
    },
    [dispatch, state.activeSessionId],
  );

  const respondPermission = useCallback(
    (requestId: JsonRpcId, optionId: string) => {
      // Look up the selected option's kind from the pending permission
      const pending = stateRef.current.pendingPermission;
      const option = pending?.request.options.find((o) => o.optionId === optionId);

      // Store auto-permission if it's an "always" kind
      if (option?.kind === 'allow_always' && pending?.sessionId) {
        autoPermissionsRef.current[pending.sessionId] = 'allow';
      } else if (option?.kind === 'reject_always' && pending?.sessionId) {
        autoPermissionsRef.current[pending.sessionId] = 'reject';
      }

      const resp = buildResponse(requestId, {
        outcome: { optionId, outcome: 'selected' },
      });
      sendRawRef.current(JSON.stringify(resp));
      dispatch({ type: 'PERMISSION_RESOLVED' });
    },
    [dispatch],
  );

  return {
    cancelPrompt,
    closeSession,
    connect,
    createSession,
    creatingSession,
    disconnect,
    dropQueuedPrompt,
    loadSession,
    manualReconnect,
    reconnect: connect,
    reconnectAttempt,
    respondPermission,
    runtimeError: null,
    sendPrompt,
    setMode,
    setModel,
    status: status as WsStatus,
    switchSession,
  };
}
