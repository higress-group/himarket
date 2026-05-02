import {
  PlusOutlined,
  DeleteOutlined,
  ExclamationCircleOutlined,
  DownOutlined,
  RocketOutlined,
  FileTextOutlined,
  ImportOutlined,
  LinkOutlined,
} from '@ant-design/icons';
import { Card, Button, Modal, message, Space, Dropdown } from 'antd';
import { useCallback, useEffect, useState } from 'react';

import { apiProductApi, apiDefinitionApi } from '@/lib/api';
import type {
  ApiProduct,
  ApiProductConfig,
  LinkedService,
  ApiDefinition,
  McpMetaItem,
} from '@/types/api-product';

import {
  McpServerConfigPanel,
  AgentApiConfigPanel,
  ModelApiConfigPanel,
  RestApiConfigPanel,
} from './config-panels';
import ToolsConfigEditorModal from '../mcp/ToolsConfigEditorModal';
import { DeployMcpModal } from './deploy-mcp-modal/DeployMcpModal';
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
  // 沙箱部署弹窗控制
  const [deployModalMcpServerId, setDeployModalMcpServerId] = useState<string | null>(null);

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

  // MCP 数据（不再查询 meta，直接从 apiProduct.mcpConfig 读取）
  const mcpMetaList: McpMetaItem[] = [];
  const parsedTools = useParsedMcpTools(apiProduct, mcpMetaList);
  const connConfig = useMcpConnectionConfig(
    apiProduct,
    linkedService,
    mcpMetaList,
    selectedDomainIndex,
  );

  // 加载 ApiDefinition 详情（当 sourceType 为 API_DEFINITION 时）
  useEffect(() => {
    if (linkedService?.sourceType === 'API_DEFINITION' && linkedService.apiDefinitionId) {
      apiDefinitionApi
        .getApiDefinition(linkedService.apiDefinitionId)
        .then((res: unknown) => {
          const data = (res as { data?: ApiDefinition }).data;
          if (data) setApiDefinition(data);
        })
        .catch(() => {
          setApiDefinition(null);
        });
    } else {
      setApiDefinition(null);
    }
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
      content: '确定要解除与当前API的关联吗？',
      icon: <ExclamationCircleOutlined />,
      onOk() {
        return apiProductApi
          .deleteApiProductRef(apiProduct.productId)
          .then(() => {
            message.success('解除关联成功');
            onLinkedServiceUpdate(null);
            handleRefresh();
          })
          .catch(() => {
            message.error('解除关联失败');
          });
      },
      title: '确认解除关联',
    });
  };

  // Link Modal 控制
  const [isModalVisible, setIsModalVisible] = useState(false);

  // Tools Editor 控制
  const [toolsEditorOpen, setToolsEditorOpen] = useState(false);

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
        apiName = linkedService.apigRefConfig.apiName || '未命名';
        apiType = 'REST API';
        sourceInfo = 'API网关';
        gatewayInfo = linkedService.gatewayId || '未知';
      }
    } else if (apiProduct.type === 'MCP_SERVER') {
      apiType = 'MCP Server';
      if (
        linkedService.sourceType === 'GATEWAY' &&
        linkedService.apigRefConfig &&
        'mcpServerName' in linkedService.apigRefConfig
      ) {
        apiName = linkedService.apigRefConfig.mcpServerName || '未命名';
        sourceInfo = 'AI网关';
        gatewayInfo = linkedService.gatewayId || '未知';
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.higressRefConfig) {
        apiName = linkedService.higressRefConfig.mcpServerName || '未命名';
        sourceInfo = 'Higress网关';
        gatewayInfo = linkedService.gatewayId || '未知';
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.adpAIGatewayRefConfig) {
        if ('modelApiName' in linkedService.adpAIGatewayRefConfig) {
          apiName = linkedService.adpAIGatewayRefConfig.modelApiName || '未命名';
          sourceInfo = '专有云AI网关';
          gatewayInfo = linkedService.gatewayId || '未知';
        } else {
          apiName = linkedService.adpAIGatewayRefConfig.mcpServerName || '未命名';
          sourceInfo = '专有云AI网关';
          gatewayInfo = linkedService.gatewayId || '未知';
        }
      } else if (linkedService.sourceType === 'GATEWAY' && linkedService.apsaraGatewayRefConfig) {
        if ('mcpServerName' in linkedService.apsaraGatewayRefConfig) {
          apiName = linkedService.apsaraGatewayRefConfig.mcpServerName || '未命名';
        } else {
          apiName = linkedService.apsaraGatewayRefConfig.modelApiName || '未命名';
        }
        sourceInfo = '飞天企业版AI网关';
        gatewayInfo = linkedService.gatewayId || '未知';
      } else if (
        linkedService.sourceType === 'NACOS' &&
        linkedService.nacosRefConfig &&
        'mcpServerName' in linkedService.nacosRefConfig
      ) {
        apiName = linkedService.nacosRefConfig.mcpServerName || '未命名';
        sourceInfo = 'Nacos服务发现';
        gatewayInfo = linkedService.nacosId || '未知';
      } else if (linkedService.sourceType === 'API_DEFINITION') {
        apiName = apiDefinition?.name || apiProduct.name || '未命名';
        sourceInfo = '自定义';
        gatewayInfo = '-';
      } else if (linkedService.sourceType === 'CUSTOM') {
        apiName = apiProduct.name || '未命名';
        sourceInfo = '自定义配置';
        gatewayInfo = '-';
      }
    } else if (apiProduct.type === 'AGENT_API') {
      apiType = 'Agent API';
      if (
        linkedService.sourceType === 'GATEWAY' &&
        linkedService.apigRefConfig &&
        'agentApiName' in linkedService.apigRefConfig
      ) {
        apiName = linkedService.apigRefConfig.agentApiName || '未命名';
        sourceInfo = 'AI网关';
        gatewayInfo = linkedService.gatewayId || '未知';
      } else if (
        linkedService.sourceType === 'NACOS' &&
        linkedService.nacosRefConfig &&
        'agentName' in linkedService.nacosRefConfig
      ) {
        apiName = linkedService.nacosRefConfig.agentName || '未命名';
        sourceInfo = 'Nacos Agent Registry';
        gatewayInfo = linkedService.nacosId || '未知';
      }
    } else if (apiProduct.type === 'MODEL_API') {
      apiType = 'Model API';
      if (
        linkedService.sourceType === 'GATEWAY' &&
        linkedService.apigRefConfig &&
        'modelApiName' in linkedService.apigRefConfig
      ) {
        apiName = linkedService.apigRefConfig.modelApiName || '未命名';
        sourceInfo = 'AI网关';
        gatewayInfo = linkedService.gatewayId || '未知';
      } else if (
        linkedService.sourceType === 'GATEWAY' &&
        linkedService.higressRefConfig &&
        'modelRouteName' in linkedService.higressRefConfig
      ) {
        apiName = linkedService.higressRefConfig.modelRouteName || '未命名';
        sourceInfo = 'Higress网关';
        gatewayInfo = linkedService.gatewayId || '未知';
      } else if (
        linkedService.sourceType === 'GATEWAY' &&
        linkedService.adpAIGatewayRefConfig &&
        'modelApiName' in linkedService.adpAIGatewayRefConfig
      ) {
        apiName = linkedService.adpAIGatewayRefConfig.modelApiName || '未命名';
        sourceInfo = '专有云AI网关';
        gatewayInfo = linkedService.gatewayId || '未知';
      } else if (
        linkedService.sourceType === 'GATEWAY' &&
        linkedService.apsaraGatewayRefConfig &&
        'modelApiName' in linkedService.apsaraGatewayRefConfig
      ) {
        apiName = linkedService.apsaraGatewayRefConfig.modelApiName || '未命名';
        sourceInfo = '飞天企业版AI网关';
        gatewayInfo = linkedService.gatewayId || '未知';
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
            <div className="text-gray-500 mb-4">暂未关联任何API</div>
            {isMcp ? (
              <Space size="middle">
                <Button
                  icon={<LinkOutlined />}
                  onClick={() => setIsModalVisible(true)}
                  type="primary"
                >
                  关联API
                </Button>
                <Dropdown
                  menu={{
                    items: [
                      {
                        icon: <RocketOutlined />,
                        key: 'quick',
                        label: '标准创建（推荐）',
                        onClick: () => setQuickCreateVisible(true),
                      },
                      {
                        icon: <ImportOutlined />,
                        key: 'json',
                        label: '从JSON导入',
                        onClick: () => setJsonImportVisible(true),
                      },
                      {
                        icon: <FileTextOutlined />,
                        key: 'oas',
                        label: 'HTTP转MCP',
                        onClick: () => setOasCreateVisible(true),
                      },
                    ],
                  }}
                  trigger={['click']}
                >
                  <Button icon={<PlusOutlined />}>
                    自定义 <DownOutlined />
                  </Button>
                </Dropdown>
              </Space>
            ) : (
              <Button
                icon={<PlusOutlined />}
                onClick={() => setIsModalVisible(true)}
                type="primary"
              >
                关联API
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
          <Button danger icon={<DeleteOutlined />} onClick={handleDelete} type="primary">
            解除关联
          </Button>
        }
        title="关联详情"
      >
        <div>
          <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
            <span className="text-xs text-gray-600">名称:</span>
            <span className="col-span-2 text-xs text-gray-900">
              {serviceInfo.apiName || '未命名'}
            </span>
            <span className="text-xs text-gray-600">类型:</span>
            <span className="col-span-2 text-xs text-gray-900">{serviceInfo.apiType}</span>
          </div>
          <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
            <span className="text-xs text-gray-600">来源:</span>
            <span className="col-span-2 text-xs text-gray-900">{serviceInfo.sourceInfo}</span>
            {linkedService?.sourceType !== 'CUSTOM' &&
              linkedService?.sourceType !== 'API_DEFINITION' && (
                <>
                  <span className="text-xs text-gray-600">
                    {linkedService?.sourceType === 'NACOS' ? 'Nacos ID:' : '网关ID:'}
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
        <h1 className="text-2xl font-bold mb-2">API关联</h1>
        <p className="text-gray-600">管理Product关联的API</p>
      </div>

      {renderLinkInfo()}

      {apiProduct.type === 'MCP_SERVER' &&
        linkedService &&
        (apiProduct.mcpConfig || mcpMetaList.length > 0) && (
          <McpServerConfigPanel
            apiProduct={apiProduct}
            domainOptions={connConfig.domainOptions}
            hotHttpJson={connConfig.hotHttpJson}
            hotSseJson={connConfig.hotSseJson}
            httpJson={connConfig.httpJson}
            localJson={connConfig.localJson}
            mcpMetaList={mcpMetaList}
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

      <DeployMcpModal
        mcpMetaList={mcpMetaList}
        mcpServerId={deployModalMcpServerId}
        onCancel={() => {
          setDeployModalMcpServerId(null);
        }}
        onDeploySuccess={() => {
          setDeployModalMcpServerId(null);
          handleRefresh();
        }}
        productId={apiProduct.productId}
      />

      <ToolsConfigEditorModal
        initialValue={(() => {
          const tc = mcpMetaList[0]?.toolsConfig;
          if (!tc) return '';
          if (typeof tc === 'string') return tc;
          return JSON.stringify(tc, null, 2);
        })()}
        mcpServerId={mcpMetaList[0]?.mcpServerId || ''}
        onCancel={() => setToolsEditorOpen(false)}
        onSave={async () => {
          setToolsEditorOpen(false);
          await handleRefresh();
        }}
        open={toolsEditorOpen}
      />
    </div>
  );
}
