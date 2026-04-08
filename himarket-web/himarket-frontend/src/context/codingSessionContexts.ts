import { createContext, type Dispatch } from 'react';

import { initialState } from './codingSessionReducer';

import type { CodingAction, CodingState } from './codingSessionTypes';

export const CodingStateContext = createContext<CodingState>(initialState);
export const CodingDispatchContext = createContext<Dispatch<CodingAction>>(() => {});
