import { DefaultModel } from "./index";
import { ApiOutlined, RobotOutlined, BulbOutlined, ThunderboltOutlined, UserOutlined } from '@ant-design/icons';
import McpServerIcon from './McpServerIcon';

interface ProductIconRendererProps {
  iconType?: string;
  type?: string;
  className?: string;
}

function DefaultIconByType({ type, style }: { type?: string; style?: React.CSSProperties }) {
  if (type === 'REST_API') return <ApiOutlined style={style} />;
  if (type === 'AGENT_API') return <RobotOutlined style={style} />;
  if (type === 'MODEL_API') return <BulbOutlined style={style} />;
  if (type === 'AGENT_SKILL') return <ThunderboltOutlined style={style} />;
  if (type === 'WORKER') return <UserOutlined style={style} />;
  if (type === 'MCP_SERVER') return <McpServerIcon style={style} />;
  return null;
}

/**
 * Product icon renderer component.
 * Supports: URL image, Base64 image, type-based default icon, and SVG default.
 */
export function ProductIconRenderer({ iconType, type, className = "w-4 h-4" }: ProductIconRendererProps) {
  // URL or base64 image
  if (iconType && iconType !== "default") {
    if (iconType.startsWith('http') || iconType.startsWith('data:image')) {
      return <img src={iconType} alt="icon" className={`${className} object-cover rounded`} />;
    }
  }

  // Fall back to type-based icon
  const typeIcon = DefaultIconByType({ type, style: { fontSize: '22px', color: '#6366f1' } });
  if (typeIcon) return typeIcon;

  // Final fallback: SVG cube
  return <DefaultModel className={className} />;
}
