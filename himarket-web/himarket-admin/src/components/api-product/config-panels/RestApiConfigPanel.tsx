import { Card } from 'antd';

import { useLocale } from '@/contexts/LocaleContext';
import type { ApiProductConfig } from '@/types/api-product';

import { RestApiDocsViewer } from '../RestApiDocsViewer';

interface RestApiConfigPanelProps {
  apiConfig: ApiProductConfig;
}

export function RestApiConfigPanel({ apiConfig }: RestApiConfigPanelProps) {
  const { t } = useLocale();

  return (
    <Card title={t('product.linkApi.configDetail')}>
      <RestApiDocsViewer apiSpec={apiConfig.spec} />
    </Card>
  );
}
