import {
  PlusOutlined,
  DeleteOutlined,
  ExclamationCircleOutlined,
  DownOutlined,
  RocketOutlined,
  FileTextOutlined,
  ImportOutlined,
  LinkOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { Card, Button, Modal, message, Space, Dropdown } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';

import { useLocale } from '@/contexts/LocaleContext';
import { apiProductApi, apiDefinitionApi } from '@/lib/api';
import type {
  ApiProduct,
  ApiProductConfig,
  LinkedService,
  ApiDefinition,
} from '@/types/api-product';

import {
  McpServerConfigPanel,
  AgentApiConfigPanel,
  ModelApiConfigPanel,
  RestApiConfigPanel,
} from './config-panels';
import { useMcpConnectionConfig } from './hooks/useMcpConnectionConfig';
import { useParsedMcpTools } from './hooks/useParsedMcpTools';
import { LinkApiModal } from './link-api-modal/LinkApiModal';
import { McpJsonImportModal } from './McpJsonImportModal';
import { McpOasCreateModal } from './McpOasCreateModal';
import { McpQuickCreateModal } from './McpQuickCreateModal';

interface ApiProductLinkApiProps {
  apiProduct: ApiProduct;
  linkedService: LinkedService | null;
  onLinkedServiceUpdate: (linkedService: LinkedService | null) => void;
  handleRefresh: () => void;
}

export function ApiProductLinkApi({
  apiProduct,
  handleRefresh,
  linkedService,
  onLinkedServiceUpdate,
}: ApiProductLinkApiProps) {
  const { t } = useLocale();

  // 自定义创建弹窗控制
  const [quickCreateVisible, setQuickCreateVisible] = useState(false);
  const [oasCreateVisible, setOasCreateVisible] = useState(false);
  const [jsonImportVisible, setJsonImportVisible] = useState(false);

  // ApiDefinition 数据（自定义创建时）
  const [apiDefinition, setApiDefinition] = useState<ApiDefinition | null>(null);

  // 域名选择索引
  const [selectedDomainIndex, setSelectedDomainIndex] = useState(0);
  const [selectedAgentDomainIndex, setSelectedAgentDomainIndex] = useState(0);
  const [selectedModelDomainIndex, setSelectedModelDomainIndex] = useState(0);

  // 同步配置加载状态
  const [syncLoading, setSyncLoading] = useState(false);
  const lastApiDefinitionIdRef = useRef('');

  const parsedTools = useParsedMcpTools(apiProduct);
  const connConfig = useMcpConnectionConfig(apiProduct, linkedService, selectedDomainIndex);

  // 加载 ApiDefinition 详情（当 sourceType 为 API_DEFINITION 时）
  useEffect(() => {
    if (linkedService?.sourceType !== 'API_DEFINITION' || !linkedService.apiDefinitionId) {
      lastApiDefinitionIdRef.current = '';
      setApiDefinition(null);
      return;
    }

    if (lastApiDefinitionIdRef.current === linkedService.apiDefinitionId) {
      return;
    }
    const apiDefinitionId = linkedService.apiDefinitionId;
    lastApiDefinitionIdRef.current = apiDefinitionId;

    apiDefinitionApi
      .getApiDefinition(apiDefinitionId)
      .then((res: unknown) => {
        if (lastApiDefinitionIdRef.current !== apiDefinitionId) {
          return;
        }

        const data = (res as { data?: ApiDefinition }).data;
        if (data) setApiDefinition(data);
      })
      .catch(() => {
        if (lastApiDefinitionIdRef.current !== apiDefinitionId) {
          return;
        }

        setApiDefinition(null);
      });
  }, [linkedService?.sourceType, linkedService?.apiDefinitionId]);

  // 产品切换重置域名索引
  useEffect(() => {
    setSelectedDomainIndex(0);
    setSelectedAgentDomainIndex(0);
    setSelectedModelDomainIndex(0);
  }, [apiProduct.productId]);

  // 删除关联
  const handleDelete = () => {
    if (!linkedService) return;

    Modal.confirm({
      cancelText: t('common.cancel'),
      content: t('product.linkApi.confirmDelete'),
      icon: <ExclamationCircleOutlined />,
      okText: t('product.linkApi.removeLink'),
      okType: 'danger',
      onOk() {
        return apiProductApi
          .deleteApiProductRef(apiProduct.productId)
          .then(() => {
            message.success(t('product.linkApi.deleteSuccess'));
            onLinkedServiceUpdate(null);
            handleRefresh();
          })
          .catch(() => {
            message.error(t('product.linkApi.deleteFailed'));
          });
      },
      title: t('product.linkApi.confirmDeleteTitle'),
    });
  };

  // 同步配置
  const handleSyncConfig = () => {
    Modal.confirm({
      cancelText: t('common.cancel'),
      content: t('product.linkApi.syncConfirm', { name: apiProduct.name }),
      okText: t('product.linkApi.syncOk'),
      onOk: async () => {
        setSyncLoading(true);
        try {
          await apiProductApi.reloadProductConfig(apiProduct.productId);
          message.success(t('product.linkApi.syncSuccess'));
          handleRefresh();
        } catch {
          message.error(t('product.linkApi.syncFailed'));
        } finally {
          setSyncLoading(false);
        }
      },
      title: t('product.linkApi.confirmSync'),
    });
  };

  // Link Modal 控制
  const [isModalVisible, setIsModalVisible] = useState(false);

  const getServiceInfo = () => {
    if (!linkedService) return null;

    let apiName = '';
    let apiType = '';
    let sourceInfo = '';
    let gatewayInfo = '';

    if (apiProduct.type === 'REST_API') {
      if (
        linkedService.sourceType === 'GATEWAY' &&
        linkedService.apigRefConfig &&
        'apiName' in linkedService.apigRefConfig
      ) {
        apiName = linkedService.apigRefConfig.apiName || t('common.unnamed');
        apiType = 'REST API';
        sourceInfo = t('product.linkApi.apiGateway');
        gatewayInfo = linkedService.gatewayId || t('common.unknown');
      }
    } else if (apiProduct.type === 'MCP_SERVER') {
      apiType = 'MCP Server';
      if (
        linkedService.sourceType === 'GATEWAY' &&
        linkedService.apigRefConfig &&
        'mcpServerName' in linkedService.apigRefConfig
      ) {
        apiName = linkedService.apigRefConfig.mcpServerName || t('common.unnamed');
        sourceInfo = t('product.linkApi.apiGateway');
        gatewayInfo = linkedService.gatewayId || t('common.unknown');
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.higressRefConfig) {
        apiName = linkedService.higressRefConfig.mcpServerName || t('common.unnamed');
        sourceInfo = t('product.linkApi.higressGateway');
        gatewayInfo = linkedService.gatewayId || t('common.unknown');
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.adpAIGatewayRefConfig) {
        if ('modelApiName' in linkedService.adpAIGatewayRefConfig) {
          apiName = linkedService.adpAIGatewayRefConfig.modelApiName || t('common.unnamed');
          sourceInfo = t('product.linkApi.privateAiGateway');
          gatewayInfo = linkedService.gatewayId || t('common.unknown');
        } else {
          apiName = linkedService.adpAIGatewayRefConfig.mcpServerName || t('common.unnamed');
          sourceInfo = t('product.linkApi.privateAiGateway');
          gatewayInfo = linkedService.gatewayId || t('common.unknown');
        }
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.apsaraGatewayRefConfig) {
        if ('mcpServerName' in linkedService.apsaraGatewayRefConfig) {
          apiName = linkedService.apsaraGatewayRefConfig.mcpServerName || t('common.unnamed');
        } else {
          apiName = linkedService.apsaraGatewayRefConfig.modelApiName || t('common.unnamed');
        }
        sourceInfo = t('product.linkApi.apsaraGateway');
        gatewayInfo = linkedService.gatewayId || t('common.unknown');
      } else if (
        linkedService.sourceType === 'NACOS' &&
        linkedService.nacosRefConfig &&
        'mcpServerName' in linkedService.nacosRefConfig
      ) {
        apiName = linkedService.nacosRefConfig.mcpServerName || t('common.unnamed');
        sourceInfo = t('product.linkApi.nacosDiscovery');
        gatewayInfo = linkedService.nacosId || t('common.unknown');
      } else if (linkedService.sourceType === 'API_DEFINITION') {
        apiName =
          apiProduct.mcpConfig?.mcpServerName ||
          apiDefinition?.name ||
          apiProduct.name ||
          t('common.unnamed');
        sourceInfo = t('product.linkApi.custom');
        gatewayInfo = '-';
      } else if (linkedService.sourceType === 'CUSTOM') {
        apiName = apiProduct.mcpConfig?.mcpServerName || apiProduct.name || t('common.unnamed');
        sourceInfo = t('product.linkApi.customConfig');
        gatewayInfo = '-';
      }
    } else if (apiProduct.type === 'AGENT_API') {
      apiType = 'Agent API';
      if (
        linkedService.sourceType === 'GATEWAY' &&
        linkedService.apigRefConfig &&
        'agentApiName' in linkedService.apigRefConfig
      ) {
        apiName = linkedService.apigRefConfig.agentApiName || t('common.unnamed');
        sourceInfo = t('product.linkApi.apiGateway');
        gatewayInfo = linkedService.gatewayId || t('common.unknown');
      } else if (
        linkedService.sourceType === 'NACOS' &&
        linkedService.nacosRefConfig &&
        'agentName' in linkedService.nacosRefConfig
      ) {
        apiName = linkedService.nacosRefConfig.agentName || t('common.unnamed');
        sourceInfo = 'Nacos Agent Registry';
        gatewayInfo = linkedService.nacosId || t('common.unknown');
      }
    } else if (apiProduct.type === 'MODEL_API') {
      apiType = 'Model API';
      if (
        linkedService.sourceType === 'GATEWAY' &&
        linkedService.apigRefConfig &&
        'modelApiName' in linkedService.apigRefConfig
      ) {
        apiName = linkedService.apigRefConfig.modelApiName || t('common.unnamed');
        sourceInfo = t('product.linkApi.apiGateway');
        gatewayInfo = linkedService.gatewayId || t('common.unknown');
      } else if (
        linkedService.sourceType === 'GATEWAY' &&
        linkedService.higressRefConfig &&
        'modelRouteName' in linkedService.higressRefConfig
      ) {
        apiName = linkedService.higressRefConfig.modelRouteName || t('common.unnamed');
        sourceInfo = t('product.linkApi.higressGateway');
        gatewayInfo = linkedService.gatewayId || t('common.unknown');
      } else if (
        linkedService.sourceType === 'GATEWAY' &&
        linkedService.adpAIGatewayRefConfig &&
        'modelApiName' in linkedService.adpAIGatewayRefConfig
      ) {
        apiName = linkedService.adpAIGatewayRefConfig.modelApiName || t('common.unnamed');
        sourceInfo = t('product.linkApi.privateAiGateway');
        gatewayInfo = linkedService.gatewayId || t('common.unknown');
      } else if (
        linkedService.sourceType === 'GATEWAY' &&
        linkedService.apsaraGatewayRefConfig &&
        'modelApiName' in linkedService.apsaraGatewayRefConfig
      ) {
        apiName = linkedService.apsaraGatewayRefConfig.modelApiName || t('common.unnamed');
        sourceInfo = t('product.linkApi.apsaraGateway');
        gatewayInfo = linkedService.gatewayId || t('common.unknown');
      }
    }

    return { apiName, apiType, gatewayInfo, sourceInfo };
  };

  const renderLinkInfo = () => {
    const serviceInfo = getServiceInfo();
    const isMcp = apiProduct.type === 'MCP_SERVER';

    if (!linkedService || !serviceInfo) {
      return (
        <Card className="mb-6">
          <div className="text-center py-8">
            <div className="text-gray-500 mb-4">{t('product.linkApi.noLinkedApi')}</div>
            {isMcp ? (
              <Space size="middle">
                <Button
                  icon={<LinkOutlined />}
                  onClick={() => setIsModalVisible(true)}
                  type="primary"
                >
                  {t('product.linkApi.linkApi')}
                </Button>
                <Dropdown
                  menu={{
                    items: [
                      {
                        icon: <RocketOutlined />,
                        key: 'quick',
                        label: t('product.linkApi.standardCreate'),
                        onClick: () => setQuickCreateVisible(true),
                      },
                      {
                        icon: <ImportOutlined />,
                        key: 'json',
                        label: t('product.linkApi.uploadFromJson'),
                        onClick: () => setJsonImportVisible(true),
                      },
                      {
                        icon: <FileTextOutlined />,
                        key: 'oas',
                        label: t('product.linkApi.httpToMcp'),
                        onClick: () => setOasCreateVisible(true),
                      },
                    ],
                  }}
                  trigger={['click']}
                >
                  <Button icon={<PlusOutlined />}>
                    {t('product.linkApi.addCustom')} <DownOutlined />
                  </Button>
                </Dropdown>
              </Space>
            ) : (
              <Button
                icon={<PlusOutlined />}
                onClick={() => setIsModalVisible(true)}
                type="primary"
              >
                {t('product.linkApi.linkApi')}
              </Button>
            )}
          </div>
        </Card>
      );
    }

    return (
      <Card
        className="mb-6"
        extra={
          <Space>
            <Button
              icon={<ReloadOutlined spin={syncLoading} />}
              loading={syncLoading}
              onClick={handleSyncConfig}
            >
              {t('product.linkApi.syncConfig')}
            </Button>
            <Button danger icon={<DeleteOutlined />} onClick={handleDelete} type="primary">
              {t('product.linkApi.removeLink')}
            </Button>
          </Space>
        }
        title={t('product.linkApi.linkDetail')}
      >
        <div>
          <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
            <span className="text-xs text-gray-600">{t('common.name')}:</span>
            <span className="col-span-2 text-xs text-gray-900">
              {serviceInfo.apiName || t('common.unnamed')}
            </span>
            <span className="text-xs text-gray-600">{t('common.type')}:</span>
            <span className="col-span-2 text-xs text-gray-900">{serviceInfo.apiType}</span>
          </div>
          <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
            <span className="text-xs text-gray-600">{t('product.linkApi.source')}:</span>
            <span className="col-span-2 text-xs text-gray-900">{serviceInfo.sourceInfo}</span>
            {linkedService?.sourceType !== 'CUSTOM' &&
              linkedService?.sourceType !== 'API_DEFINITION' && (
                <>
                  <span className="text-xs text-gray-600">
                    {linkedService?.sourceType === 'NACOS'
                      ? 'Nacos ID:'
                      : `${t('product.linkApi.gatewayId')}:`}
                  </span>
                  <span className="col-span-2 text-xs text-gray-700">
                    {serviceInfo.gatewayInfo}
                  </span>
                </>
              )}
          </div>
        </div>
      </Card>
    );
  };

  const handleLinkSuccess = useCallback(async () => {
    setIsModalVisible(false);
    try {
      const res = await apiProductApi.getApiProductRef(apiProduct.productId);
      onLinkedServiceUpdate(res.data || null);
    } catch {
      onLinkedServiceUpdate(null);
    }
    handleRefresh();
  }, [apiProduct.productId, handleRefresh, onLinkedServiceUpdate]);

  return (
    <div className="p-6 space-y-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold mb-2">{t('product.linkApi.title')}</h1>
        <p className="text-gray-600">{t('product.linkApi.description')}</p>
      </div>

      {renderLinkInfo()}

      {apiProduct.type === 'MCP_SERVER' && linkedService && apiProduct.mcpConfig && (
        <McpServerConfigPanel
          apiProduct={apiProduct}
          domainOptions={connConfig.domainOptions}
          httpJson={connConfig.httpJson}
          localJson={connConfig.localJson}
          onDomainChange={setSelectedDomainIndex}
          parsedTools={parsedTools}
          selectedDomainIndex={selectedDomainIndex}
          sseJson={connConfig.sseJson}
        />
      )}
      {apiProduct.type === 'AGENT_API' && apiProduct.agentConfig?.agentAPIConfig && (
        <AgentApiConfigPanel
          agentConfig={apiProduct.agentConfig}
          onDomainChange={setSelectedAgentDomainIndex}
          selectedDomainIndex={selectedAgentDomainIndex}
        />
      )}
      {apiProduct.type === 'MODEL_API' && apiProduct.modelConfig?.modelAPIConfig && (
        <ModelApiConfigPanel
          modelConfig={apiProduct.modelConfig}
          onDomainChange={setSelectedModelDomainIndex}
          selectedDomainIndex={selectedModelDomainIndex}
        />
      )}
      {apiProduct.type === 'REST_API' && linkedService && (
        <RestApiConfigPanel apiConfig={apiProduct.apiConfig as ApiProductConfig} />
      )}

      <LinkApiModal
        apiProduct={apiProduct}
        linkedService={linkedService}
        onCancel={() => setIsModalVisible(false)}
        onOk={handleLinkSuccess}
        open={isModalVisible}
      />

      <McpQuickCreateModal
        onCancel={() => setQuickCreateVisible(false)}
        onSuccess={() => {
          setQuickCreateVisible(false);
          handleRefresh();
        }}
        productId={apiProduct.productId}
        visible={quickCreateVisible}
      />

      <McpOasCreateModal
        onCancel={() => setOasCreateVisible(false)}
        onSuccess={() => {
          setOasCreateVisible(false);
          handleRefresh();
        }}
        productId={apiProduct.productId}
        visible={oasCreateVisible}
      />

      <McpJsonImportModal
        onCancel={() => setJsonImportVisible(false)}
        onSuccess={() => {
          setJsonImportVisible(false);
          handleRefresh();
        }}
        productId={apiProduct.productId}
        visible={jsonImportVisible}
      />
    </div>
  );
}
