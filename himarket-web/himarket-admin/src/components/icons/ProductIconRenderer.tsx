import { DefaultModel } from "./index";

interface ProductIconRendererProps {
  iconType?: string;
  className?: string;
}

/**
 * 通用的产品图标渲染组件
 * 支持：URL 图片、Base64 图片、默认图标
 */
export function ProductIconRenderer({ iconType, className = "w-4 h-4" }: ProductIconRendererProps) {
  // 如果是默认图标或空值
  if (!iconType || iconType === "default") {
    return <DefaultModel className={className} />;
  }

  // 如果是 URL 或 base64 图片
  if (iconType.startsWith('http') || iconType.startsWith('data:image')) {
    return <img src={iconType} alt="icon" className={`${className} object-cover rounded`} />;
  }

  // 其他情况使用默认图标
  return <DefaultModel className={className} />;
}
