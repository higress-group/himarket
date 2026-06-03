import { AppstoreOutlined, UnorderedListOutlined } from '@ant-design/icons';
import { Segmented, Tooltip } from 'antd';

import { useLocale } from '@/contexts/LocaleContext';
import type { ViewMode } from '@/hooks/useAdminViewMode';

interface ViewModeToggleProps {
  disabled?: boolean;
  onChange: (value: ViewMode) => void;
  value: ViewMode;
}

export function ViewModeToggle({ disabled, onChange, value }: ViewModeToggleProps) {
  const { t } = useLocale();

  return (
    <Segmented<ViewMode>
      disabled={disabled}
      onChange={onChange}
      options={[
        {
          label: (
            <Tooltip title={t('common.listView')}>
              <span
                aria-label={t('common.listView')}
                className="inline-flex h-6 items-center gap-1 px-1"
              >
                <UnorderedListOutlined />
                <span>{t('common.list')}</span>
              </span>
            </Tooltip>
          ),
          value: 'TABLE',
        },
        {
          label: (
            <Tooltip title={t('common.cardView')}>
              <span
                aria-label={t('common.cardView')}
                className="inline-flex h-6 items-center gap-1 px-1"
              >
                <AppstoreOutlined />
                <span>{t('common.card')}</span>
              </span>
            </Tooltip>
          ),
          value: 'CARD',
        },
      ]}
      value={value}
    />
  );
}
