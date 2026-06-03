import '@testing-library/jest-dom/vitest';
import '../i18n';

import { vi } from 'vitest';

if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = vi.fn();
}
