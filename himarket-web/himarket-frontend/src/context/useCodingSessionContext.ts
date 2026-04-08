import { useContext, type Dispatch } from 'react';

import { CodingDispatchContext, CodingStateContext } from './codingSessionContexts';

import type { CodingAction, CodingSessionData, CodingState } from './codingSessionTypes';
import type { Artifact } from '../types/artifact';

export function useCodingState(): CodingState {
  return useContext(CodingStateContext);
}

export function useCodingDispatch(): Dispatch<CodingAction> {
  return useContext(CodingDispatchContext);
}

export function useActiveCodingSession(): CodingSessionData | null {
  const state = useCodingState();
  if (!state.activeSessionId) return null;
  return state.sessions[state.activeSessionId] ?? null;
}

export function useActiveArtifacts(): Artifact[] {
  const session = useActiveCodingSession();
  return session?.artifacts ?? [];
}
