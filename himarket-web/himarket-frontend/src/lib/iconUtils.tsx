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
 * 获取名称的首字母（中文取首字，英文取首字母）
 * @param name - 产品名称
 * @returns 首字母或首字
 */
function getFirstChar(name: string): string {
  if (!name) return "?";
  // 取第一个字符
  const firstChar = name.charAt(0);
  return firstChar.toUpperCase();
}

/**
 * 获取图标的字符串表示
 * @param icon - 产品图标对象
 * @param name - 产品名称，用于无图标时生成首字母
 * @returns 图标的字符串表示，无图标时返回 name 的首字母
 */
export function getIconString(icon?: IProductIcon, name?: string): string {
  if (!icon || !icon.value) {
    // 无图标时返回 name 的首字母（中文首字，英文首字母）
    return name ? getFirstChar(name) : "?";
  }

  if (icon.type === "URL") {
    return icon.value;
  }

  if (icon.type === "BASE64") {
    return icon.value.startsWith('data:') ? icon.value : `data:image/png;base64,${icon.value}`;
  }

  return name ? getFirstChar(name) : "?";
}
