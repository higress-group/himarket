import request, { type RespI } from '../request';

export interface PortalUiConfig {
  logo: string | null;
  icon: string | null;
  menuVisibility: Record<string, boolean> | null;
}

export interface PortalProfile {
  portalId: string;
  name: string;
  description: string;
  portalUiConfig: PortalUiConfig;
}

export function getPortalProfile() {
  return request.get<RespI<PortalProfile>, RespI<PortalProfile>>('/portals/profile');
}
