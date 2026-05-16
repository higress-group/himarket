export interface CachedUserInfo {
  avatar?: string;
  displayName: string;
  email?: string;
}

let cachedUserInfo: CachedUserInfo | null = null;
let loading = false;

export function clearCachedUserInfo() {
  cachedUserInfo = null;
  loading = false;
}

export function getCachedUserInfo() {
  return cachedUserInfo;
}

export function isUserInfoLoading() {
  return loading;
}

export function setCachedUserInfo(userInfo: CachedUserInfo | null) {
  cachedUserInfo = userInfo;
}

export function setUserInfoLoading(value: boolean) {
  loading = value;
}
