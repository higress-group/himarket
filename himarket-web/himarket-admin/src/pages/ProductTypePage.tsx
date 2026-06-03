import { PlusOutlined, ImportOutlined, DownOutlined } from '@ant-design/icons';
import { Button, Modal, message, Dropdown } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import ImportProductsModal from '@/components/api-product/ImportProductsModal';
import ProductTable from '@/components/api-product/ProductTable';
import type { ProductTableRef } from '@/components/api-product/ProductTable';
import { AdminPageHeader } from '@/components/common';
import ImportMcpModal from '@/components/mcp-vendor/ImportMcpModal';
import { useLocale } from '@/contexts/LocaleContext';
import { nacosApi, workerApi, skillApi } from '@/lib/api';
import type { NacosInstance } from '@/types/gateway';

import type { MenuProps } from 'antd';

type ProductType = 'MODEL_API' | 'MCP_SERVER' | 'AGENT_SKILL' | 'WORKER' | 'AGENT_API' | 'REST_API';
type StandardImportSource = 'GATEWAY' | 'NACOS';
type ImportMenuItems = NonNullable<MenuProps['items']>;
type ImportMenuLabels = {
  fromGateway: string;
  fromNacos: string;
  fromThirdPartyMarket: string;
};

const PRODUCT_TYPES = [
  { key: 'MODEL_API' as const, label: 'Model API', path: 'model-api' },
  { key: 'MCP_SERVER' as const, label: 'MCP Server', path: 'mcp-server' },
  { key: 'AGENT_API' as const, label: 'Agent API', path: 'agent-api' },
  { key: 'AGENT_SKILL' as const, label: 'Agent Skill', path: 'agent-skill' },
  { key: 'WORKER' as const, label: 'Worker', path: 'worker' },
  { key: 'REST_API' as const, label: 'REST API', path: 'rest-api' },
];

interface ProductTypePageProps {
  productType: ProductType;
}

function getImportMenuItems(productType: ProductType, labels: ImportMenuLabels): ImportMenuItems {
  switch (productType) {
    case 'MCP_SERVER':
      return [
        { key: 'GATEWAY', label: labels.fromGateway },
        { key: 'NACOS', label: labels.fromNacos },
        { type: 'divider' },
        { key: 'MARKET', label: labels.fromThirdPartyMarket },
      ];
    case 'AGENT_API':
      return [
        { key: 'GATEWAY', label: labels.fromGateway },
        { key: 'NACOS', label: labels.fromNacos },
      ];
    case 'MODEL_API':
    case 'REST_API':
      return [{ key: 'GATEWAY', label: labels.fromGateway }];
    default:
      return [];
  }
}

