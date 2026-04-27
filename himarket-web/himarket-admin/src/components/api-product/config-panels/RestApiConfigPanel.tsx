import { Card } from 'antd';

import type { ApiProductConfig } from '@/types/api-product';

import { SwaggerUIWrapper } from '../SwaggerUIWrapper';

interface RestApiConfigPanelProps {
  apiConfig: ApiProductConfig;
}

export function RestApiConfigPanel({ apiConfig }: RestApiConfigPanelProps) {
  return (
    <Card title="配置详情">
      <div>
        <h4 className="text-base font-medium mb-4">REST API接口文档</h4>
        <SwaggerUIWrapper apiSpec={apiConfig.spec} />
      </div>
    </Card>
  );
}
