import {
  CheckCircleFilled,
  ClockCircleFilled,
  ExclamationCircleFilled,
  InfoCircleFilled,
} from '@ant-design/icons';

import type { ReactNode } from 'react';

type StatusTone = 'success' | 'warning' | 'info' | 'neutral';
type StatusIcon = 'check' | 'clock' | 'info' | 'warning';

interface StatusIndicatorProps {
  children: ReactNode;
  className?: string;
  icon?: StatusIcon;
  iconSize?: number;
  tone: StatusTone;
}

const toneClassNames: Record<StatusTone, string> = {
  info: 'text-blue-500',
  neutral: 'text-gray-400',
  success: 'text-green-500',
  warning: 'text-yellow-500',
};

const defaultIcons: Record<StatusTone, StatusIcon> = {
  info: 'info',
  neutral: 'info',
  success: 'check',
  warning: 'warning',
};

const icons: Record<StatusIcon, typeof CheckCircleFilled> = {
  check: CheckCircleFilled,
  clock: ClockCircleFilled,
  info: InfoCircleFilled,
  warning: ExclamationCircleFilled,
};

export function StatusIndicator({
  children,
  className = '',
  icon,
  iconSize = 12,
  tone,
}: StatusIndicatorProps) {
  const Icon = icons[icon || defaultIcons[tone]];

  return (
    <span
      className={`inline-flex shrink-0 items-center gap-1.5 whitespace-nowrap text-xs font-medium text-gray-600 ${className}`}
    >
      <Icon className={toneClassNames[tone]} style={{ fontSize: iconSize }} />
      <span>{children}</span>
    </span>
  );
}
