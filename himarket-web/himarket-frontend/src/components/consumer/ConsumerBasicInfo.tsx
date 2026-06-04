import { Descriptions } from 'antd';
import { useTranslation } from 'react-i18next';

import type { Consumer } from '../../types/consumer';

interface ConsumerBasicInfoProps {
  consumer: Consumer;
}

export function ConsumerBasicInfo({ consumer }: ConsumerBasicInfoProps) {
  const { t } = useTranslation('consumer');

  return (
    <div className="bg-white/60 backdrop-blur-sm rounded-[10px] p-6 border border-white/60 shadow-sm">
      <h3 className="text-base font-semibold text-gray-900 mb-4">{t('detail.basicInfo')}</h3>
      <Descriptions column={2} size="middle">
        <Descriptions.Item label={t('detail.name')}>{consumer.name}</Descriptions.Item>
        <Descriptions.Item label={t('detail.description')}>
          {consumer.description || '-'}
        </Descriptions.Item>
      </Descriptions>
    </div>
  );
}
