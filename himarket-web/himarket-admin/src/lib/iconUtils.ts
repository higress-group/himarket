import { IProductIcon } from "./apis/typing";

/**
 * 获取图标的字符串表示（用于向后兼容）
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
