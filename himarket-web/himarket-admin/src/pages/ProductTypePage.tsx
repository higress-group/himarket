import { PlusOutlined, ImportOutlined, DownOutlined } from '@ant-design/icons';
import { Button, Modal, message, Dropdown } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';

import ImportProductsModal from '@/components/api-product/ImportProductsModal';
import ProductTable from '@/components/api-product/ProductTable';
import type { ProductTableRef } from '@/components/api-product/ProductTable';
import ImportMcpModal from '@/components/mcp-vendor/ImportMcpModal';
import { nacosApi, workerApi, skillApi } from '@/lib/api';
import type { NacosInstance } from '@/types/gateway';

import type { MenuProps } from 'antd';

type ProductType = 'MODEL_API' | 'MCP_SERVER' | 'AGENT_SKILL' | 'WORKER' | 'AGENT_API' | 'REST_API';
type StandardImportSource = 'GATEWAY' | 'NACOS';
type ImportMenuItems = NonNullable<MenuProps['items']>;

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

function getImportMenuItems(productType: ProductType): ImportMenuItems {
  switch (productType) {
    case 'MCP_SERVER':
      return [
        { key: 'GATEWAY', label: '从网关导入' },
        { key: 'NACOS', label: '从 Nacos 导入' },
        { type: 'divider' },
        { key: 'MARKET', label: '从第三方市场导入' },
      ];
    case 'AGENT_API':
      return [
        { key: 'GATEWAY', label: '从网关导入' },
        { key: 'NACOS', label: '从 Nacos 导入' },
      ];
    case 'MODEL_API':
    case 'REST_API':
      return [{ key: 'GATEWAY', label: '从网关导入' }];
    default:
      return [];
  }
}

const ProductTypePage: React.FC<ProductTypePageProps> = ({ productType }) => {
  const navigate = useNavigate();
  const tableRef = useRef<ProductTableRef>(null);
  const [importLoading, setImportLoading] = useState(false);
  const [defaultNacos, setDefaultNacos] = useState<NacosInstance | null>(null);
  const [importModalVisible, setImportModalVisible] = useState(false);
  const [importSource, setImportSource] = useState<StandardImportSource | null>(null);
  const [mcpImportOpen, setMcpImportOpen] = useState(false);

  const showNacosImport = productType === 'AGENT_SKILL' || productType === 'WORKER';
  const showImportMenu = productType !== 'AGENT_SKILL' && productType !== 'WORKER';
  const isMcpServer = productType === 'MCP_SERVER';
  const importMenuItems = getImportMenuItems(productType);

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
      message.warning('请先配置默认 Nacos 实例');
      return;
    }

    const isWorker = productType === 'WORKER';
    const typeName = isWorker ? 'Workers' : 'Skills';

    Modal.confirm({
      cancelText: '取消',
      content: `将从默认 Nacos 实例 "${defaultNacos.nacosName || defaultNacos.nacosId}" 导入所有 ${typeName}，是否继续？`,
      okText: '确认导入',
      onOk: async () => {
        setImportLoading(true);
        try {
          const namespace = defaultNacos.defaultNamespace || 'public';
          const res = isWorker
            ? await workerApi.importFromNacos(defaultNacos.nacosId, namespace)
            : await skillApi.importFromNacos(defaultNacos.nacosId, namespace);

          const importResult = res.data;
          if (importResult.successCount > 0) {
            message.success(`成功导入 ${importResult.successCount} 个 ${typeName}`);
            tableRef.current?.refresh();
          } else {
            message.info(`没有新的 ${typeName} 需要导入`);
          }
        } catch (error: unknown) {
          message.error(
            (error as { response?: { data?: { message?: string } } }).response?.data?.message ||
              `导入 ${typeName} 失败`,
          );
        } finally {
          setImportLoading(false);
        }
      },
      title: `从 Nacos 导入 ${typeName}`,
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
      {/* Page Header */}
      <div className="flex items-start justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">API Products</h1>
          <p className="text-gray-500 mt-2">管理和配置您的所有 API 产品</p>
        </div>
        <div className="flex items-center gap-3">
          {!isMcpServer && showNacosImport && (
            <Button
              disabled={!defaultNacos}
              icon={<ImportOutlined />}
              loading={importLoading}
              onClick={handleImportFromNacos}
            >
              从 Nacos 导入
            </Button>
          )}
          {showImportMenu && importMenuItems.length > 0 && (
            <Dropdown
              menu={{ items: importMenuItems, onClick: handleImportMenuClick }}
              trigger={['click']}
            >
              <Button icon={<ImportOutlined />}>
                导入
                <DownOutlined />
              </Button>
            </Dropdown>
          )}
          <Button
            icon={<PlusOutlined />}
            onClick={() => tableRef.current?.handleCreate()}
            type="primary"
          >
            创建
          </Button>
        </div>
      </div>

      {/* Product Type Tabs */}
      <div className="border-b border-gray-200">
        <nav className="-mb-px flex space-x-8">
          {PRODUCT_TYPES.map((type) => (
            <button
              className={`whitespace-nowrap pb-3 px-1 border-b-2 font-medium text-sm transition-colors ${
                productType === type.key
                  ? 'border-indigo-500 text-indigo-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
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
