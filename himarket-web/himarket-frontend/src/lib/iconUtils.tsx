import type { IProductIcon } from "./apis/typing";

/**
 * 解析 meta.icon（JSON 字符串或纯 URL）为 IProductIcon 对象
 */
export function parseMetaIcon(metaIcon?: string): IProductIcon | undefined {
  if (!metaIcon) return undefined;
  try {
    const parsed = JSON.parse(metaIcon);
    if (parsed?.type && parsed?.value) return parsed as IProductIcon;
  } catch { /* not JSON, treat as URL */ }
  // 纯 URL 或 base64
  if (metaIcon.startsWith("http") || metaIcon.startsWith("data:image")) {
    return { type: "URL", value: metaIcon };
  }
  return undefined;
}

/**
 * 获取图标的字符串表示
 * @param icon - 产品图标对象
 * @returns 图标的字符串表示
 */
export function getIconString(icon?: IProductIcon): string {
  if (!icon || !icon.value) {
    return "default"; // 标记为使用默认图标
  }

  if (icon.type === "URL") {
    return icon.value;
  }

  if (icon.type === "BASE64") {
    return icon.value.startsWith('data:') ? icon.value : `data:image/png;base64,${icon.value}`;
  }

  return "default";
}
