import { useEffect, useRef, useState } from 'react';
import { Button, Modal, message } from 'antd';
import { PlusOutlined, ImportOutlined } from '@ant-design/icons';
import { nacosApi, workerApi, skillApi } from '@/lib/api';
import ProductTable from '@/components/api-product/ProductTable';
import type { ProductTableRef } from '@/components/api-product/ProductTable';
import ImportMcpModal from '@/components/mcp-vendor/ImportMcpModal';
import McpCreationSelector from '@/components/mcp-creation/McpCreationSelector';
import McpStepWizard from '@/components/mcp-creation/McpStepWizard';
import type { CreationMode } from '@/components/mcp-creation/types';
import ImportProductsModal from '@/components/api-product/ImportProductsModal';

// 产品类型标题映射
const TYPE_TITLES: Record<string, string> = {
  MODEL_API: 'Model API Products',
  MCP_SERVER: 'MCP Server Products',
  AGENT_SKILL: 'Agent Skill Products',
  WORKER: 'Worker Products',
  AGENT_API: 'Agent API Products',
  REST_API: 'REST API Products',
};

const TYPE_SUBTITLES: Record<string, string> = {
  MODEL_API: '管理和配置您的 Model API 产品',
  MCP_SERVER: '管理和配置您的 MCP Server 产品',
  AGENT_SKILL: '管理和配置您的 Agent Skill 产品',
  WORKER: '管理和配置您的 Worker 产品',
  AGENT_API: '管理和配置您的 Agent API 产品',
  REST_API: '管理和配置您的 REST API 产品',
};

interface ProductTypePageProps {
  productType: 'MODEL_API' | 'MCP_SERVER' | 'AGENT_SKILL' | 'WORKER' | 'AGENT_API' | 'REST_API';
}

const ProductTypePage: React.FC<ProductTypePageProps> = ({ productType }) => {
  const tableRef = useRef<ProductTableRef>(null);
  const [importLoading, setImportLoading] = useState(false);
  const [defaultNacos, setDefaultNacos] = useState<any>(null);
  const [importModalVisible, setImportModalVisible] = useState(false);

  const showNacosImport = productType === 'AGENT_SKILL' || productType === 'WORKER';
  const isMcpServer = productType === 'MCP_SERVER';
  const [mcpImportOpen, setMcpImportOpen] = useState(false);

  // Unified MCP creation flow state
  const [selectorVisible, setSelectorVisible] = useState(false);
  const [wizardVisible, setWizardVisible] = useState(false);
  const [wizardMode, setWizardMode] = useState<Exclude<CreationMode, 'vendor'>>('manual');

  // Fetch default Nacos instance for import feature
  useEffect(() => {
    if (showNacosImport) {
      nacosApi.getDefaultNacos().then((res: any) => {
        setDefaultNacos(res.data);
      }).catch(() => {
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
      title: `从 Nacos 导入 ${typeName}`,
      content: `将从默认 Nacos 实例 "${defaultNacos.nacosName || defaultNacos.nacosId}" 导入所有 ${typeName}，是否继续？`,
      okText: '确认导入',
      cancelText: '取消',
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
        } catch (error: any) {
          message.error(error.response?.data?.message || `导入 ${typeName} 失败`);
        } finally {
          setImportLoading(false);
        }
      },
    });
  };

  /** Handle creation mode selection from McpCreationSelector */
  const handleMcpModeSelect = (mode: CreationMode) => {
    setSelectorVisible(false);
    if (mode === 'vendor') {
      // Open existing ImportMcpModal for third-party market import
      setMcpImportOpen(true);
    } else {
      // Open McpStepWizard for manual / gateway / nacos modes
      setWizardMode(mode);
      setWizardVisible(true);
    }
  };

  /** Handle wizard success — refresh product list */
  const handleWizardSuccess = () => {
    setWizardVisible(false);
    tableRef.current?.refresh();
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{TYPE_TITLES[productType]}</h1>
          <p className="text-gray-500 mt-2">{TYPE_SUBTITLES[productType]}</p>
        </div>
        <div className="flex items-center gap-3">
          {/* MCP_SERVER: single unified "创建 MCP" button */}
          {isMcpServer && (
            <Button
              onClick={() => setSelectorVisible(true)}
              type="primary"
              icon={<PlusOutlined />}
            >
              创建 MCP
            </Button>
          )}
          {/* Non-MCP types: keep original buttons */}
          {!isMcpServer && showNacosImport && (
            <Button
              onClick={handleImportFromNacos}
              loading={importLoading}
              disabled={!defaultNacos}
              icon={<ImportOutlined />}
            >
              从 Nacos 导入
            </Button>
          )}
          <Button
            onClick={() => setImportModalVisible(true)}
            icon={<ImportOutlined />}
          >
            批量导入
          </Button>
          {!isMcpServer && (
            <Button
              onClick={() => tableRef.current?.handleCreate()}
              type="primary"
              icon={<PlusOutlined />}
            >
              创建 API Product
            </Button>
          )}
        </div>
      </div>

      <ProductTable productType={productType} ref={tableRef} />

      {/* MCP creation flow modals */}
      {isMcpServer && (
        <>
          <McpCreationSelector
            visible={selectorVisible}
            onCancel={() => setSelectorVisible(false)}
            onSelect={handleMcpModeSelect}
          />
          <McpStepWizard
            visible={wizardVisible}
            mode={wizardMode}
            onCancel={() => setWizardVisible(false)}
            onSuccess={handleWizardSuccess}
          />
          <ImportMcpModal
            open={mcpImportOpen}
            onClose={() => setMcpImportOpen(false)}
            onImportSuccess={() => tableRef.current?.refresh()}
          />
        </>
      )}

      <ImportProductsModal
        visible={importModalVisible}
        onCancel={() => setImportModalVisible(false)}
        onSuccess={() => {
          setImportModalVisible(false);
          tableRef.current?.refresh();
        }}
        productType={productType}
      />
    </div>
  );
};

export default ProductTypePage;
