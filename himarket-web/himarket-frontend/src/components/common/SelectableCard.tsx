import type React from 'react';

/**
 * 通用可选卡片组件 Props
 * 用于 CLI 工具选择、MCP 卡片、Skill 卡片的统一选中/未选中视觉样式
 */
export interface SelectableCardProps {
  /** 是否选中 */
  selected: boolean;
  /** 是否禁用 */
  disabled?: boolean;
  /** 点击回调 */
  onClick: () => void;
  /** 卡片内容 */
  children: React.ReactNode;
}

/**
 * 通用可选卡片组件
 *
 * 三种视觉状态：
 * - 选中：蓝色边框高亮 (border-blue-500)
 * - 未选中：灰色边框 (border-gray-200)
 * - 禁用：置灰，不可点击 (opacity-50, cursor-not-allowed)
 */
export const SelectableCard: React.FC<SelectableCardProps> = ({
  children,
  disabled = false,
  onClick,
  selected,
}) => {
  const handleClick = () => {
    if (!disabled) {
      onClick();
    }
  };

  const borderClass = disabled
    ? 'border-gray-200'
    : selected
      ? 'border-blue-500 bg-blue-50'
      : 'border-gray-200 hover:border-blue-300';

  const cursorClass = disabled ? 'cursor-not-allowed opacity-50' : 'cursor-pointer';

  return (
    <div
      aria-disabled={disabled}
      aria-selected={selected}
      className={`rounded-lg border-2 p-3 transition-colors ${borderClass} ${cursorClass}`}
      onClick={handleClick}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          handleClick();
        }
      }}
      role="button"
      tabIndex={disabled ? -1 : 0}
    >
      {children}
    </div>
  );
};
