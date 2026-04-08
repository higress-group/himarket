import { render, screen } from '@testing-library/react';
import fc from 'fast-check';
import { describe, expect, it } from 'vitest';

import { ErrorMessage } from './ErrorMessage';

// Feature: acp-error-response-handling, Property 3: 错误消息渲染包含完整错误信息
describe('ErrorMessage property-based tests', () => {
  /**
   * Property 3: 错误消息渲染包含完整错误信息
   *
   * For any random error code (integer) and error message (non-empty string),
   * the ErrorMessage component's rendered output contains both the text
   * representation of the error code and the error message text.
   *
   * Validates: Requirements 4.2
   */
  it('renders both error code and message for any valid code/message', () => {
    fc.assert(
      fc.property(
        fc.integer(),
        fc.string({ minLength: 1 }).filter((s) => s.trim().length > 0),
        (code, message) => {
          const { unmount } = render(<ErrorMessage code={code} message={message} />);

          // The component renders "错误 {code}", so the code text should be present
          expect(screen.getByRole('alert').textContent).toContain(String(code));

          // The message text should also be present in the rendered output
          expect(screen.getByRole('alert').textContent).toContain(message);

          // Cleanup to avoid DOM pollution between iterations
          unmount();
        },
      ),
      { numRuns: 100 },
    );
  });
});
