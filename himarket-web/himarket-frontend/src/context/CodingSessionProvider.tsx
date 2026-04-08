import { useReducer, type ReactNode } from 'react';

import { CodingDispatchContext, CodingStateContext } from './codingSessionContexts';
import { codingReducer, initialState } from './codingSessionReducer';

export function CodingSessionProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(codingReducer, initialState);
  return (
    <CodingStateContext.Provider value={state}>
      <CodingDispatchContext.Provider value={dispatch}>{children}</CodingDispatchContext.Provider>
    </CodingStateContext.Provider>
  );
}
