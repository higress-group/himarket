import { DefaultModel } from "./index";

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
 * 预定义的渐变背景色配置，确保字体颜色与背景搭配良好
 */
const GRADIENT_COLORS = [
  { bg: "from-blue-500 to-indigo-600", text: "text-white" },
  { bg: "from-emerald-500 to-teal-600", text: "text-white" },
  { bg: "from-violet-500 to-purple-600", text: "text-white" },
  { bg: "from-rose-500 to-pink-600", text: "text-white" },
  { bg: "from-amber-500 to-orange-600", text: "text-white" },
  { bg: "from-cyan-500 to-sky-600", text: "text-white" },
  { bg: "from-fuchsia-500 to-pink-600", text: "text-white" },
  { bg: "from-lime-500 to-green-600", text: "text-white" },
];

/**
 * 根据字符获取固定的颜色索引（确保同一字符始终显示相同颜色）
 * @param char - 单个字符
 * @returns 颜色配置
 */
function getColorByChar(char: string) {
  const code = char.charCodeAt(0);
  const index = code % GRADIENT_COLORS.length;
  return GRADIENT_COLORS[index];
}

/**
 * 通用的产品图标渲染组件
 * 支持：URL 图片、Base64 图片、首字母/首字、默认图标
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

  // 如果是单个字符（首字母/首字），渲染文字图标
  if (isSingleChar(iconType)) {
    const colorConfig = getColorByChar(iconType);
    return (
      <div className={`${className} flex items-center justify-center bg-gradient-to-br ${colorConfig.bg} ${colorConfig.text} font-bold rounded`}>
        <span className="text-lg">{iconType}</span>
      </div>
    );
  }

  // 其他情况使用默认图标
  return <DefaultModel className={className} />;
}
