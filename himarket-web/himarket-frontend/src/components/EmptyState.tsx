import { InboxOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';

interface EmptyStateProps {
  productType: string;
}

export function EmptyState({ productType }: EmptyStateProps) {
  const { t } = useTranslation('emptyState');

  const typeKey = [
    'MODEL_API',
    'MCP_SERVER',
    'AGENT_API',
    'REST_API',
    'AGENT_SKILL',
    'WORKER',
  ].includes(productType)
    ? productType
    : null;

  const title = typeKey ? t(`types.${typeKey}.title`) : t('defaultTitle');
  const desc = typeKey ? t(`types.${typeKey}.desc`) : t('defaultDesc');

  return (
    <div className="col-span-full flex flex-col items-center justify-center py-24 text-center">
      <InboxOutlined className="text-5xl text-gray-300 mb-4" />
      <h3 className="text-lg font-medium text-gray-600 mb-2">{title}</h3>
      <p className="text-gray-400 max-w-md mb-3">{desc}</p>
    </div>
  );
}
