import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { ChangesView } from './ChangesView';

const useActiveCodingSessionMock = vi.fn();

vi.mock('../../context/CodingSessionContext', () => ({
  useActiveCodingSession: () => useActiveCodingSessionMock(),
}));

describe('ChangesView', () => {
  it('includes delete-style diff where newText is empty string', () => {
    useActiveCodingSessionMock.mockReturnValue({
      id: 'q1',
      messages: [
        {
          content: [
            {
              newText: '',
              oldText: 'a',
              path: '/tmp/delete.txt',
              type: 'diff',
            },
          ],
          id: 'tc-item-1',
          kind: 'edit',
          status: 'completed',
          title: 'Delete line',
          toolCallId: 'tc-1',
          type: 'tool_call',
        },
      ],
    });

    render(<ChangesView />);

    expect(screen.getByText('1 file changed')).toBeInTheDocument();
    expect(screen.getByText('/tmp/delete.txt')).toBeInTheDocument();
  });
});
