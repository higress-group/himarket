import {
  PlusOutlined,
  DeleteOutlined,
  ExclamationCircleOutlined,
  CopyOutlined,
  CloudUploadOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { Card, Button, Modal, message, Space, Tag } from 'antd';
import { useCallback, useEffect, useState } from 'react';

import { apiProductApi, mcpServerApi } from '@/lib/api';
import { copyToClipboard, formatDateTime } from '@/lib/utils';
import type { ApiProduct, ApiProductConfig, LinkedService } from '@/types/api-product';

import {
  McpServerConfigPanel,
  AgentApiConfigPanel,
  ModelApiConfigPanel,
  RestApiConfigPanel,
} from './config-panels';
import { McpCustomConfigModal } from './McpCustomConfigModal';
import ToolsConfigEditorModal from '../mcp/ToolsConfigEditorModal';
import { DeployMcpModal } from './deploy-mcp-modal/DeployMcpModal';
import { useMcpConnectionConfig } from './hooks/useMcpConnectionConfig';
import { useMcpMeta } from './hooks/useMcpMeta';
import { useParsedMcpTools } from './hooks/useParsedMcpTools';
import { LinkApiModal } from './link-api-modal/LinkApiModal';

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

  // 域名选择索引
  const [selectedDomainIndex, setSelectedDomainIndex] = useState(0);
  const [selectedAgentDomainIndex, setSelectedAgentDomainIndex] = useState(0);
  const [selectedModelDomainIndex, setSelectedModelDomainIndex] = useState(0);

  // MCP 数据
  const { fetch: fetchMcpMeta, metaList: mcpMetaList } = useMcpMeta();
  const parsedTools = useParsedMcpTools(apiProduct, mcpMetaList);
  const connConfig = useMcpConnectionConfig(
    apiProduct,
    linkedService,
    mcpMetaList,
    selectedDomainIndex,
  );

  // 初始化加载
  useEffect(() => {
    if (apiProduct.type === 'MCP_SERVER') {
      fetchMcpMeta(apiProduct.productId);
    }
  }, [apiProduct.type, apiProduct.productId, fetchMcpMeta]);

  // 产品切换重置域名索引
  useEffect(() => {
    setSelectedDomainIndex(0);
    setSelectedAgentDomainIndex(0);
    setSelectedModelDomainIndex(0);
  }, [apiProduct.productId]);

  // 获取工具列表
  const [fetchingTools, setFetchingTools] = useState(false);
  const handleRefreshTools = async () => {
    const meta = mcpMetaList[0];
    if (!meta?.mcpServerId) return;
    setFetchingTools(true);
    try {
      await mcpServerApi.refreshTools(meta.mcpServerId);
      message.success('工具列表获取成功');
      await fetchMcpMeta(apiProduct.productId);
      await handleRefresh();
    } catch (e: unknown) {
      message.error(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message ||
          '获取工具列表失败',
      );
    } finally {
      setFetchingTools(false);
    }
  };

  // 删除关联
  const handleDelete = () => {
    const isMcp = apiProduct.type === 'MCP_SERVER';
    if (!isMcp && !linkedService) return;

    Modal.confirm({
      content: isMcp
        ? '确定要解除当前MCP配置吗？这将同时删除关联数据和MCP元信息。'
        : '确定要解除与当前API的关联吗？',
      icon: <ExclamationCircleOutlined />,
      onOk() {
        const deletePromise = isMcp
          ? mcpServerApi.deleteMetaByProduct(apiProduct.productId)
          : apiProductApi.deleteApiProductRef(apiProduct.productId);

        return deletePromise
          .then(() => {
            message.success(isMcp ? '解除配置成功' : '解除关联成功');
            onLinkedServiceUpdate(null);
            if (isMcp) {
              fetchMcpMeta(apiProduct.productId);
            }
            handleRefresh();
          })
          .catch(() => {
            message.error(isMcp ? '解除配置失败' : '解除关联失败');
          });
      },
      title: isMcp ? '确认解除配置' : '确认解除关联',
    });
  };

  // Link Modal 控制
  const [isModalVisible, setIsModalVisible] = useState(false);

  // Custom Config Modal 控制
  const [isCustomConfigModalVisible, setIsCustomConfigModalVisible] = useState(false);

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

    if (isMcp && mcpMetaList.length > 0) {
      return (
        <Card
          className="mb-6"
          extra={
            <Space>
              {mcpMetaList[0]?.sandboxRequired && !mcpMetaList[0]?.endpointUrl && (
                <Button
                  icon={<CloudUploadOutlined />}
                  onClick={() => setDeployModalMcpServerId(mcpMetaList[0]?.mcpServerId || null)}
                  type="primary"
                >
                  部署到沙箱
                </Button>
              )}
              {mcpMetaList[0]?.sandboxRequired && mcpMetaList[0]?.endpointUrl && (
                <Button
                  danger
                  onClick={() => {
                    Modal.confirm({
                      cancelText: '返回',
                      content:
                        '取消托管将删除沙箱中的部署实例和连接地址，已订阅的用户将无法继续使用。确定要取消吗？',
                      icon: <ExclamationCircleOutlined />,
                      okButtonProps: { danger: true },
                      okText: '确认取消',
                      onOk: async () => {
                        try {
                          await mcpServerApi.undeploySandbox(mcpMetaList[0]?.mcpServerId || '');
                          message.success('已取消沙箱托管');
                          fetchMcpMeta(apiProduct.productId);
                        } catch {
                          // 错误已由拦截器处理
                        }
                      },
                      title: '确认取消托管',
                    });
                  }}
                >
                  取消托管
                </Button>
              )}
              <Button
                disabled={!!mcpMetaList[0]?.endpointUrl}
                icon={<SettingOutlined />}
                onClick={() => {
                  if (mcpMetaList[0]?.endpointUrl) {
                    message.warning('请先取消沙箱部署后再修改配置');
                    return;
                  }
                  setIsCustomConfigModalVisible(true);
                }}
                title={mcpMetaList[0]?.endpointUrl ? '请先取消沙箱部署后再修改配置' : undefined}
              >
                修改配置
              </Button>
              <Button danger icon={<DeleteOutlined />} onClick={handleDelete} type="primary">
                解除配置
              </Button>
            </Space>
          }
          title="MCP 配置信息"
        >
          {mcpMetaList.map((meta) => (
            <div className="space-y-1" key={meta.mcpServerId}>
              <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                <span className="text-xs text-gray-600">MCP 名称:</span>
                <span className="col-span-2 text-xs text-gray-900 font-mono">{meta.mcpName}</span>
                <span className="text-xs text-gray-600">展示名称:</span>
                <span className="col-span-2 text-xs text-gray-900">{meta.displayName}</span>
              </div>
              <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                <span className="text-xs text-gray-600">协议类型:</span>
                <span className="col-span-2 text-xs text-gray-900">
                  {meta.protocolType?.toUpperCase()}
                </span>
                <span className="text-xs text-gray-600">来源:</span>
                <span className="col-span-2 text-xs text-gray-900">
                  {meta.origin === 'GATEWAY'
                    ? '网关导入'
                    : meta.origin === 'NACOS'
                      ? 'Nacos导入'
                      : meta.origin === 'ADMIN'
                        ? '管理员手动创建'
                        : meta.origin === 'AGENTRUNTIME'
                          ? 'AgentRuntime导入'
                          : meta.origin === 'OPEN_API'
                            ? 'Open API 导入'
                            : '自定义配置'}
                </span>
              </div>
              {meta.repoUrl && (
                <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                  <span className="text-xs text-gray-600">仓库地址:</span>
                  <a
                    className="col-span-5 text-xs text-blue-500 hover:underline truncate"
                    href={meta.repoUrl}
                    rel="noopener noreferrer"
                    target="_blank"
                  >
                    {meta.repoUrl}
                  </a>
                </div>
              )}
              {meta.description && (
                <div className="grid grid-cols-6 gap-8 pt-2 pb-2">
                  <span className="text-xs text-gray-600">描述:</span>
                  <span className="col-span-5 text-xs text-gray-700 leading-relaxed">
                    {meta.description}
                  </span>
                </div>
              )}
              {meta.sandboxRequired && (
                <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                  <span className="text-xs text-gray-600">沙箱托管:</span>
                  <div className="col-span-5 flex items-center gap-2">
                    {meta.endpointUrl ? (
                      <>
                        <Tag
                          className="m-0"
                          color={meta.endpointStatus === 'ACTIVE' ? 'green' : 'default'}
                        >
                          {meta.endpointStatus === 'ACTIVE'
                            ? '运行中'
                            : meta.endpointStatus || '未知'}
                        </Tag>
                        <span className="text-xs text-gray-700 font-mono break-all">
                          {meta.endpointUrl}
                        </span>
                        <CopyOutlined
                          className="text-gray-400 hover:text-blue-600 cursor-pointer transition-colors flex-shrink-0"
                          onClick={async () => {
                            try {
                              await copyToClipboard(meta.endpointUrl || '');
                              message.success('连接地址已复制');
                            } catch {
                              message.error('复制失败');
                            }
                          }}
                          style={{ fontSize: '12px' }}
                        />
                      </>
                    ) : meta.endpointStatus === 'INACTIVE' ? (
                      <>
                        <Tag className="m-0" color="red">
                          部署失败
                        </Tag>
                        <Button
                          className="p-0 text-xs"
                          onClick={() => setDeployModalMcpServerId(meta.mcpServerId || null)}
                          size="small"
                          type="link"
                        >
                          重新部署
                        </Button>
                      </>
                    ) : (
                      <Tag className="m-0" color="default">
                        未部署
                      </Tag>
                    )}
                  </div>
                </div>
              )}
              {meta.tags &&
                (() => {
                  try {
                    const tags = JSON.parse(meta.tags);
                    return Array.isArray(tags) && tags.length > 0 ? (
                      <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                        <span className="text-xs text-gray-600">标签:</span>
                        <div className="col-span-5 flex flex-wrap gap-1">
                          {tags.map((tag: string) => (
                            <Tag className="m-0" color="blue" key={tag}>
                              {tag}
                            </Tag>
                          ))}
                        </div>
                      </div>
                    ) : null;
                  } catch {
                    return null;
                  }
                })()}
              <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                {meta.createdBy && (
                  <>
                    <span className="text-xs text-gray-600">创建人:</span>
                    <span className="col-span-2 text-xs text-gray-900">{meta.createdBy}</span>
                  </>
                )}
                {meta.createAt && (
                  <>
                    <span className="text-xs text-gray-600">创建时间:</span>
                    <span className="col-span-2 text-xs text-gray-700">
                      {formatDateTime(meta.createAt)}
                    </span>
                  </>
                )}
              </div>
            </div>
          ))}
        </Card>
      );
    }

    if (!linkedService || !serviceInfo) {
      return (
        <Card className="mb-6">
          <div className="text-center py-8">
            <div className="text-gray-500 mb-4">
              {isMcp ? '暂未配置MCP Server' : '暂未关联任何API'}
            </div>
            {isMcp ? (
              <div className="max-w-2xl mx-auto">
                <div className="grid grid-cols-2 gap-4 mt-2">
                  <div
                    className="group cursor-pointer rounded-xl border-2 border-dashed border-gray-200 hover:border-blue-400 p-5 transition-all duration-200 hover:bg-blue-50/50"
                    onClick={() => setIsModalVisible(true)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        setIsModalVisible(true);
                      }
                    }}
                    role="button"
                    tabIndex={0}
                  >
                    <div className="w-10 h-10 rounded-lg bg-blue-50 group-hover:bg-blue-100 flex items-center justify-center mx-auto mb-3 transition-colors">
                      <CloudUploadOutlined className="text-blue-500 text-lg" />
                    </div>
                    <div className="font-medium text-sm text-gray-800 mb-1">从网关/Nacos导入</div>
                    <div className="text-xs text-gray-400 leading-relaxed">
                      关联已有网关或 Nacos 中注册的 MCP Server
                    </div>
                  </div>
                  <div
                    className="group cursor-pointer rounded-xl border-2 border-dashed border-gray-200 hover:border-purple-400 p-5 transition-all duration-200 hover:bg-purple-50/50"
                    onClick={() => setIsCustomConfigModalVisible(true)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        setIsCustomConfigModalVisible(true);
                      }
                    }}
                    role="button"
                    tabIndex={0}
                  >
                    <div className="w-10 h-10 rounded-lg bg-purple-50 group-hover:bg-purple-100 flex items-center justify-center mx-auto mb-3 transition-colors">
                      <SettingOutlined className="text-purple-500 text-lg" />
                    </div>
                    <div className="font-medium text-sm text-gray-800 mb-1">自定义数据</div>
                    <div className="text-xs text-gray-400 leading-relaxed">
                      手动配置 MCP Server 的连接信息和工具定义
                    </div>
                  </div>
                </div>
              </div>
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
            {linkedService?.sourceType !== 'CUSTOM' && (
              <>
                <span className="text-xs text-gray-600">
                  {linkedService?.sourceType === 'NACOS' ? 'Nacos ID:' : '网关ID:'}
                </span>
                <span className="col-span-2 text-xs text-gray-700">{serviceInfo.gatewayInfo}</span>
              </>
            )}
          </div>
          {linkedService?.sourceType === 'CUSTOM' && mcpMetaList.length > 0 && (
            <>
              {mcpMetaList.map((meta) => (
                <div key={meta.mcpServerId}>
                  <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                    <span className="text-xs text-gray-600">MCP 名称:</span>
                    <span className="col-span-2 text-xs text-gray-900 font-mono">
                      {meta.mcpName}
                    </span>
                    <span className="text-xs text-gray-600">展示名称:</span>
                    <span className="col-span-2 text-xs text-gray-900">{meta.displayName}</span>
                  </div>
                  <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                    <span className="text-xs text-gray-600">协议类型:</span>
                    <span className="col-span-2 text-xs text-gray-900">
                      {meta.protocolType?.toUpperCase()}
                    </span>
                  </div>
                  {meta.repoUrl && (
                    <div className="grid grid-cols-6 gap-8 items-center pt-2 pb-2">
                      <span className="text-xs text-gray-600">仓库地址:</span>
                      <a
                        className="col-span-5 text-xs text-blue-500 hover:underline truncate"
                        href={meta.repoUrl}
                        rel="noopener noreferrer"
                        target="_blank"
                      >
                        {meta.repoUrl}
                      </a>
                    </div>
                  )}
                </div>
              ))}
            </>
          )}
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
    if (apiProduct.type === 'MCP_SERVER') {
      await fetchMcpMeta(apiProduct.productId);
    }
  }, [apiProduct.productId, apiProduct.type, fetchMcpMeta, handleRefresh, onLinkedServiceUpdate]);

  return (
    <div className="p-6 space-y-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold mb-2">
          {apiProduct.type === 'MCP_SERVER' ? '配置MCP' : 'API关联'}
        </h1>
        <p className="text-gray-600">
          {apiProduct.type === 'MCP_SERVER'
            ? '管理Product关联的MCP Server'
            : '管理Product关联的API'}
        </p>
      </div>

      {renderLinkInfo()}

      {apiProduct.type === 'MCP_SERVER' && (apiProduct.mcpConfig || mcpMetaList.length > 0) && (
        <McpServerConfigPanel
          apiProduct={apiProduct}
          domainOptions={connConfig.domainOptions}
          fetchingTools={fetchingTools}
          hotHttpJson={connConfig.hotHttpJson}
          hotSseJson={connConfig.hotSseJson}
          httpJson={connConfig.httpJson}
          localJson={connConfig.localJson}
          mcpMetaList={mcpMetaList}
          onDomainChange={setSelectedDomainIndex}
          onEditTools={() => setToolsEditorOpen(true)}
          onRefreshTools={handleRefreshTools}
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

      <McpCustomConfigModal
        initialMcpMeta={mcpMetaList.length > 0 ? (mcpMetaList[0] as Record<string, unknown>) : null}
        onCancel={() => setIsCustomConfigModalVisible(false)}
        onOk={async (values: unknown) => {
          const v = values as Record<string, unknown>;
          const iconJson = v.icon
            ? JSON.stringify({ data: v.icon, type: 'BASE64' })
            : v.iconUrl
              ? JSON.stringify({ type: 'URL', url: v.iconUrl })
              : undefined;

          await mcpServerApi.saveMeta({
            connectionConfig: v.mcpConfigJson as string,
            description: v.description as string,
            displayName: v.mcpDisplayName as string,
            extraParams: (v.extraParams as string[])?.length
              ? JSON.stringify(v.extraParams as string[])
              : undefined,
            icon: iconJson,
            mcpName: v.mcpServerName as string,
            origin: 'ADMIN',
            productId: apiProduct.productId,
            protocolType: v.protocolType as string,
            publishStatus: 'DRAFT',
            repoUrl: v.repoUrl as string,
            sandboxRequired: (v.sandboxRequired as boolean) || false,
            serviceIntro: v.serviceIntro as string,
            sourceType: 'config',
            tags: v.tags ? JSON.stringify(v.tags as string[]) : undefined,
            visibility: 'PUBLIC',
          });

          if ((v.sandboxRequired as boolean) && v.deployNow && v.sandboxId) {
            const metaRes = await mcpServerApi.listMetaByProduct(apiProduct.productId);
            const metaList = metaRes?.data || [];
            const newMeta = metaList.find(
              (m: Record<string, unknown>) => m.mcpName === v.mcpServerName,
            );
            if (newMeta?.mcpServerId) {
              await mcpServerApi.deploySandbox(newMeta.mcpServerId, {
                authType: (v.authType as string) || 'none',
                namespace: v.namespace as string,
                paramValues:
                  (v.adminParamValues as Record<string, unknown>) &&
                  Object.keys(v.adminParamValues as Record<string, unknown>).length > 0
                    ? JSON.stringify(v.adminParamValues as Record<string, unknown>)
                    : undefined,
                resourceSpec:
                  (v.cpuRequest as string) ||
                  (v.cpuLimit as string) ||
                  (v.memoryRequest as string) ||
                  (v.memoryLimit as string) ||
                  (v.ephemeralStorage as string)
                    ? JSON.stringify({
                        cpuLimit: (v.cpuLimit as string) || undefined,
                        cpuRequest: (v.cpuRequest as string) || undefined,
                        ephemeralStorage: (v.ephemeralStorage as string) || undefined,
                        memoryLimit: (v.memoryLimit as string) || undefined,
                        memoryRequest: (v.memoryRequest as string) || undefined,
                      })
                    : undefined,
                sandboxId: v.sandboxId as string,
                transportType: (v.transportType as string) || 'sse',
              });

              const maxAttempts = 15;
              let deployed = false;
              for (let i = 0; i < maxAttempts; i++) {
                await new Promise((r) => setTimeout(r, 3000));
                const pollRes = await mcpServerApi.listMetaByProduct(apiProduct.productId);
                const pollList = pollRes?.data || [];
                const activeMeta = (Array.isArray(pollList) ? pollList : []).find(
                  (m) => m.mcpServerId === newMeta.mcpServerId,
                );
                if (activeMeta?.endpointStatus === 'ACTIVE' && activeMeta?.endpointUrl) {
                  message.success('沙箱部署完成');
                  deployed = true;
                  break;
                }
              }
              if (!deployed) {
                message.warning('沙箱部署超时，请稍后刷新页面查看状态');
              }
            }
          }

          setIsCustomConfigModalVisible(false);
          await fetchMcpMeta(apiProduct.productId);
          await handleRefresh();
        }}
        productDescription={apiProduct.description}
        productDocument={apiProduct.document}
        productIcon={apiProduct.icon}
        productName={apiProduct.name}
        visible={isCustomConfigModalVisible}
      />

      <DeployMcpModal
        mcpMetaList={mcpMetaList}
        mcpServerId={deployModalMcpServerId}
        onCancel={() => {
          setDeployModalMcpServerId(null);
        }}
        onDeploySuccess={() => {
          setDeployModalMcpServerId(null);
          fetchMcpMeta(apiProduct.productId);
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
          await fetchMcpMeta(apiProduct.productId);
          await handleRefresh();
        }}
        open={toolsEditorOpen}
      />
    </div>
  );
}
