import React from 'react';
import Icon from '@ant-design/icons';
import type { CustomIconComponentProps } from '@ant-design/icons/lib/components/Icon';
import { ToolOutlined } from '@ant-design/icons';

// 基于真实的 MCP.svg 文件的图标组件
const McpSvg = () => (
  <svg
    width="1em"
    height="1em"
    viewBox="0 0 200 200"
    fill="currentColor"
    xmlns="http://www.w3.org/2000/svg"
  >
    <g fill="currentColor">
      <path d="M97.7 185.2 l-8.7 -8.7 40.5 -40.5 c22.3 -22.3 40.5 -40.9 40.5 -41.5 0 -0.6 -6.8 -7.8 -15 -16 l-15 -15 -32.8 32.8 -32.7 32.7 -4.3 -4.2 -4.2 -4.3 32.7 -32.7 32.8 -32.8 -15.5 -15.5 -15.5 -15.5 -41 41 -41.1 41 -3.9 -4 -4 -4 45.3 -45.3 45.2 -45.2 43.2 43.2 43.3 43.3 -41 41 -41 41 5 5 4.9 5 -3.9 4 c-2.1 2.2 -4.2 4 -4.5 4 -0.3 0 -4.5 -3.9 -9.3 -8.8z"/>
      <path d="M53.7 141.2 l-23.7 -23.7 35.3 -35.3 35.3 -35.2 3.9 4 3.9 4 -30.9 31 -31 31 15.5 15.5 15.5 15.5 31 -31 31 -31 4.3 4.3 4.2 4.2 -35.3 35.3 -35.2 35.2 -23.8 -23.8z"/>
    </g>
  </svg>
);

// Icon主题配置
const USE_ANTD_ICON = false;

const McpServerIcon: React.FC<Partial<CustomIconComponentProps>> = (props) => {
  if (USE_ANTD_ICON) {
    return <ToolOutlined {...props} />;
  }
  
  // 使用原始自定义SVG Icon
  return <Icon component={McpSvg} {...props} />;
};

export default McpServerIcon;
