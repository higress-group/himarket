import { CopyOutlined, RobotOutlined } from '@ant-design/icons';
import { Button, message, Tabs, Collapse, Select } from 'antd';
import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';

import MarkdownRender from '../components/MarkdownRender';
import { ProductDetailLayout } from '../components/ProductDetailLayout';
import APIs, { type IProductDetail } from '../lib/apis';
import { copyToClipboard, formatDomainWithPort } from '../lib/utils';
import { ProductType } from '../types';

import type { IAgentConfig } from '../lib/apis/typing';

const { Panel } = Collapse;

function AgentDetail() {
  const { agentProductId } = useParams();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [data, setData] = useState<IProductDetail>();
  const [agentConfig, setAgentConfig] = useState<IAgentConfig>();
  const [selectedAgentDomainIndex, setSelectedAgentDomainIndex] = useState<number>(0);

  useEffect(() => {
    const fetchDetail = async () => {
      if (!agentProductId) {
        return;
      }
      setLoading(true);
      setError('');
      try {
        const response = await APIs.getProduct({ id: agentProductId });
        if (response.code === 'SUCCESS' && response.data) {
          setData(response.data);

          // 处理Agent配置
          if (response.data.type === ProductType.AGENT_API) {
            const agentProduct = response.data;

            if (agentProduct.agentConfig) {
              setAgentConfig(agentProduct.agentConfig);
            }
          }
        } else {
          setError(response.message || '数据加载失败');
        }
      } catch (error) {
        console.error('API请求失败:', error);
        setError('加载失败，请稍后重试');
      } finally {
        setLoading(false);
      }
    };
    fetchDetail();
  }, [agentProductId]);

  // 当产品切换时重置域名选择索引
  useEffect(() => {
    setSelectedAgentDomainIndex(0);
  }, [data?.productId]);

  // 获取所有唯一域名
  const getAllUniqueDomains = () => {
    if (!agentConfig?.agentAPIConfig?.routes) return [];

    const domainsMap = new Map<string, { domain: string; port?: number; protocol: string }>();

    agentConfig.agentAPIConfig.routes.forEach((route) => {
      if (route.domains && route.domains.length > 0) {
        route.domains.forEach((domain) => {
          const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
          const key = `${domain.protocol}://${formattedDomain}`;
          domainsMap.set(key, domain);
        });
      }
    });

    return Array.from(domainsMap.values());
  };

  const allUniqueDomains = getAllUniqueDomains();

  // 生成域名选择器选项
  const agentDomainOptions = allUniqueDomains.map((domain, index) => {
    const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
    return {
      label: `${domain.protocol.toLowerCase()}://${formattedDomain}`,
      value: index,
    };
  });

  // Helper functions for route display - moved to component level
  const getMatchTypePrefix = (matchType: string) => {
    switch (matchType) {
      case 'Exact':
        return '等于';
      case 'Prefix':
        return '前缀是';
      case 'Regex':
        return '正则是';
      default:
        return '等于';
    }
  };

  const getRouteDisplayText = (
    route: NonNullable<IAgentConfig['agentAPIConfig']['routes']>[0],
    domainIndex: number = 0,
  ) => {
    if (!route.match) return 'Unknown Route';

    const path = route.match.path?.value || '/';
    const pathType = route.match.path?.type;

    // 拼接域名信息 - 使用选择的域名索引
    let domainInfo = '';
    if (allUniqueDomains.length > 0 && allUniqueDomains.length > domainIndex) {
      const selectedDomain = allUniqueDomains[domainIndex];
      if (selectedDomain) {
        const formattedDomain = formatDomainWithPort(
          selectedDomain.domain,
          selectedDomain.port,
          selectedDomain.protocol,
        );
        domainInfo = `${selectedDomain.protocol.toLowerCase()}://${formattedDomain}`;
      }
    } else if (route.domains && route.domains.length > 0) {
      // 回退到路由的第一个域名
      const domain = route.domains[0];
      if (domain) {
        const formattedDomain = formatDomainWithPort(domain.domain, domain.port, domain.protocol);
        domainInfo = `${domain.protocol.toLowerCase()}://${formattedDomain}`;
      }
    }

    // 构建基本路由信息（匹配符号直接加到path后面）
    let pathWithSuffix = path;
    if (pathType === 'Prefix') {
      pathWithSuffix = `${path}*`;
    } else if (pathType === 'Regex') {
      pathWithSuffix = `${path}~`;
    }

    let routeText = `${domainInfo}${pathWithSuffix}`;

    // 添加描述信息
    if (route.description && route.description.trim()) {
      routeText += ` - ${route.description.trim()}`;
    }

    return routeText;
  };

  const getMethodsText = (route: NonNullable<IAgentConfig['agentAPIConfig']['routes']>[0]) => {
    if (!route.match?.methods || route.match.methods.length === 0) {
      return 'ANY';
    }
    return route.match.methods.join(', ');
  };

  const leftContent = data ? (
    <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-6 pt-0">
      <Tabs
        defaultActiveKey="overview"
        items={[
          {
            children: data?.document ? (
              <div className="min-h-[400px]">
                <div className="prose prose-lg">
                  <MarkdownRender content={data.document} />
                </div>
              </div>
            ) : (
              <div className="text-gray-500 text-center py-16">暂无概览信息</div>
            ),
            key: 'overview',
            label: '概览',
          },
          {
            children: agentConfig?.agentAPIConfig ? (
              <div className="space-y-6">
                {/* 协议信息 */}
                {agentConfig.agentAPIConfig.agentProtocols &&
                  agentConfig.agentAPIConfig.agentProtocols.length > 0 && (
                    <div className="p-4 bg-gray-50 rounded-xl">
                      <div className="text-sm text-gray-500 mb-1">支持协议</div>
                      <div className="text-sm font-medium text-gray-900">
                        {agentConfig.agentAPIConfig.agentProtocols.join(', ')}
                      </div>
                    </div>
                  )}

                {/* A2A 协议：额外显示 AgentCard */}
                {agentConfig.agentAPIConfig.agentProtocols?.includes('a2a') &&
                  agentConfig.agentAPIConfig.agentCard && (
                    <div className="p-6 bg-white border border-gray-200 rounded-xl">
                      <h3 className="text-lg font-semibold mb-4 text-gray-900">Agent Card 信息</h3>
                      <div className="space-y-4">
                        {/* 基本信息 */}
                        <div className="grid grid-cols-2 gap-4">
                          <div className="p-3 bg-gray-50 rounded-lg">
                            <div className="text-sm text-gray-500 mb-1">名称</div>
                            <div className="font-medium text-gray-900">
                              {agentConfig.agentAPIConfig.agentCard.name}
                            </div>
                          </div>
                          <div className="p-3 bg-gray-50 rounded-lg">
                            <div className="text-sm text-gray-500 mb-1">版本</div>
                            <div className="font-medium text-gray-900">
                              {agentConfig.agentAPIConfig.agentCard.version}
                            </div>
                          </div>
                        </div>

                        {agentConfig.agentAPIConfig.agentCard.protocolVersion && (
                          <div className="p-3 bg-gray-50 rounded-lg">
                            <div className="text-sm text-gray-500 mb-1">协议版本</div>
                            <div className="font-mono text-sm text-gray-900">
                              {agentConfig.agentAPIConfig.agentCard.protocolVersion}
                            </div>
                          </div>
                        )}

                        {agentConfig.agentAPIConfig.agentCard.description && (
                          <div className="p-3 bg-gray-50 rounded-lg">
                            <div className="text-sm text-gray-500 mb-1">描述</div>
                            <div className="text-gray-900">
                              {agentConfig.agentAPIConfig.agentCard.description}
                            </div>
                          </div>
                        )}

                        {agentConfig.agentAPIConfig.agentCard.url && (
                          <div className="p-3 bg-gray-50 rounded-lg">
                            <div className="text-sm text-gray-500 mb-1">URL</div>
                            <div className="font-mono text-sm text-gray-900">
                              {agentConfig.agentAPIConfig.agentCard.url}
                            </div>
                          </div>
                        )}

                        {agentConfig.agentAPIConfig.agentCard.preferredTransport && (
                          <div className="p-3 bg-gray-50 rounded-lg">
                            <div className="text-sm text-gray-500 mb-1">传输协议</div>
                            <div className="text-gray-900">
                              {agentConfig.agentAPIConfig.agentCard.preferredTransport}
                            </div>
                          </div>
                        )}

                        {/* Additional Interfaces */}
                        {agentConfig.agentAPIConfig.agentCard.additionalInterfaces &&
                          agentConfig.agentAPIConfig.agentCard.additionalInterfaces.length > 0 && (
                            <div>
                              <div className="text-sm text-gray-500 mb-2">附加接口</div>
                              <div className="space-y-2">
                                {agentConfig.agentAPIConfig.agentCard.additionalInterfaces.map(
                                  (
                                    iface: {
                                      transport?: string;
                                      url: string;
                                      [key: string]: unknown;
                                    },
                                    idx: number,
                                  ) => (
                                    <div
                                      className="border border-gray-200 rounded-lg p-3 bg-gray-50"
                                      key={idx}
                                    >
                                      <div className="flex items-center gap-2 mb-1">
                                        <span className="text-xs bg-blue-100 text-blue-700 px-2 py-1 rounded font-medium">
                                          {iface.transport || 'Unknown'}
                                        </span>
                                      </div>
                                      <div className="font-mono text-sm text-gray-700 break-all">
                                        {iface.url}
                                      </div>
                                      {/* 显示其他附加字段 */}
                                      {Object.keys(iface).filter(
                                        (k) => k !== 'transport' && k !== 'url',
                                      ).length > 0 && (
                                        <div className="mt-2 text-xs text-gray-500">
                                          {Object.entries(iface)
                                            .filter(([k]) => k !== 'transport' && k !== 'url')
                                            .map(([k, v]) => (
                                              <div key={k}>
                                                <span className="font-medium">{k}:</span>{' '}
                                                {String(v)}
                                              </div>
                                            ))}
                                        </div>
                                      )}
                                    </div>
                                  ),
                                )}
                              </div>
                            </div>
                          )}

                        {/* Skills */}
                        {agentConfig.agentAPIConfig.agentCard.skills &&
                          agentConfig.agentAPIConfig.agentCard.skills.length > 0 && (
                            <div>
                              <div className="text-sm text-gray-500 mb-2">技能列表</div>
                              <div className="space-y-2">
                                {agentConfig.agentAPIConfig.agentCard.skills.map(
                                  (
                                    skill: {
                                      id: string;
                                      name: string;
                                      description?: string;
                                      tags?: string[];
                                    },
                                    idx: number,
                                  ) => (
                                    <div
                                      className="border border-gray-200 rounded-lg p-3 bg-white"
                                      key={idx}
                                    >
                                      <div className="font-medium text-gray-900">{skill.name}</div>
                                      {skill.description && (
                                        <div className="text-sm text-gray-600 mt-1">
                                          {skill.description}
                                        </div>
                                      )}
                                      {skill.tags && skill.tags.length > 0 && (
                                        <div className="flex gap-2 mt-2 flex-wrap">
                                          {skill.tags.map((tag: string, tagIdx: number) => (
                                            <span
                                              className="text-xs bg-blue-100 text-blue-700 px-2 py-1 rounded"
                                              key={tagIdx}
                                            >
                                              {tag}
                                            </span>
                                          ))}
                                        </div>
                                      )}
                                    </div>
                                  ),
                                )}
                              </div>
                            </div>
                          )}

                        {/* Capabilities */}
                        {agentConfig.agentAPIConfig.agentCard.capabilities && (
                          <div>
                            <div className="text-sm text-gray-500 mb-2">能力</div>
                            <pre className="bg-gray-50 p-3 rounded-lg text-sm overflow-auto text-gray-900">
                              {JSON.stringify(
                                agentConfig.agentAPIConfig.agentCard.capabilities,
                                null,
                                2,
                              )}
                            </pre>
                          </div>
                        )}
                      </div>
                    </div>
                  )}

                {/* 路由配置（如果有）*/}
                {agentConfig.agentAPIConfig.routes &&
                  agentConfig.agentAPIConfig.routes.length > 0 && (
                    <div>
                      <div className="text-sm font-medium text-gray-900 mb-3">路由配置</div>

                      {/* 域名选择器 */}
                      {agentDomainOptions.length > 1 && (
                        <div className="mb-4">
                          <div className="flex border border-gray-300 rounded-md overflow-hidden">
                            <span className="flex-shrink-0 bg-gray-50 px-3 py-2 text-xs text-gray-600 border-r border-gray-300 flex items-center whitespace-nowrap">
                              域名:
                            </span>
                            <div className="flex-1">
                              <Select
                                className="w-full"
                                onChange={setSelectedAgentDomainIndex}
                                placeholder="选择域名"
                                size="middle"
                                value={selectedAgentDomainIndex}
                                variant="borderless"
                              >
                                {agentDomainOptions.map((option) => (
                                  <Select.Option key={option.value} value={option.value}>
                                    <span className="text-sm text-gray-900 font-mono">
                                      {option.label}
                                    </span>
                                  </Select.Option>
                                ))}
                              </Select>
                            </div>
                          </div>
                        </div>
                      )}

                      <div className="border border-gray-200 rounded-xl overflow-hidden bg-white">
                        <Collapse expandIconPosition="end" ghost>
                          {agentConfig.agentAPIConfig.routes.map((route, index) => (
                            <Panel
                              header={
                                <div className="flex items-center justify-between py-3 px-4 hover:bg-gray-50/50 transition-colors">
                                  <div className="flex-1">
                                    <div className="font-mono text-sm font-medium text-blue-600 mb-1">
                                      {getRouteDisplayText(route, selectedAgentDomainIndex)}
                                    </div>
                                    <div className="text-xs text-gray-500">
                                      方法:{' '}
                                      <span className="font-medium text-gray-700">
                                        {getMethodsText(route)}
                                      </span>
                                    </div>
                                  </div>
                                  <Button
                                    className="ml-2"
                                    icon={<CopyOutlined />}
                                    onClick={async (e) => {
                                      e.stopPropagation();
                                      if (
                                        allUniqueDomains.length > 0 &&
                                        allUniqueDomains.length > selectedAgentDomainIndex
                                      ) {
                                        const selectedDomain =
                                          allUniqueDomains[selectedAgentDomainIndex];
                                        if (selectedDomain) {
                                          const path = route.match?.path?.value || '/';
                                          const formattedDomain = formatDomainWithPort(
                                            selectedDomain.domain,
                                            selectedDomain.port,
                                            selectedDomain.protocol,
                                          );
                                          const fullUrl = `${selectedDomain.protocol.toLowerCase()}://${formattedDomain}${path}`;
                                          copyToClipboard(fullUrl).then(() => {
                                            message.success(`链接已复制到剪贴板`);
                                          });
                                        }
                                      } else if (route.domains && route.domains.length > 0) {
                                        const domain = route.domains[0];
                                        if (domain) {
                                          const path = route.match?.path?.value || '/';
                                          const formattedDomain = formatDomainWithPort(
                                            domain.domain,
                                            domain.port,
                                            domain.protocol,
                                          );
                                          const fullUrl = `${domain.protocol.toLowerCase()}://${formattedDomain}${path}`;
                                          copyToClipboard(fullUrl).then(() => {
                                            message.success(`链接已复制到剪贴板`);
                                          });
                                        }
                                      }
                                    }}
                                    size="small"
                                    type="text"
                                  />
                                </div>
                              }
                              key={index}
                              style={{
                                borderBottom:
                                  index < (agentConfig.agentAPIConfig.routes?.length || 0) - 1
                                    ? '1px solid #e5e7eb'
                                    : 'none',
                              }}
                            >
                              <div className="px-4 pb-4 space-y-4">
                                {/* 域名信息 */}
                                <div>
                                  <div className="text-xs text-gray-500 mb-2">域名:</div>
                                  <div className="space-y-1">
                                    {route.domains?.map((domain, domainIndex: number) => {
                                      const formattedDomain = formatDomainWithPort(
                                        domain.domain,
                                        domain.port,
                                        domain.protocol,
                                      );
                                      return (
                                        <div
                                          className="text-sm font-mono text-gray-700 bg-gray-50 px-3 py-2 rounded-lg"
                                          key={domainIndex}
                                        >
                                          {domain.protocol.toLowerCase()}://{formattedDomain}
                                        </div>
                                      );
                                    })}
                                  </div>
                                </div>

                                {/* 匹配规则 */}
                                <div className="grid grid-cols-2 gap-4">
                                  <div className="p-3 bg-gray-50 rounded-lg">
                                    <div className="text-xs text-gray-500 mb-1">路径:</div>
                                    <div className="font-mono text-sm text-gray-900">
                                      {getMatchTypePrefix(route.match?.path?.type)}{' '}
                                      {route.match?.path?.value}
                                    </div>
                                  </div>
                                  <div className="p-3 bg-gray-50 rounded-lg">
                                    <div className="text-xs text-gray-500 mb-1">方法:</div>
                                    <div className="text-sm text-gray-900">
                                      {route.match?.methods
                                        ? route.match.methods.join(', ')
                                        : 'ANY'}
                                    </div>
                                  </div>
                                </div>

                                {/* 请求头匹配 */}
                                {route.match?.headers && route.match.headers.length > 0 && (
                                  <div>
                                    <div className="text-xs text-gray-500 mb-2">请求头匹配:</div>
                                    <div className="space-y-1">
                                      {route.match.headers.map((header, headerIndex: number) => (
                                        <div
                                          className="text-sm font-mono bg-gray-50 px-3 py-2 rounded-lg"
                                          key={headerIndex}
                                        >
                                          {header.name} {getMatchTypePrefix(header.type)}{' '}
                                          {header.value}
                                        </div>
                                      ))}
                                    </div>
                                  </div>
                                )}

                                {/* 查询参数匹配 */}
                                {route.match?.queryParams && route.match.queryParams.length > 0 && (
                                  <div>
                                    <div className="text-xs text-gray-500 mb-2">查询参数匹配:</div>
                                    <div className="space-y-1">
                                      {route.match.queryParams.map((param, paramIndex: number) => (
                                        <div
                                          className="text-sm font-mono bg-gray-50 px-3 py-2 rounded-lg"
                                          key={paramIndex}
                                        >
                                          {param.name} {getMatchTypePrefix(param.type)}{' '}
                                          {param.value}
                                        </div>
                                      ))}
                                    </div>
                                  </div>
                                )}

                                {/* 描述 */}
                                {route.description && (
                                  <div>
                                    <div className="text-xs text-gray-500 mb-1">描述:</div>
                                    <div className="text-sm text-gray-700">{route.description}</div>
                                  </div>
                                )}
                              </div>
                            </Panel>
                          ))}
                        </Collapse>
                      </div>
                    </div>
                  )}
              </div>
            ) : (
              <div className="text-gray-500 text-center py-16">暂无配置信息</div>
            ),
            key: 'configuration',
            label: `配置${agentConfig?.agentAPIConfig?.routes ? ` (${agentConfig.agentAPIConfig.routes.length})` : ''}`,
          },
        ]}
        size="large"
      />
    </div>
  ) : null;

  const rightContent = (
    <div className="bg-white/60 backdrop-blur-sm rounded-2xl border border-white/40 p-6">
      <h3 className="text-base font-semibold text-gray-900 mb-4">Agent调试</h3>
      <div className="text-center py-12">
        <div className="mb-4">
          <RobotOutlined className="text-4xl text-gray-300" />
        </div>
        <div className="text-gray-500 mb-2 text-sm">Agent调试功能</div>
        <div className="text-sm text-gray-400">🚀 敬请期待</div>
      </div>
    </div>
  );

  return (
    <ProductDetailLayout
      error={error || (!data ? '未找到对应的Agent API' : undefined)}
      headerProps={
        data
          ? {
              agentConfig: agentConfig,
              description: data.description,
              icon: data.icon,
              name: data.name,
              productType: 'AGENT_API',
              updatedAt: data.updatedAt,
            }
          : undefined
      }
      leftContent={leftContent}
      loading={loading}
      rightContent={rightContent}
    />
  );
}

export default AgentDetail;