const ProductTypePage: React.FC<ProductTypePageProps> = ({ productType }) => {
  const navigate = useNavigate();
  const { t } = useLocale();
  const tableRef = useRef<ProductTableRef>(null);
  const [importLoading, setImportLoading] = useState(false);
  const [defaultNacos, setDefaultNacos] = useState<NacosInstance | null>(null);
  const [importModalVisible, setImportModalVisible] = useState(false);
  const [importSource, setImportSource] = useState<StandardImportSource | null>(null);
  const [mcpImportOpen, setMcpImportOpen] = useState(false);

  const showNacosImport = productType === 'AGENT_SKILL' || productType === 'WORKER';
  const showImportMenu = productType !== 'AGENT_SKILL' && productType !== 'WORKER';
  const isMcpServer = productType === 'MCP_SERVER';
  const importMenuItems = getImportMenuItems(productType, {
    fromGateway: t('product.import.fromGateway'),
    fromNacos: t('product.import.fromNacos'),
    fromThirdPartyMarket: t('product.import.fromThirdPartyMarket'),
  });

  // Fetch default Nacos instance for import feature
  useEffect(() => {
    if (showNacosImport) {
      nacosApi
        .getDefaultNacos()
        .then((res: unknown) => {
          setDefaultNacos((res as { data: NacosInstance | null }).data ?? null);
        })
        .catch(() => {
          setDefaultNacos(null);
        });
    }
  }, [productType, showNacosImport]);

  const handleImportFromNacos = async () => {
    if (!defaultNacos) {
      message.warning(t('product.import.defaultNacosMissing'));
      return;
    }

    const isWorker = productType === 'WORKER';
    const typeName = isWorker ? 'Workers' : 'Skills';

    Modal.confirm({
      cancelText: t('common.cancel'),
      content: t('product.import.confirmDefaultNacosContent', {
        name: defaultNacos.nacosName || defaultNacos.nacosId,
        type: typeName,
      }),
      okText: t('product.import.confirmImport'),
      onOk: async () => {
        setImportLoading(true);
        try {
          const namespace = defaultNacos.defaultNamespace || 'public';
          const res = isWorker
            ? await workerApi.importFromNacos(defaultNacos.nacosId, namespace)
            : await skillApi.importFromNacos(defaultNacos.nacosId, namespace);

          const importResult = res.data;
          if (importResult.successCount > 0) {
            message.success(
              t('product.import.successTypedCount', {
                count: importResult.successCount,
                type: typeName,
              }),
            );
            tableRef.current?.refresh();
          } else {
            message.info(t('product.import.noNewTypedItems', { type: typeName }));
          }
        } catch (error: unknown) {
          message.error(
            (error as { response?: { data?: { message?: string } } }).response?.data?.message ||
              t('product.import.typedFailed', { type: typeName }),
          );
        } finally {
          setImportLoading(false);
        }
      },
      title: t('product.import.nacosSourceTitle', { type: typeName }),
    });
  };

  const handleImportMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'MARKET') {
      setMcpImportOpen(true);
      return;
    }

    setImportSource(key as StandardImportSource);
    setImportModalVisible(true);
  };

  return (
    <div className="space-y-6">
      <AdminPageHeader
        actions={
          <>
            {!isMcpServer && showNacosImport && (
              <Button
                disabled={!defaultNacos}
                icon={<ImportOutlined />}
                loading={importLoading}
                onClick={handleImportFromNacos}
              >
                {t('action.importFromNacos')}
              </Button>
            )}
            {showImportMenu && importMenuItems.length > 0 && (
              <Dropdown
                menu={{ items: importMenuItems, onClick: handleImportMenuClick }}
                trigger={['click']}
              >
                <Button icon={<ImportOutlined />}>
                  {t('action.import')}
                  <DownOutlined />
                </Button>
              </Dropdown>
            )}
            <Button
              icon={<PlusOutlined />}
              onClick={() => tableRef.current?.handleCreate()}
              type="primary"
            >
              {t('action.create')}
            </Button>
          </>
        }
        description={t('page.apiProducts.description')}
        title={t('page.apiProducts.title')}
      />

      <div>
        <nav className="flex flex-wrap gap-1 rounded-lg border border-gray-200 bg-gray-50 p-1">
          {PRODUCT_TYPES.map((type) => (
            <button
              className={`h-9 whitespace-nowrap rounded-md px-3 text-sm font-medium transition-colors ${
                productType === type.key
                  ? 'bg-white text-gray-950 shadow-sm'
                  : 'text-gray-500 hover:bg-white/70 hover:text-gray-800'
              }`}
              key={type.key}
              onClick={() => navigate(`/api-products/${type.path}`)}
            >
              {type.label}
            </button>
          ))}
        </nav>
      </div>

      <ProductTable productType={productType} ref={tableRef} />

      {/* MCP third-party market import modal */}
      {isMcpServer && (
        <ImportMcpModal
          onClose={() => setMcpImportOpen(false)}
          onImportSuccess={() => tableRef.current?.refresh()}
          open={mcpImportOpen}
        />
      )}

      <ImportProductsModal
        importSource={importSource ?? undefined}
        onCancel={() => {
          setImportModalVisible(false);
          setImportSource(null);
        }}
        onSuccess={() => {
          setImportModalVisible(false);
          setImportSource(null);
          tableRef.current?.refresh();
        }}
        productType={productType as 'REST_API' | 'MCP_SERVER' | 'AGENT_API' | 'MODEL_API'}
        visible={importModalVisible}
      />
    </div>
  );
};

export default ProductTypePage;
