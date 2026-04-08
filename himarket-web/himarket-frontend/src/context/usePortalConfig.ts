import { useContext } from 'react';

import { PortalConfigContext } from './PortalConfigContext.utils';

export function usePortalConfig() {
  return useContext(PortalConfigContext);
}
