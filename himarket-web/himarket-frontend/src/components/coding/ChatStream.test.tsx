import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { ChatStream } from './ChatStream';

const useActiveCodingSessionMock = vi.fn();

vi.mock('../../context/CodingSessionContext', () => ({
  useActiveCodingSession: () => useActiveCodingSessionMock(),
  useCodingState: () => ({ sandboxStatus: null }),
}));

describe('ChatStream plan rendering', () => {
  it('renders plan/todo card only once for one plan message', () => {
    useActiveCodingSessionMock.mockReturnValue({
      id: 'q1',
      isProcessing: false,
      lastCompletedAt: null,
      lastStopReason: null,
      messages: [
        {
          entries: [{ content: 'step 1', status: 'pending' }],
          id: 'p1',
          type: 'plan',
        },
      ],
      selectedToolCallId: null,
    });

    render(<ChatStream onSelectToolCall={() => {}} />);

    expect(screen.getAllByText('Todo')).toHaveLength(1);
  });
});
