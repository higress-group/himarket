import request, { type RespI } from "../request";

export interface PortalUiConfig {
  logo: string | null;
  icon: string | null;
  menuVisibility: Record<string, boolean> | null;
}

export function getPortalUiConfig() {
  return request.get<RespI<PortalUiConfig>, RespI<PortalUiConfig>>(
    '/portal-config/ui'
  );
}
