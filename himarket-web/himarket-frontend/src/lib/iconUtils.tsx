import { DefaultModel } from "../components/icon";
import type { IProductIcon } from "./apis/typing";

/**
 * 渲染产品图标
 * @param icon - 产品图标对象（可能为 null/undefined）
 * @param alt - 图片的 alt 文本
 * @param className - 额外的 CSS 类名
 * @returns React 元素
 */
export function renderProductIcon(
  icon?: IProductIcon,
  alt: string = "icon",
  className: string = "w-full h-full object-cover"
): JSX.Element {
  // 如果没有 icon 或 icon 为空，使用默认图标
  if (!icon || !icon.value) {
    return <DefaultModel />;
  }

  // 如果是 URL 类型
  if (icon.type === "URL") {
    return <img src={icon.value} alt={alt} className={className} />;
  }

  // 如果是 BASE64 类型
  if (icon.type === "BASE64") {
    return <img src={`data:image/png;base64,${icon.value}`} alt={alt} className={className} />;
  }

  // 其他情况返回默认图标
  return <DefaultModel />;
}

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
    return `data:image/png;base64,${icon.value}`;
  }

  return "default";
}
