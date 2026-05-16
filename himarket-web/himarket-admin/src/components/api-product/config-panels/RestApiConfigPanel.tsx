import { Card } from 'antd';

import type { ApiProductConfig } from '@/types/api-product';

import { RestApiDocsViewer } from '../RestApiDocsViewer';

interface RestApiConfigPanelProps {
  apiConfig: ApiProductConfig;
}

export function RestApiConfigPanel({ apiConfig }: RestApiConfigPanelProps) {
  return (
    <Card title="配置详情">
      <RestApiDocsViewer apiSpec={apiConfig.spec} />
    </Card>
  );
}
