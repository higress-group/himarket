import { InboxOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';

interface EmptyStateProps {
  className?: string;
  description?: string;
  productType?: string;
}

export function EmptyState({ className = '', description, productType }: EmptyStateProps) {
  const { t } = useTranslation('emptyState');

  if (description) {
    return (
      <div className={`flex flex-col items-center justify-center py-16 ${className}`}>
        <div className="mb-2 flex h-10 w-10 items-center justify-center rounded-full bg-gray-100">
          <InboxOutlined className="text-base text-gray-400" />
        </div>
        <div className="text-sm text-gray-500">{description}</div>
      </div>
    );
  }

  const typeKey = productType
    ? ['MODEL_API', 'MCP_SERVER', 'AGENT_API', 'REST_API', 'AGENT_SKILL', 'WORKER'].includes(
        productType,
      )
      ? productType
      : null
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
