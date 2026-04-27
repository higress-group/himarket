import { DefaultModel } from './index';

interface ProductIconRendererProps {
  iconType?: string;
  className?: string;
}

/**
 * 判断字符串是否为单个字符（首字母/首字）
 * 单个字符说明是无图标时降级显示的名称首字
 */
function isSingleChar(str: string): boolean {
  // 使用 Array.from 正确处理 Unicode 字符（包括中文）
  return Array.from(str).length === 1;
}

/**
 * 通用的产品图标渲染组件
 * 支持：URL 图片、Base64 图片、首字母/首字、默认图标
 */
export function ProductIconRenderer({ className = 'w-4 h-4', iconType }: ProductIconRendererProps) {
  // 如果是默认图标或空值
  if (!iconType || iconType === 'default') {
    return <DefaultModel className={className} />;
  }

  // 如果是 URL 或 base64 图片
  if (iconType.startsWith('http') || iconType.startsWith('data:image')) {
    return <img alt="icon" className={`${className} object-cover rounded`} src={iconType} />;
  }

  // 如果是单个字符（首字母/首字），渲染文字图标（统一中性色）
  if (isSingleChar(iconType)) {
    return (
      <div
        className={`${className} flex items-center justify-center bg-slate-100 text-slate-600 font-bold rounded`}
      >
        <span className="text-lg">{iconType}</span>
      </div>
    );
  }

  // 其他情况使用默认图标
  return <DefaultModel className={className} />;
}
